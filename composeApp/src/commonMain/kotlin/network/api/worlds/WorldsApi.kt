package io.github.vrcmteam.vrcm.network.api.worlds

import io.github.vrcmteam.vrcm.network.api.attributes.WORLDS_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.instances.data.InstanceData
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.ktor.client.*
import io.ktor.client.request.*

class WorldsApi(private val client: HttpClient)  {
    /**
     * 获取单个世界的详细信息
     * 
     * @param worldId 世界ID
     * @return WorldData 世界数据对象
     */
    suspend fun getWorldById(worldId: String): WorldData =
        client.get("$WORLDS_API_PREFIX/$worldId").checkSuccess()

    suspend fun getWorldInstanceById(worldId: String, instanceId: String): InstanceData =
        client.get("$WORLDS_API_PREFIX/$worldId/$instanceId").checkSuccess()

}