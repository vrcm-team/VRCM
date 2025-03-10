package io.github.vrcmteam.vrcm.network.api.instances


import io.github.vrcmteam.vrcm.network.api.attributes.INSTANCES_API_SUFFIX
import io.github.vrcmteam.vrcm.network.api.instances.data.InstanceData
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
    
    /**
     * 批量获取多个实例信息
     * 
     * @param instanceIds 实例ID列表
     * @return 成功获取的实例数据列表
     */
    suspend fun getInstancesByIds(instanceIds: List<String>): List<InstanceData> {
        // 由于VRC API不支持批量获取，这里使用并行请求方式获取多个实例信息
        return instanceIds.mapNotNull { instanceId ->
            try {
                instanceByLocation(instanceId)
            } catch (e: Exception) {
                // 单个实例请求失败不影响整体结果
                null
            }
        }
    }
}
