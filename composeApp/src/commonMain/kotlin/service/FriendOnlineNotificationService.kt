package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
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

/** Sends opt-in friend presence alerts for the active account. */
class FriendOnlineNotificationService(
    private val favoriteService: FavoriteService,
    private val friendService: FriendService,
    private val settingsDao: SettingsDao,
    private val notifier: FriendOnlineNotifier,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val started = atomic(false)
    private val tracker = FriendPresenceTracker()
    private var activeToken: AccountSessionToken? = null
    private var favoriteJob: Job? = null

    /** Starts one account-bound observer set. Calling it repeatedly is safe. */
    fun start() {
        if (!started.compareAndSet(expect = false, update = true)) return
        scope.launch {
            SharedFlowCentre.currentSession.collect { session ->
                favoriteJob?.cancel()
                mutex.withLock {
                    activeToken = session?.token
                    tracker.reset()
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
                }
            }
        }
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
            else notifier.notifyOffline(transition.userId, transition.displayName)
        }
    }
}
