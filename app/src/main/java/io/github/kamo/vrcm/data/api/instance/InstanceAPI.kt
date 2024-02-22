package io.github.kamo.vrcm.data.api.instance


import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

const val instances_API_SUFFIX = "instances"

class InstanceAPI(private val client: HttpClient) {

    suspend fun instanceByLocation(location: String): InstanceInfo {
        return client.get { url { path(instances_API_SUFFIX, location) } }.body()
    }
}
