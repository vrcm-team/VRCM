package io.github.vrcmteam.vrcm.network.api.instances


import io.github.vrcmteam.vrcm.network.api.attributes.INSTANCES_API_SUFFIX
import io.github.vrcmteam.vrcm.network.api.instances.data.InstanceData
import io.github.vrcmteam.vrcm.network.extensions.ifOK
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.path


class InstancesApi(private val client: HttpClient) {
    suspend fun instanceByLocation(location: String): Result<InstanceData> {
        return client.get { url { path(INSTANCES_API_SUFFIX, location) } }
            .ifOK { body<InstanceData>() }
    }
}
