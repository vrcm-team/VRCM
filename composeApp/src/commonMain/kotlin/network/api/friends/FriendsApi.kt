package io.github.vrcmteam.vrcm.network.api.friends

import io.github.vrcmteam.vrcm.network.api.attributes.AUTH_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.attributes.USER_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FriendsApi(private val client: HttpClient) {

    fun friendsFlow(offline: Boolean = false, offset: Int = 0, n: Int = 50): Flow<List<FriendData>> = flow {
        var count = 0
        while (true) {
            val bodyList: List<FriendData> = client.get() {
                url { path(AUTH_API_PREFIX,USER_API_PREFIX,"friends") }
                parameters {
                    parameter("offline", offline.toString())
                    parameter("offset", (offset + count * n).toString())
                    parameter("n", n.toString())
                }
            }
                .body()
            if (bodyList.isEmpty()) break
            emit(bodyList)
            count++
        }
    }
}