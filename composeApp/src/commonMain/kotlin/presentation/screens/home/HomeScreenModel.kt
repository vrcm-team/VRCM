package io.github.vrcmteam.vrcm.presentation.screens.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.NotificationType
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.auth.data.CurrentUserData
import io.github.vrcmteam.vrcm.network.api.notification.NotificationApi
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.users.data.UpdateUserInfoData
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.extensions.onApiFailure
import io.github.vrcmteam.vrcm.presentation.screens.home.data.BoopNotificationResolver
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationItemData
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationResponseTarget
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationUserPresentation
import io.github.vrcmteam.vrcm.presentation.screens.home.data.responseTarget
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.FriendLocationPagerModel
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.FriendService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.koin.core.logger.Logger


class HomeScreenModel(
    private val authService: AuthService,
    private val usersApi: UsersApi,
    private val notificationApi: NotificationApi,
    private val friendService: FriendService,
    private val friendLocationPagerModel: FriendLocationPagerModel,
    private val logger: Logger,
) : ViewModel() {

    private val boopNotificationResolver = BoopNotificationResolver()

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

    private val _notifications = mutableStateOf<List<NotificationItemData>>(emptyList())
    val notifications by _notifications

    private val _friendRequestNotifications = mutableStateOf<List<NotificationItemData>>(emptyList())
    val friendRequestNotifications by _friendRequestNotifications

    fun init() {
        friendService.preloadFriendList()
        friendLocationPagerModel.preloadFriendLocations()
        refreshCurrentUser()
        refreshFriendRequestNotification()
        refreshNotifications()
        viewModelScope.launch {
            authService.currentUserState.collect { _currentUser.value = it }
        }
    }

    fun refreshAllNotification() {
        refreshFriendRequestNotification()
        refreshNotifications()
    }

    private fun refreshFriendRequestNotification() =
        viewModelScope.launch(Dispatchers.IO) {
            authService.reTryAuthCatching { notificationApi.fetchNotificationsV2(NotificationType.FriendRequest.value) }
                .onHomeFailure()
                .onSuccess {
                    runCatching {
                        _friendRequestNotifications.value = it.map { data ->
                            val user = usersApi.fetchUser(data.senderUserId)
                            NotificationItemData(
                                id = data.id,
                                imageUrl = user.profileImageUrl,
                                title = user.displayName,
                                message = user.displayName,
                                createdAt = data.createdAt,
                                senderUserId = data.senderUserId,
                                link = "user:${data.senderUserId}",
                                type = data.type.value,
                                actions = listOf(
                                    NotificationItemData.ActionData(
                                        data = "",
                                        type = "Hide"
                                    ),
                                    NotificationItemData.ActionData(
                                        data = "",
                                        type = "Accept"
                                    )
                                )
                            )
                        }
                    }.onHomeFailure()
                }
        }

    private fun refreshNotifications() =
        viewModelScope.launch(Dispatchers.IO) {
            authService.reTryAuthCatching { notificationApi.fetchNotifications() }
                .onHomeFailure()
                .onSuccess { data ->
                    val friendPresentations = friendService.friendMap.mapValues { (_, friend) ->
                        NotificationUserPresentation(
                            imageUrl = friend.profileImageUrl,
                            displayName = friend.displayName,
                        )
                    }
                    _notifications.value = boopNotificationResolver.resolve(
                        notifications = data.map(::NotificationItemData),
                        friends = friendPresentations,
                    ) { userId ->
                        usersApi.fetchUser(userId).let { user ->
                            NotificationUserPresentation(
                                imageUrl = user.profileImageUrl,
                                displayName = user.displayName,
                            )
                        }
                    }
                }
        }

    fun responseAllNotification(
        item: NotificationItemData,
        action: NotificationItemData.ActionData,
        boopSuccessMessage: String,
        boopAlreadySentMessage: String,
    ) {
        when (item.responseTarget(action)) {
            NotificationResponseTarget.BOOP_USER_API -> {
                item.senderId?.let { boopUser(it, boopSuccessMessage, boopAlreadySentMessage) }
                return
            }
            NotificationResponseTarget.NOTIFICATION_API -> Unit
        }

        val id = item.id
        val type = item.type
        if (type == NotificationType.FriendRequest.value) {
            responseFriendRequest(id, action)
        } else {
            responseNotification(id, action)
        }
    }

    private fun responseFriendRequest(id: String, response: NotificationItemData.ActionData) {
        if (response.type == "Accept") {
            acceptFriendRequest(id)
        } else {
            hideNotification(id)
        }
    }

    private fun responseNotification(id: String, response: NotificationItemData.ActionData) = notificationAction {
        notificationApi.responseNotification(id, response)
    }

    private fun boopUser(userId: String, successMessage: String, alreadySentMessage: String) {
        viewModelScope.launch(Dispatchers.IO) {
            authService.reTryAuthCatching { usersApi.boop(userId) }
                .onSuccess {
                    SharedFlowCentre.toastText.emit(ToastText.Success(successMessage))
                }
                .onFailure { error ->
                    if (error is VRCApiException && error.code == 429) {
                        SharedFlowCentre.toastText.emit(ToastText.Info(alreadySentMessage))
                    } else {
                        Result.failure<Unit>(error).onHomeFailure()
                    }
                }
        }
    }


    private fun acceptFriendRequest(notificationId: String) = notificationAction {
        notificationApi.acceptFriendRequest(notificationId)
    }

    private fun hideNotification(notificationId: String) = notificationAction {
        notificationApi.deleteNotification(notificationId)
    }

    private fun notificationAction(action: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            authService.reTryAuthCatching { action() }
                .onHomeFailure()
                .onSuccess {
                    runCatching { refreshAllNotification() }
                        .onHomeFailure()
                }
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



