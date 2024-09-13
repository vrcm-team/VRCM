package io.github.vrcmteam.vrcm.presentation.screens.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.NotificationType
import io.github.vrcmteam.vrcm.network.api.auth.data.CurrentUserData
import io.github.vrcmteam.vrcm.network.api.notification.NotificationApi
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
    private val logger: Logger,
) : ScreenModel {

    private val _currentUser = mutableStateOf<CurrentUserData?>(null)

    val currentUser by _currentUser

    private val _notifications = mutableStateOf<List<NotificationItemData>>(emptyList())
    val notifications by _notifications

    private val _friendRequestNotifications = mutableStateOf<List<NotificationItemData>>(emptyList())
    val friendRequestNotifications by _friendRequestNotifications

    fun ini() {
        refreshCurrentUser()
        refreshFriendRequestNotification()
        refreshNotifications()
    }

    fun refreshAllNotification() {
        refreshFriendRequestNotification()
        refreshNotifications()
    }

    private fun refreshFriendRequestNotification() =
        screenModelScope.launch(Dispatchers.IO) {
            authService.reTryAuthCatching { notificationApi.fetchNotificationsV2(NotificationType.FriendRequest.value) }
                .onHomeFailure()
                .onSuccess {
                    runCatching {
                        _friendRequestNotifications.value = it.map { data ->
                            val user = usersApi.fetchUser(data.senderUserId)
                            NotificationItemData(
                                id = data.id,
                                imageUrl = user.profileImageUrl,
                                message = user.displayName,
                                createdAt = data.createdAt,
                                senderUserId = data.senderUserId,
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
        screenModelScope.launch(Dispatchers.IO) {
            authService.reTryAuthCatching { notificationApi.fetchNotifications() }
                .onHomeFailure()
                .onSuccess {
                    _notifications.value = it.map { data ->
                        NotificationItemData(data)
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


    private fun acceptFriendRequest(notificationId: String) = notificationAction {
        notificationApi.acceptFriendRequest(notificationId)
    }

    private fun hideNotification(notificationId: String) = notificationAction {
        notificationApi.deleteNotification(notificationId)
    }

    private fun notificationAction(action: suspend () -> Unit) {
        screenModelScope.launch(Dispatchers.IO) {
            authService.reTryAuthCatching { action() }
                .onHomeFailure()
                .onSuccess {
                    runCatching { refreshAllNotification() }
                        .onHomeFailure()
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






