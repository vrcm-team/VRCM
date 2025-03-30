package io.github.vrcmteam.vrcm.network.api.favorite

import io.github.vrcmteam.vrcm.core.extensions.fetchDataList
import io.github.vrcmteam.vrcm.network.api.attributes.*
import io.github.vrcmteam.vrcm.network.api.favorite.data.AddFavoriteRequest
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteData
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteGroupData
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteLimits
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toCollection

class FavoriteApi(private val client: HttpClient) {

    fun fetchFavorite(
        favoriteType: FavoriteType,
        offset: Int = 0,
        n: Int = 50,
    ): Flow<List<FavoriteData>> = flow {
            fetchDataList(offset, n) { currentOffset, _ ->
                client.get(FAVORITES_API_PREFIX) {
                    parameters {
                        parameter("type", favoriteType.value)
                        parameter("offset", currentOffset.toString())
                        parameter("n", n.toString())
                    }
                }.body()
            }
        }
        
    /**
     * 添加收藏
     *
     * @param favoriteId 收藏目标ID，可以是世界ID、用户ID或头像ID
     * @param favoriteType 收藏类型
     * @param tag 标签，对于世界收藏可以是"worlds1"到"worlds4"
     * @return 返回添加的收藏数据
     */
    suspend fun addFavorite(
        favoriteId: String,
        favoriteType: FavoriteType,
        tag: String
    ): FavoriteData {
        val request = AddFavoriteRequest(
            type = favoriteType.value,
            favoriteId = favoriteId,
            tags = listOf(tag)
        )
        
        return client.post(FAVORITES_API_PREFIX) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.checkSuccess()
    }

    
    /**
     * 获取指定类型的收藏组列表
     *
     * @param favoriteType 收藏类型
     * @param n 返回的对象数量，默认60，最大100
     * @param offset 偏移量，从0开始
     * @param ownerId 获取指定用户的收藏组，留空表示获取当前用户的收藏组
     * @return 指定类型的收藏组列表
     */
    suspend fun getFavoriteGroupsByType(
        favoriteType: FavoriteType,
        n: Int = 50,
        offset: Int = 0,
        ownerId: String? = null
    ): List<FavoriteGroupData> {
         return flow{
            fetchFavoriteGroupList(
                favoriteType = favoriteType,
                n = n,
                offset = offset,
                ownerId = ownerId

            )
        }.toCollection(mutableListOf()).flatten()
    }

    private suspend fun  FlowCollector<List<FavoriteGroupData>>.fetchFavoriteGroupList(
        favoriteType: FavoriteType,
        n: Int = 50,
        offset: Int = 0,
        ownerId: String? = null
    ){
        val limitedN = n.coerceIn(1, 100)
        fetchDataList(offset, limitedN){ currentOffset , _ ->
            client.get("$FAVORITE_API_PREFIX/groups") {
                // 限制n的范围为1-100
                parameters {
                    parameter("n", limitedN.toString())
                    parameter("offset", currentOffset.toString())
                    parameter("type", favoriteType.value)
                    if (ownerId != null) {
                        parameter("ownerId", ownerId)
                    }
                }
            }.checkSuccess<List<FavoriteGroupData>>()
        }
    }

    /**
     * 删除收藏
     *
     * @param favoriteId 收藏记录ID（注意：这是FavoriteData的favoriteId）
     */
    suspend fun deleteFavorite(favoriteId: String) {
        client.delete("$FAVORITES_API_PREFIX/$favoriteId").checkSuccess<Unit>()
    }

    /**
     * 获取收藏限制信息
     * 
     * @return 返回用户的收藏限制数据
     */
    suspend fun getFavoriteLimits(): FavoriteLimits {
        return client.get("$AUTH_API_PREFIX/$USER_API_PREFIX/$FAVORITE_LIMITS_API_SUFFIX").checkSuccess()
    }

}