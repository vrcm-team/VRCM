package io.github.vrcmteam.vrcm.network.api.worlds

import io.github.vrcmteam.vrcm.core.extensions.fetchDataList
import io.github.vrcmteam.vrcm.network.api.attributes.WORLDS_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.instances.data.InstanceData
import io.github.vrcmteam.vrcm.network.api.worlds.data.FavoritedWorld
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

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
        
    /**
     * 搜索世界
     * 
     * @param search 搜索关键词
     * @param featured 是否只显示精选世界
     * @param sort 排序方式，默认为流行度
     * @param user 设置为"me"可搜索自己的世界
     * @param userId 根据用户ID过滤
     * @param n 返回结果数量，默认为60
     * @param order 结果排序顺序，默认为降序
     * @param offset 结果偏移量
     * @param releaseStatus 根据发布状态过滤
     * @param tag 包含的标签（逗号分隔）
     * @param notag 排除的标签（逗号分隔）
     * @return List<WorldData> 世界数据列表
     */
    suspend fun searchWorld(
        search: String,
        featured: Boolean? = null,
        sort: String = "popularity",
        user: String? = null,
        userId: String? = null,
        n: Int = 60,
        order: String = "descending",
        offset: Int = 0,
        releaseStatus: String? = null,
        tag: String? = null,
        notag: String? = null
    ): List<WorldData> =
        client.get(WORLDS_API_PREFIX) {
            parameter("search", search)
            featured?.let { parameter("featured", it) }
            parameter("sort", sort)
            user?.let { parameter("user", it) }
            userId?.let { parameter("userId", it) }
            parameter("n", n)
            parameter("order", order)
            parameter("offset", offset)
            releaseStatus?.let { parameter("releaseStatus", it) }
            tag?.let { parameter("tag", it) }
            notag?.let { parameter("notag", it) }
        }.checkSuccess()
        
    /**
     * 获取收藏的世界列表（流式版本）
     * 自动分页获取所有收藏世界数据
     *
     * @param n 每页返回结果数量，默认为50
     * @param offset 偏移量，默认为0
     * @param tag 过滤标签
     * @return Flow<List<FavoritedWorld>> 收藏的世界数据列表流
     */
    fun favoritedWorldsFlow(
        n: Int = 50,
        offset: Int = 0,
        tag: String? = null,
    ): Flow<List<FavoritedWorld>> = flow {
        fetchDataList(offset = offset, n = n) { currentOffset, pageSize ->
            getFavoritedWorlds(
                n = pageSize,
                offset = currentOffset,
                tag = tag,
            )
        }
    }
    
    /**
     * 获取收藏的世界列表
     * 
     * @param featured 是否只显示精选世界
     * @param sort 排序方式，默认为流行度
     * @param n 返回结果数量，默认为60，最大100
     * @param order 结果排序顺序，默认为降序
     * @param offset 结果偏移量
     * @param search 按世界名称搜索
     * @param tag 包含的标签（逗号分隔）
     * @param notag 排除的标签（逗号分隔）
     * @param releaseStatus 根据发布状态过滤
     * @param maxUnityVersion 资产支持的最大Unity版本
     * @param minUnityVersion 资产支持的最小Unity版本
     * @param platform 资产支持的平台
     * @param userId 查看目标用户的信息（仅管理员）
     * @return List<FavoritedWorld> 收藏的世界数据列表，包含收藏信息
     */
    suspend fun getFavoritedWorlds(
        featured: Boolean? = null,
        sort: String? = null,
        n: Int = 50,
        order: String? = null,
        offset: Int? = null,
        search: String? = null,
        tag: String? = null,
        notag: String? = null,
        releaseStatus: String? = null,
        maxUnityVersion: String? = null,
        minUnityVersion: String? = null,
        platform: String? = null,
        userId: String? = null
    ): List<FavoritedWorld> =
        client.get("$WORLDS_API_PREFIX/favorites") {
            parameter("n", n)
            featured?.let { parameter("featured", it) }
            sort?.let { parameter("sort", it) }
            order?.let { parameter("order", it) }
            offset?.let { parameter("offset", it) }
            search?.let { parameter("search", it) }
            tag?.let { parameter("tag", it) }
            notag?.let { parameter("notag", it) }
            releaseStatus?.let { parameter("releaseStatus", it) }
            maxUnityVersion?.let { parameter("maxUnityVersion", it) }
            minUnityVersion?.let { parameter("minUnityVersion", it) }
            platform?.let { parameter("platform", it) }
            userId?.let { parameter("userId", it) }
        }.checkSuccess()
}