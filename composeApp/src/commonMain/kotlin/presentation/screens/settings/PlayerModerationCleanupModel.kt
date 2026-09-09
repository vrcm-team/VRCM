package io.github.vrcmteam.vrcm.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.playermoderation.data.PlayerModerationData
import io.github.vrcmteam.vrcm.network.api.playermoderation.data.PlayerModerationType
import io.github.vrcmteam.vrcm.service.PlayerModerationCleanupResponse
import io.github.vrcmteam.vrcm.service.PlayerModerationCleanupSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerModerationTypeCount(
    val type: PlayerModerationType,
    val targetCount: Int,
)

enum class PlayerModerationCleanupResultKind {
    Success,
    NoRecords,
    PartialFailure,
    Failure,
}

data class PlayerModerationCleanupResult(
    val kind: PlayerModerationCleanupResultKind,
    val removedCount: Int,
    val failedCount: Int,
)

data class PlayerModerationCleanupState(
    val availableTypes: List<PlayerModerationTypeCount> = emptyList(),
    val selectedType: PlayerModerationType? = null,
    val sessionToken: AccountSessionToken? = SharedFlowCentre.currentSession.value?.token,
    val isSessionAvailable: Boolean = sessionToken != null,
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val loadFailed: Boolean = false,
    val isClearing: Boolean = false,
    val processedCount: Int = 0,
    val totalCount: Int = 0,
    val result: PlayerModerationCleanupResult? = null,
)

/** Coordinates a confirmed, session-bound cleanup for one supported override type. */
class PlayerModerationCleanupModel(
    private val source: PlayerModerationCleanupSource,
) : ViewModel() {
    private val _state = MutableStateFlow(PlayerModerationCleanupState())
    val state: StateFlow<PlayerModerationCleanupState> = _state.asStateFlow()

    private var activeSessionToken: AccountSessionToken? = SharedFlowCentre.currentSession.value?.token
    private var requestGeneration = 0L
    private var refreshJob: Job? = null
    private var cleanupJob: Job? = null

    init {
        viewModelScope.launch {
            SharedFlowCentre.currentSession.collect { session ->
                val nextToken = session?.token
                val previousToken = activeSessionToken
                if (nextToken == previousToken) return@collect

                val refreshRunning = refreshJob?.isActive == true
                val cleanupRunning = cleanupJob?.isActive == true
                val requestRunning = refreshRunning || cleanupRunning
                activeSessionToken = nextToken

                // A request may renew authentication for the same account. Its response token
                // decides whether the request can continue; an unrelated replacement returns a
                // stale token and is stopped before another API call.
                if (requestRunning && previousToken?.userId == nextToken?.userId && nextToken != null) {
                    _state.update {
                        it.copy(sessionToken = nextToken, isSessionAvailable = true)
                    }
                    return@collect
                }

                requestGeneration++
                refreshJob?.cancel()
                cleanupJob?.cancel()
                refreshJob = null
                cleanupJob = null
                _state.value = PlayerModerationCleanupState(sessionToken = nextToken)
                if (nextToken != null) refresh()
            }
        }
    }

    fun loadIfNeeded() {
        if (!_state.value.hasLoaded && refreshJob?.isActive != true) refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true || cleanupJob?.isActive == true) return
        val token = SharedFlowCentre.currentSession.value?.token ?: run {
            activeSessionToken = null
            _state.value = PlayerModerationCleanupState(sessionToken = null)
            return
        }
        activeSessionToken = token
        val generation = ++requestGeneration
        _state.update {
            it.copy(
                sessionToken = token,
                isSessionAvailable = true,
                isLoading = true,
                loadFailed = false,
                result = null,
            )
        }
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = source.getAll(token)
                    ?: return@launch restartAfterStaleResponse(generation, wasCleanup = false)
                if (adopt(response, generation) == null) {
                    return@launch restartAfterStaleResponse(generation, wasCleanup = false)
                }
                response.result.fold(
                    onSuccess = { records -> publishAvailable(records) },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        _state.update {
                            it.copy(isLoading = false, hasLoaded = true, loadFailed = true)
                        }
                    },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (isCurrent(token, generation)) {
                    _state.update {
                        it.copy(isLoading = false, hasLoaded = true, loadFailed = true)
                    }
                }
            }
        }
    }

    fun select(type: PlayerModerationType) {
        if (_state.value.isClearing || _state.value.availableTypes.none { it.type == type }) return
        _state.update { it.copy(selectedType = type, result = null) }
    }

    fun clearSelected(
        expectedType: PlayerModerationType? = _state.value.selectedType,
        expectedSessionToken: AccountSessionToken? = _state.value.sessionToken,
    ) {
        val current = _state.value
        if (current.isClearing || cleanupJob?.isActive == true) return
        val type = expectedType
            ?.takeIf { it == current.selectedType }
            ?.takeIf { selected -> current.availableTypes.any { it.type == selected } }
            ?: return
        val token = expectedSessionToken
            ?.takeIf { it == current.sessionToken && SharedFlowCentre.isCurrentSession(it) }
            ?: return
        activeSessionToken = token
        val generation = ++requestGeneration
        _state.update {
            it.copy(
                isClearing = true,
                processedCount = 0,
                totalCount = 0,
                result = null,
            )
        }

        cleanupJob = viewModelScope.launch(Dispatchers.IO) {
            var operationToken = token
            try {
                val authoritative = source.get(operationToken, type)
                    ?: return@launch restartAfterStaleResponse(generation, wasCleanup = true)
                operationToken = adopt(authoritative, generation)
                    ?: return@launch restartAfterStaleResponse(generation, wasCleanup = true)
                val records = authoritative.result.getOrElse { error ->
                    if (error is CancellationException) throw error
                    publishCleanupFailure()
                    return@launch
                }
                val targets = records.targetsFor(type)
                if (targets.isEmpty()) {
                    publishNoRecords(type)
                    return@launch
                }

                _state.update { it.copy(totalCount = targets.size) }
                targets.forEachIndexed { index, targetUserId ->
                    val response = source.remove(operationToken, targetUserId, type)
                        ?: return@launch restartAfterStaleResponse(generation, wasCleanup = true)
                    operationToken = adopt(response, generation)
                        ?: return@launch restartAfterStaleResponse(generation, wasCleanup = true)
                    response.result.exceptionOrNull()?.let { error ->
                        if (error is CancellationException) throw error
                    }
                    _state.update { it.copy(processedCount = index + 1) }
                }

                val verification = source.get(operationToken, type)
                    ?: return@launch restartAfterStaleResponse(generation, wasCleanup = true)
                operationToken = adopt(verification, generation)
                    ?: return@launch restartAfterStaleResponse(generation, wasCleanup = true)
                val remaining = verification.result.getOrElse { error ->
                    if (error is CancellationException) throw error
                    publishCleanupFailure()
                    return@launch
                }.targetsFor(type).toSet()
                val removed = targets.count { it !in remaining }
                publishVerifiedResult(type, removed = removed, remaining = remaining.size)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (isCurrent(operationToken, generation)) publishCleanupFailure()
            }
        }
    }

    private fun publishAvailable(records: List<PlayerModerationData>) {
        val counts = PlayerModerationType.entries.mapNotNull { type ->
            records.targetsFor(type).size
                .takeIf { it > 0 }
                ?.let { PlayerModerationTypeCount(type, it) }
        }
        _state.update { current ->
            current.copy(
                availableTypes = counts,
                selectedType = current.selectedType
                    ?.takeIf { selected -> counts.any { it.type == selected } }
                    ?: counts.firstOrNull()?.type,
                isLoading = false,
                hasLoaded = true,
                loadFailed = false,
            )
        }
    }

    private fun publishNoRecords(type: PlayerModerationType) {
        _state.update { current ->
            val remainingTypes = current.availableTypes.filterNot { it.type == type }
            current.copy(
                availableTypes = remainingTypes,
                selectedType = remainingTypes.firstOrNull()?.type,
                isClearing = false,
                processedCount = 0,
                totalCount = 0,
                result = PlayerModerationCleanupResult(
                    PlayerModerationCleanupResultKind.NoRecords,
                    removedCount = 0,
                    failedCount = 0,
                ),
            )
        }
    }

    private fun publishVerifiedResult(type: PlayerModerationType, removed: Int, remaining: Int) {
        val kind = when {
            remaining == 0 -> PlayerModerationCleanupResultKind.Success
            removed > 0 -> PlayerModerationCleanupResultKind.PartialFailure
            else -> PlayerModerationCleanupResultKind.Failure
        }
        _state.update { current ->
            val remainingTypes = if (remaining == 0) {
                current.availableTypes.filterNot { it.type == type }
            } else {
                current.availableTypes.map {
                    if (it.type == type) it.copy(targetCount = remaining) else it
                }
            }
            current.copy(
                availableTypes = remainingTypes,
                selectedType = type.takeIf { remaining > 0 }
                    ?: remainingTypes.firstOrNull()?.type,
                isClearing = false,
                result = PlayerModerationCleanupResult(kind, removed, remaining),
            )
        }
    }

    private fun publishCleanupFailure() {
        _state.update {
            it.copy(
                isClearing = false,
                result = PlayerModerationCleanupResult(
                    PlayerModerationCleanupResultKind.Failure,
                    removedCount = 0,
                    failedCount = 0,
                ),
            )
        }
    }

    private fun <T> adopt(
        response: PlayerModerationCleanupResponse<T>,
        generation: Long,
    ): AccountSessionToken? {
        val responseToken = response.sessionToken
        if (requestGeneration != generation || !SharedFlowCentre.isCurrentSession(responseToken)) {
            return null
        }
        activeSessionToken = responseToken
        _state.update {
            it.copy(sessionToken = responseToken, isSessionAvailable = true)
        }
        return responseToken
    }

    private fun restartAfterStaleResponse(generation: Long, wasCleanup: Boolean) {
        if (requestGeneration != generation) return
        requestGeneration++
        if (wasCleanup) cleanupJob = null else refreshJob = null
        val currentToken = SharedFlowCentre.currentSession.value?.token
        activeSessionToken = currentToken
        _state.value = PlayerModerationCleanupState(sessionToken = currentToken)
        if (currentToken != null) viewModelScope.launch { refresh() }
    }

    private fun isCurrent(token: AccountSessionToken, generation: Long): Boolean =
        requestGeneration == generation && SharedFlowCentre.isCurrentSession(token)
}

private fun List<PlayerModerationData>.targetsFor(type: PlayerModerationType): List<String> =
    asSequence()
        .filter { it.type == type.apiValue }
        .map(PlayerModerationData::targetUserId)
        .distinct()
        .toList()
