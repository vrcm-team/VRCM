package io.github.vrcmteam.vrcm.presentation.screens.user

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.playermoderation.ChatboxModerationType
import io.github.vrcmteam.vrcm.network.api.playermoderation.PlayerChatboxModerationApi
import io.github.vrcmteam.vrcm.network.api.playermoderation.PlayerChatboxModerationData
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal sealed interface PlayerChatboxModerationState {
    data object Unavailable : PlayerChatboxModerationState
    data object Checking : PlayerChatboxModerationState
    data class Failed(val cause: Throwable) : PlayerChatboxModerationState

    data class Ready(
        val isMuted: Boolean,
        internal val targetUserId: String,
        internal val sessionToken: AccountSessionToken,
        internal val activeTypes: Set<ChatboxModerationType>,
    ) : PlayerChatboxModerationState

    data class Updating(val willMute: Boolean) : PlayerChatboxModerationState
}

private data class ChatboxModerationContext(
    val targetUserId: String,
    val sessionToken: AccountSessionToken,
)

private data class ChatboxModerationSnapshot(
    val isMuted: Boolean,
    val activeTypes: Set<ChatboxModerationType>,
)

/**
 * Keeps Chatbox overrides scoped to the exact account session and profile target that requested
 * them. A failed paired update always reloads the remote state instead of restoring a stale value.
 */
internal class PlayerChatboxModerationController(
    initialTargetUserId: String,
    private val authService: AuthService,
    private val moderationApi: PlayerChatboxModerationApi,
    private val scope: CoroutineScope,
) {
    private val targetUserId = atomic(initialTargetUserId)
    private val refreshPending = atomic(false)
    private val refreshWorkerRunning = atomic(false)
    private val mutationRunning = atomic(false)
    private val _state = MutableStateFlow<PlayerChatboxModerationState>(
        currentContext()?.let { PlayerChatboxModerationState.Checking }
            ?: PlayerChatboxModerationState.Unavailable,
    )
    val state: StateFlow<PlayerChatboxModerationState> = _state.asStateFlow()

    init {
        requestRefresh()
        scope.launch {
            var observedToken = SharedFlowCentre.currentSession.value?.token
            SharedFlowCentre.currentSession.collect { session ->
                val nextToken = session?.token
                if (nextToken != observedToken) {
                    observedToken = nextToken
                    requestRefresh()
                }
            }
        }
    }

    fun setTargetUserId(userId: String) {
        if (targetUserId.getAndSet(userId) != userId) requestRefresh()
    }

    fun retry() {
        if (_state.value is PlayerChatboxModerationState.Failed) requestRefresh()
    }

    fun toggle(
        onSuccess: suspend (isMuted: Boolean) -> Unit,
        onFailure: suspend (Throwable) -> Unit,
    ) {
        val ready = _state.value as? PlayerChatboxModerationState.Ready ?: return
        val context = currentContext()
        if (context?.targetUserId != ready.targetUserId ||
            context.sessionToken != ready.sessionToken
        ) {
            requestRefresh()
            return
        }
        if (!mutationRunning.compareAndSet(expect = false, update = true)) return

        val desiredType = if (ready.isMuted) {
            ChatboxModerationType.UnmuteChat
        } else {
            ChatboxModerationType.MuteChat
        }
        if (!_state.compareAndSet(
                expect = ready,
                update = PlayerChatboxModerationState.Updating(
                    willMute = desiredType == ChatboxModerationType.MuteChat,
                ),
            )
        ) {
            mutationRunning.value = false
            startRefreshWorkerIfNeeded()
            return
        }

        scope.launch {
            try {
                val response = authService.runSessionBoundCatching(ready.sessionToken) {
                    ChatboxModerationType.entries
                        .filter { it in ready.activeTypes }
                        .forEach { moderationApi.remove(ready.targetUserId, it) }
                    moderationApi.moderate(ready.targetUserId, desiredType).also { created ->
                        check(
                            created.targetUserId == ready.targetUserId &&
                                created.type == desiredType.apiValue
                        ) {
                            "Chatbox moderation response did not match the request"
                        }
                    }
                }

                val acceptedResponse = response?.takeIf {
                    isCurrentResponse(ready.targetUserId, it.sessionToken)
                }
                if (acceptedResponse == null) {
                    requestRefresh()
                    return@launch
                }

                acceptedResponse.result.fold(
                    onSuccess = {
                        if (isMutationResponseCurrent(
                                ready.targetUserId,
                                acceptedResponse.sessionToken,
                            )
                        ) {
                            refreshPending.value = false
                            _state.value = PlayerChatboxModerationState.Ready(
                                isMuted = desiredType == ChatboxModerationType.MuteChat,
                                targetUserId = ready.targetUserId,
                                sessionToken = acceptedResponse.sessionToken,
                                activeTypes = setOf(desiredType),
                            )
                            if (isCurrentResponse(
                                    ready.targetUserId,
                                    acceptedResponse.sessionToken,
                                )
                            ) {
                                onSuccess(desiredType == ChatboxModerationType.MuteChat)
                            } else {
                                requestRefresh()
                            }
                        } else {
                            requestRefresh()
                        }
                    },
                    onFailure = { error ->
                        if (isMutationResponseCurrent(
                                ready.targetUserId,
                                acceptedResponse.sessionToken,
                            )
                        ) {
                            refreshPending.value = true
                            _state.value = PlayerChatboxModerationState.Checking
                            if (isMutationResponseCurrent(
                                    ready.targetUserId,
                                    acceptedResponse.sessionToken,
                                )
                            ) {
                                onFailure(error)
                            } else {
                                requestRefresh()
                            }
                        } else {
                            requestRefresh()
                        }
                    },
                )
            } finally {
                mutationRunning.value = false
                startRefreshWorkerIfNeeded()
            }
        }
    }

    private fun requestRefresh() {
        val context = currentContext()
        if (context == null) {
            refreshPending.value = false
            _state.value = PlayerChatboxModerationState.Unavailable
            return
        }
        refreshPending.value = true
        _state.value = PlayerChatboxModerationState.Checking
        startRefreshWorkerIfNeeded()
    }

    private fun startRefreshWorkerIfNeeded() {
        if (!refreshPending.value || mutationRunning.value) return
        if (!refreshWorkerRunning.compareAndSet(expect = false, update = true)) return
        scope.launch {
            try {
                drainRefreshes()
            } finally {
                refreshWorkerRunning.value = false
                if (refreshPending.value && !mutationRunning.value) {
                    startRefreshWorkerIfNeeded()
                }
            }
        }
    }

    private suspend fun drainRefreshes() {
        while (refreshPending.getAndSet(false)) {
            if (mutationRunning.value) {
                refreshPending.value = true
                return
            }
            val context = currentContext()
            if (context == null) {
                _state.value = PlayerChatboxModerationState.Unavailable
                continue
            }

            _state.value = PlayerChatboxModerationState.Checking
            val response = authService.runSessionBoundCatching(context.sessionToken) {
                moderationApi.getForTarget(context.targetUserId)
            }
            val acceptedResponse = response?.takeIf {
                isCurrentResponse(context.targetUserId, it.sessionToken)
            }
            if (acceptedResponse == null) {
                if (currentContext() != null) refreshPending.value = true
                continue
            }

            acceptedResponse.result.fold(
                onSuccess = { moderations ->
                    val snapshot = moderations.chatboxSnapshot(context.targetUserId)
                    refreshPending.value = false
                    _state.value = PlayerChatboxModerationState.Ready(
                        isMuted = snapshot.isMuted,
                        targetUserId = context.targetUserId,
                        sessionToken = acceptedResponse.sessionToken,
                        activeTypes = snapshot.activeTypes,
                    )
                },
                onFailure = { error ->
                    _state.value = PlayerChatboxModerationState.Failed(error)
                },
            )
        }
    }

    private fun currentContext(): ChatboxModerationContext? {
        val session = SharedFlowCentre.currentSession.value ?: return null
        val target = targetUserId.value
        if (target == session.account.userId) return null
        return ChatboxModerationContext(target, session.token)
    }

    private fun isCurrentResponse(
        expectedTargetUserId: String,
        responseToken: AccountSessionToken?,
    ): Boolean {
        val current = currentContext() ?: return false
        return responseToken != null &&
            current.targetUserId == expectedTargetUserId &&
            current.sessionToken == responseToken
    }

    private fun isMutationResponseCurrent(
        expectedTargetUserId: String,
        responseToken: AccountSessionToken?,
    ): Boolean = isCurrentResponse(expectedTargetUserId, responseToken) &&
        (_state.value is PlayerChatboxModerationState.Updating ||
            _state.value is PlayerChatboxModerationState.Checking)
}

@OptIn(ExperimentalTime::class)
private fun List<PlayerChatboxModerationData>.chatboxSnapshot(
    targetUserId: String,
): ChatboxModerationSnapshot {
    val matching = mapNotNull { moderation ->
        val type = ChatboxModerationType.fromApiValue(moderation.type) ?: return@mapNotNull null
        if (moderation.targetUserId != targetUserId) return@mapNotNull null
        moderation to type
    }
    val activeTypes = matching.mapTo(linkedSetOf()) { it.second }
    val timestamps = matching.map { (moderation, _) ->
        runCatching { Instant.parse(moderation.created) }.getOrNull()
    }
    val latestType = if (timestamps.all { it != null }) {
        matching.indices.maxWithOrNull(
            compareBy<Int> { timestamps[it] }
                .thenBy { it },
        )?.let { matching[it].second }
    } else {
        matching.lastOrNull()?.second
    }
    return ChatboxModerationSnapshot(
        isMuted = latestType == ChatboxModerationType.MuteChat,
        activeTypes = activeTypes,
    )
}
