package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.friends.FriendsApi
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.network.websocket.data.WebSocketEvent
import io.github.vrcmteam.vrcm.network.websocket.data.content.FriendActiveContent
import io.github.vrcmteam.vrcm.network.websocket.data.content.FriendLocationContent
import io.github.vrcmteam.vrcm.network.websocket.data.content.FriendOfflineContent
import io.github.vrcmteam.vrcm.network.websocket.data.content.FriendOnlineContent
import io.github.vrcmteam.vrcm.network.websocket.data.content.FriendUpdateContent
import io.github.vrcmteam.vrcm.network.websocket.data.type.FriendEvents
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.storage.FriendListCacheDao
import io.github.vrcmteam.vrcm.storage.data.FriendListCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
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
) {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val friendMapLock = Any()
    private val friendStore = FriendStateStore()
    private val refreshCoordinator = FriendRefreshCoordinator()
    private val accountTracker = FriendAccountTracker()
    private var activeAccountUserId: String? = null
    private var preloadJob: Job? = null

    private val _friendState = MutableStateFlow<Map<String, FriendData>>(emptyMap())
    val friendState: StateFlow<Map<String, FriendData>> = _friendState.asStateFlow()

    val friendMap: Map<String, FriendData>
        get() = friendState.value

    private val _friendUpdateFlow = MutableSharedFlow<FriendUpdateEvent>()
    val friendUpdateFlow: SharedFlow<FriendUpdateEvent> = _friendUpdateFlow.asSharedFlow()

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
                        ToastText.Error("处理好友实时事件 ${socketEvent.type} 失败: ${e.message}")
                    )
                },
            )
        }
        serviceScope.launch {
            SharedFlowCentre.authed.collect { account ->
                synchronized(friendMapLock) {
                    if (activeAccountUserId != account.userId) {
                        restoreCachedFriendList(account.userId)
                    }
                    accountTracker.onAuthenticated(account.userId)
                }
                preloadFriendList()
            }
        }
        serviceScope.launch {
            SharedFlowCentre.logout.collect {
                synchronized(friendMapLock) {
                    accountTracker.onLogout()
                    activeAccountUserId = null
                    friendStore.clear()
                    publishFriendState()
                }
            }
        }
    }

    private suspend fun handleWebSocketEvent(socketEvent: WebSocketEvent) {
        when (socketEvent.type) {
            FriendEvents.FriendOnline.typeName -> {
                val content = json.decodeFromString<FriendOnlineContent>(socketEvent.content)
                val friend = updateFriend(content.userId, content::mergeWith)
                    ?: return refreshAfterIncompleteEvent()
                _friendUpdateFlow.emit(FriendUpdateEvent.Online(friend))
            }

            FriendEvents.FriendActive.typeName -> {
                val content = json.decodeFromString<FriendActiveContent>(socketEvent.content)
                val friend = content.toFriendData()
                putFriend(friend)
                _friendUpdateFlow.emit(FriendUpdateEvent.Active(friend))
            }

            FriendEvents.FriendOffline.typeName -> {
                val content = json.decodeFromString<FriendOfflineContent>(socketEvent.content)
                updateOrRemoveFriend(content.userId) { existing ->
                    existing?.copy(
                        location = LocationType.Offline.value,
                        travelingToLocation = "",
                        status = UserStatus.Offline,
                    )
                }
                _friendUpdateFlow.emit(FriendUpdateEvent.Offline(content.userId))
            }

            FriendEvents.FriendLocation.typeName -> {
                val content = json.decodeFromString<FriendLocationContent>(socketEvent.content)
                val friend = updateFriend(content.userId, content::mergeWith)
                    ?: return refreshAfterIncompleteEvent()
                _friendUpdateFlow.emit(FriendUpdateEvent.LocationChanged(friend))
            }

            FriendEvents.FriendUpdate.typeName -> {
                val content = json.decodeFromString<FriendUpdateContent>(socketEvent.content)
                val friend = updateFriend(content.user.id) { existing ->
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
                } ?: return refreshAfterIncompleteEvent()
                _friendUpdateFlow.emit(FriendUpdateEvent.Updated(friend))
            }

            FriendEvents.FriendAdd.typeName -> {
                refreshFriendList()
                _friendUpdateFlow.emit(FriendUpdateEvent.RefreshRequired)
            }

            FriendEvents.FriendDelete.typeName -> {
                val content = json.decodeFromString<FriendOfflineContent>(socketEvent.content)
                removeFriend(content.userId)
                _friendUpdateFlow.emit(FriendUpdateEvent.Delete(content.userId))
            }
        }
    }

    private suspend fun refreshAfterIncompleteEvent() {
        refreshFriendList()
        _friendUpdateFlow.emit(FriendUpdateEvent.RefreshRequired)
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
        userId: String,
        update: (FriendData?) -> FriendData?,
    ): FriendData? = synchronized(friendMapLock) {
        val updated = friendStore.updateFromEvent(userId, update) ?: return@synchronized null
        publishFriendState()
        updated
    }

    private fun updateOrRemoveFriend(
        userId: String,
        update: (FriendData?) -> FriendData?,
    ): FriendData? = synchronized(friendMapLock) {
        val updated = friendStore.updateOrRemoveFromEvent(userId, update)
        publishFriendState()
        updated
    }

    private fun putFriend(friend: FriendData) = mutateFriendStore {
        friendStore.putFromEvent(friend)
    }

    private fun removeFriend(userId: String) = mutateFriendStore {
        friendStore.removeFromEvent(userId)
    }

    fun clearFriendData() = mutateFriendStore {
        friendStore.clear()
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

    private inline fun mutateFriendStore(update: () -> Unit) {
        synchronized(friendMapLock) {
            update()
            publishFriendState()
        }
    }

    private fun publishFriendState() {
        val snapshot = friendStore.snapshot
        if (_friendState.value == snapshot) return
        _friendState.value = snapshot
        activeAccountUserId?.takeIf { snapshot.isNotEmpty() }?.let { userId ->
            friendListCacheDao.save(userId, FriendListCache(snapshot.values.toList()))
        }
    }

    private fun restoreCachedFriendList(userId: String) {
        activeAccountUserId = userId
        val friends = friendListCacheDao.load(userId)?.friends.orEmpty().map { friend ->
            if (friend.location == LocationType.Offline.value) {
                friend.copy(status = UserStatus.Offline, travelingToLocation = "")
            } else {
                friend
            }
        }
        friendStore.restore(friends)
        publishFriendState()
    }

    fun preloadFriendList() {
        if (preloadJob?.isActive == true) return
        preloadJob = serviceScope.launch { refreshFriendList() }
    }

    suspend fun sendFriendRequest(userId: String) =
        authService.reTryAuthCatching { friendsApi.sendFriendRequest(userId) }

    suspend fun deleteFriendRequest(userId: String) =
        authService.reTryAuthCatching { friendsApi.deleteFriendRequest(userId) }

    suspend fun unfriend(userId: String) =
        authService.reTryAuthCatching { friendsApi.unfriend(userId) }
}

sealed class FriendUpdateEvent {
    data class Online(val friend: FriendData) : FriendUpdateEvent()
    data class Active(val friend: FriendData) : FriendUpdateEvent()
    data class Offline(val userId: String) : FriendUpdateEvent()
    data class LocationChanged(val friend: FriendData) : FriendUpdateEvent()
    data class Updated(val friend: FriendData) : FriendUpdateEvent()
    data object RefreshRequired : FriendUpdateEvent()
    data class Delete(val userId: String) : FriendUpdateEvent()
}

internal suspend fun collectFriendWebSocketEvents(
    events: Flow<WebSocketEvent>,
    handle: suspend (WebSocketEvent) -> Unit,
    onFailure: suspend (WebSocketEvent, Exception) -> Unit,
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
