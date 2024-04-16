package io.github.vrcmteam.vrcm.network.api.friends

import io.github.vrcmteam.vrcm.core.extensions.fetchDataList
import io.github.vrcmteam.vrcm.network.api.attributes.AUTH_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.attributes.USER_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.network.extensions.ifOK
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

class FriendsApi(private val client: HttpClient) {
    /**
     * 获取特定所有好友信息
     */
    fun friendsFlow(
        offline: Boolean = false,
        offset: Int = 0,
        n: Int = 50
    ): Flow<List<FriendData>> =
        flow {
            fetchFriendList(
                offline = offline,
                offset = offset,
                n = n
            )
        }

    /**
     * 获取所有好友信息
     */
    fun allFriendsFlow(): Flow<List<FriendData>> =
        flow {
            fetchFriendList(offline = false)
            fetchFriendList(offline = true)
        }

    /**
     * 获取好友信息
     */
    private suspend fun FlowCollector<List<FriendData>>.fetchFriendList(
        offline: Boolean = false,
        offset: Int = 0,
        n: Int = 50
    ){
        fetchDataList(offset,n) { currentOffset, _ ->
            client.get {
                url { path(AUTH_API_PREFIX, USER_API_PREFIX, "friends") }
                parameters {
                    parameter("offline", offline.toString())
                    parameter("offset", currentOffset.toString())
                    parameter("n", n.toString())
                }
            }.ifOK { body<List<FriendData>>() }.getOrThrow()
        }
    }
}
