package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AccountWebSocketEvent
import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.friends.FriendsApi
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.network.websocket.data.content.FriendActiveContent
import io.github.vrcmteam.vrcm.network.websocket.data.content.FriendLocationContent
import io.github.vrcmteam.vrcm.network.websocket.data.content.FriendOfflineContent
import io.github.vrcmteam.vrcm.network.websocket.data.content.FriendOnlineContent
import io.github.vrcmteam.vrcm.network.websocket.data.content.FriendUpdateContent
import io.github.vrcmteam.vrcm.network.websocket.data.type.FriendEvents
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.storage.FriendListCacheDao
import io.github.vrcmteam.vrcm.storage.AccountCacheManager
import io.github.vrcmteam.vrcm.storage.AccountCacheWriteToken
import io.github.vrcmteam.vrcm.storage.data.FriendListCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
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
import kotlinx.serialization.json.Json

class FriendService(
    private val friendsApi: FriendsApi,
    private val authService: AuthService,
    private val json: Json,
    private val friendListCacheDao: FriendListCacheDao,
    private val accountCacheManager: AccountCacheManager,
) {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val friendMapLock = Any()
    private val friendStore = FriendStateStore()
    private val refreshCoordinator = FriendRefreshCoordinator()
    private val accountTracker = FriendAccountTracker()
    private var activeAccountUserId: String? = null
    private var activeSessionToken: AccountSessionToken? = null
    private val cacheWriter =
        ConflatedAccountCacheWriter<PendingFriendCacheSnapshot>(serviceScope) { _, pending ->
            accountCacheManager.saveFriendListIfCurrent(
                token = pending.token,
                cache = FriendListCache(pending.snapshot.values.toList()),
            )
    }
    private val preloadTask = AccountBoundTask<String>(
        scope = serviceScope,
        isCurrent = { userId ->
            synchronized(friendMapLock) { activeAccountUserId == userId }
        },
        runTask = { refreshFriendList() },
    )

    private val _friendState = MutableStateFlow<Map<String, FriendData>>(emptyMap())
    val friendState: StateFlow<Map<String, FriendData>> = _friendState.asStateFlow()

    val friendMap: Map<String, FriendData>
        get() = friendState.value

    private val _friendUpdateFlow = MutableSharedFlow<AccountFriendUpdateEvent>()
    val friendUpdateFlow: SharedFlow<AccountFriendUpdateEvent> = _friendUpdateFlow.asSharedFlow()

    init {
        authService.accountDtoOrNull()?.userId?.takeIf(String::isNotBlank)?.let { userId ->
            synchronized(friendMapLock) { restoreCachedFriendList(userId) }
        }
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
            SharedFlowCentre.authed.collect { session ->
                val account = session.account
                synchronized(friendMapLock) {
                    activeSessionToken = session.token
                    if (activeAccountUserId != account.userId) {
                        restoreCachedFriendList(account.userId)
                    }
                    accountTracker.onAuthenticated(account.userId)
                }
                preloadFriendList(account.userId)
            }
        }
        serviceScope.launch {
            SharedFlowCentre.logout.collect {
                synchronized(friendMapLock) {
                    accountTracker.onLogout()
                    activeAccountUserId = null
                    activeSessionToken = null
                    friendStore.clear()
                    publishFriendState()
                }
                preloadTask.cancelAndJoin()
            }
        }
    }

    private suspend fun handleWebSocketEvent(accountEvent: AccountWebSocketEvent) {
        val sessionToken = accountEvent.token
        if (!isCurrentSession(sessionToken)) return
        val socketEvent = accountEvent.event
        when (socketEvent.type) {
            FriendEvents.FriendOnline.typeName -> {
                val content = json.decodeFromString<FriendOnlineContent>(socketEvent.content)
                val friend = updateFriend(sessionToken, content.userId, content::mergeWith)
                    ?: return refreshAfterIncompleteEvent(sessionToken)
                emitFriendUpdate(sessionToken, FriendUpdateEvent.Online(friend))
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
                emitFriendUpdate(sessionToken, FriendUpdateEvent.Offline(content.userId))
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
    ) {
        _friendUpdateFlow.emit(AccountFriendUpdateEvent(sessionToken, event))
    }

    /**
     * 刷新好友列表。分页回调按页顺序执行，缓存仅在完整请求成功后发布。
     */
    suspend fun refreshFriendList(
        offline: Boolean? = null,
        onUpdater: suspend (List<FriendData>) -> Unit = {},
    ): Boolean = refreshCoordinator.runRefresh {
        val refreshToken = synchronized(friendMapLock) { friendStore.beginRefresh() }
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
        token: FriendRefreshToken,
        friends: Collection<FriendData>,
        replaceUntouched: Boolean,
    ): Boolean = synchronized(friendMapLock) {
        val committed = friendStore.mergeRefresh(token, friends, replaceUntouched)
        if (committed) publishFriendState()
        committed
    }

    private fun publishFriendState() {
        val snapshot = friendStore.snapshot
        if (_friendState.value != snapshot) {
            _friendState.value = snapshot
        }
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

    private fun restoreCachedFriendList(userId: String) {
        activeAccountUserId = userId
        val friends = friendListCacheDao.load(userId)?.friends.orEmpty()
            .map(FriendData::asCachedOffline)
        friendStore.restore(friends)
        publishFriendState()
    }

    private fun isCurrentSession(sessionToken: AccountSessionToken): Boolean =
        synchronized(friendMapLock) { isCurrentSessionLocked(sessionToken) }

    private fun isCurrentSessionLocked(sessionToken: AccountSessionToken): Boolean =
        activeSessionToken == sessionToken && SharedFlowCentre.isCurrentSession(sessionToken)

    fun preloadFriendList() {
        val userId = synchronized(friendMapLock) { activeAccountUserId } ?: return
        preloadFriendList(userId)
    }

    private fun preloadFriendList(userId: String) = preloadTask.start(userId)

    suspend fun sendFriendRequest(userId: String) =
        authService.reTryAuthCatching { friendsApi.sendFriendRequest(userId) }

    suspend fun deleteFriendRequest(userId: String) =
        authService.reTryAuthCatching { friendsApi.deleteFriendRequest(userId) }

    suspend fun unfriend(userId: String) =
        authService.reTryAuthCatching { friendsApi.unfriend(userId) }
}

private data class PendingFriendCacheSnapshot(
    val token: AccountCacheWriteToken,
    val snapshot: Map<String, FriendData>,
)

internal fun FriendData.asCachedOffline(): FriendData = copy(
    location = LocationType.Offline.value,
    travelingToLocation = "",
    status = UserStatus.Offline,
)

sealed class FriendUpdateEvent {
    data class Online(val friend: FriendData) : FriendUpdateEvent()
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
