package io.github.kamo.vrcm.data.api.instance


import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.path

const val instances_API_SUFFIX = "instances"

class InstanceAPI(private val client: HttpClient) {

    suspend fun instanceByLocation(location: String): InstanceData {
        return client.get { url { path(instances_API_SUFFIX, location) } }.body()
    }
}
