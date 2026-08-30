package io.github.vrcmteam.vrcm.presentation.screens.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    fun setSearchText(value: String) {
        _state.value = _state.value.copy(searchText = value)
    }

    fun loadIfNeeded() {
        if (!_state.value.hasLoaded && refreshJob?.isActive != true) refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val groups = usersApi.getUserGroups(authService.accountDto().userId)
                    .filter { it.groupId.isNotBlank() }
                    .distinctBy { it.groupId }
                    .sortedBy { it.name.lowercase() }
                _state.value = _state.value.copy(
                    groups = groups,
                    isLoading = false,
                    hasLoaded = true,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    hasLoaded = true,
                    error = error.message.orEmpty(),
                )
            }
        }
    }
}
