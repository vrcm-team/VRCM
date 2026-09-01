package io.github.vrcmteam.vrcm.network.api.instances


import io.github.vrcmteam.vrcm.network.api.attributes.INSTANCES_API_SUFFIX
import io.github.vrcmteam.vrcm.network.api.instances.data.InstanceCreationOptions
import io.github.vrcmteam.vrcm.network.api.instances.data.InstanceData
import io.github.vrcmteam.vrcm.network.api.instances.data.toCreateInstanceRequest
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*


class InstancesApi(private val client: HttpClient) {
    /**
     * 通过位置获取单个实例信息
     */
    suspend fun instanceByLocation(location: String): InstanceData {
        return client.get { url { path(INSTANCES_API_SUFFIX, location) } }
            .checkSuccess()
    }

    suspend fun createInstance(options: InstanceCreationOptions): InstanceData {
        return client.post(INSTANCES_API_SUFFIX) {
            contentType(ContentType.Application.Json)
            setBody(options.toCreateInstanceRequest())
        }.checkSuccess()
    }
}
