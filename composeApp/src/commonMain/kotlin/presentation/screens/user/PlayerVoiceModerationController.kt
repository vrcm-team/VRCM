package io.github.vrcmteam.vrcm.presentation.screens.user

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.playermoderation.PlayerModerationApi
import io.github.vrcmteam.vrcm.network.api.playermoderation.PlayerModerationData
import io.github.vrcmteam.vrcm.network.api.playermoderation.VoiceModerationType
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

internal sealed interface PlayerVoiceModerationState {
    data object Unavailable : PlayerVoiceModerationState
    data class Checking(internal val targetUserId: String) : PlayerVoiceModerationState
    data class Failed(val cause: Throwable) : PlayerVoiceModerationState

    data class Ready(
        val isMuted: Boolean,
        internal val targetUserId: String,
        internal val sessionToken: AccountSessionToken,
        internal val activeTypes: Set<VoiceModerationType>,
    ) : PlayerVoiceModerationState

    data class Updating(val willMute: Boolean) : PlayerVoiceModerationState
}

private data class VoiceModerationContext(
    val targetUserId: String,
    val sessionToken: AccountSessionToken,
)

private data class VoiceModerationSnapshot(
    val isMuted: Boolean,
    val activeTypes: Set<VoiceModerationType>,
)

private data class DatedVoiceModeration(
    val type: VoiceModerationType,
    val createdAtMillis: Long?,
    val responseIndex: Int,
)

/**
 * Keeps player voice overrides scoped to the exact account session and profile target that
 * requested them. Checks are coalesced so a context change cannot strand the UI in loading.
 */
internal class PlayerVoiceModerationController(
    initialTargetUserId: String,
    private val authService: AuthService,
    private val playerModerationApi: PlayerModerationApi,
    private val scope: CoroutineScope,
) {
    private val targetUserId = atomic(initialTargetUserId)
    private val pendingRefresh = atomic<VoiceModerationContext?>(null)
    private val refreshWorkerRunning = atomic(false)
    private val mutationRunning = atomic(false)
    private val _state = MutableStateFlow<PlayerVoiceModerationState>(
        currentContext()?.let { PlayerVoiceModerationState.Checking(it.targetUserId) }
            ?: PlayerVoiceModerationState.Unavailable,
    )
    val state: StateFlow<PlayerVoiceModerationState> = _state.asStateFlow()

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
        if (_state.value is PlayerVoiceModerationState.Failed) requestRefresh()
    }

    fun toggle(
        onSuccess: suspend (isMuted: Boolean) -> Unit,
        onFailure: suspend (Throwable) -> Unit,
    ) {
        val ready = _state.value as? PlayerVoiceModerationState.Ready ?: return
        val currentContext = currentContext()
        if (currentContext?.targetUserId != ready.targetUserId ||
            currentContext.sessionToken != ready.sessionToken
        ) {
            requestRefresh()
            return
        }
        if (!mutationRunning.compareAndSet(expect = false, update = true)) return

        val desiredType = if (ready.isMuted) {
            VoiceModerationType.Unmute
        } else {
            VoiceModerationType.Mute
        }
        val updating = PlayerVoiceModerationState.Updating(
            willMute = desiredType == VoiceModerationType.Mute,
        )
        if (!_state.compareAndSet(
                expect = ready,
                update = updating,
            )
        ) {
            mutationRunning.value = false
            startRefreshWorkerIfNeeded()
            return
        }

        scope.launch {
            try {
                val response = authService.runSessionBoundCatching(ready.sessionToken) {
                    VoiceModerationType.entries
                        .filter { it in ready.activeTypes }
                        .forEach { playerModerationApi.remove(ready.targetUserId, it) }
                    playerModerationApi.moderate(ready.targetUserId, desiredType).also { created ->
                        check(created.targetUserId == ready.targetUserId &&
                            created.type == desiredType.apiValue
                        ) {
                            "Voice moderation response did not match the request"
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

                val responseContext = VoiceModerationContext(
                    targetUserId = ready.targetUserId,
                    sessionToken = acceptedResponse.sessionToken,
                )
                acceptedResponse.result.fold(
                    onSuccess = {
                        val committed = commitMutationState(
                            updating = updating,
                            responseContext = responseContext,
                            next = PlayerVoiceModerationState.Ready(
                                isMuted = desiredType == VoiceModerationType.Mute,
                                targetUserId = ready.targetUserId,
                                sessionToken = acceptedResponse.sessionToken,
                                activeTypes = setOf(desiredType),
                            ),
                        )
                        if (committed) {
                            discardPendingRefresh(responseContext)
                            onSuccess(desiredType == VoiceModerationType.Mute)
                        } else {
                            requestRefresh()
                        }
                    },
                    onFailure = { error ->
                        val claimed = commitMutationState(
                            updating = updating,
                            responseContext = responseContext,
                            next = PlayerVoiceModerationState.Checking(ready.targetUserId),
                        )
                        val refreshContext = requestRefresh()
                        if (claimed &&
                            refreshContext == responseContext &&
                            currentContext() == responseContext
                        ) {
                            onFailure(error)
                        }
                    },
                )
            } finally {
                mutationRunning.value = false
                startRefreshWorkerIfNeeded()
            }
        }
    }

    private fun requestRefresh(): VoiceModerationContext? {
        val context = currentContext()
        pendingRefresh.value = context
        if (context == null) {
            _state.value = PlayerVoiceModerationState.Unavailable
            return null
        }
        _state.value = PlayerVoiceModerationState.Checking(context.targetUserId)
        startRefreshWorkerIfNeeded()
        return context
    }

    private fun startRefreshWorkerIfNeeded() {
        if (pendingRefresh.value == null || mutationRunning.value) return
        if (!refreshWorkerRunning.compareAndSet(expect = false, update = true)) return
        scope.launch {
            try {
                drainRefreshes()
            } finally {
                refreshWorkerRunning.value = false
                if (pendingRefresh.value != null && !mutationRunning.value) {
                    startRefreshWorkerIfNeeded()
                }
            }
        }
    }

    private suspend fun drainRefreshes() {
        while (true) {
            if (mutationRunning.value) {
                return
            }
            pendingRefresh.getAndSet(null) ?: return
            val context = currentContext()
            if (context == null) {
                _state.value = PlayerVoiceModerationState.Unavailable
                continue
            }

            val checking = PlayerVoiceModerationState.Checking(context.targetUserId)
            _state.value = checking
            val response = authService.runSessionBoundCatching(context.sessionToken) {
                playerModerationApi.getForTarget(context.targetUserId)
            }
            val acceptedResponse = response?.takeIf {
                isCurrentResponse(context.targetUserId, it.sessionToken)
            }
            if (acceptedResponse == null) {
                if (currentContext() != null && pendingRefresh.value == null) requestRefresh()
                continue
            }

            val responseContext = VoiceModerationContext(
                targetUserId = context.targetUserId,
                sessionToken = acceptedResponse.sessionToken,
            )
            acceptedResponse.result.fold(
                onSuccess = { moderations ->
                    val snapshot = moderations.voiceSnapshot(context.targetUserId)
                    val committed = _state.compareAndSet(
                        expect = checking,
                        update = PlayerVoiceModerationState.Ready(
                            isMuted = snapshot.isMuted,
                            targetUserId = context.targetUserId,
                            sessionToken = acceptedResponse.sessionToken,
                            activeTypes = snapshot.activeTypes,
                        ),
                    )
                    if (committed) discardPendingRefresh(responseContext)
                },
                onFailure = { error ->
                    val committed = _state.compareAndSet(
                        expect = checking,
                        update = PlayerVoiceModerationState.Failed(error),
                    )
                    if (committed) discardPendingRefresh(responseContext)
                },
            )
        }
    }

    private fun commitMutationState(
        updating: PlayerVoiceModerationState.Updating,
        responseContext: VoiceModerationContext,
        next: PlayerVoiceModerationState,
    ): Boolean {
        while (currentContext() == responseContext) {
            val expected = when (val state = _state.value) {
                updating -> state
                PlayerVoiceModerationState.Checking(responseContext.targetUserId) -> state
                else -> return false
            }
            if (_state.compareAndSet(expected, next)) return true
        }
        return false
    }

    private fun discardPendingRefresh(responseContext: VoiceModerationContext) {
        val pending = pendingRefresh.value ?: return
        if (pending == responseContext) pendingRefresh.compareAndSet(pending, null)
    }

    private fun currentContext(): VoiceModerationContext? {
        val session = SharedFlowCentre.currentSession.value ?: return null
        val target = targetUserId.value
        if (target == session.account.userId) return null
        return VoiceModerationContext(target, session.token)
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
}

@OptIn(ExperimentalTime::class)
private fun List<PlayerModerationData>.voiceSnapshot(targetUserId: String): VoiceModerationSnapshot {
    val matching = mapNotNull { moderation ->
        val type = VoiceModerationType.fromApiValue(moderation.type) ?: return@mapNotNull null
        if (moderation.targetUserId != targetUserId) return@mapNotNull null
        moderation to type
    }
    val activeTypes = matching.mapTo(linkedSetOf()) { it.second }
    val dated = matching.mapIndexed { index, (moderation, type) ->
        DatedVoiceModeration(
            type = type,
            createdAtMillis = runCatching {
                Instant.parse(moderation.created).toEpochMilliseconds()
            }.getOrNull(),
            responseIndex = index,
        )
    }
    val latestType = if (dated.all { it.createdAtMillis != null }) {
        dated.maxWithOrNull(
            compareBy<DatedVoiceModeration> { it.createdAtMillis }
                .thenBy { it.responseIndex },
        )?.type
    } else {
        dated.lastOrNull()?.type
    }
    return VoiceModerationSnapshot(
        isMuted = latestType == VoiceModerationType.Mute,
        activeTypes = activeTypes,
    )
}
