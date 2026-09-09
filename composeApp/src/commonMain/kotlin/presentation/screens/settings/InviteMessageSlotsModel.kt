package io.github.vrcmteam.vrcm.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.invite.InviteApi
import io.github.vrcmteam.vrcm.network.api.invite.inviteMessageCodePointCount
import io.github.vrcmteam.vrcm.network.api.invite.data.InviteMessageData
import io.github.vrcmteam.vrcm.network.api.invite.data.InviteMessageType
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class InviteMessageSession(
    val userId: String,
    val generation: Long,
)

internal data class InviteMessageSourceResult<T>(
    val result: Result<T>,
    val session: InviteMessageSession,
)

internal interface InviteMessageSlotsSource {
    val sessions: Flow<InviteMessageSession?>

    fun isCurrent(session: InviteMessageSession): Boolean

    suspend fun load(
        session: InviteMessageSession,
        messageType: InviteMessageType,
    ): InviteMessageSourceResult<List<InviteMessageData>>?

    suspend fun update(
        session: InviteMessageSession,
        messageType: InviteMessageType,
        slot: Int,
        message: String,
    ): InviteMessageSourceResult<List<InviteMessageData>>?

    suspend fun reset(
        session: InviteMessageSession,
        messageType: InviteMessageType,
        slot: Int,
    ): InviteMessageSourceResult<List<InviteMessageData>>?
}

internal class AuthenticatedInviteMessageSlotsSource(
    private val authService: AuthService,
    private val inviteApi: InviteApi,
) : InviteMessageSlotsSource {
    override val sessions: Flow<InviteMessageSession?> =
        SharedFlowCentre.currentSession
            .map { it?.token?.toInviteMessageSession() }
            .distinctUntilChanged()

    override fun isCurrent(session: InviteMessageSession): Boolean =
        SharedFlowCentre.isCurrentSession(session.toAccountSessionToken())

    override suspend fun load(
        session: InviteMessageSession,
        messageType: InviteMessageType,
    ) = runForSession(session) {
        inviteApi.getInviteMessages(session.userId, messageType)
    }

    override suspend fun update(
        session: InviteMessageSession,
        messageType: InviteMessageType,
        slot: Int,
        message: String,
    ) = runForSession(session) {
        inviteApi.updateInviteMessage(session.userId, messageType, slot, message)
    }

    override suspend fun reset(
        session: InviteMessageSession,
        messageType: InviteMessageType,
        slot: Int,
    ) = runForSession(session) {
        inviteApi.resetInviteMessage(session.userId, messageType, slot)
    }

    private suspend fun <T> runForSession(
        session: InviteMessageSession,
        request: suspend () -> T,
    ): InviteMessageSourceResult<T>? =
        authService.runSessionBoundCatching(session.toAccountSessionToken(), request)
            ?.let { response ->
                InviteMessageSourceResult(
                    result = response.result,
                    session = response.sessionToken.toInviteMessageSession(),
                )
            }
}

internal enum class InviteMessageEditValidation {
    Valid,
    Blank,
    TooLong,
    Unchanged,
}

internal fun validateInviteMessage(
    value: String,
    original: String,
): InviteMessageEditValidation {
    val normalized = value.trim()
    return when {
        normalized.isEmpty() -> InviteMessageEditValidation.Blank
        normalized.inviteMessageCodePointCount() > InviteApi.MAX_INVITE_MESSAGE_CODE_POINTS ->
            InviteMessageEditValidation.TooLong
        normalized == original -> InviteMessageEditValidation.Unchanged
        else -> InviteMessageEditValidation.Valid
    }
}

internal enum class InviteMessageFeedbackKind {
    Updated,
    Reset,
    Cooldown,
    UpdateFailed,
    ResetFailed,
}

internal data class InviteMessageFeedback(
    val id: Long,
    val kind: InviteMessageFeedbackKind,
)

internal enum class InviteMessageMutationKind {
    Update,
    Reset,
}

internal data class PendingInviteMessageMutation(
    val messageType: InviteMessageType,
    val slot: Int,
    val kind: InviteMessageMutationKind,
)

internal data class InviteMessageSlotsUiState(
    val session: InviteMessageSession? = null,
    val selectedType: InviteMessageType = InviteMessageType.Message,
    val messages: List<InviteMessageData> = emptyList(),
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val pendingMutation: PendingInviteMessageMutation? = null,
    val feedback: InviteMessageFeedback? = null,
)

internal class InviteMessageSlotsModel(
    private val source: InviteMessageSlotsSource,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow(InviteMessageSlotsUiState())
    val state: StateFlow<InviteMessageSlotsUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var mutationJob: Job? = null
    private var loadRequestId = 0L
    private var mutationRequestId = 0L
    private var feedbackId = 0L

    init {
        viewModelScope.launch {
            source.sessions.collect { session -> activateSession(session) }
        }
    }

    fun selectType(messageType: InviteMessageType) {
        val current = _state.value
        if (current.selectedType == messageType) return
        _state.value = current.copy(
            selectedType = messageType,
            messages = emptyList(),
            loadFailed = false,
        )
        current.session?.takeIf(source::isCurrent)?.let { session ->
            startLoad(session, messageType)
        }
    }

    fun retry() {
        val current = _state.value
        if (current.isLoading || !current.loadFailed) return
        current.session?.takeIf(source::isCurrent)?.let { session ->
            startLoad(session, current.selectedType)
        }
    }

    fun updateMessage(slot: Int, value: String): Boolean {
        val current = _state.value
        val item = current.messages.firstOrNull { it.slot == slot } ?: return false
        if (validateInviteMessage(value, item.message) != InviteMessageEditValidation.Valid) return false
        val normalized = value.trim()
        return startMutation(item, InviteMessageMutationKind.Update) { session, messageType ->
            source.update(session, messageType, slot, normalized)
        }
    }

    fun resetMessage(slot: Int): Boolean {
        val item = _state.value.messages.firstOrNull { it.slot == slot } ?: return false
        return startMutation(item, InviteMessageMutationKind.Reset) { session, messageType ->
            source.reset(session, messageType, slot)
        }
    }

    fun clearFeedback(id: Long) {
        val current = _state.value
        if (current.feedback?.id == id) _state.value = current.copy(feedback = null)
    }

    private fun activateSession(session: InviteMessageSession?) {
        val current = _state.value
        val previousSession = current.session
        if (session == previousSession) return
        if (session != null &&
            previousSession != null &&
            session.userId == previousSession.userId
        ) {
            _state.value = current.copy(session = session)
            if (loadJob?.isActive != true && mutationJob?.isActive != true) {
                startLoad(session, current.selectedType)
            }
            return
        }

        loadRequestId++
        mutationRequestId++
        loadJob?.cancel()
        mutationJob?.cancel()
        val selectedType = current.selectedType
        _state.value = InviteMessageSlotsUiState(
            session = session,
            selectedType = selectedType,
            isLoading = session != null,
        )
        session?.let { startLoad(it, selectedType) }
    }

    private fun startLoad(
        session: InviteMessageSession,
        messageType: InviteMessageType,
    ) {
        if (!source.isCurrent(session)) return
        val requestId = ++loadRequestId
        loadJob?.cancel()
        _state.value = _state.value.copy(
            messages = emptyList(),
            isLoading = true,
            loadFailed = false,
        )
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val response = try {
                withContext(workerDispatcher) { source.load(session, messageType) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                InviteMessageSourceResult(Result.failure(error), session)
            }
            if (requestId != loadRequestId) return@launch
            val current = _state.value
            if (current.session?.userId != session.userId || current.selectedType != messageType) {
                return@launch
            }
            if (response == null ||
                response.session.userId != session.userId ||
                !source.isCurrent(response.session)
            ) {
                recoverStaleLoad(requestId, session.userId, messageType)
                return@launch
            }
            _state.value = response.result.fold(
                onSuccess = { messages ->
                    current.copy(
                        session = response.session,
                        messages = messages.sortedBy(InviteMessageData::slot),
                        isLoading = false,
                        loadFailed = false,
                    )
                },
                onFailure = {
                    current.copy(
                        session = response.session,
                        messages = emptyList(),
                        isLoading = false,
                        loadFailed = true,
                    )
                },
            )
        }
        loadJob = job
        job.start()
    }

    private fun startMutation(
        item: InviteMessageData,
        kind: InviteMessageMutationKind,
        request: suspend (
            session: InviteMessageSession,
            messageType: InviteMessageType,
        ) -> InviteMessageSourceResult<List<InviteMessageData>>?,
    ): Boolean {
        val current = _state.value
        val session = current.session ?: return false
        if (!source.isCurrent(session) || current.pendingMutation != null) return false
        if (!item.canBeUpdated || item.remainingCooldownMinutes > 0) {
            publishFeedback(InviteMessageFeedbackKind.Cooldown)
            return false
        }
        val mutation = PendingInviteMessageMutation(current.selectedType, item.slot, kind)
        _state.value = current.copy(pendingMutation = mutation)
        val requestId = ++mutationRequestId
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val response = try {
                    withContext(workerDispatcher) { request(session, mutation.messageType) }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Throwable) {
                    InviteMessageSourceResult(Result.failure(error), session)
                }
                if (requestId != mutationRequestId) return@launch
                val latest = _state.value
                if (latest.session?.userId != session.userId || latest.pendingMutation != mutation) {
                    return@launch
                }
                if (response == null ||
                    response.session.userId != session.userId ||
                    !source.isCurrent(response.session)
                ) {
                    recoverStaleMutation(requestId, session.userId, mutation)
                    return@launch
                }
                response.result.fold(
                    onSuccess = { messages ->
                        if (latest.selectedType == mutation.messageType) {
                            loadRequestId++
                            loadJob?.cancel()
                        }
                        _state.value = latest.copy(
                            session = response.session,
                            messages = if (latest.selectedType == mutation.messageType) {
                                messages.sortedBy(InviteMessageData::slot)
                            } else {
                                latest.messages
                            },
                            pendingMutation = null,
                            feedback = nextFeedback(
                                if (kind == InviteMessageMutationKind.Update) {
                                    InviteMessageFeedbackKind.Updated
                                } else {
                                    InviteMessageFeedbackKind.Reset
                                }
                            ),
                        )
                    },
                    onFailure = { error ->
                        _state.value = latest.copy(
                            session = response.session,
                            pendingMutation = null,
                            feedback = nextFeedback(error.toFeedback(kind)),
                        )
                    },
                )
            } finally {
                val latest = _state.value
                if (requestId == mutationRequestId &&
                    latest.session == session &&
                    latest.pendingMutation == mutation
                ) {
                    _state.value = latest.copy(pendingMutation = null)
                }
            }
        }
        mutationJob = job
        job.start()
        return true
    }

    private fun recoverStaleLoad(
        requestId: Long,
        userId: String,
        messageType: InviteMessageType,
    ) {
        val current = _state.value
        if (requestId != loadRequestId ||
            current.session?.userId != userId ||
            current.selectedType != messageType
        ) {
            return
        }
        loadRequestId++
        loadJob = null
        val activeSession = current.session
        if (source.isCurrent(activeSession)) {
            startLoad(activeSession, messageType)
        } else {
            _state.value = current.copy(isLoading = false, loadFailed = false)
        }
    }

    private fun recoverStaleMutation(
        requestId: Long,
        userId: String,
        mutation: PendingInviteMessageMutation,
    ) {
        val current = _state.value
        if (requestId != mutationRequestId ||
            current.session?.userId != userId ||
            current.pendingMutation != mutation
        ) {
            return
        }
        mutationRequestId++
        mutationJob = null
        _state.value = current.copy(pendingMutation = null)
        val activeSession = current.session
        if (source.isCurrent(activeSession)) {
            startLoad(activeSession, current.selectedType)
        }
    }

    private fun publishFeedback(kind: InviteMessageFeedbackKind) {
        _state.value = _state.value.copy(feedback = nextFeedback(kind))
    }

    private fun nextFeedback(kind: InviteMessageFeedbackKind) =
        InviteMessageFeedback(id = ++feedbackId, kind = kind)
}

private fun Throwable.toFeedback(kind: InviteMessageMutationKind): InviteMessageFeedbackKind =
    if (this is VRCApiException && code == 429) {
        InviteMessageFeedbackKind.Cooldown
    } else if (kind == InviteMessageMutationKind.Update) {
        InviteMessageFeedbackKind.UpdateFailed
    } else {
        InviteMessageFeedbackKind.ResetFailed
    }

private fun AccountSessionToken.toInviteMessageSession() =
    InviteMessageSession(userId = userId, generation = generation)

private fun InviteMessageSession.toAccountSessionToken() =
    AccountSessionToken(userId = userId, generation = generation)
