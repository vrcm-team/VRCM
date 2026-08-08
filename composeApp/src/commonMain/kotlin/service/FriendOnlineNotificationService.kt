package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.network.api.notification.NotificationApi
import io.github.vrcmteam.vrcm.network.api.notification.data.NotificationData
import io.github.vrcmteam.vrcm.network.websocket.data.type.NotificationEvents
import io.github.vrcmteam.vrcm.presentation.notifications.FriendOnlineNotifier
import io.github.vrcmteam.vrcm.storage.SettingsDao
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
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
    private val knownBoopIds = mutableSetOf<String>()
    private var boopsSeeded = false

    /** Starts one account-bound observer set. Calling it repeatedly is safe. */
    fun start() {
        if (!started.compareAndSet(expect = false, update = true)) return
        scope.launch {
            SharedFlowCentre.currentSession.collect { session ->
                favoriteJob?.cancel()
                mutex.withLock {
                    activeToken = session?.token
                    tracker.reset()
                    knownBoopIds.clear()
                    boopsSeeded = false
                }
                session?.let { account ->
                    favoriteJob = scope.launch {
                        favoriteService.loadFavoriteByGroup(FavoriteType.Friend)
                        favoriteService.favoritesByGroup(FavoriteType.Friend).collect { groups ->
                            val ids = groups.values.flatten().map { it.favoriteId }.toSet()
                            mutex.withLock {
                                if (activeToken == account.token) {
                                    tracker.updateFavorites(ids, friendService.friendMap)
                                }
                            }
                        }
                    }
                    refreshBoops(account.token, seedOnly = true)
                }
            }
        }
        scope.launch { friendService.friendState.collect(::onFriendsChanged) }
        scope.launch {
            SharedFlowCentre.webSocket.collect { event ->
                if (event.event.type == NotificationEvents.Notification.typeName &&
                    SharedFlowCentre.isCurrentSession(event.token)
                ) {
                    refreshBoops(event.token)
                }
            }
        }
    }

    private suspend fun refreshBoops(token: AccountSessionToken, seedOnly: Boolean = false) {
        if (!settingsDao.boopNotificationsEnabled || !SharedFlowCentre.isCurrentSession(token)) return
        val boops = try {
            notificationApi.fetchNotifications()
                .filter { it.type.equals("boop", ignoreCase = true) }
        } catch (error: Exception) {
            logger.warn("Unable to refresh Boop notifications: ${error.message.orEmpty()}")
            return
        }
        val newBoops = mutex.withLock {
            if (activeToken != token || !SharedFlowCentre.isCurrentSession(token) ||
                !settingsDao.boopNotificationsEnabled
            ) {
                return@withLock emptyList()
            }
            if (!boopsSeeded || seedOnly) {
                knownBoopIds.clear()
                knownBoopIds += boops.map(NotificationData::id)
                boopsSeeded = true
                emptyList()
            } else {
                boops.filter { knownBoopIds.add(it.id) }
            }
        }
        newBoops.forEach { boop ->
            if (SharedFlowCentre.isCurrentSession(token)) notifyBoop(boop)
        }
    }

    private suspend fun notifyBoop(boop: NotificationData) {
        val sender = boop.senderUsername?.takeIf(String::isNotBlank)
            ?: boop.senderUserId?.takeIf(String::isNotBlank)
            ?: "Unknown"
        // Delivering a local alert must not change the unread state on VRChat's servers.
        notifier.notifyBoop(boop.id, sender, boop.details?.emojiId ?: boop.data.emojiId)
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
