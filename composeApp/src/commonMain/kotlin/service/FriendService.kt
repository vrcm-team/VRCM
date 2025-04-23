package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.friends.FriendsApi
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.network.websocket.data.type.FriendEvents
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.launch

class FriendService(
    private val friendsApi: FriendsApi,
    private val authService: AuthService
) {
    // 缓存好友数据，以ID为键
    private val _friendMap = mutableMapOf<String, FriendData>()

    val friendMap: Map<String, FriendData> =_friendMap

    init {
        // 监听WebSocket事件
        CoroutineScope(Dispatchers.IO).launch {
            SharedFlowCentre.webSocket.collect { socketEvent ->
                when (socketEvent.type) {
                    FriendEvents.FriendActive.typeName,
                    FriendEvents.FriendOffline.typeName,
                    FriendEvents.FriendAdd.typeName,
                    FriendEvents.FriendDelete.typeName,
                    FriendEvents.FriendOnline.typeName,
                        -> {
                        // 这里监听到一个好友的状态变化就会全量刷新一次
                        // 只更新监听到的那个好友的状态的话怕数据不一致所以全量刷新(暂时)
                        refreshFriendList()
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
    suspend fun refreshFriendList(onUpdater: () -> Unit = {}) {

        try {
            val count = friendsApi.allFriendsFlow()
                .retry(1) {
                    // 如果是登录失效了就会重新登录并重试一次
                    if (it is VRCApiException) authService.doReTryAuth() else false
                }
                .catch { e ->
                    SharedFlowCentre.toastText.emit(ToastText.Error("获取好友列表失败: ${e.message}"))
                }
                .onEach { friends ->
                    updateFriendMap(friends)
                    onUpdater()
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
     * 更新好友数据映射
     */
    private fun updateFriendMap(friends: List<FriendData>) {
        _friendMap.putAll(friends.associateBy { it.id })
    }


    /**
     * 清除好友数据
     */
    fun clearFriendData() {
        _friendMap.clear()
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