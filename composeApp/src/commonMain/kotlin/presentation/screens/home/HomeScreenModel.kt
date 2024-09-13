package io.github.vrcmteam.vrcm.presentation.screens.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.NotificationType
import io.github.vrcmteam.vrcm.network.api.auth.data.CurrentUserData
import io.github.vrcmteam.vrcm.network.api.notification.NotificationApi
import io.github.vrcmteam.vrcm.network.api.notification.data.NotificationDataV2
import io.github.vrcmteam.vrcm.network.api.notification.data.ResponseData
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.extensions.onApiFailure
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationItemData
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.koin.core.logger.Logger


class HomeScreenModel(
    private val authService: AuthService,
    private val usersApi: UsersApi,
    private val notificationApi: NotificationApi,
    private val logger: Logger
) : ScreenModel {

    private val _currentUser = mutableStateOf<CurrentUserData?>(null)

    val currentUser by _currentUser

    private val _notifications = mutableStateOf<List<NotificationItemData>>(emptyList())
    val notifications by _notifications

    private val _friendRequestNotifications = mutableStateOf<List<NotificationItemData>>(emptyList())
    val friendRequestNotifications by _friendRequestNotifications

    fun ini()  {
        refreshCurrentUser()
        refreshFriendRequestNotification()
        refreshNotifications()
    }

    fun refreshAllNotification() {
        refreshFriendRequestNotification()
        refreshNotifications()
    }

    fun refreshFriendRequestNotification() =
        screenModelScope.launch(Dispatchers.IO) {
            authService.reTryAuth { notificationApi.fetchNotificationsV2(NotificationType.FriendRequest.value) }
                .onHomeFailure()
                .onSuccess {
                    _friendRequestNotifications.value = it.map { data ->
                        val user = usersApi.fetchUser(data.senderUserId)
                        NotificationItemData(
                            id = data.id,
                            imageUrl = user.profileImageUrl,
                            message = user.displayName,
                            createdAt = data.createdAt,
                            type = data.type.value,
                            actions = listOf(
                                NotificationItemData.ActionData(
                                    data = "",
                                    type = "Accept"
                                ),
                                NotificationItemData.ActionData(
                                    data = "",
                                    type = "Hide"
                                )
                            )
                        )
                    }
                }
        }

    fun refreshNotifications() =
        screenModelScope.launch(Dispatchers.IO) {
            authService.reTryAuth { notificationApi.fetchNotifications() }
                .onHomeFailure()
                .onSuccess {
                    _notifications.value = it.map { data ->
                        NotificationItemData(
                            id = data.id,
                            imageUrl = data.imageUrl,
                            message = data.message,
                            createdAt = data.createdAt,
                            type = data.type,
                            actions = data.responses.map { responses ->
                                NotificationItemData.ActionData(
                                    data = responses.responseData,
                                    type = responses.type
                                )
                            }
                        )
                    }
                }
        }

    fun responseAllNotification(id: String, type: String, action: NotificationItemData.ActionData) {
        if (type == NotificationType.FriendRequest.value) {
            responseFriendRequest(id, action)
        } else {
            responseNotification(id, action)
        }
    }

    fun responseNotification(id: String, response: NotificationItemData.ActionData) {
        screenModelScope.launch(Dispatchers.IO) {
            authService.reTryAuth { notificationApi.responseNotification(id,response) }
                .onHomeFailure()
                .onSuccess {
                    refreshAllNotification()
                }
        }
    }

    fun responseFriendRequest(id: String, response: NotificationItemData.ActionData) {
        if (response.type == "Accept") {
            acceptFriendRequest(id)
        } else {
            hideNotification(id)
        }
    }

    fun acceptFriendRequest(notificationId: String) {
        screenModelScope.launch(Dispatchers.IO) {
            authService.reTryAuth { notificationApi.acceptFriendRequest(notificationId) }
                .onHomeFailure()
                .onSuccess {
                    refreshAllNotification()
                }
        }
    }

    fun hideNotification(notificationId: String) {
        screenModelScope.launch(Dispatchers.IO) {
            authService.reTryAuth { notificationApi.deleteNotification(notificationId) }
                .onHomeFailure()
                .onSuccess {
                    refreshAllNotification()
                }
        }
    }

    private fun refreshCurrentUser() =
        screenModelScope.launch(Dispatchers.IO) {
            authService.reTryAuth { authService.currentUser(isRefresh = true) }
                .onHomeFailure()
                .onSuccess {
                    _currentUser.value = it
                }
        }


    private inline fun <T> Result<T>.onHomeFailure() =
        onApiFailure("Home") {
            logger.error(it)
            screenModelScope.launch {
                SharedFlowCentre.toastText.emit(ToastText.Error(it))
            }
        }
}






