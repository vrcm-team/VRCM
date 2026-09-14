package io.github.vrcmteam.vrcm.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.playermoderation.data.PlayerModerationData
import io.github.vrcmteam.vrcm.network.api.playermoderation.data.PlayerModerationType
import io.github.vrcmteam.vrcm.service.PlayerModerationCleanupResponse
import io.github.vrcmteam.vrcm.service.PlayerModerationCleanupSource
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Instant

internal data class PlayerModerationTypeCount(
    val type: PlayerModerationType,
    val targetCount: Int,
)

internal enum class PlayerModerationCleanupResultKind {
    Success,
    NoRecords,
    PartialFailure,
    Failure,
}

internal data class PlayerModerationCleanupResult(
    val kind: PlayerModerationCleanupResultKind,
    val removedCount: Int,
    val failedCount: Int,
)

internal data class PlayerModerationListItem(
    val record: PlayerModerationData,
    val key: String,
)

internal data class PlayerModerationState(
    val records: List<PlayerModerationData> = emptyList(),
    val selectedFilter: String? = null,
    val availableTypes: List<PlayerModerationTypeCount> = emptyList(),
    val selectedCleanupType: PlayerModerationType? = null,
    val sessionToken: AccountSessionToken? = SharedFlowCentre.currentSession.value?.token,
    val isSessionAvailable: Boolean = sessionToken != null,
    val isLoading: Boolean = isSessionAvailable,
    val hasLoaded: Boolean = false,
    val loadFailed: Boolean = false,
    val isClearing: Boolean = false,
    val processedCount: Int = 0,
    val totalCount: Int = 0,
    val result: PlayerModerationCleanupResult? = null,
) {
    val availableFilterTypes: List<String> = records.map(PlayerModerationData::type).distinct()
    private val keyedRecords: List<PlayerModerationListItem> = records.withStableKeys()
    val visibleRecords: List<PlayerModerationListItem> = selectedFilter?.let { selected ->
        keyedRecords.filter { it.record.type == selected }
    } ?: keyedRecords
}

/** Owns the session-bound record list and confirmed cleanup flow for player moderation. */
internal class PlayerModerationListScreenModel(
    private val source: PlayerModerationCleanupSource,
) : ViewModel() {
    private val _state = MutableStateFlow(PlayerModerationState())
    val state: StateFlow<PlayerModerationState> = _state.asStateFlow()

    private var activeSessionToken: AccountSessionToken? = SharedFlowCentre.currentSession.value?.token
    private val requestGeneration = atomic(0L)
    private var refreshJob: Job? = null
    private var cleanupJob: Job? = null

    init {
        viewModelScope.launch {
            SharedFlowCentre.currentSession.collect { session ->
                val nextToken = session?.token
                if (nextToken == null) {
                    if (SharedFlowCentre.currentSession.value != null || activeSessionToken == null) {
                        return@collect
                    }
                    activeSessionToken = null
                    requestGeneration.incrementAndGet()
                    refreshJob?.cancel()
                    cleanupJob?.cancel()
                    refreshJob = null
                    cleanupJob = null
                    _state.value = PlayerModerationState(sessionToken = null)
                    return@collect
                }

                var shouldRefresh = false
                SharedFlowCentre.commitIfCurrentSession(nextToken) {
                    val previousToken = activeSessionToken
                    if (nextToken == previousToken) return@commitIfCurrentSession true

                    val requestRunning = refreshJob?.isActive == true || cleanupJob?.isActive == true
                    activeSessionToken = nextToken

                    // A request may renew authentication for the same account. Its response token
                    // decides whether the request can continue; an unrelated replacement returns a
                    // stale token and is stopped before another API call.
                    if (requestRunning && previousToken?.userId == nextToken.userId) {
                        _state.update {
                            it.copy(sessionToken = nextToken, isSessionAvailable = true)
                        }
                    } else {
                        requestGeneration.incrementAndGet()
                        refreshJob?.cancel()
                        cleanupJob?.cancel()
                        refreshJob = null
                        cleanupJob = null
                        _state.value = PlayerModerationState(sessionToken = nextToken)
                        shouldRefresh = true
                    }
                    true
                }
                if (shouldRefresh) refresh()
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
            _state.value = PlayerModerationState(sessionToken = null)
            return
        }
        val generation = requestGeneration.incrementAndGet()
        if (!commitIfCurrent(token, generation) {
                activeSessionToken = token
                _state.update {
                    it.copy(
                        sessionToken = token,
                        isSessionAvailable = true,
                        isLoading = true,
                        loadFailed = false,
                        result = null,
                    )
                }
            }
        ) {
            return
        }
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            var operationToken = token
            try {
                val response = source.getAll(token)
                    ?: return@launch restartAfterStaleResponse(generation, wasCleanup = false)
                operationToken = adopt(response, generation)
                    ?: return@launch restartAfterStaleResponse(generation, wasCleanup = false)
                val published = response.result.fold(
                    onSuccess = { records -> publishAvailable(records, operationToken, generation) },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        publishLoadFailure(operationToken, generation)
                    },
                )
                if (!published) restartAfterStaleResponse(generation, wasCleanup = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (!publishLoadFailure(operationToken, generation)) {
                    restartAfterStaleResponse(generation, wasCleanup = false)
                }
            }
        }
    }

    fun selectFilter(type: String?) {
        val current = _state.value
        if (current.isClearing || type != null && current.records.none { it.type == type }) return
        val matchingCleanupType = type
            ?.let(PlayerModerationType::fromApiValue)
            ?.takeIf { selected -> current.availableTypes.any { it.type == selected } }
        _state.update {
            it.copy(
                selectedFilter = type,
                selectedCleanupType = matchingCleanupType ?: it.selectedCleanupType,
            )
        }
    }

    fun selectCleanupType(type: PlayerModerationType) {
        if (_state.value.isLoading || _state.value.isClearing ||
            _state.value.availableTypes.none { it.type == type }
        ) {
            return
        }
        _state.update { it.copy(selectedCleanupType = type, result = null) }
    }

    fun clearSelected(
        expectedType: PlayerModerationType? = _state.value.selectedCleanupType,
        expectedSessionToken: AccountSessionToken? = _state.value.sessionToken,
    ) {
        val current = _state.value
        if (current.isLoading || current.isClearing ||
            refreshJob?.isActive == true || cleanupJob?.isActive == true
        ) {
            return
        }
        val type = expectedType
            ?.takeIf { it == current.selectedCleanupType }
            ?.takeIf { selected -> current.availableTypes.any { it.type == selected } }
            ?: return
        val token = expectedSessionToken
            ?.takeIf { it == current.sessionToken && SharedFlowCentre.isCurrentSession(it) }
            ?: return
        val generation = requestGeneration.incrementAndGet()
        if (!commitIfCurrent(token, generation) {
                activeSessionToken = token
                _state.update {
                    it.copy(
                        isClearing = true,
                        processedCount = 0,
                        totalCount = 0,
                        result = null,
                    )
                }
            }
        ) {
            return
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
                    if (!publishCleanupFailure(operationToken, generation)) {
                        restartAfterStaleResponse(generation, wasCleanup = true)
                    }
                    return@launch
                }
                val targets = records.targetsFor(type)
                if (targets.isEmpty()) {
                    if (!publishNoRecords(type, operationToken, generation)) {
                        restartAfterStaleResponse(generation, wasCleanup = true)
                    }
                    return@launch
                }

                if (!publishTotal(targets.size, operationToken, generation)) {
                    restartAfterStaleResponse(generation, wasCleanup = true)
                    return@launch
                }
                targets.forEachIndexed { index, targetUserId ->
                    val response = source.remove(operationToken, targetUserId, type)
                        ?: return@launch restartAfterStaleResponse(generation, wasCleanup = true)
                    operationToken = adopt(response, generation)
                        ?: return@launch restartAfterStaleResponse(generation, wasCleanup = true)
                    response.result.exceptionOrNull()?.let { error ->
                        if (error is CancellationException) throw error
                    }
                    if (!publishProgress(index + 1, operationToken, generation)) {
                        restartAfterStaleResponse(generation, wasCleanup = true)
                        return@launch
                    }
                }

                val verification = source.get(operationToken, type)
                    ?: return@launch restartAfterStaleResponse(generation, wasCleanup = true)
                operationToken = adopt(verification, generation)
                    ?: return@launch restartAfterStaleResponse(generation, wasCleanup = true)
                val remainingRecords = verification.result.getOrElse { error ->
                    if (error is CancellationException) throw error
                    if (!publishCleanupFailure(operationToken, generation)) {
                        restartAfterStaleResponse(generation, wasCleanup = true)
                    }
                    return@launch
                }.filter { it.type == type.apiValue }
                val remainingTargets = remainingRecords.targetsFor(type).toSet()
                val removed = targets.count { it !in remainingTargets }
                if (
                    !publishVerifiedResult(
                        type = type,
                        removed = removed,
                        remainingRecords = remainingRecords,
                        token = operationToken,
                        generation = generation,
                    )
                ) {
                    restartAfterStaleResponse(generation, wasCleanup = true)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (!publishCleanupFailure(operationToken, generation)) {
                    restartAfterStaleResponse(generation, wasCleanup = true)
                }
            }
        }
    }

    private fun publishAvailable(
        records: List<PlayerModerationData>,
        token: AccountSessionToken,
        generation: Long,
    ): Boolean {
        val orderedRecords = records.stableNewestFirst()
        val counts = orderedRecords.cleanupTypeCounts()
        return commitIfCurrent(token, generation) {
            _state.update { current ->
                current.copy(
                    records = orderedRecords,
                    selectedFilter = current.selectedFilter?.takeIf { selected ->
                        orderedRecords.any { it.type == selected }
                    },
                    availableTypes = counts,
                    selectedCleanupType = current.selectedCleanupType
                        ?.takeIf { selected -> counts.any { it.type == selected } }
                        ?: counts.firstOrNull()?.type,
                    isLoading = false,
                    hasLoaded = true,
                    loadFailed = false,
                )
            }
        }
    }

    private fun publishLoadFailure(
        token: AccountSessionToken,
        generation: Long,
    ): Boolean = commitIfCurrent(token, generation) {
        _state.update {
            it.copy(isLoading = false, hasLoaded = true, loadFailed = true)
        }
    }

    private fun publishTotal(
        total: Int,
        token: AccountSessionToken,
        generation: Long,
    ): Boolean = commitIfCurrent(token, generation) {
        _state.update { it.copy(totalCount = total) }
    }

    private fun publishProgress(
        processed: Int,
        token: AccountSessionToken,
        generation: Long,
    ): Boolean = commitIfCurrent(token, generation) {
        _state.update { it.copy(processedCount = processed) }
    }

    private fun publishNoRecords(
        type: PlayerModerationType,
        token: AccountSessionToken,
        generation: Long,
    ): Boolean = commitIfCurrent(token, generation) {
        _state.update { current ->
            val remainingRecords = current.records.filterNot { it.type == type.apiValue }
            val remainingTypes = remainingRecords.cleanupTypeCounts()
            current.copy(
                records = remainingRecords,
                selectedFilter = current.selectedFilter
                    ?.takeIf { selected -> remainingRecords.any { it.type == selected } },
                availableTypes = remainingTypes,
                selectedCleanupType = remainingTypes.firstOrNull()?.type,
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

    private fun publishVerifiedResult(
        type: PlayerModerationType,
        removed: Int,
        remainingRecords: List<PlayerModerationData>,
        token: AccountSessionToken,
        generation: Long,
    ): Boolean {
        val remaining = remainingRecords.targetsFor(type).size
        val kind = when {
            remaining == 0 -> PlayerModerationCleanupResultKind.Success
            removed > 0 -> PlayerModerationCleanupResultKind.PartialFailure
            else -> PlayerModerationCleanupResultKind.Failure
        }
        return commitIfCurrent(token, generation) {
            _state.update { current ->
                val records = (
                    current.records.filterNot { it.type == type.apiValue } + remainingRecords
                ).stableNewestFirst()
                val remainingTypes = records.cleanupTypeCounts()
                current.copy(
                    records = records,
                    selectedFilter = current.selectedFilter
                        ?.takeIf { selected -> records.any { it.type == selected } },
                    availableTypes = remainingTypes,
                    selectedCleanupType = type.takeIf { remaining > 0 }
                        ?: remainingTypes.firstOrNull()?.type,
                    isClearing = false,
                    result = PlayerModerationCleanupResult(kind, removed, remaining),
                )
            }
        }
    }

    private fun publishCleanupFailure(
        token: AccountSessionToken,
        generation: Long,
    ): Boolean = commitIfCurrent(token, generation) {
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
        val adopted = commitIfCurrent(responseToken, generation) {
            activeSessionToken = responseToken
            _state.update {
                it.copy(sessionToken = responseToken, isSessionAvailable = true)
            }
        }
        return responseToken.takeIf { adopted }
    }

    private fun restartAfterStaleResponse(generation: Long, wasCleanup: Boolean) {
        viewModelScope.launch {
            val currentToken = SharedFlowCentre.currentSession.value?.token ?: return@launch
            val shouldRefresh = commitIfCurrent(currentToken, generation) {
                requestGeneration.incrementAndGet()
                if (wasCleanup) cleanupJob = null else refreshJob = null
                activeSessionToken = currentToken
                _state.value = PlayerModerationState(sessionToken = currentToken)
            }
            if (shouldRefresh) refresh()
        }
    }

    private fun commitIfCurrent(
        token: AccountSessionToken,
        generation: Long,
        commit: () -> Unit,
    ): Boolean = SharedFlowCentre.commitIfCurrentSession(token) {
        if (requestGeneration.value != generation) {
            false
        } else {
            commit()
            true
        }
    }
}

private fun List<PlayerModerationData>.targetsFor(type: PlayerModerationType): List<String> =
    asSequence()
        .filter { it.type == type.apiValue }
        .map(PlayerModerationData::targetUserId)
        .distinct()
        .toList()

private fun List<PlayerModerationData>.cleanupTypeCounts(): List<PlayerModerationTypeCount> =
    PlayerModerationType.entries.mapNotNull { type ->
        targetsFor(type).size
            .takeIf { it > 0 }
            ?.let { PlayerModerationTypeCount(type, it) }
    }

/** Sorts only when every record has a valid time; otherwise preserves the complete server order. */
@OptIn(kotlin.time.ExperimentalTime::class)
internal fun List<PlayerModerationData>.stableNewestFirst(): List<PlayerModerationData> {
    val dated = mapIndexed { index, record ->
        IndexedValue(index, runCatching { Instant.parse(record.created) }.getOrNull())
    }
    if (dated.any { it.value == null }) return this

    return indices.sortedWith(
        compareByDescending<Int> { dated[it].value!! }
            .thenBy { dated[it].index },
    ).map(::get)
}

private fun List<PlayerModerationData>.withStableKeys(): List<PlayerModerationListItem> {
    val idCounts = groupingBy { it.id }.eachCount()
    return mapIndexed { index, record ->
        val uniqueId = record.id.takeIf { it.isNotBlank() && idCounts[it] == 1 }
        PlayerModerationListItem(
            record = record,
            key = uniqueId ?: buildString {
                append("fallback:")
                append(record.id)
                append(':')
                append(record.targetUserId)
                append(':')
                append(record.type)
                append(':')
                append(record.created)
                append(':')
                append(index)
            },
        )
    }
}
