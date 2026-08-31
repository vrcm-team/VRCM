package io.github.vrcmteam.vrcm.presentation.screens.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.auth.data.CurrentUserData
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.users.data.UpdateUserInfoData
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.extensions.onApiFailure
import io.github.vrcmteam.vrcm.presentation.navigation.AppRoute
import io.github.vrcmteam.vrcm.presentation.screens.meetup.MeetupCardDisplayRoute
import io.github.vrcmteam.vrcm.presentation.screens.meetup.MeetupCardEditorRoute
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.meetup.MeetupCardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.koin.core.logger.Logger


class HomeScreenModel(
    private val authService: AuthService,
    private val usersApi: UsersApi,
    private val logger: Logger,
    private val meetupCardRepository: MeetupCardRepository,
) : ViewModel() {
    private val shellState = HomeShellState()

    val selectedDestinationIndex: Int
        get() = shellState.selectedDestinationIndex

    val selectedHomeTabIndex: Int
        get() = shellState.selectedHomeTabIndex

    val drawerVisible: Boolean
        get() = shellState.drawerVisible

    val settingsVisible: Boolean
        get() = shellState.settingsVisible

    /** 长按头像的入口分流：已有配置直接展示，首次使用进入编辑器。 */
    fun meetupCardStartRoute(): AppRoute = if (meetupCardRepository.isConfigured(userId)) {
        MeetupCardDisplayRoute(userId)
    } else {
        MeetupCardEditorRoute(userId)
    }

    private val _currentUser = mutableStateOf<CurrentUserData?>(null)

    val userId: String
        get() = authService.accountDto().userId

    val iconUrl: String
        get() = authService.accountDto().iconUrl.orEmpty()

    var currentUser by _currentUser

    internal fun selectDestination(destination: HomeDestination): Boolean {
        return shellState.selectDestination(destination)
    }

    internal fun selectHomeTab(tab: HomeTab) {
        shellState.selectHomeTab(tab)
    }

    fun showDrawer() {
        shellState.showDrawer()
    }

    fun hideDrawer() {
        shellState.hideDrawer()
    }

    fun showSettings() {
        shellState.showSettings()
    }

    fun hideSettings() {
        shellState.hideSettings()
    }

    fun clearOverlays() {
        shellState.clearOverlays()
    }

    fun logout() {
        clearOverlays()
        viewModelScope.launch { authService.logout() }
    }

    init {
        refreshCurrentUser()
        viewModelScope.launch {
            authService.currentUserState.collect { _currentUser.value = it }
        }
    }

    private fun refreshCurrentUser() =
        viewModelScope.launch(Dispatchers.IO) {
            authService.reTryAuthCatching { authService.currentUser(isRefresh = true) }
                .onHomeFailure()
                .onSuccess {
                    _currentUser.value = it
                }
        }


    private inline fun <T> Result<T>.onHomeFailure() =
        onApiFailure("Home") {
            logger.error(it)
            viewModelScope.launch {
                SharedFlowCentre.toastText.emit(ToastText.Error(it))
            }
        }
    fun updateUserStatus(userStatus: UserStatus, statusDescription: String) {
        viewModelScope.launch(Dispatchers.IO) {
            authService.reTryAuthCatching {
                usersApi.updateUserInfo(
                    userId = userId,
                    updateUserInfoData = UpdateUserInfoData(
                        status = userStatus,
                        statusDescription = statusDescription
                    )
                )
            }.onHomeFailure()
                .onSuccess {
                    refreshCurrentUser()
                }
        }
    }


}

internal enum class HomeDestination {
    Home,
    Search,
    Notifications,
    Friends,
}

internal enum class HomeTab {
    Location,
    Activity,
}

@Stable
internal class HomeShellState {
    var selectedDestinationIndex by mutableIntStateOf(HomeDestination.Home.ordinal)
        private set

    var selectedHomeTabIndex by mutableIntStateOf(HomeTab.Location.ordinal)
        private set

    var drawerVisible by mutableStateOf(false)
        private set

    var settingsVisible by mutableStateOf(false)
        private set

    fun selectDestination(destination: HomeDestination): Boolean {
        val reselected = selectedDestinationIndex == destination.ordinal
        selectedDestinationIndex = destination.ordinal
        return reselected
    }

    fun selectHomeTab(tab: HomeTab) {
        selectedHomeTabIndex = tab.ordinal
    }

    fun showDrawer() {
        settingsVisible = false
        drawerVisible = true
    }

    fun hideDrawer() {
        drawerVisible = false
    }

    fun showSettings() {
        drawerVisible = false
        settingsVisible = true
    }

    fun hideSettings() {
        settingsVisible = false
    }

    fun clearOverlays() {
        drawerVisible = false
        settingsVisible = false
    }
}
