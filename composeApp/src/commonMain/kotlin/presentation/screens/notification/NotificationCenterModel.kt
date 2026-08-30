package io.github.vrcmteam.vrcm.presentation.screens.notification

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.vrcmteam.vrcm.core.shared.AccountWebSocketEvent
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.NotificationType
import io.github.vrcmteam.vrcm.network.api.notification.NotificationApi
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.websocket.data.type.NotificationEvents
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.extensions.onApiFailure
import io.github.vrcmteam.vrcm.presentation.screens.home.data.BoopNotificationResolver
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationInboxState
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationItemData
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationReadTarget
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationResponseTarget
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationSource
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationUserPresentation
import io.github.vrcmteam.vrcm.presentation.screens.home.data.readTarget
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
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
    private val refreshRequests = Channel<AccountSessionToken>(Channel.CONFLATED)
    private var refreshJob: Job? = null

    private var inboxState by mutableStateOf(NotificationInboxState())

    val notifications: List<NotificationItemData>
        get() = inboxState.pipeline

    val friendRequestNotifications: List<NotificationItemData>
        get() = inboxState.legacy

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
            for (token in refreshRequests) {
                refreshJob = launch(Dispatchers.IO) { refreshAllNotification(token) }
                refreshJob?.join()
            }
        }
        modelScope.launch {
            SharedFlowCentre.currentSession.collectLatest { session ->
                refreshJob?.cancel()
                inboxState = NotificationInboxState()
                pendingNotificationActions = emptyMap()
                pendingReadNotificationIds = emptySet()
                pendingDeleteNotificationIds = emptySet()
                isRefreshing = false
                hasRefreshError = false
                session?.token?.let(::queueNotificationRefresh)
            }
        }
        modelScope.launch {
            SharedFlowCentre.webSocket.collect { event ->
                event.notificationRefreshToken(SharedFlowCentre.currentSession.value?.token)
                    ?.let(::queueNotificationRefresh)
            }
        }
    }

    fun refreshAllNotification() {
        SharedFlowCentre.currentSession.value?.token?.let(::queueNotificationRefresh)
    }

    private fun queueNotificationRefresh(token: AccountSessionToken) {
        if (SharedFlowCentre.isCurrentSession(token)) refreshRequests.trySend(token)
    }

    private suspend fun refreshAllNotification(token: AccountSessionToken) {
        if (!SharedFlowCentre.isCurrentSession(token)) return
        isRefreshing = true
        hasRefreshError = false
        try {
            val (friendRequestsResult, notificationsResult) = supervisorScope {
                async { loadFriendRequests() } to async { loadNotifications() }
            }.let { (friendRequests, notifications) ->
                friendRequests.await() to notifications.await()
            }
            if (!SharedFlowCentre.isCurrentSession(token)) return

            friendRequestsResult
                .onFailure { if (it is CancellationException) throw it }
                .onNotificationFailure()
                .onSuccess {
                    inboxState = inboxState.replace(NotificationSource.LEGACY, it)
                }
            notificationsResult
                .onFailure { if (it is CancellationException) throw it }
                .onNotificationFailure()
                .onSuccess {
                    inboxState = inboxState.replace(NotificationSource.PIPELINE, it)
                }
            hasRefreshError = friendRequestsResult.isFailure || notificationsResult.isFailure
        } finally {
            if (SharedFlowCentre.isCurrentSession(token)) isRefreshing = false
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
        val responseTarget = item.responseTarget(action)
        if (responseTarget == NotificationResponseTarget.NAVIGATION_LINK) return
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

        when (responseTarget) {
            NotificationResponseTarget.BOOP_USER_API -> {
                val senderId = item.senderId
                if (senderId == null) {
                    finishNotificationAction(item.id)
                    return
                }
                boopUser(
                    item = item,
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
            NotificationResponseTarget.NAVIGATION_LINK -> return
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
                val result = runNotificationMutation(token) {
                    when (item.readTarget) {
                        NotificationReadTarget.PIPELINE_SEE ->
                            notificationApi.markPipelineNotificationAsRead(item.id)
                        NotificationReadTarget.LEGACY_SEE ->
                            notificationApi.markLegacyNotificationAsRead(item.id)
                    }
                } ?: return@launch
                result
                    .onNotificationFailure()
                    .onSuccess {
                        if (SharedFlowCentre.isCurrentSession(token)) {
                            inboxState = inboxState.markSeen(item)
                        }
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
                val result = runNotificationMutation(token) {
                    deleteRemoteNotification(item)
                } ?: return@launch
                if (SharedFlowCentre.isCurrentSession(token)) {
                    inboxState = inboxState.afterNotificationAction(item, result)
                }
                result.onNotificationFailure()
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
        item: NotificationItemData,
        token: AccountSessionToken,
        userId: String,
        emojiId: String?,
        successMessage: String,
        alreadySentMessage: String,
        disabledMessage: String,
    ) {
        modelScope.launch(Dispatchers.IO) {
            try {
                val result = boopService.send(userId, emojiId)
                if (!SharedFlowCentre.isCurrentSession(token)) return@launch
                inboxState = inboxState.afterBoopResult(item, result)
                when (result) {
                    BoopResult.Sent -> {
                        SharedFlowCentre.toastText.emit(ToastText.Success(successMessage))
                        runNotificationMutation(token) { deleteRemoteNotification(item) }
                            ?.onNotificationFailure()
                    }

                    BoopResult.Cooldown -> SharedFlowCentre.toastText.emit(ToastText.Info(alreadySentMessage))
                    BoopResult.Disabled -> SharedFlowCentre.toastText.emit(ToastText.Error(disabledMessage))
                    is BoopResult.Failed -> Result.failure<Unit>(result.error).onNotificationFailure()
                    BoopResult.InFlight, BoopResult.SessionChanged -> Unit
                }
            } finally {
                if (SharedFlowCentre.isCurrentSession(token)) finishNotificationAction(item.id)
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
                val result = runNotificationMutation(token) { action() } ?: return@launch
                if (SharedFlowCentre.isCurrentSession(token)) {
                    inboxState = inboxState.afterNotificationAction(item, result)
                }
                result
                    .onNotificationFailure()
                    .onSuccess {
                        if (SharedFlowCentre.isCurrentSession(token)) {
                            queueNotificationRefresh(token)
                        }
                    }
            } finally {
                if (SharedFlowCentre.isCurrentSession(token)) finishNotificationAction(item.id)
            }
        }
    }

    private suspend fun deleteRemoteNotification(item: NotificationItemData) {
        when (item.source) {
            NotificationSource.PIPELINE -> notificationApi.deleteNotificationV2(item.id)
            NotificationSource.LEGACY -> notificationApi.deleteNotification(item.id)
        }
    }

    private suspend fun <T> runNotificationMutation(
        token: AccountSessionToken,
        action: suspend () -> T,
    ): Result<T>? {
        val response = authService.runSessionBoundCatching(token, action) ?: return null
        return response.result.takeIf { SharedFlowCentre.isCurrentSession(response.sessionToken) }
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

internal fun NotificationInboxState.afterBoopResult(
    item: NotificationItemData,
    result: BoopResult,
): NotificationInboxState = if (result == BoopResult.Sent) consume(item) else this

internal fun NotificationInboxState.afterNotificationAction(
    item: NotificationItemData,
    result: Result<*>,
): NotificationInboxState = if (result.isSuccess) consume(item) else this

internal fun AccountWebSocketEvent.notificationRefreshToken(
    currentToken: AccountSessionToken?,
): AccountSessionToken? {
    if (token != currentToken) return null
    val refreshRequired = when (event.type) {
        NotificationEvents.Notification.typeName,
        NotificationEvents.NotificationV2.typeName,
        NotificationEvents.NotificationV2Update.typeName,
        NotificationEvents.ResponseNotification.typeName,
        NotificationEvents.SeeNotification.typeName,
        NotificationEvents.HideNotification.typeName,
        NotificationEvents.ClearNotification.typeName -> true
        else -> false
    }
    return token.takeIf { refreshRequired }
}
