package io.github.vrcmteam.vrcm.presentation.screens.notification

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
import io.github.vrcmteam.vrcm.presentation.screens.home.data.identity
import io.github.vrcmteam.vrcm.presentation.screens.home.data.readTarget
import io.github.vrcmteam.vrcm.presentation.screens.home.data.responseTarget
import io.github.vrcmteam.vrcm.presentation.screens.home.data.unreadCount
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.BoopResult
import io.github.vrcmteam.vrcm.service.BoopService
import io.github.vrcmteam.vrcm.service.FriendService
import io.github.vrcmteam.vrcm.service.UserProfileEnrichmentService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.koin.core.logger.Logger

/** Application-scoped notification state shared by the home badge and notification screen. */
class NotificationCenterModel(
    private val authService: AuthService,
    private val usersApi: UsersApi,
    private val userProfileEnrichmentService: UserProfileEnrichmentService,
    private val notificationApi: NotificationApi,
    private val friendService: FriendService,
    private val logger: Logger,
    private val boopService: BoopService,
) : AutoCloseable {
    private val modelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val boopNotificationResolver = BoopNotificationResolver()
    private val refreshRequests = Channel<AccountSessionToken>(Channel.CONFLATED)
    private var refreshJob: Job? = null
    private val stateStore = NotificationCenterStateStore(
        scope = modelScope,
        initialState = NotificationCenterUiState(
            sessionToken = SharedFlowCentre.currentSession.value?.token,
        ),
        reducerDispatcher = Dispatchers.Main.immediate,
    )
    private val state: NotificationCenterUiState
        get() = stateStore.value

    val notifications: List<NotificationItemData>
        get() = state.inboxState.pipeline

    val friendRequestNotifications: List<NotificationItemData>
        get() = state.inboxState.legacy

    val isRefreshing: Boolean
        get() = state.isRefreshing

    val hasRefreshError: Boolean
        get() = state.hasRefreshError

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
                reduceState { NotificationCenterUiState(sessionToken = session?.token) }
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
        reduceForSession(token) { it.copy(isRefreshing = true, hasRefreshError = false) }
        try {
            val (friendRequestsResult, notificationsResult) = supervisorScope {
                async { loadFriendRequests(token) } to async { loadNotifications() }
            }.let { (friendRequests, notifications) ->
                friendRequests.await() to notifications.await()
            }
            if (!SharedFlowCentre.isCurrentSession(token)) return

            friendRequestsResult
                .onFailure { if (it is CancellationException) throw it }
                .onNotificationFailure()
            notificationsResult
                .onFailure { if (it is CancellationException) throw it }
                .onNotificationFailure()
            reduceForSession(token) { current ->
                val withFriendRequests = friendRequestsResult.getOrNull()?.let { notifications ->
                    current.inboxState.replace(NotificationSource.LEGACY, notifications)
                } ?: current.inboxState
                val refreshedInbox = notificationsResult.getOrNull()?.let { notifications ->
                    withFriendRequests.replace(NotificationSource.PIPELINE, notifications)
                } ?: withFriendRequests
                current.copy(
                    inboxState = refreshedInbox,
                    hasRefreshError = friendRequestsResult.isFailure || notificationsResult.isFailure,
                )
            }
        } finally {
            if (SharedFlowCentre.isCurrentSession(token)) {
                reduceForSession(token) { it.copy(isRefreshing = false) }
            }
        }
    }

    private fun reduceState(
        reducer: (NotificationCenterUiState) -> NotificationCenterUiState,
    ) {
        stateStore.reduce(reducer)
    }

    private fun reduceForSession(
        token: AccountSessionToken,
        reducer: (NotificationCenterUiState) -> NotificationCenterUiState,
    ) {
        reduceState { current ->
            if (current.sessionToken == token) reducer(current) else current
        }
    }

    private suspend fun loadFriendRequests(
        token: AccountSessionToken,
    ): Result<List<NotificationItemData>> =
        authService.reTryAuthCatching {
            notificationApi.fetchNotificationsV2(NotificationType.FriendRequest.value)
        }.mapCatching { data ->
            val usersById = userProfileEnrichmentService.fetchProfiles(
                sessionToken = token,
                userIds = data.map { it.senderUserId },
            )
            data.map { notification ->
                val user = usersById[notification.senderUserId]
                NotificationItemData(
                    n = notification,
                    imageUrl = user?.profileImageUrl.orEmpty(),
                    title = user?.displayName ?: notification.senderUserId,
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
        val responseTarget = item.responseTarget(action)
        if (responseTarget == NotificationResponseTarget.NAVIGATION_LINK) return
        if (
            item.type == NotificationType.FriendRequest.value &&
            !action.type.equals("Accept", ignoreCase = true)
        ) {
            deleteNotification(item)
            return
        }
        launchReservedMutation(item, PendingNotificationMutation.Action(action)) { token ->
            when (responseTarget) {
                NotificationResponseTarget.BOOP_USER_API -> {
                    val senderId = item.senderId ?: return@launchReservedMutation
                    boopUser(
                        item = item,
                        token = token,
                        userId = senderId,
                        emojiId = boopEmojiId,
                        successMessage = boopSuccessMessage,
                        alreadySentMessage = boopAlreadySentMessage,
                        disabledMessage = boopDisabledMessage,
                    )
                }

                NotificationResponseTarget.NOTIFICATION_API -> {
                    if (item.type == NotificationType.FriendRequest.value) {
                        notificationAction(item, token) { notificationApi.acceptFriendRequest(item.id) }
                    } else {
                        notificationAction(item, token) {
                            notificationApi.responseNotification(item.id, action)
                        }
                    }
                }

                NotificationResponseTarget.NAVIGATION_LINK -> Unit
            }
        }
    }

    fun markNotificationAsRead(item: NotificationItemData) {
        if (item.seen) return
        launchReservedMutation(item, PendingNotificationMutation.Read) { token ->
            val result = runNotificationMutation(token) {
                when (item.readTarget) {
                    NotificationReadTarget.PIPELINE_SEE ->
                        notificationApi.markPipelineNotificationAsRead(item.id)
                    NotificationReadTarget.LEGACY_SEE ->
                        notificationApi.markLegacyNotificationAsRead(item.id)
                }
            } ?: return@launchReservedMutation
            result
                .onNotificationFailure()
                .onSuccess {
                    reduceForSession(token) { current ->
                        current.copy(inboxState = current.inboxState.markSeen(item))
                    }
                }
        }
    }

    fun deleteNotification(item: NotificationItemData) {
        launchReservedMutation(item, PendingNotificationMutation.Delete) { token ->
            val result = runNotificationMutation(token) {
                deleteRemoteNotification(item)
            } ?: return@launchReservedMutation
            reduceForSession(token) { current ->
                current.copy(
                    inboxState = current.inboxState.afterNotificationAction(item, result),
                )
            }
            result.onNotificationFailure()
        }
    }

    internal fun pendingAction(item: NotificationItemData): NotificationItemData.ActionData? =
        (state.pendingMutations[item.identity] as? PendingNotificationMutation.Action)?.action

    internal fun isNotificationPending(item: NotificationItemData): Boolean =
        item.identity in state.pendingMutations

    private fun launchReservedMutation(
        item: NotificationItemData,
        mutation: PendingNotificationMutation,
        block: suspend (AccountSessionToken) -> Unit,
    ) {
        val token = SharedFlowCentre.currentSession.value?.token ?: return
        modelScope.launch {
            if (!stateStore.reserveMutation(token, item.identity, mutation)) return@launch
            modelScope.launch(Dispatchers.IO) {
                try {
                    block(token)
                } finally {
                    finishNotificationMutation(item, token)
                }
            }
        }
    }

    private suspend fun boopUser(
        item: NotificationItemData,
        token: AccountSessionToken,
        userId: String,
        emojiId: String?,
        successMessage: String,
        alreadySentMessage: String,
        disabledMessage: String,
    ) {
        val result = boopService.send(userId, emojiId)
        if (!SharedFlowCentre.isCurrentSession(token)) return
        reduceForSession(token) { current ->
            current.copy(inboxState = current.inboxState.afterBoopResult(item, result))
        }
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
    }

    private suspend fun notificationAction(
        item: NotificationItemData,
        token: AccountSessionToken,
        action: suspend () -> Unit,
    ) {
        val result = runNotificationMutation(token) { action() } ?: return
        reduceForSession(token) { current ->
            current.copy(
                inboxState = current.inboxState.afterNotificationAction(item, result),
            )
        }
        result
            .onNotificationFailure()
            .onSuccess {
                if (SharedFlowCentre.isCurrentSession(token)) {
                    queueNotificationRefresh(token)
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

    private fun finishNotificationMutation(
        item: NotificationItemData,
        token: AccountSessionToken,
    ) {
        reduceForSession(token) { current ->
            current.copy(
                pendingMutations = current.pendingMutations - item.identity,
            )
        }
    }

    override fun close() {
        refreshRequests.close()
        stateStore.close()
        modelScope.cancel()
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
