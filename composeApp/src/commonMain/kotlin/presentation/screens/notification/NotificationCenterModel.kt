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
import io.github.vrcmteam.vrcm.presentation.screens.home.data.unreadCount
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.BoopResult
import io.github.vrcmteam.vrcm.service.BoopService
import io.github.vrcmteam.vrcm.service.FriendService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.atomicfu.atomic
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
    private val refreshGeneration = atomic(0)
    private var refreshJob: Job? = null

    var notifications by mutableStateOf<List<NotificationItemData>>(emptyList())
        private set

    var friendRequestNotifications by mutableStateOf<List<NotificationItemData>>(emptyList())
        private set

    var pendingNotificationActions by
        mutableStateOf<Map<String, NotificationItemData.ActionData>>(emptyMap())
        private set

    var pendingReadNotificationIds by mutableStateOf<Set<String>>(emptySet())
        private set

    var pendingDeleteNotificationIds by mutableStateOf<Set<String>>(emptySet())
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    var hasRefreshError by mutableStateOf(false)
        private set

    val unreadCount: Int
        get() = (friendRequestNotifications + notifications).unreadCount

    val hasUnread: Boolean
        get() = unreadCount > 0

    init {
        modelScope.launch {
            SharedFlowCentre.currentSession.collectLatest { session ->
                refreshJob?.cancel()
                notifications = emptyList()
                friendRequestNotifications = emptyList()
                pendingNotificationActions = emptyMap()
                pendingReadNotificationIds = emptySet()
                pendingDeleteNotificationIds = emptySet()
                isRefreshing = false
                hasRefreshError = false
                session?.token?.let(::refreshAllNotification)
            }
        }
    }

    fun refreshAllNotification() {
        SharedFlowCentre.currentSession.value?.token?.let(::refreshAllNotification)
    }

    private fun refreshAllNotification(token: AccountSessionToken) {
        val generation = refreshGeneration.incrementAndGet()
        refreshJob?.cancel()
        refreshJob = modelScope.launch(Dispatchers.IO) {
            if (SharedFlowCentre.isCurrentSession(token)) {
                isRefreshing = true
                hasRefreshError = false
            }
            try {
                val (friendRequestsResult, notificationsResult) = supervisorScope {
                    async { loadFriendRequests() } to async { loadNotifications() }
                }.let { (friendRequests, notifications) ->
                    friendRequests.await() to notifications.await()
                }
                if (!SharedFlowCentre.isCurrentSession(token) || generation != refreshGeneration.value) return@launch

                friendRequestsResult
                    .onFailure { if (it is CancellationException) throw it }
                    .onNotificationFailure()
                    .onSuccess { friendRequestNotifications = it }
                notificationsResult
                    .onFailure { if (it is CancellationException) throw it }
                    .onNotificationFailure()
                    .onSuccess { notifications = it }
                hasRefreshError = friendRequestsResult.isFailure || notificationsResult.isFailure
            } finally {
                if (SharedFlowCentre.isCurrentSession(token) && generation == refreshGeneration.value) {
                    isRefreshing = false
                }
            }
        }
    }

    private suspend fun loadFriendRequests(): Result<List<NotificationItemData>> =
        authService.reTryAuthCatching {
            notificationApi.fetchNotificationsV2(NotificationType.FriendRequest.value)
        }.mapCatching { data ->
            data.map { notification ->
                val user = usersApi.fetchUser(notification.senderUserId)
                NotificationItemData(
                    n = notification,
                    imageUrl = user.profileImageUrl,
                    title = user.displayName,
                    actions = listOf(
                        NotificationItemData.ActionData(data = "", type = "Hide"),
                        NotificationItemData.ActionData(data = "", type = "Accept"),
                    ),
                )
            }
        }

    private suspend fun loadNotifications(): Result<List<NotificationItemData>> =
        authService.reTryAuthCatching { notificationApi.fetchNotifications() }
            .mapCatching { data ->
                val friendPresentations = friendService.friendMap.mapValues { (_, friend) ->
                    NotificationUserPresentation(
                        imageUrl = friend.profileImageUrl,
                        displayName = friend.displayName,
                    )
                }
                boopNotificationResolver.resolve(
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

    fun respondToNotification(
        item: NotificationItemData,
        action: NotificationItemData.ActionData,
        boopEmojiId: String? = null,
        boopSuccessMessage: String,
        boopAlreadySentMessage: String,
        boopDisabledMessage: String,
    ) {
        if (isNotificationPending(item.id)) return
        if (
            item.type == NotificationType.FriendRequest.value &&
            !action.type.equals("Accept", ignoreCase = true)
        ) {
            deleteNotification(item)
            return
        }
        pendingNotificationActions += item.id to action
        val token = SharedFlowCentre.currentSession.value?.token
        if (token == null) {
            finishNotificationAction(item.id)
            return
        }

        when (item.responseTarget(action)) {
            NotificationResponseTarget.BOOP_USER_API -> {
                val senderId = item.senderId
                if (senderId == null) {
                    finishNotificationAction(item.id)
                    return
                }
                boopUser(
                    notificationId = item.id,
                    token = token,
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
            notificationAction(item, token) { notificationApi.acceptFriendRequest(item.id) }
        } else {
            notificationAction(item, token) { notificationApi.responseNotification(item.id, action) }
        }
    }

    fun markNotificationAsRead(item: NotificationItemData) {
        if (item.seen || isNotificationPending(item.id)) return
        val token = SharedFlowCentre.currentSession.value?.token ?: return
        pendingReadNotificationIds += item.id
        modelScope.launch(Dispatchers.IO) {
            try {
                authService.reTryAuthCatching { notificationApi.markNotificationAsRead(item.id) }
                    .onNotificationFailure()
                    .onSuccess {
                        if (SharedFlowCentre.isCurrentSession(token)) updateSeen(item.id)
                    }
            } finally {
                if (SharedFlowCentre.isCurrentSession(token)) {
                    pendingReadNotificationIds -= item.id
                }
            }
        }
    }

    fun deleteNotification(item: NotificationItemData) {
        if (isNotificationPending(item.id)) return
        val token = SharedFlowCentre.currentSession.value?.token ?: return
        pendingDeleteNotificationIds += item.id
        modelScope.launch(Dispatchers.IO) {
            try {
                val result = authService.reTryAuthCatching {
                    if (item.type == NotificationType.FriendRequest.value) {
                        notificationApi.deleteNotification(item.id)
                    } else {
                        notificationApi.deleteNotificationV2(item.id)
                    }
                }
                result.onNotificationFailure().onSuccess {
                    if (SharedFlowCentre.isCurrentSession(token)) removeNotification(item)
                }
            } finally {
                if (SharedFlowCentre.isCurrentSession(token)) {
                    pendingDeleteNotificationIds -= item.id
                }
            }
        }
    }

    fun isNotificationPending(notificationId: String): Boolean =
        notificationId in pendingNotificationActions ||
            notificationId in pendingReadNotificationIds ||
            notificationId in pendingDeleteNotificationIds

    private fun boopUser(
        notificationId: String,
        token: AccountSessionToken,
        userId: String,
        emojiId: String?,
        successMessage: String,
        alreadySentMessage: String,
        disabledMessage: String,
    ) {
        modelScope.launch(Dispatchers.IO) {
            try {
                when (val result = boopService.send(userId, emojiId)) {
                    BoopResult.Sent -> {
                        SharedFlowCentre.toastText.emit(ToastText.Success(successMessage))
                    }

                    BoopResult.Cooldown -> SharedFlowCentre.toastText.emit(ToastText.Info(alreadySentMessage))
                    BoopResult.Disabled -> SharedFlowCentre.toastText.emit(ToastText.Error(disabledMessage))
                    is BoopResult.Failed -> Result.failure<Unit>(result.error).onNotificationFailure()
                    BoopResult.InFlight, BoopResult.SessionChanged -> Unit
                }
            } finally {
                if (SharedFlowCentre.isCurrentSession(token)) finishNotificationAction(notificationId)
            }
        }
    }

    private fun notificationAction(
        item: NotificationItemData,
        token: AccountSessionToken,
        action: suspend () -> Unit,
    ) {
        modelScope.launch(Dispatchers.IO) {
            try {
                authService.reTryAuthCatching { action() }
                    .onNotificationFailure()
                    .onSuccess {
                        if (SharedFlowCentre.isCurrentSession(token)) {
                            refreshAllNotification(token)
                        }
                    }
            } finally {
                if (SharedFlowCentre.isCurrentSession(token)) finishNotificationAction(item.id)
            }
        }
    }

    private fun updateSeen(notificationId: String) {
        notifications = notifications.map { item ->
            if (item.id == notificationId) item.copy(seen = true) else item
        }
        friendRequestNotifications = friendRequestNotifications.map { item ->
            if (item.id == notificationId) item.copy(seen = true) else item
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
