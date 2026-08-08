package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.network.api.notification.NotificationApi
import io.github.vrcmteam.vrcm.network.websocket.data.type.NotificationEvents
import io.github.vrcmteam.vrcm.presentation.notifications.FriendOnlineNotifier
import io.github.vrcmteam.vrcm.storage.SettingsDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Sends opt-in Android alerts only for favorited friends entering or leaving the game. */
class FriendOnlineNotificationService(
    private val favoriteService: FavoriteService,
    private val notificationApi: NotificationApi,
    private val friendService: FriendService,
    private val settingsDao: SettingsDao,
    private val notifier: FriendOnlineNotifier,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val tracker = FavoriteFriendPresenceTracker()
    private var activeToken: AccountSessionToken? = null
    private var favoriteJob: Job? = null
    private val knownBoopIds = mutableSetOf<String>()
    private var boopsSeeded = false

    init {
        scope.launch {
            SharedFlowCentre.currentSession.collect { session ->
                favoriteJob?.cancel()
                mutex.withLock { activeToken = session?.token; tracker.reset() }
                session?.let { account ->
                    favoriteJob = scope.launch {
                        favoriteService.loadFavoriteByGroup(FavoriteType.Friend)
                        favoriteService.favoritesByGroup(FavoriteType.Friend).collect { groups ->
                            val ids = groups.values.flatten().map { it.favoriteId }.toSet()
                            mutex.withLock {
                                if (activeToken == account.token) tracker.updateFavorites(ids, friendService.friendMap)
                            }
                        }
                    }
                }
            }
        }
        scope.launch { friendService.friendState.collect(::onFriendsChanged) }
        scope.launch {
            SharedFlowCentre.webSocket.collect { event ->
                if (event.event.type == NotificationEvents.Notification.typeName) refreshBoops()
            }
        }
        scope.launch { refreshBoops(seedOnly = true) }
    }

    private suspend fun refreshBoops(seedOnly: Boolean = false) {
        if (!settingsDao.boopNotificationsEnabled) return
        val boops = runCatching { notificationApi.fetchNotifications() }
            .getOrDefault(emptyList())
            .filter { it.type.equals("boop", ignoreCase = true) }
        if (!boopsSeeded || seedOnly) {
            knownBoopIds += boops.map { it.id }
            boopsSeeded = true
            for (boop in boops.filter { !it.seen }) notifyBoop(boop)
            return
        }
        for (boop in boops.filter { knownBoopIds.add(it.id) }) notifyBoop(boop)
    }

    private suspend fun notifyBoop(boop: io.github.vrcmteam.vrcm.network.api.notification.data.NotificationData) {
        val sender = boop.senderUsername?.takeIf(String::isNotBlank)
            ?: boop.senderUserId?.takeIf(String::isNotBlank)
            ?: "Unknown"
        notifier.notifyBoop(boop.id, sender, boop.details?.emojiId ?: boop.data.emojiId)
        runCatching { notificationApi.markNotificationAsRead(boop.id) }
    }
    private suspend fun onFriendsChanged(friends: Map<String, FriendData>) {
        val transitions = mutex.withLock {
            if (activeToken == null) emptyList() else tracker.observe(friends)
        }
        if (!settingsDao.friendPresenceNotificationsEnabled) return
        transitions.forEach { transition ->
            if (transition.inGame) notifier.notifyOnline(transition.friend ?: return@forEach)
            else notifier.notifyOffline(transition.userId, transition.displayName)
        }
    }
}
