package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.attributes.NotificationType
import io.github.vrcmteam.vrcm.network.api.notification.NotificationApi
import io.github.vrcmteam.vrcm.network.api.notification.data.NotificationData
import io.github.vrcmteam.vrcm.network.api.notification.data.NotificationDataV2
import io.github.vrcmteam.vrcm.network.api.groups.GroupsApi
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.websocket.data.content.NotificationContent
import io.github.vrcmteam.vrcm.network.websocket.data.content.NotificationV2UpdateContent
import io.github.vrcmteam.vrcm.network.websocket.data.type.NotificationEvents
import io.github.vrcmteam.vrcm.presentation.notifications.FriendOnlineNotifier
import io.github.vrcmteam.vrcm.storage.SettingsDao
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.koin.core.logger.Logger

/** Sends opt-in Android alerts for friend presence changes and incoming inbox notifications. */
class FriendOnlineNotificationService(
    private val favoriteService: FavoriteService,
    private val notificationApi: NotificationApi,
    private val friendService: FriendService,
    private val settingsDao: SettingsDao,
    private val notifier: FriendOnlineNotifier,
    private val usersApi: UsersApi,
    private val groupsApi: GroupsApi,
    private val json: Json,
    private val logger: Logger,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val started = atomic(false)
    private val tracker = FriendPresenceTracker()
    private var activeToken: AccountSessionToken? = null
    private var favoriteJob: Job? = null
    private val knownNotificationIds = mutableSetOf<String>()
    private val notificationDeliveryTracker = NotificationDeliveryTracker()

    // 两条拉取路径各自记录是否已播种：开关可以中途打开，晚开的那一类不能补发历史。
    private var inboxSeeded = false
    private var friendRequestsSeeded = false

    /** Starts one account-bound observer set. Calling it repeatedly is safe. */
    fun start() {
        if (!started.compareAndSet(expect = false, update = true)) return
        scope.launch {
            SharedFlowCentre.currentSession.collect { session ->
                favoriteJob?.cancel()
                mutex.withLock {
                    activeToken = session?.token
                    tracker.reset()
                    knownNotificationIds.clear()
                    notificationDeliveryTracker.reset()
                    inboxSeeded = false
                    friendRequestsSeeded = false
                }
                session?.let { account ->
                    favoriteJob = scope.launch {
                        favoriteService.loadFavoriteByGroup(FavoriteType.Friend)
                        // friendActivitySource 只在首次完整好友刷新后发布，并且携带账号令牌。
                        // 收藏数据只提供分组归属，不能把 presence 监听范围缩成收藏好友。
                        combine(
                            favoriteService.favoritesByGroup(FavoriteType.Friend),
                            friendService.friendActivitySource,
                        ) { groups, source -> groups to source }
                            .collect { (groups, source) ->
                                // 名单可以整组选，所以要记住每位好友属于哪些收藏分组。
                                val groupIdsByUser = buildMap<String, MutableSet<String>> {
                                    groups.forEach { (group, favorites) ->
                                        favorites.forEach { favorite ->
                                            getOrPut(favorite.favoriteId) { mutableSetOf() } += group.id
                                        }
                                    }
                                }
                                onFriendsChanged(account.token, source, groupIdsByUser)
                            }
                    }
                    refreshInboxNotifications(account.token, seedOnly = true)
                }
            }
        }
        scope.launch {
            SharedFlowCentre.webSocket.collect { event ->
                if (!SharedFlowCentre.isCurrentSession(event.token)) return@collect
                when (event.event.type) {
                    NotificationEvents.Notification.typeName ->
                        handlePipelineNotification(event.token, event.event.content, isV2 = false)

                    NotificationEvents.NotificationV2.typeName ->
                        handlePipelineNotification(event.token, event.event.content, isV2 = true)

                    NotificationEvents.NotificationV2Update.typeName ->
                        handlePipelineNotificationUpdate(event.token, event.event.content)
                }
            }
        }
    }

    private fun anyInboxNotificationEnabled(): Boolean =
        settingsDao.boopNotificationsEnabled || settingsDao.groupAnnouncementNotificationsEnabled

    /** Polls the inbox for the foreground service's disconnected-WebSocket fallback. */
    fun refreshInboxNotifications() {
        val token = activeToken ?: return
        scope.launch { refreshInboxNotifications(token) }
    }

    private suspend fun handlePipelineNotification(
        token: AccountSessionToken,
        content: String,
        isV2: Boolean,
    ) {
        val notification = runCatching { json.decodeFromString<NotificationContent>(content) }
            .onFailure {
                logger.warn("Unable to decode Pipeline notification; falling back to inbox refresh: ${it.message.orEmpty()}")
            }
            .getOrNull()
        if (notification == null) {
            refreshInboxNotifications(token)
            return
        }

        val shouldDeliver = mutex.withLock {
            if (activeToken != token || !SharedFlowCentre.isCurrentSession(token)) return@withLock false
            knownNotificationIds += notification.id
            if (isV2) {
                notificationDeliveryTracker.shouldDeliverV2(
                    id = notification.id,
                    version = notification.version,
                    relatedId = notification.relatedNotificationsId,
                    isPipelineEvent = true,
                )
            } else {
                notificationDeliveryTracker.shouldDeliverLegacy(notification.id)
            }
        }
        if (!shouldDeliver) return

        logger.debug("Dispatching Pipeline notification ${notification.id} (${notification.type})")
        val eventId = notification.relatedNotificationsId?.takeIf(String::isNotBlank)
            ?: notification.id
        when {
            notification.type.equals(NotificationType.FriendRequest.value, ignoreCase = true) -> {
                dispatchFriendRequest(
                    notificationId = notification.id,
                    senderUserId = notification.senderUserId,
                    senderUsername = notification.senderUsername,
                )
            }

            notification.type.equals(BOOP_TYPE, ignoreCase = true) -> {
                if (!settingsDao.boopNotificationsEnabled) return
                notifier.notifyBoop(
                    eventId,
                    notification.senderName(),
                    notification.details?.emojiId ?: notification.data.emojiId,
                    iconUrl = resolveUserIcon(notification.senderUserId),
                )
            }

            isGroupNotificationType(notification.type) -> {
                if (!settingsDao.groupAnnouncementNotificationsEnabled) return
                val details = notification.details ?: notification.data
                notifier.notifyGroupEvent(
                    eventId,
                    notification.type,
                    details.groupName ?: notification.title.orEmpty(),
                    details.announcementTitle ?: notification.message.ifBlank { notification.title.orEmpty() },
                    iconUrl = notification.imageUrl
                        ?: details.imageUrl
                        ?: resolveGroupIcon(
                            groupId = notification.groupId
                                ?: details.groupId
                                ?: details.ownerId
                                ?: extractGroupId(notification.link),
                        ),
                )
            }

            else -> refreshInboxNotifications(token)
        }
    }

    private suspend fun handlePipelineNotificationUpdate(
        token: AccountSessionToken,
        content: String,
    ) {
        val update = runCatching { json.decodeFromString<NotificationV2UpdateContent>(content) }
            .onFailure {
                logger.warn("Unable to decode Pipeline notification-v2-update: ${it.message.orEmpty()}")
            }
            .getOrNull()
        if (update == null) {
            refreshInboxNotifications(token)
            return
        }

        logger.debug("Refreshing Pipeline notification ${update.id} at version ${update.version}")
        // Updates are partial and may only contain a new related notification ID. The REST item is
        // the canonical full snapshot; its version and relation decide whether this is a new alert.
        refreshInboxNotifications(token)
    }

    /**
     * 拉取收件箱通知并按类型分派。
     *
     * 戳一戳与群组公告来自当前 `/notifications` 收件箱，好友请求仍要单独走旧版
     * `/auth/user/notifications`——本仓库的 NotificationCenterModel 也是这么分开拉的。
     *
     * 两条路径共用一份已见 ID 集合（两边的 id 都是通知 ID），但**播种状态各记各的**：开关是可以
     * 中途打开的，如果只用一个标记，先开戳一戳、之后再开好友请求时，那一类的历史消息会因为从没
     * 进过基线而被整批当成新消息推出来。因此每条路径在本会话内第一次真正取回数据时只并入 ID、
     * 不产出通知，之后才走增量分派。
     */
    private suspend fun refreshInboxNotifications(
        token: AccountSessionToken,
        seedOnly: Boolean = false,
    ) {
        if (!SharedFlowCentre.isCurrentSession(token)) return
        val inboxRequested = anyInboxNotificationEnabled()
        val inboxNotifications = if (inboxRequested) {
            try {
                notificationApi.fetchNotifications()
            } catch (error: Exception) {
                logger.warn("Unable to refresh notifications: ${error.message.orEmpty()}")
                null
            }
        } else {
            null
        }
        val friendRequestsRequested = settingsDao.friendRequestNotificationsEnabled
        val friendRequests = if (friendRequestsRequested) {
            try {
                notificationApi.fetchNotificationsV2(NotificationType.FriendRequest.value)
            } catch (error: Exception) {
                logger.warn("Unable to refresh friend requests: ${error.message.orEmpty()}")
                null
            }
        } else {
            null
        }
        // 请求过但失败的路径这一轮整体跳过：只用半份数据去重，会让另一半在下次被当成新消息补发。
        if (inboxRequested && inboxNotifications == null) return
        if (friendRequestsRequested && friendRequests == null) return
        if (inboxNotifications == null && friendRequests == null) return

        val pending = mutex.withLock {
            if (activeToken != token || !SharedFlowCentre.isCurrentSession(token)) {
                return@withLock emptyList<suspend () -> Unit>()
            }
            buildList {
                inboxNotifications?.let { notifications ->
                    val seeding = seedOnly || !inboxSeeded
                    inboxSeeded = true
                    notifications.forEach { notification ->
                        knownNotificationIds += notification.id
                        val shouldDeliver = notificationDeliveryTracker.shouldDeliverV2(
                            id = notification.id,
                            version = notification.version,
                            relatedId = notification.relatedNotificationsId,
                            seedOnly = seeding,
                        )
                        if (shouldDeliver) {
                            add { dispatchInboxNotification(notification) }
                        }
                    }
                }
                friendRequests?.let { requests ->
                    val seeding = seedOnly || !friendRequestsSeeded
                    friendRequestsSeeded = true
                    requests.forEach { request ->
                        val unseen = knownNotificationIds.add(request.id)
                        if (unseen && !seeding && notificationDeliveryTracker.shouldDeliverLegacy(request.id)) {
                            add { dispatchFriendRequest(request) }
                        }
                    }
                }
            }
        }
        pending.forEach { deliver ->
            if (SharedFlowCentre.isCurrentSession(token)) deliver()
        }
    }

    private suspend fun dispatchFriendRequest(request: NotificationDataV2) {
        dispatchFriendRequest(
            notificationId = request.id,
            senderUserId = request.senderUserId,
            senderUsername = null,
        )
    }

    private suspend fun dispatchFriendRequest(
        notificationId: String,
        senderUserId: String?,
        senderUsername: String?,
    ) {
        if (!settingsDao.friendRequestNotificationsEnabled) return
        val senderUser = senderUserId?.let { id -> runCatching { usersApi.fetchUser(id) }.getOrNull() }
        val sender = senderUsername?.takeIf(String::isNotBlank)
            ?: senderUser?.displayName?.takeIf(String::isNotBlank)
            ?: senderUserId?.takeIf(String::isNotBlank)
            ?: "Unknown"
        notifier.notifyFriendRequest(notificationId, sender, iconUrl = senderUser?.iconUrl)
    }

    /** 本地弹出提醒不改变服务器上的已读状态。 */
    private suspend fun dispatchInboxNotification(notification: NotificationData) {
        val eventId = notification.relatedNotificationsId?.takeIf(String::isNotBlank)
            ?: notification.id
        val sender = notification.data.boopingUserDisplayName?.takeIf(String::isNotBlank)
            ?: notification.senderUsername?.takeIf(String::isNotBlank)
            ?: notification.senderUserId?.takeIf(String::isNotBlank)
            ?: "Unknown"
        when {
            notification.type.equals(BOOP_TYPE, ignoreCase = true) -> {
                if (!settingsDao.boopNotificationsEnabled) return
                notifier.notifyBoop(
                    eventId,
                    sender,
                    notification.details?.emojiId ?: notification.data.emojiId,
                    iconUrl = resolveUserIcon(notification.senderUserId)
                        ?: notification.imageUrl
                        ?: notification.details?.imageUrl
                        ?: notification.data.imageUrl,
                )
            }

            isGroupNotificationType(notification.type) -> {
                if (!settingsDao.groupAnnouncementNotificationsEnabled) return
                // 群组通知本来就只发给已加入的群组成员，不需要再按群组过滤一次。
                val details = notification.details ?: notification.data
                notifier.notifyGroupEvent(
                    eventId,
                    notification.type,
                    details.groupName ?: notification.title.orEmpty(),
                    details.announcementTitle ?: notification.message.ifBlank { notification.title.orEmpty() },
                    iconUrl = notification.imageUrl
                        ?: details.imageUrl
                        ?: resolveGroupIcon(
                            groupId = notification.groupId
                                ?: details.groupId
                                ?: details.ownerId
                                ?: extractGroupId(notification.link),
                        ),
                )
            }
        }
    }

    private suspend fun resolveUserIcon(userId: String?): String? = userId
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let { id -> runCatching { usersApi.fetchUser(id).iconUrl }.getOrNull() }

    private suspend fun resolveGroupIcon(groupId: String?): String? = groupId
        ?.trim()
        ?.takeIf { it.startsWith("grp_") }
        ?.takeIf(String::isNotEmpty)
        ?.let { id -> runCatching { groupsApi.fetchGroup(id).iconUrl }.getOrNull() }

    private fun extractGroupId(link: String?): String? = link
        ?.split(',', '&', '?')
        ?.firstNotNullOfOrNull { part ->
            val marker = "grp_"
            part.substringAfter(marker, "")
                .takeIf(String::isNotEmpty)
                ?.let { marker + it.takeWhile { char -> char.isLetterOrDigit() || char == '-' } }
        }

    private suspend fun onFriendsChanged(
        token: AccountSessionToken,
        source: FriendActivitySourceSnapshot?,
        groupIdsByUser: Map<String, Set<String>>,
    ) {
        val transitions = mutex.withLock {
            if (activeToken != token || source?.token != token) {
                emptyList()
            } else {
                tracker.observe(source.friends.associateBy { it.id })
            }
        }
        if (transitions.isEmpty()) return
        // 上线与下线是两个独立开关，名单同时作用于两者。
        val onlineEnabled = settingsDao.friendPresenceNotificationsEnabled
        val offlineEnabled = settingsDao.friendOfflineNotificationsEnabled
        if (!onlineEnabled && !offlineEnabled) return
        val filter = settingsDao.friendPresenceFilter
        transitions.forEach { transition ->
            val enabled = if (transition.inGame) onlineEnabled else offlineEnabled
            if (!enabled) return@forEach
            if (!filter.allows(transition.userId, groupIdsByUser[transition.userId].orEmpty())) {
                return@forEach
            }
            if (transition.inGame) notifier.notifyOnline(transition.friend ?: return@forEach)
            else notifier.notifyOffline(
                friendId = transition.userId,
                displayName = transition.displayName,
                iconUrl = transition.friend?.iconUrl,
            )
        }
    }

    private companion object {
        const val BOOP_TYPE = "boop"

    }
}

/**
 * VRChat notification types that represent group announcements, events, or management messages.
 * Group invites are intentionally excluded because they have a dedicated in-app flow.
 */
internal fun isGroupNotificationType(type: String): Boolean = type.trim().lowercase() in GROUP_NOTIFICATION_TYPES

private val GROUP_NOTIFICATION_TYPES = setOf(
    "groupchange",
    "group.announcement",
    "group.event.created",
    "group.event.starting",
    "group.informative",
    "group.joinrequest",
    "group.transfer",
    "group.queueready",
)

private fun NotificationContent.senderName(): String =
    data.boopingUserDisplayName?.takeIf(String::isNotBlank)
        ?: senderUsername?.takeIf(String::isNotBlank)
        ?: senderUserId?.takeIf(String::isNotBlank)
        ?: "Unknown"
