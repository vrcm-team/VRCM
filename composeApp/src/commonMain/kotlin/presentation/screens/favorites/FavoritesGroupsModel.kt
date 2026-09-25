package io.github.vrcmteam.vrcm.presentation.screens.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.groups.GroupsApi
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.users.data.LimitedUserGroup
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStrings
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MyGroupsState(
    val groups: List<LimitedUserGroup> = emptyList(),
    val searchText: String = "",
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: String? = null,
) {
    val visibleGroups: List<LimitedUserGroup>
        get() = groups.filter { group ->
            searchText.isBlank() || group.name.contains(searchText, ignoreCase = true) ||
                group.shortCode.contains(searchText, ignoreCase = true)
        }
}

/** Loads only groups returned by VRChat's current-user membership endpoint. */
class FavoritesGroupsModel(
    private val usersApi: UsersApi,
    private val authService: AuthService,
    private val groupsApi: GroupsApi,
) : ViewModel() {
    private val _state = MutableStateFlow(MyGroupsState())
    val state: StateFlow<MyGroupsState> = _state.asStateFlow()
    private var refreshJob: Job? = null
    private var activeSessionToken: AccountSessionToken? = SharedFlowCentre.currentSession.value?.token
    private var requestGeneration = 0L
    private val _removalState = MutableStateFlow(SelectionRemovalState())
    internal val removalState: StateFlow<SelectionRemovalState> = _removalState.asStateFlow()
    private var removalJob: Job? = null
    private var removalRequest = 0L
    private var locale: LocaleStrings? = null

    init {
        viewModelScope.launch {
            SharedFlowCentre.currentSession.collect { session ->
                val nextToken = session?.token
                if (activeSessionToken == nextToken) return@collect

                val shouldReload = _state.value.hasLoaded || refreshJob?.isActive == true
                val userChanged = activeSessionToken?.userId != nextToken?.userId
                requestGeneration++
                activeSessionToken = nextToken
                refreshJob?.cancel()
                refreshJob = null
                if (userChanged) {
                    removalRequest++
                    removalJob?.cancel()
                    removalJob = null
                    _removalState.value = SelectionRemovalState()
                }
                _state.value = if (userChanged) MyGroupsState() else {
                    _state.value.copy(isLoading = false, error = null)
                }
                if (nextToken != null && shouldReload && !_removalState.value.isSubmitting) refresh()
            }
        }
    }

    fun updateLocale(value: LocaleStrings) {
        locale = value
    }

    fun setSearchText(value: String) {
        _state.value = _state.value.copy(searchText = value)
    }

    fun loadIfNeeded() {
        if (!_state.value.hasLoaded && refreshJob?.isActive != true) refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true || _removalState.value.isSubmitting) return
        val sessionToken = activeSessionToken ?: SharedFlowCentre.currentSession.value?.token ?: return
        val generation = ++requestGeneration
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            if (!accepts(sessionToken, generation)) return@launch
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val result = authService.reTryAuthCatching {
                    usersApi.getUserGroups(sessionToken.userId)
                }
                if (!accepts(sessionToken, generation)) return@launch
                result.onSuccess { groups ->
                    val loadedGroups = groups
                        .filter { it.groupId.isNotBlank() }
                        .distinctBy { it.groupId }
                        .sortedBy { it.name.lowercase() }
                    _state.value = _state.value.copy(
                        groups = loadedGroups,
                        isLoading = false,
                        hasLoaded = true,
                    )
                    retainExistingGroupSelections(loadedGroups.mapTo(mutableSetOf()) { it.groupId })
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    _state.value = _state.value.copy(
                        isLoading = false,
                        hasLoaded = true,
                        error = error.message.orEmpty(),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (accepts(sessionToken, generation)) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        hasLoaded = true,
                        error = error.message.orEmpty(),
                    )
                }
            }
        }
    }

    fun enterGroupSelectionMode() {
        if (_removalState.value.isSubmitting || _state.value.isLoading || _state.value.groups.isEmpty()) {
            return
        }
        _removalState.value = SelectionRemovalState(selectionMode = true)
    }

    fun beginGroupSelection(groupId: String) {
        if (_removalState.value.isSubmitting || _state.value.isLoading ||
            _state.value.groups.none { it.groupId == groupId }
        ) {
            return
        }
        _removalState.value = SelectionRemovalState(
            selectionMode = true,
            selectedIds = setOf(groupId),
        )
    }

    fun exitGroupSelectionMode() {
        if (_removalState.value.isSubmitting) return
        _removalState.value = SelectionRemovalState()
    }

    fun toggleGroupSelection(groupId: String) {
        if (_state.value.groups.none { it.groupId == groupId }) return
        _removalState.update { state ->
            if (!state.selectionMode || state.isSubmitting) return@update state
            state.copy(
                selectedIds = if (groupId in state.selectedIds) {
                    state.selectedIds - groupId
                } else {
                    state.selectedIds + groupId
                },
                confirmationVisible = false,
                completedCount = 0,
                totalCount = 0,
                results = emptyMap(),
            )
        }
    }

    fun toggleVisibleGroupSelection(visibleGroupIds: Set<String>) {
        val availableIds = _state.value.groups.mapTo(mutableSetOf()) { it.groupId }
        val selectableIds = visibleGroupIds intersect availableIds
        _removalState.update { state ->
            if (!state.selectionMode || state.isSubmitting || selectableIds.isEmpty()) {
                return@update state
            }
            val allVisibleSelected = selectableIds.all { it in state.selectedIds }
            state.copy(
                selectedIds = if (allVisibleSelected) {
                    state.selectedIds - selectableIds
                } else {
                    state.selectedIds + selectableIds
                },
                confirmationVisible = false,
                completedCount = 0,
                totalCount = 0,
                results = emptyMap(),
            )
        }
    }

    fun requestGroupLeaveConfirmation() {
        retainExistingGroupSelections(_state.value.groups.mapTo(mutableSetOf()) { it.groupId })
        _removalState.update { state ->
            if (!state.selectionMode || state.isSubmitting || state.selectedIds.isEmpty()) state
            else state.copy(confirmationVisible = true)
        }
    }

    fun dismissGroupLeaveConfirmation() {
        _removalState.update { state ->
            if (state.isSubmitting) state else state.copy(confirmationVisible = false)
        }
    }

    fun confirmGroupLeave() {
        val state = _removalState.value
        if (!state.selectionMode || state.isSubmitting || !state.confirmationVisible) return
        val sessionToken = activeSessionToken?.takeIf(SharedFlowCentre::isCurrentSession) ?: return
        val availableIds = _state.value.groups.mapTo(mutableSetOf()) { it.groupId }
        val selectedIds = state.selectedIds.filter { it in availableIds }
        if (selectedIds.isEmpty()) {
            _removalState.value = state.copy(selectedIds = emptySet(), confirmationVisible = false)
            return
        }

        val requestId = ++removalRequest
        requestGeneration++
        refreshJob?.cancel()
        refreshJob = null
        _state.update { it.copy(isLoading = false) }
        _removalState.value = state.copy(
            selectedIds = selectedIds.toSet(),
            confirmationVisible = false,
            isSubmitting = true,
            completedCount = 0,
            totalCount = selectedIds.size,
            results = emptyMap(),
        )
        removalJob = viewModelScope.launch(Dispatchers.IO) {
            val ownerId = sessionToken.userId
            var currentToken = sessionToken
            try {
                for (groupId in selectedIds) {
                    val response = try {
                        authService.runSessionBoundCatching(currentToken) {
                            groupsApi.leaveGroup(groupId)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        io.github.vrcmteam.vrcm.service.SessionBoundResponse(
                            Result.failure(error),
                            currentToken,
                        )
                    }
                    if (response == null || !SharedFlowCentre.isCurrentSession(response.sessionToken)) {
                        finishInterruptedGroupLeave(ownerId, requestId)
                        return@launch
                    }
                    currentToken = response.sessionToken
                    if (!acceptsRemoval(ownerId, requestId)) return@launch

                    val error = response.result.exceptionOrNull()
                    val result = SelectionRemovalResult(
                        itemId = groupId,
                        errorMessage = error?.message?.ifBlank { "Request failed" }
                            ?: if (response.result.isFailure) "Request failed" else null,
                    )
                    if (result.succeeded) {
                        _state.update { current ->
                            current.copy(groups = current.groups.filterNot { it.groupId == groupId })
                        }
                    }
                    _removalState.update { current ->
                        current.copy(
                            selectedIds = if (result.succeeded) {
                                current.selectedIds - groupId
                            } else {
                                current.selectedIds
                            },
                            completedCount = current.completedCount + 1,
                            results = current.results + (groupId to result),
                        )
                    }
                }

                if (!SharedFlowCentre.isCurrentSession(currentToken) ||
                    !acceptsRemoval(ownerId, requestId)
                ) {
                    finishInterruptedGroupLeave(ownerId, requestId)
                    return@launch
                }
                val completed = _removalState.value
                val remainingGroupIds = _state.value.groups.mapTo(mutableSetOf()) { it.groupId }
                val failedIds = completed.results.values
                    .filterNot(SelectionRemovalResult::succeeded)
                    .mapTo(mutableSetOf(), SelectionRemovalResult::itemId)
                    .intersect(remainingGroupIds)
                _removalState.value = completed.copy(
                    selectionMode = failedIds.isNotEmpty(),
                    selectedIds = failedIds,
                    isSubmitting = false,
                )
                showGroupLeaveSummary(completed.successCount, completed.failureCount)
            } finally {
                if (removalRequest == requestId) removalJob = null
            }
        }
    }

    private fun retainExistingGroupSelections(availableIds: Set<String>) {
        _removalState.update { state ->
            val retained = state.selectedIds intersect availableIds
            if (retained == state.selectedIds) state else state.copy(
                selectedIds = retained,
                confirmationVisible = state.confirmationVisible && retained.isNotEmpty(),
            )
        }
    }

    private suspend fun finishInterruptedGroupLeave(ownerId: String, requestId: Long) {
        if (!acceptsRemoval(ownerId, requestId)) return
        val current = _removalState.value
        val interruptedIds = current.selectedIds - current.results.keys
        val interruptedResults = interruptedIds.associateWith { groupId ->
            SelectionRemovalResult(groupId, "Session changed")
        }
        val completed = current.copy(
            completedCount = current.totalCount,
            results = current.results + interruptedResults,
        )
        _removalState.value = completed.copy(
            selectionMode = completed.selectedIds.isNotEmpty(),
            isSubmitting = false,
        )
        showGroupLeaveSummary(completed.successCount, completed.failureCount)
    }

    private fun acceptsRemoval(ownerId: String, requestId: Long): Boolean =
        removalRequest == requestId &&
            SharedFlowCentre.currentSession.value?.token?.userId == ownerId

    private suspend fun showGroupLeaveSummary(successCount: Int, failureCount: Int) {
        val strings = locale ?: return
        val message = when {
            failureCount == 0 -> strings.groupSelectionLeaveSuccess
                .replaceFirst("%d", successCount.toString())
            successCount == 0 -> strings.groupSelectionLeaveFailed
                .replaceFirst("%d", failureCount.toString())
            else -> strings.groupSelectionLeavePartialFailure
                .replaceFirst("%d", successCount.toString())
                .replaceFirst("%d", failureCount.toString())
        }
        SharedFlowCentre.toastText.emit(
            if (failureCount == 0) ToastText.Success(message) else ToastText.Error(message)
        )
    }

    private fun accepts(token: AccountSessionToken, generation: Long): Boolean =
        requestGeneration == generation && activeSessionToken == token &&
            SharedFlowCentre.isCurrentSession(token)

}
