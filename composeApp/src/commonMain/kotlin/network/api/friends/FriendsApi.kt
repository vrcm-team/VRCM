package io.github.vrcmteam.vrcm.network.api.friends

import io.github.vrcmteam.vrcm.network.api.attributes.AUTH_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.attributes.USER_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.network.extensions.ifOK
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.parameters
import io.ktor.http.path
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FriendsApi(private val client: HttpClient) {

    fun friendsFlow(
        offline: Boolean = false,
        offset: Int = 0,
        n: Int = 50
    ): Result<Flow<List<FriendData>>> = runCatching {
        flow {
            var count = 0
            while (true) {
                val bodyList: List<FriendData> = client.get {
                    url { path(AUTH_API_PREFIX, USER_API_PREFIX, "friends") }
                    parameters {
                        parameter("offline", offline.toString())
                        parameter("offset", (offset + count * n).toString())
                        parameter("n", n.toString())
                    }
                }.ifOK { body<List<FriendData>>() }.getOrThrow()
                if (bodyList.isEmpty()) break
                emit(bodyList)
                count++
            }
        }
    }
}