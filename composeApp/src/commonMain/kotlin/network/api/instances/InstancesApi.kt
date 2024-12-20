package io.github.vrcmteam.vrcm.network.api.instances


import io.github.vrcmteam.vrcm.network.api.attributes.INSTANCES_API_SUFFIX
import io.github.vrcmteam.vrcm.network.api.instances.data.InstanceData
import io.github.vrcmteam.vrcm.network.extensions.checkSuccessResult
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*


class InstancesApi(private val client: HttpClient) {
    suspend fun instanceByLocation(location: String): Result<InstanceData> {
        return client.get { url { path(INSTANCES_API_SUFFIX, location) } }
            .checkSuccessResult()
    }
}
