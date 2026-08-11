package io.github.vrcmteam.vrcm.presentation.screens.home

import androidx.compose.runtime.getValue
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
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.FriendLocationPagerModel
import io.github.vrcmteam.vrcm.presentation.screens.meetup.MeetupCardDisplayRoute
import io.github.vrcmteam.vrcm.presentation.screens.meetup.MeetupCardEditorRoute
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.meetup.MeetupCardRepository
import io.github.vrcmteam.vrcm.service.FriendService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.koin.core.logger.Logger


class HomeScreenModel(
    private val authService: AuthService,
    private val usersApi: UsersApi,
    private val friendService: FriendService,
    private val friendLocationPagerModel: FriendLocationPagerModel,
    private val logger: Logger,
    private val meetupCardRepository: MeetupCardRepository,
) : ViewModel() {

    /** 长按头像的入口分流：已有配置直接展示，首次使用进入编辑器。 */
    fun meetupCardStartRoute(): AppRoute = if (meetupCardRepository.isConfigured(userId)) {
        MeetupCardDisplayRoute(userId)
    } else {
        MeetupCardEditorRoute(userId)
    }

    private val _currentUser = mutableStateOf<CurrentUserData?>(null)

    var selectedPagerIndex by mutableIntStateOf(0)
        private set

    val userId: String
        get() = authService.accountDto().userId

    val iconUrl: String
        get() = authService.accountDto().iconUrl.orEmpty()

    var currentUser by _currentUser

    fun onPagerSettled(index: Int) {
        selectedPagerIndex = index
    }

    init {
        friendService.preloadFriendList()
        friendLocationPagerModel.preloadFriendLocations()
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
