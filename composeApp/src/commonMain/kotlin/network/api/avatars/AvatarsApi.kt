package io.github.vrcmteam.vrcm.network.api.avatars

import io.github.vrcmteam.vrcm.core.extensions.fetchDataList
import io.github.vrcmteam.vrcm.network.api.attributes.AVATARS_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class AvatarsApi(private val client: HttpClient) {

    suspend fun getAvatarById(avatarId: String): AvatarData =
        client.get("$AVATARS_API_PREFIX/$avatarId").checkSuccess()

    suspend fun getFavoritedAvatars(
        n: Int = 50,
        offset: Int = 0,
        tag: String? = null,
        search: String? = null,
        sort: String? = null,
        order: String? = null,
        featured: Boolean? = null,
    ): List<AvatarData> =
        client.get("$AVATARS_API_PREFIX/favorites") {
            parameter("n", n)
            parameter("offset", offset)
            tag?.let { parameter("tag", it) }
            search?.let { parameter("search", it) }
            sort?.let { parameter("sort", it) }
            order?.let { parameter("order", it) }
            featured?.let { parameter("featured", it) }
        }.checkSuccess()

    /**
     * 获取用户的头像列表（分页）
     *
     * @param featured 是否只显示精选头像
     * @param sort 排序方式，默认为更新时间
     * @param user 设置为"me"可搜索自己的头像
     * @param userId 根据用户ID过滤
     * @param n 返回结果数量，默认为50
     * @param order 结果排序顺序，默认为降序
     * @param offset 结果偏移量
     * @param releaseStatus 根据发布状态过滤，可选 public/private/hidden/all
     * @return List<AvatarData> 头像数据列表
     */
    suspend fun getAvatars(
        featured: Boolean? = null,
        sort: String = "updated",
        user: String? = null,
        userId: String? = null,
        n: Int = 50,
        order: String = "descending",
        offset: Int = 0,
        releaseStatus: String? = null,
    ): List<AvatarData> =
        client.get(AVATARS_API_PREFIX) {
            featured?.let { parameter("featured", it) }
            parameter("sort", sort)
            user?.let { parameter("user", it) }
            userId?.let { parameter("userId", it) }
            parameter("n", n)
            parameter("order", order)
            parameter("offset", offset)
            releaseStatus?.let { parameter("releaseStatus", it) }
        }.checkSuccess()

    /**
     * 流式获取用户的头像列表（自动分页）
     *
     * @param user 设置为"me"可搜索自己的头像
     * @param userId 根据用户ID过滤
     * @param sort 排序方式
     * @param order 排序顺序
     * @param releaseStatus 发布状态过滤
     * @param n 每页数量
     * @return Flow<List<AvatarData>> 头像数据列表流
     */
    fun avatarsFlow(
        user: String? = null,
        userId: String? = null,
        sort: String = "updated",
        order: String = "descending",
        releaseStatus: String = "all",
        n: Int = 50,
    ): Flow<List<AvatarData>> = flow {
        fetchDataList(offset = 0, n = n) { currentOffset, pageSize ->
            getAvatars(
                user = user,
                userId = userId,
                sort = sort,
                order = order,
                releaseStatus = releaseStatus,
                n = pageSize,
                offset = currentOffset,
            )
        }
    }
}
