package io.github.vrcmteam.vrcm.presentation.screens.notification

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.NotificationType
import io.github.vrcmteam.vrcm.network.api.notification.NotificationApi
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.extensions.onApiFailure
import io.github.vrcmteam.vrcm.presentation.screens.home.data.BoopNotificationResolver
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationItemData
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationResponseTarget
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationUserPresentation
import io.github.vrcmteam.vrcm.presentation.screens.home.data.responseTarget
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.BoopResult
import io.github.vrcmteam.vrcm.service.BoopService
import io.github.vrcmteam.vrcm.service.FriendService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.logger.Logger

/** Application-scoped notification state shared by the home badge and notification screen. */
class NotificationCenterModel(
    private val authService: AuthService,
    private val usersApi: UsersApi,
    private val notificationApi: NotificationApi,
    private val friendService: FriendService,
    private val logger: Logger,
    private val boopService: BoopService,
) {
    private val modelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val boopNotificationResolver = BoopNotificationResolver()

    var notifications by mutableStateOf<List<NotificationItemData>>(emptyList())
        private set

    var friendRequestNotifications by mutableStateOf<List<NotificationItemData>>(emptyList())
        private set

    var pendingNotificationActions by
        mutableStateOf<Map<String, NotificationItemData.ActionData>>(emptyMap())
        private set

    init {
        modelScope.launch {
            SharedFlowCentre.currentSession.collectLatest { session ->
                notifications = emptyList()
                friendRequestNotifications = emptyList()
                pendingNotificationActions = emptyMap()
                session?.token?.let(::refreshAllNotification)
            }
        }
    }

    fun refreshAllNotification() {
        SharedFlowCentre.currentSession.value?.token?.let(::refreshAllNotification)
    }

    private fun refreshAllNotification(token: AccountSessionToken) {
        refreshFriendRequestNotification(token)
        refreshNotifications(token)
    }

    private fun refreshFriendRequestNotification(token: AccountSessionToken) =
        modelScope.launch(Dispatchers.IO) {
            authService.reTryAuthCatching {
                notificationApi.fetchNotificationsV2(NotificationType.FriendRequest.value)
            }.onNotificationFailure().onSuccess { data ->
                runCatching {
                    data.map { notification ->
                        val user = usersApi.fetchUser(notification.senderUserId)
                        NotificationItemData(
                            id = notification.id,
                            imageUrl = user.profileImageUrl,
                            title = user.displayName,
                            message = user.displayName,
                            createdAt = notification.createdAt,
                            senderUserId = notification.senderUserId,
                            link = "user:${notification.senderUserId}",
                            type = notification.type.value,
                            actions = listOf(
                                NotificationItemData.ActionData(data = "", type = "Hide"),
                                NotificationItemData.ActionData(data = "", type = "Accept"),
                            ),
                        )
                    }
                }.onNotificationFailure().onSuccess { resolved ->
                    if (SharedFlowCentre.isCurrentSession(token)) {
                        friendRequestNotifications = resolved
                    }
                }
            }
        }

    private fun refreshNotifications(token: AccountSessionToken) =
        modelScope.launch(Dispatchers.IO) {
            authService.reTryAuthCatching { notificationApi.fetchNotifications() }
                .onNotificationFailure()
                .onSuccess { data ->
                    val friendPresentations = friendService.friendMap.mapValues { (_, friend) ->
                        NotificationUserPresentation(
                            imageUrl = friend.profileImageUrl,
                            displayName = friend.displayName,
                        )
                    }
                    val resolved = boopNotificationResolver.resolve(
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
                    if (SharedFlowCentre.isCurrentSession(token)) notifications = resolved
                }
        }

    fun responseAllNotification(
        item: NotificationItemData,
        action: NotificationItemData.ActionData,
        boopEmojiId: String? = null,
        boopSuccessMessage: String,
        boopAlreadySentMessage: String,
        boopDisabledMessage: String,
    ) {
        if (pendingNotificationActions.containsKey(item.id)) return
        pendingNotificationActions += item.id to action

        when (item.responseTarget(action)) {
            NotificationResponseTarget.BOOP_USER_API -> {
                val senderId = item.senderId
                if (senderId == null) {
                    finishNotificationAction(item.id)
                    return
                }
                boopUser(
                    notificationId = item.id,
                    userId = senderId,
                    emojiId = boopEmojiId,
                    successMessage = boopSuccessMessage,
                    alreadySentMessage = boopAlreadySentMessage,
                    disabledMessage = boopDisabledMessage,
                )
                return
            }

            NotificationResponseTarget.NOTIFICATION_API -> Unit
        }

        if (item.type == NotificationType.FriendRequest.value) {
            if (action.type == "Accept") {
                notificationAction(item) { notificationApi.acceptFriendRequest(item.id) }
            } else {
                notificationAction(item) { notificationApi.deleteNotification(item.id) }
            }
        } else {
            notificationAction(item) { notificationApi.responseNotification(item.id, action) }
        }
    }

    private fun boopUser(
        notificationId: String,
        userId: String,
        emojiId: String?,
        successMessage: String,
        alreadySentMessage: String,
        disabledMessage: String,
    ) {
        val token = SharedFlowCentre.currentSession.value?.token
        modelScope.launch(Dispatchers.IO) {
            try {
                when (val result = boopService.send(userId, emojiId)) {
                    BoopResult.Sent -> {
                        authService.reTryAuthCatching { notificationApi.deleteNotificationV2(notificationId) }
                            .onNotificationFailure()
                            .onSuccess {
                                if (token != null && SharedFlowCentre.isCurrentSession(token)) {
                                    notifications = notifications.filterNot { it.id == notificationId }
                                }
                            }
                        SharedFlowCentre.toastText.emit(ToastText.Success(successMessage))
                    }

                    BoopResult.Cooldown -> SharedFlowCentre.toastText.emit(ToastText.Info(alreadySentMessage))
                    BoopResult.Disabled -> SharedFlowCentre.toastText.emit(ToastText.Error(disabledMessage))
                    is BoopResult.Failed -> Result.failure<Unit>(result.error).onNotificationFailure()
                    BoopResult.InFlight, BoopResult.SessionChanged -> Unit
                }
            } finally {
                finishNotificationAction(notificationId)
            }
        }
    }

    private fun notificationAction(item: NotificationItemData, action: suspend () -> Unit) {
        val token = SharedFlowCentre.currentSession.value?.token
        modelScope.launch(Dispatchers.IO) {
            try {
                authService.reTryAuthCatching { action() }
                    .onNotificationFailure()
                    .onSuccess {
                        if (token != null && SharedFlowCentre.isCurrentSession(token)) {
                            removeNotification(item)
                            refreshAllNotification(token)
                        }
                    }
            } finally {
                finishNotificationAction(item.id)
            }
        }
    }

    private fun removeNotification(item: NotificationItemData) {
        if (item.type == NotificationType.FriendRequest.value) {
            friendRequestNotifications = friendRequestNotifications.filterNot { it.id == item.id }
        } else {
            notifications = notifications.filterNot { it.id == item.id }
        }
    }

    private fun finishNotificationAction(notificationId: String) {
        pendingNotificationActions -= notificationId
    }

    private inline fun <T> Result<T>.onNotificationFailure() =
        onApiFailure("NotificationCenter") {
            logger.error(it)
            modelScope.launch { SharedFlowCentre.toastText.emit(ToastText.Error(it)) }
        }
}
