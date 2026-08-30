package io.github.vrcmteam.vrcm.presentation.screens.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.users.data.LimitedUserGroup
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) : ViewModel() {
    private val _state = MutableStateFlow(MyGroupsState())
    val state: StateFlow<MyGroupsState> = _state.asStateFlow()
    private var refreshJob: Job? = null
    private var activeSessionToken: AccountSessionToken? = SharedFlowCentre.currentSession.value?.token
    private var requestGeneration = 0L

    init {
        viewModelScope.launch {
            SharedFlowCentre.currentSession.collect { session ->
                val previousUserId = activeSessionToken?.userId
                val nextToken = session?.token
                if (previousUserId == nextToken?.userId) {
                    activeSessionToken = nextToken
                    return@collect
                }

                val shouldReload = _state.value.hasLoaded || refreshJob?.isActive == true
                requestGeneration++
                activeSessionToken = nextToken
                refreshJob?.cancel()
                refreshJob = null
                _state.value = MyGroupsState()
                if (nextToken != null && shouldReload) refresh()
            }
        }
    }

    fun setSearchText(value: String) {
        _state.value = _state.value.copy(searchText = value)
    }

    fun loadIfNeeded() {
        if (!_state.value.hasLoaded && refreshJob?.isActive != true) refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        val sessionToken = activeSessionToken ?: SharedFlowCentre.currentSession.value?.token ?: return
        val generation = ++requestGeneration
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            if (!accepts(sessionToken, generation)) return@launch
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val result = authService.reTryAuthCatching {
                    usersApi.getUserGroups(sessionToken.userId)
                }
                if (!acceptsUser(sessionToken.userId, generation)) return@launch
                result.onSuccess { groups ->
                    _state.value = _state.value.copy(
                        groups = groups
                            .filter { it.groupId.isNotBlank() }
                            .distinctBy { it.groupId }
                            .sortedBy { it.name.lowercase() },
                        isLoading = false,
                        hasLoaded = true,
                    )
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
                if (acceptsUser(sessionToken.userId, generation)) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        hasLoaded = true,
                        error = error.message.orEmpty(),
                    )
                }
            }
        }
    }

    private fun accepts(token: AccountSessionToken, generation: Long): Boolean =
        requestGeneration == generation && activeSessionToken == token &&
            SharedFlowCentre.isCurrentSession(token)

    private fun acceptsUser(userId: String, generation: Long): Boolean =
        requestGeneration == generation &&
            SharedFlowCentre.currentSession.value?.token?.userId == userId
}
