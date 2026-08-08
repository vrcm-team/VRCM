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
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.extensions.onApiFailure
import io.github.vrcmteam.vrcm.presentation.screens.home.data.BoopNotificationResolver
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationItemData
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationResponseTarget
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationUserPresentation
import io.github.vrcmteam.vrcm.presentation.screens.home.data.responseTarget
import io.github.vrcmteam.vrcm.presentation.navigation.AppRoute
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.FriendLocationPagerModel
import io.github.vrcmteam.vrcm.presentation.screens.meetup.MeetupCardDisplayRoute
import io.github.vrcmteam.vrcm.presentation.screens.meetup.MeetupCardEditorRoute
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.meetup.MeetupCardRepository
import io.github.vrcmteam.vrcm.service.FriendService
import io.github.vrcmteam.vrcm.service.BoopResult
import io.github.vrcmteam.vrcm.service.BoopService
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
    private val boopService: BoopService,
    private val meetupCardRepository: MeetupCardRepository,
) : ViewModel() {

    /** 长按头像的入口分流：已有配置直接展示，首次使用进入编辑器。 */
    fun meetupCardStartRoute(): AppRoute = if (meetupCardRepository.hasConfig(userId)) {
        MeetupCardDisplayRoute(userId)
    } else {
        MeetupCardEditorRoute(userId)
    }

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

    private val _pendingNotificationActions =
        mutableStateOf<Map<String, NotificationItemData.ActionData>>(emptyMap())
    val pendingNotificationActions by _pendingNotificationActions

    init {
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
        if (_pendingNotificationActions.value.containsKey(item.id)) return
        _pendingNotificationActions.value += item.id to action

        when (item.responseTarget(action)) {
            NotificationResponseTarget.BOOP_USER_API -> {
                val senderId = item.senderId
                if (senderId == null) {
                    finishNotificationAction(item.id)
                    return
                }
                boopUser(item.id, senderId, boopSuccessMessage, boopAlreadySentMessage)
                return
            }
            NotificationResponseTarget.NOTIFICATION_API -> Unit
        }

        val type = item.type
        if (type == NotificationType.FriendRequest.value) {
            responseFriendRequest(item, action)
        } else {
            responseNotification(item, action)
        }
    }

    private fun responseFriendRequest(item: NotificationItemData, response: NotificationItemData.ActionData) {
        if (response.type == "Accept") {
            acceptFriendRequest(item)
        } else {
            hideNotification(item)
        }
    }

    private fun responseNotification(item: NotificationItemData, response: NotificationItemData.ActionData) =
        notificationAction(item) {
            notificationApi.responseNotification(item.id, response)
        }

    private fun removeNotification(item: NotificationItemData) {
        if (item.type == NotificationType.FriendRequest.value) {
            _friendRequestNotifications.value = _friendRequestNotifications.value.filterNot { it.id == item.id }
        } else {
            _notifications.value = _notifications.value.filterNot { it.id == item.id }
        }
    }

    private fun boopUser(
        notificationId: String,
        userId: String,
        successMessage: String,
        alreadySentMessage: String,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (val result = boopService.send(userId)) {
                    BoopResult.Sent -> {
                        _notifications.value = _notifications.value.filterNot { it.id == notificationId }
                        authService.reTryAuthCatching { notificationApi.deleteNotification(notificationId) }
                            .onFailure { error -> Result.failure<Unit>(error).onHomeFailure() }
                        SharedFlowCentre.toastText.emit(ToastText.Success(successMessage))
                    }
                    BoopResult.Cooldown -> {
                        SharedFlowCentre.toastText.emit(ToastText.Info(alreadySentMessage))
                    }
                    is BoopResult.Failed -> Result.failure<Unit>(result.error).onHomeFailure()
                    BoopResult.InFlight, BoopResult.SessionChanged -> Unit
                }
            } finally {
                finishNotificationAction(notificationId)
            }
        }
    }


    private fun acceptFriendRequest(item: NotificationItemData) = notificationAction(item) {
        notificationApi.acceptFriendRequest(item.id)
    }

    private fun hideNotification(item: NotificationItemData) = notificationAction(item) {
        notificationApi.deleteNotification(item.id)
    }

    private fun notificationAction(item: NotificationItemData, action: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                authService.reTryAuthCatching { action() }
                    .onHomeFailure()
                    .onSuccess {
                        removeNotification(item)
                        refreshAllNotification()
                    }
            } finally {
                finishNotificationAction(item.id)
            }
        }
    }

    private fun finishNotificationAction(notificationId: String) {
        _pendingNotificationActions.value -= notificationId
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
