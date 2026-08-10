package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AccountWebSocketEvent
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.auth.data.CurrentUserData
import io.github.vrcmteam.vrcm.network.api.friends.FriendsApi
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.network.websocket.data.content.FriendActiveContent
import io.github.vrcmteam.vrcm.network.websocket.data.content.FriendLocationContent
import io.github.vrcmteam.vrcm.network.websocket.data.content.FriendOfflineContent
import io.github.vrcmteam.vrcm.network.websocket.data.content.FriendOnlineContent
import io.github.vrcmteam.vrcm.network.websocket.data.content.FriendUpdateContent
import io.github.vrcmteam.vrcm.network.websocket.data.content.UserLocationContent
import io.github.vrcmteam.vrcm.network.websocket.data.type.FriendEvents
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.storage.FriendListCacheStore
import io.github.vrcmteam.vrcm.storage.AccountCacheManager
import io.github.vrcmteam.vrcm.storage.AccountCacheWriteToken
import io.github.vrcmteam.vrcm.storage.data.FriendListCache
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.serialization.json.Json

class FriendService(
    private val friendsApi: FriendsApi,
    private val authService: AuthService,
    private val json: Json,
    private val friendListCacheStore: FriendListCacheStore,
    private val accountCacheManager: AccountCacheManager,
) {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val friendMapLock = SynchronizedObject()
    private val friendStore = FriendStateStore()
    private val refreshCoordinator = FriendRefreshCoordinator()
    private val currentUserRefreshMutex = Mutex()
    private val accountTracker = FriendAccountTracker()
    private var activeAccountUserId: String? = null
    private var activeSessionToken: AccountSessionToken? = null
    private var currentUserLocationRevision = 0L
    private val cacheWriter =
        ConflatedAccountCacheWriter<PendingFriendCacheSnapshot>(serviceScope) { _, pending ->
            accountCacheManager.saveFriendListIfCurrent(
                token = pending.token,
                cache = FriendListCache(pending.snapshot.values.toList()),
            )
    }
    private val preloadTask = AccountBoundTask<AccountSessionToken>(
        scope = serviceScope,
        isCurrent = ::isCurrentSession,
        runTask = { refreshFriendList(it) },
    )
    private val offlineLastActivityRefreshSequence = atomic(0L)
    private val offlineLastActivityRefreshTask = AccountBoundTask<OfflineLastActivityRefreshRequest>(
        scope = serviceScope,
        isCurrent = { isCurrentSession(it.sessionToken) },
        runTask = { refreshFriendList(it.sessionToken, offline = true) },
    )

    private val _friendState = MutableStateFlow<Map<String, FriendData>>(emptyMap())
    val friendState: StateFlow<Map<String, FriendData>> = _friendState.asStateFlow()
    private val _currentUserLocation = MutableStateFlow<FriendPresence?>(null)
    val currentUserLocation: StateFlow<FriendPresence?> = _currentUserLocation.asStateFlow()
    private val _friendActivitySource = MutableStateFlow<FriendActivitySourceSnapshot?>(null)
    internal val friendActivitySource: StateFlow<FriendActivitySourceSnapshot?> =
        _friendActivitySource.asStateFlow()
    private val _friendLastActivitySource = MutableStateFlow<FriendActivitySourceSnapshot?>(null)
    internal val friendLastActivitySource: StateFlow<FriendActivitySourceSnapshot?> =
        _friendLastActivitySource.asStateFlow()

    private val _initialRefreshCompleted = MutableStateFlow(false)

    /**
     * True once the current session has completed one full friend list refresh.
     *
     * Until then [friendState] may hold the locally restored snapshot, whose presence is forced to
     * offline by [asCachedOffline] and therefore must not be treated as a real presence reading.
     * Resets on every account switch and logout.
     */
    val initialRefreshCompleted: StateFlow<Boolean> = _initialRefreshCompleted.asStateFlow()

    val friendMap: Map<String, FriendData>
        get() = friendState.value

    private val _friendUpdateFlow = MutableSharedFlow<AccountFriendUpdateEvent>()
    val friendUpdateFlow: SharedFlow<AccountFriendUpdateEvent> = _friendUpdateFlow.asSharedFlow()

    init {
        SharedFlowCentre.currentSession.value?.let(::activateSession)
        serviceScope.launch {
            collectFriendWebSocketEvents(
                events = SharedFlowCentre.webSocket,
                handle = ::handleWebSocketEvent,
                onFailure = { socketEvent, e ->
                    SharedFlowCentre.toastText.emit(
                        ToastText.Error("处理好友实时事件 ${socketEvent.event.type} 失败: ${e.message}")
                    )
                },
            )
        }
        serviceScope.launch {
            SharedFlowCentre.currentSession.collect { session ->
                if (session == null) {
                    synchronized(friendMapLock) {
                        accountTracker.onLogout()
                        activeAccountUserId = null
                        activeSessionToken = null
                        friendStore.clear()
                        publishFriendState()
                        currentUserLocationRevision++
                        _currentUserLocation.value = null
                        _friendActivitySource.value = null
                        _friendLastActivitySource.value = null
                        _initialRefreshCompleted.value = false
                    }
                    preloadTask.cancelAndJoin()
                    offlineLastActivityRefreshTask.cancelAndJoin()
                } else {
                    offlineLastActivityRefreshTask.cancelAndJoin()
                    activateSession(session)
                }
            }
        }
    }

    private fun activateSession(session: AuthenticatedAccount) {
        var restoreCacheBeforeRefresh = false
        val activated = synchronized(friendMapLock) {
            if (activeSessionToken == session.token) return@synchronized false
            activeSessionToken = session.token
            if (activeAccountUserId != session.account.userId) {
                currentUserLocationRevision++
                _currentUserLocation.value = null
                _friendActivitySource.value = null
                activeAccountUserId = session.account.userId
                restoreCacheBeforeRefresh = true
                // 新账号要重新完成一次完整刷新，才能把在线状态当作可信读数。
                _initialRefreshCompleted.value = false
            }
            accountTracker.onAuthenticated(session.account.userId)
            publishFriendActivitySource()
            true
        }
        if (activated) {
            if (restoreCacheBeforeRefresh) {
                // 先恢复本地回退数据，再发首次网络刷新，避免两个写入逆序提交。
                serviceScope.launch {
                    try {
                        restoreCachedFriendList(session.token)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        SharedFlowCentre.toastText.emit(
                            ToastText.Error("恢复好友缓存失败，将重新获取好友列表: ${error.message}")
                        )
                    }
                    if (isCurrentSession(session.token)) {
                        preloadFriendList(session.token)
                    }
                }
            } else {
                preloadFriendList(session.token)
            }
            serviceScope.launch {
                runCatching { refreshCurrentUserLocation(session.token) }
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun handleWebSocketEvent(accountEvent: AccountWebSocketEvent) {
        val sessionToken = accountEvent.token
        if (!isCurrentSession(sessionToken)) return
        val socketEvent = accountEvent.event
        val receivedAtMillis = Clock.System.now().toEpochMilliseconds()
        when (socketEvent.type) {
            "user-location" -> {
                val content = json.decodeFromString<UserLocationContent>(socketEvent.content)
                if (content.userId == null || content.userId == authService.accountDto().userId) {
                    updateCurrentUserLocation(
                        sessionToken = sessionToken,
                        presence = FriendPresence(
                            content.location,
                            content.travelingToLocation,
                        ),
                    )
                    authService.applySocketUserLocation(content.location, content.travelingToLocation)
                }
            }
            "user-update" -> {
                val content = json.decodeFromString<FriendUpdateContent>(socketEvent.content)
                authService.applySocketUserUpdate(content.user)
            }
            FriendEvents.FriendOnline.typeName -> {
                val content = json.decodeFromString<FriendOnlineContent>(socketEvent.content)
                val friend = updateFriend(sessionToken, content.userId, content::mergeWith)
                    ?: run {
                        refreshAfterIncompleteEvent(sessionToken)
                        friendMap[content.userId]
                    }
                emitFriendUpdate(
                    sessionToken = sessionToken,
                    event = FriendUpdateEvent.Online(friend, content.userId),
                    occurredAtMillis = receivedAtMillis,
                )
            }

            FriendEvents.FriendActive.typeName -> {
                val content = json.decodeFromString<FriendActiveContent>(socketEvent.content)
                val friend = content.toFriendData()
                if (!putFriend(sessionToken, friend)) return
                emitFriendUpdate(sessionToken, FriendUpdateEvent.Active(friend))
            }

            FriendEvents.FriendOffline.typeName -> {
                val content = json.decodeFromString<FriendOfflineContent>(socketEvent.content)
                if (!updateOrRemoveFriend(sessionToken, content.userId) { existing ->
                    existing?.copy(
                        location = LocationType.Offline.value,
                        travelingToLocation = "",
                        status = UserStatus.Offline,
                    )
                }) return
                emitFriendUpdate(
                    sessionToken = sessionToken,
                    event = FriendUpdateEvent.Offline(content.userId),
                    occurredAtMillis = receivedAtMillis,
                )
                offlineLastActivityRefreshTask.start(
                    OfflineLastActivityRefreshRequest(
                        sessionToken = sessionToken,
                        sequence = offlineLastActivityRefreshSequence.incrementAndGet(),
                    )
                )
            }

            FriendEvents.FriendLocation.typeName -> {
                val content = json.decodeFromString<FriendLocationContent>(socketEvent.content)
                val friend = updateFriend(sessionToken, content.userId, content::mergeWith)
                    ?: return refreshAfterIncompleteEvent(sessionToken)
                emitFriendUpdate(sessionToken, FriendUpdateEvent.LocationChanged(friend))
            }

            FriendEvents.FriendUpdate.typeName -> {
                val content = json.decodeFromString<FriendUpdateContent>(socketEvent.content)
                val friend = updateFriend(sessionToken, content.user.id) { existing ->
                    existing?.copy(
                        bio = content.user.bio,
                        bioLinks = content.user.bioLinks,
                        currentAvatarImageUrl = content.user.currentAvatarImageUrl,
                        currentAvatarTags = content.user.currentAvatarTags,
                        currentAvatarThumbnailImageUrl = content.user.currentAvatarThumbnailImageUrl,
                        displayName = content.user.displayName,
                        profilePicOverride = content.user.profilePicOverride,
                        status = content.user.status,
                        statusDescription = content.user.statusDescription,
                        tags = content.user.tags,
                        userIcon = content.user.userIcon,
                        pronouns = content.user.pronouns,
                    )
                } ?: return refreshAfterIncompleteEvent(sessionToken)
                emitFriendUpdate(sessionToken, FriendUpdateEvent.Updated(friend))
            }

            FriendEvents.FriendAdd.typeName -> {
                if (!isCurrentSession(sessionToken)) return
                refreshFriendList()
                if (!isCurrentSession(sessionToken)) return
                emitFriendUpdate(sessionToken, FriendUpdateEvent.RefreshRequired)
            }

            FriendEvents.FriendDelete.typeName -> {
                val content = json.decodeFromString<FriendOfflineContent>(socketEvent.content)
                if (!removeFriend(sessionToken, content.userId)) return
                emitFriendUpdate(sessionToken, FriendUpdateEvent.Delete(content.userId))
            }
        }
    }

    private suspend fun refreshAfterIncompleteEvent(sessionToken: AccountSessionToken) {
        if (!isCurrentSession(sessionToken)) return
        refreshFriendList()
        if (!isCurrentSession(sessionToken)) return
        emitFriendUpdate(sessionToken, FriendUpdateEvent.RefreshRequired)
    }

    private suspend fun emitFriendUpdate(
        sessionToken: AccountSessionToken,
        event: FriendUpdateEvent,
        occurredAtMillis: Long? = null,
    ) {
        _friendUpdateFlow.emit(AccountFriendUpdateEvent(sessionToken, event, occurredAtMillis))
    }

    /**
     * 刷新好友列表。分页回调按页顺序执行，缓存仅在完整请求成功后发布。
     */
    suspend fun refreshFriendList(
        offline: Boolean? = null,
        onUpdater: suspend (List<FriendData>) -> Unit = {},
    ): Boolean {
        val sessionToken = synchronized(friendMapLock) { activeSessionToken } ?: return false
        return refreshFriendList(sessionToken, offline, onUpdater)
    }

    private suspend fun refreshFriendList(
        sessionToken: AccountSessionToken,
        offline: Boolean? = null,
        onUpdater: suspend (List<FriendData>) -> Unit = {},
    ): Boolean = refreshCoordinator.runRefresh {
        val refreshToken = synchronized(friendMapLock) {
            if (!isCurrentSessionLocked(sessionToken)) return@synchronized null
            friendStore.beginRefresh()
        } ?: return@runRefresh false
        val collectedFriends = mutableListOf<FriendData>()
        var succeeded = true
        try {
            (offline?.run(friendsApi::friendsFlow) ?: friendsApi.allFriendsFlow())
                .retry(1) {
                    if (it is VRCApiException) authService.doReTryAuth() else false
                }
                .catch { e ->
                    succeeded = false
                    SharedFlowCentre.toastText.emit(ToastText.Error("获取好友列表失败: ${e.message}"))
                }
                .collect { friends ->
                    collectedFriends += friends
                    onUpdater(friends)
                }

            if (succeeded) {
                return@runRefresh commitRefresh(
                    sessionToken = sessionToken,
                    token = refreshToken,
                    friends = collectedFriends,
                    replaceUntouched = offline == null,
                )
            }
            return@runRefresh false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SharedFlowCentre.toastText.emit(ToastText.Error("获取好友列表失败: ${e.message}"))
            return@runRefresh false
        }
    }

    private fun updateFriend(
        sessionToken: AccountSessionToken,
        userId: String,
        update: (FriendData?) -> FriendData?,
    ): FriendData? = synchronized(friendMapLock) {
        if (!isCurrentSessionLocked(sessionToken)) return@synchronized null
        val updated = friendStore.updateFromEvent(userId, update) ?: return@synchronized null
        publishFriendState()
        updated
    }

    private fun updateOrRemoveFriend(
        sessionToken: AccountSessionToken,
        userId: String,
        update: (FriendData?) -> FriendData?,
    ): Boolean = synchronized(friendMapLock) {
        if (!isCurrentSessionLocked(sessionToken)) return@synchronized false
        friendStore.updateOrRemoveFromEvent(userId, update)
        publishFriendState()
        true
    }

    private fun putFriend(sessionToken: AccountSessionToken, friend: FriendData): Boolean =
        mutateFriendStore(sessionToken) {
            friendStore.putFromEvent(friend)
        }

    private fun removeFriend(sessionToken: AccountSessionToken, userId: String): Boolean =
        mutateFriendStore(sessionToken) {
            friendStore.removeFromEvent(userId)
        }

    private inline fun mutateFriendStore(
        sessionToken: AccountSessionToken,
        update: () -> Unit,
    ): Boolean = synchronized(friendMapLock) {
        if (!isCurrentSessionLocked(sessionToken)) return@synchronized false
        update()
        publishFriendState()
        true
    }

    fun clearFriendData() {
        synchronized(friendMapLock) {
            friendStore.clear()
            publishFriendState()
        }
    }

    private fun commitRefresh(
        sessionToken: AccountSessionToken,
        token: FriendRefreshToken,
        friends: Collection<FriendData>,
        replaceUntouched: Boolean,
    ): Boolean = synchronized(friendMapLock) {
        if (!isCurrentSessionLocked(sessionToken)) return@synchronized false
        val committed = friendStore.mergeRefresh(token, friends, replaceUntouched)
        if (committed) {
            if (replaceUntouched) {
                _initialRefreshCompleted.value = true
            }
            publishFriendState()
            _friendLastActivitySource.value = FriendActivitySourceSnapshot(
                token = sessionToken,
                friends = friends.toList(),
                selfLocation = null,
            )
        }
        committed
    }

    private fun publishFriendState() {
        val snapshot = friendStore.snapshot
        if (_friendState.value != snapshot) {
            _friendState.value = snapshot
        }
        publishFriendActivitySource()
        activeAccountUserId?.let { userId ->
            cacheWriter.submit(
                accountUserId = userId,
                value = PendingFriendCacheSnapshot(
                    token = accountCacheManager.captureWriteToken(userId),
                    snapshot = snapshot,
                ),
            )
        }
    }

    private fun updateCurrentUserLocation(
        sessionToken: AccountSessionToken,
        presence: FriendPresence,
    ) = synchronized(friendMapLock) {
        if (!isCurrentSessionLocked(sessionToken)) return@synchronized
        updateCurrentUserLocationLocked(presence)
    }

    /**
     * Refreshes the signed-in user's location while preserving any WebSocket update
     * that arrives before the HTTP refresh is published.
     */
    suspend fun refreshCurrentUserLocation(): CurrentUserData? {
        val sessionToken = synchronized(friendMapLock) { activeSessionToken } ?: return null
        return refreshCurrentUserLocation(sessionToken)
    }

    private suspend fun refreshCurrentUserLocation(
        sessionToken: AccountSessionToken,
    ): CurrentUserData? = currentUserRefreshMutex.withLock refresh@{
        val locationRevision = synchronized(friendMapLock) {
            if (!isCurrentSessionLocked(sessionToken)) return@refresh null
            currentUserLocationRevision
        }
        val currentUser = authService.refreshCurrentUserPresence(sessionToken) ?: return@refresh null
        synchronized(friendMapLock) {
            if (!isCurrentSessionLocked(sessionToken) || currentUser.id != sessionToken.userId) {
                return@synchronized null
            }
            if (currentUserLocationRevision == locationRevision) {
                updateCurrentUserLocationLocked(currentUser.presence.toFriendPresence())
            }
            currentUser
        }
    }

    private fun updateCurrentUserLocationLocked(presence: FriendPresence) {
        currentUserLocationRevision++
        _currentUserLocation.value = presence
        publishFriendActivitySource()
    }

    private fun publishFriendActivitySource() {
        if (!_initialRefreshCompleted.value) return
        val token = activeSessionToken ?: return
        _friendActivitySource.value = FriendActivitySourceSnapshot(
            token = token,
            friends = friendStore.snapshot.values.toList(),
            selfLocation = _currentUserLocation.value?.location,
        )
    }

    private suspend fun restoreCachedFriendList(sessionToken: AccountSessionToken) {
        val friends = friendListCacheStore.load(sessionToken.userId)?.friends.orEmpty()
            .map(FriendData::asCachedOffline)
        synchronized(friendMapLock) {
            // 读取期间可能已经切换 session 或完成网络刷新，迟到缓存不能再覆盖实时数据。
            if (!isCurrentSessionLocked(sessionToken) || _initialRefreshCompleted.value) {
                return@synchronized
            }
            friendStore.restore(friends)
            publishFriendState()
        }
    }

    private fun isCurrentSession(sessionToken: AccountSessionToken): Boolean =
        synchronized(friendMapLock) { isCurrentSessionLocked(sessionToken) }

    private fun isCurrentSessionLocked(sessionToken: AccountSessionToken): Boolean =
        activeSessionToken == sessionToken && SharedFlowCentre.isCurrentSession(sessionToken)

    fun preloadFriendList() {
        val sessionToken = synchronized(friendMapLock) { activeSessionToken } ?: return
        preloadFriendList(sessionToken)
    }

    private fun preloadFriendList(sessionToken: AccountSessionToken) = preloadTask.start(sessionToken)

    internal fun dispose() {
        serviceScope.cancel()
    }

    suspend fun sendFriendRequest(userId: String) =
        authService.reTryAuthCatching { friendsApi.sendFriendRequest(userId) }

    suspend fun deleteFriendRequest(userId: String) =
        authService.reTryAuthCatching { friendsApi.deleteFriendRequest(userId) }

    suspend fun unfriend(userId: String) =
        authService.reTryAuthCatching { friendsApi.unfriend(userId) }
}

data class FriendPresence(val location: String, val travelingToLocation: String = "")

private fun presenceLocation(world: String, instance: String): String = when {
    instance.startsWith("wrld_") -> instance
    world.startsWith("wrld_") && instance.isNotBlank() && instance != "offline" -> "$world:$instance"
    else -> instance
}

private fun io.github.vrcmteam.vrcm.network.api.auth.data.Presence.toFriendPresence() =
    FriendPresence(
        location = presenceLocation(world, instance),
        travelingToLocation = presenceLocation(travelingToWorld, travelingToInstance),
    )

private data class PendingFriendCacheSnapshot(
    val token: AccountCacheWriteToken,
    val snapshot: Map<String, FriendData>,
)

private data class OfflineLastActivityRefreshRequest(
    val sessionToken: AccountSessionToken,
    val sequence: Long,
)

internal fun FriendData.asCachedOffline(): FriendData = copy(
    location = LocationType.Offline.value,
    travelingToLocation = "",
    status = UserStatus.Offline,
)

sealed class FriendUpdateEvent {
    data class Online(val friend: FriendData?, val userId: String = friend?.id.orEmpty()) : FriendUpdateEvent()
    data class Active(val friend: FriendData) : FriendUpdateEvent()
    data class Offline(val userId: String) : FriendUpdateEvent()
    data class LocationChanged(val friend: FriendData) : FriendUpdateEvent()
    data class Updated(val friend: FriendData) : FriendUpdateEvent()
    data object RefreshRequired : FriendUpdateEvent()
    data class Delete(val userId: String) : FriendUpdateEvent()
}

data class AccountFriendUpdateEvent(
    val sessionToken: AccountSessionToken,
    val event: FriendUpdateEvent,
    val occurredAtMillis: Long? = null,
)

internal suspend fun <T> collectFriendWebSocketEvents(
    events: Flow<T>,
    handle: suspend (T) -> Unit,
    onFailure: suspend (T, Exception) -> Unit,
) {
    events.collect { event ->
        try {
            handle(event)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onFailure(event, e)
        }
    }
}
