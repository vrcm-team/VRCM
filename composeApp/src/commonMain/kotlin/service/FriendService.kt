package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.friends.FriendsApi
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.network.websocket.data.content.FriendActiveContent
import io.github.vrcmteam.vrcm.network.websocket.data.content.FriendLocationContent
import io.github.vrcmteam.vrcm.network.websocket.data.content.FriendOfflineContent
import io.github.vrcmteam.vrcm.network.websocket.data.content.FriendOnlineContent
import io.github.vrcmteam.vrcm.network.websocket.data.content.UserContent
import io.github.vrcmteam.vrcm.network.websocket.data.type.FriendEvents
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class FriendService(
    private val friendsApi: FriendsApi,
    private val authService: AuthService,
    private val json: Json,
) {
    // 缓存好友数据，以ID为键
    // 注意：_friendMap 可能被多个协程并发访问（WebSocket IO 线程 + UI 刷新协程）
    // 使用 synchronized 保护写操作，读操作通过 snapshot 保证安全
    private val _friendMap = mutableMapOf<String, FriendData>()
    private val _friendMapLock = Any()

    // 返回快照，避免迭代时被并发修改
    val friendMap: Map<String, FriendData>
        get() = synchronized(_friendMapLock) { _friendMap.toMap() }

    // 好友状态变更事件，UI 层可监听此 Flow 做增量更新
    private val _friendUpdateFlow = MutableSharedFlow<FriendUpdateEvent>()
    val friendUpdateFlow: SharedFlow<FriendUpdateEvent> = _friendUpdateFlow.asSharedFlow()

    init {
        // 监听WebSocket事件，做增量更新而非全量刷新
        CoroutineScope(Dispatchers.IO).launch {
            SharedFlowCentre.webSocket.collect { socketEvent ->
                when (socketEvent.type) {
                    FriendEvents.FriendOnline.typeName -> {
                        val content = json.decodeFromString<FriendOnlineContent>(socketEvent.content)
                        val friendData = content.toFriendData()
                        synchronized(_friendMapLock) { _friendMap[content.userId] = friendData }
                        _friendUpdateFlow.tryEmit(FriendUpdateEvent.Online(friendData))
                    }

                    FriendEvents.FriendActive.typeName -> {
                        val content = json.decodeFromString<FriendActiveContent>(socketEvent.content)
                        val friendData = content.toFriendData()
                        synchronized(_friendMapLock) { _friendMap[content.userId] = friendData }
                        _friendUpdateFlow.tryEmit(FriendUpdateEvent.Active(friendData))
                    }

                    FriendEvents.FriendOffline.typeName -> {
                        val content = json.decodeFromString<FriendOfflineContent>(socketEvent.content)
                        // 将好友位置标记为离线，但保留在 map 中（其他页面可能需要显示离线好友）
                        synchronized(_friendMapLock) {
                            _friendMap[content.userId]?.let { existing ->
                                _friendMap[content.userId] = existing.copy(location = LocationType.Offline.value)
                            }
                        }
                        _friendUpdateFlow.tryEmit(FriendUpdateEvent.Offline(content.userId))
                    }

                    FriendEvents.FriendLocation.typeName -> {
                        val content = json.decodeFromString<FriendLocationContent>(socketEvent.content)
                        val friendData = content.toFriendData()
                        synchronized(_friendMapLock) { _friendMap[content.userId] = friendData }
                        _friendUpdateFlow.tryEmit(FriendUpdateEvent.LocationChanged(friendData))
                    }

                    FriendEvents.FriendUpdate.typeName -> {
                        // friend-update 事件包含完整的 user 数据（头像、签名等变更）
                        runCatching {
                            val user = json.decodeFromString<UserContent>(socketEvent.content)
                            synchronized(_friendMapLock) {
                                _friendMap[user.id]?.let { existing ->
                                    _friendMap[user.id] = existing.copy(
                                        bio = user.bio,
                                        bioLinks = user.bioLinks,
                                        currentAvatarImageUrl = user.currentAvatarImageUrl,
                                        currentAvatarTags = user.currentAvatarTags,
                                        currentAvatarThumbnailImageUrl = user.currentAvatarThumbnailImageUrl,
                                        displayName = user.displayName,
                                        profilePicOverride = user.profilePicOverride,
                                        status = user.status,
                                        statusDescription = user.statusDescription,
                                        tags = user.tags,
                                        userIcon = user.userIcon,
                                        pronouns = user.pronouns,
                                    )
                                }
                            }
                        }
                    }

                    FriendEvents.FriendAdd.typeName -> {
                        // friend-add 事件包含完整的 user 数据，需要重新拉取该好友信息
                        // 但由于 content 结构不确定，触发全量刷新更可靠
                        refreshFriendList()
                        _friendUpdateFlow.tryEmit(FriendUpdateEvent.Add)
                    }

                    FriendEvents.FriendDelete.typeName -> {
                        // friend-delete 和 friend-offline 的 content 结构相同，都包含 userId
                        runCatching {
                            val content = json.decodeFromString<FriendOfflineContent>(socketEvent.content)
                            synchronized(_friendMapLock) { _friendMap.remove(content.userId) }
                            _friendUpdateFlow.tryEmit(FriendUpdateEvent.Delete(content.userId))
                        }.onFailure {
                            // 如果解析失败，做全量刷新兜底
                            refreshFriendList()
                            _friendUpdateFlow.tryEmit(FriendUpdateEvent.Add)
                        }
                    }

                    else -> return@collect
                }
            }
        }
        CoroutineScope(Dispatchers.Default).launch {
            SharedFlowCentre.authed.collect {
                clearFriendData()
            }
        }
    }

    /**
     * 刷新好友列表
     * @return 是否成功获取到数据
     */
    suspend fun refreshFriendList(offline: Boolean? = null, onUpdater: (List<FriendData>) -> Unit = {}) {

        try {
            val count = (offline?.run(friendsApi::friendsFlow) ?: friendsApi.allFriendsFlow())
                .retry(1) {
                    // 如果是登录失效了就会重新登录并重试一次
                    if (it is VRCApiException) authService.doReTryAuth() else false
                }
                .catch { e ->
                    SharedFlowCentre.toastText.emit(ToastText.Error("获取好友列表失败: ${e.message}"))
                }
                .onEach { friends ->
                    updateFriendMap(friends)
                    onUpdater(friends)
                }
                .count()

            if (count == 0) {
                clearFriendData()
            }

        } catch (e: Exception) {
            SharedFlowCentre.toastText.emit(ToastText.Error("获取好友列表失败: ${e.message}"))
        }
    }

    /**
     * 更新好友数据映射（全量刷新时调用）
     */
    private fun updateFriendMap(friends: List<FriendData>) {
        synchronized(_friendMapLock) { _friendMap.putAll(friends.associateBy { it.id }) }
    }


    /**
     * 清除好友数据
     */
    fun clearFriendData() {
        synchronized(_friendMapLock) { _friendMap.clear() }
    }

    /**
     * 发送好友请求
     */
    suspend fun sendFriendRequest(userId: String) = authService.reTryAuthCatching { friendsApi.sendFriendRequest(userId) }

    /**
     * 删除好友请求
     */
    suspend fun deleteFriendRequest(userId: String) = authService.reTryAuthCatching { friendsApi.deleteFriendRequest(userId) }

    /**
     * 取消好友关系
     */
    suspend fun unfriend(userId: String) = authService.reTryAuthCatching { friendsApi.unfriend(userId) }

}

/**
 * 好友状态变更事件
 */
sealed class FriendUpdateEvent {
    /** 好友上线（在游戏中） */
    data class Online(val friend: FriendData) : FriendUpdateEvent()
    /** 好友活跃（在网站上） */
    data class Active(val friend: FriendData) : FriendUpdateEvent()
    /** 好友离线 */
    data class Offline(val userId: String) : FriendUpdateEvent()
    /** 好友位置变化（切换实例） */
    data class LocationChanged(val friend: FriendData) : FriendUpdateEvent()
    /** 好友添加（需要全量刷新） */
    data object Add : FriendUpdateEvent()
    /** 好友删除 */
    data class Delete(val userId: String) : FriendUpdateEvent()
}