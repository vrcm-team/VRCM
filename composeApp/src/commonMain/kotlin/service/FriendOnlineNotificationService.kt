package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.network.api.notification.NotificationApi
import io.github.vrcmteam.vrcm.network.api.attributes.NotificationType
import io.github.vrcmteam.vrcm.network.api.notification.data.NotificationData
import io.github.vrcmteam.vrcm.network.api.notification.data.NotificationDataV2
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
import org.koin.core.logger.Logger

/** Sends opt-in Android alerts only for favorited friends entering or leaving the game. */
class FriendOnlineNotificationService(
    private val favoriteService: FavoriteService,
    private val notificationApi: NotificationApi,
    private val friendService: FriendService,
    private val settingsDao: SettingsDao,
    private val notifier: FriendOnlineNotifier,
    private val logger: Logger,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val started = atomic(false)
    private val tracker = FavoriteFriendPresenceTracker()
    private var activeToken: AccountSessionToken? = null
    private var favoriteJob: Job? = null
    private val knownNotificationIds = mutableSetOf<String>()
    private var notificationsSeeded = false
    private var favoriteGroupIdsByUser: Map<String, Set<String>> = emptyMap()

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
                    notificationsSeeded = false
                    favoriteGroupIdsByUser = emptyMap()
                }
                session?.let { account ->
                    favoriteJob = scope.launch {
                        favoriteService.loadFavoriteByGroup(FavoriteType.Friend)
                        // 冷启动时 friendState 先装的是本地缓存快照，其在线状态被强制写成离线，
                        // 拿它建立基线会在真实好友列表分页落地时把所有收藏好友判成刚刚上线。
                        // 因此把刷新状态一起并进来：未完成首次完整刷新时只记名字、不建立基线，
                        // 刷新成功后这里会重新发射一次，用真实数据补上基线。
                        combine(
                            favoriteService.favoritesByGroup(FavoriteType.Friend),
                            friendService.initialRefreshCompleted,
                        ) { groups, presenceTrusted -> groups to presenceTrusted }
                            .collect { (groups, presenceTrusted) ->
                                val ids = groups.values.flatten().map { it.favoriteId }.toSet()
                                // 名单可以整组选，所以要记住每位好友属于哪些收藏分组。
                                val groupIdsByUser = buildMap<String, MutableSet<String>> {
                                    groups.forEach { (group, favorites) ->
                                        favorites.forEach { favorite ->
                                            getOrPut(favorite.favoriteId) { mutableSetOf() } += group.id
                                        }
                                    }
                                }
                                mutex.withLock {
                                    if (activeToken == account.token) {
                                        favoriteGroupIdsByUser = groupIdsByUser
                                        tracker.updateFavorites(ids, friendService.friendMap, presenceTrusted)
                                    }
                                }
                            }
                    }
                    refreshInboxNotifications(account.token, seedOnly = true)
                }
            }
        }
        scope.launch { friendService.friendState.collect(::onFriendsChanged) }
        scope.launch {
            SharedFlowCentre.webSocket.collect { event ->
                if (event.event.type == NotificationEvents.Notification.typeName &&
                    SharedFlowCentre.isCurrentSession(event.token)
                ) {
                    refreshInboxNotifications(event.token)
                }
            }
        }
    }

    private fun anyV1NotificationEnabled(): Boolean =
        settingsDao.boopNotificationsEnabled || settingsDao.groupAnnouncementNotificationsEnabled

    /**
     * 拉取收件箱通知并按类型分派。
     *
     * 戳一戳与群组公告来自 V1 的 /notifications，好友请求要单独走 V2——本仓库的 HomeScreenModel
     * 也是这么分开拉的，V1 不返回 friendRequest。两条路径共用同一份已见 ID 集合，因为两边的 id
     * 都是通知 ID；首次拉取只把已有通知记为基线，避免把历史消息当成新消息补发。
     */
    private suspend fun refreshInboxNotifications(
        token: AccountSessionToken,
        seedOnly: Boolean = false,
    ) {
        if (!SharedFlowCentre.isCurrentSession(token)) return
        val v1Notifications = if (anyV1NotificationEnabled()) {
            try {
                notificationApi.fetchNotifications()
            } catch (error: Exception) {
                logger.warn("Unable to refresh notifications: ${error.message.orEmpty()}")
                null
            }
        } else {
            emptyList()
        }
        val friendRequests = if (settingsDao.friendRequestNotificationsEnabled) {
            try {
                notificationApi.fetchNotificationsV2(NotificationType.FriendRequest.value)
            } catch (error: Exception) {
                logger.warn("Unable to refresh friend requests: ${error.message.orEmpty()}")
                null
            }
        } else {
            emptyList()
        }
        // 任一路径失败就整轮跳过，避免只用半份数据去重后把另一半当成新消息补发。
        if (v1Notifications == null || friendRequests == null) return
        if (v1Notifications.isEmpty() && friendRequests.isEmpty() && !seedOnly) return

        val ids = v1Notifications.map(NotificationData::id) + friendRequests.map(NotificationDataV2::id)
        val pending = mutex.withLock {
            if (activeToken != token || !SharedFlowCentre.isCurrentSession(token)) {
                return@withLock emptyList<() -> Unit>()
            }
            if (!notificationsSeeded || seedOnly) {
                knownNotificationIds.clear()
                knownNotificationIds += ids
                notificationsSeeded = true
                emptyList()
            } else {
                buildList {
                    v1Notifications.forEach { notification ->
                        if (knownNotificationIds.add(notification.id)) {
                            add { dispatchV1Notification(notification) }
                        }
                    }
                    friendRequests.forEach { request ->
                        if (knownNotificationIds.add(request.id)) {
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

    private fun dispatchFriendRequest(request: NotificationDataV2) {
        if (!settingsDao.friendRequestNotificationsEnabled) return
        // V2 的字段比 V1 少，没有 senderUsername，只能退到发送者 ID。
        val sender = request.senderUserId.takeIf(String::isNotBlank) ?: "Unknown"
        notifier.notifyFriendRequest(request.id, sender)
    }

    /** 本地弹出提醒不改变服务器上的已读状态。 */
    private fun dispatchV1Notification(notification: NotificationData) {
        val sender = notification.senderUsername?.takeIf(String::isNotBlank)
            ?: notification.senderUserId?.takeIf(String::isNotBlank)
            ?: "Unknown"
        when {
            notification.type.equals(BOOP_TYPE, ignoreCase = true) -> {
                if (!settingsDao.boopNotificationsEnabled) return
                notifier.notifyBoop(
                    notification.id,
                    sender,
                    notification.details?.emojiId ?: notification.data.emojiId,
                )
            }

            notification.type.startsWith(GROUP_ANNOUNCEMENT_TYPE_PREFIX, ignoreCase = true) -> {
                if (!settingsDao.groupAnnouncementNotificationsEnabled) return
                // 群组公告本来就只发给已加入的群组成员，不需要再按群组过滤一次。
                val details = notification.details ?: notification.data
                notifier.notifyGroupAnnouncement(
                    notification.id,
                    details.groupName ?: notification.title.orEmpty(),
                    details.announcementTitle ?: notification.message,
                )
            }
        }
    }

    private suspend fun onFriendsChanged(friends: Map<String, FriendData>) {
        val (transitions, groupIdsByUser) = mutex.withLock {
            if (activeToken == null) {
                emptyList<FriendPresenceTransition>() to emptyMap()
            } else {
                tracker.observe(friends) to favoriteGroupIdsByUser
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
            else notifier.notifyOffline(transition.userId, transition.displayName)
        }
    }

    private companion object {
        const val BOOP_TYPE = "boop"

        /** VRChat 把群组类通知统一放在 group. 前缀下，公告是其中的 group.announcement。 */
        const val GROUP_ANNOUNCEMENT_TYPE_PREFIX = "group.announcement"
    }
}
