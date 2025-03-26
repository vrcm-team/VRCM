package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.favorite.FavoriteApi
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteData
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteGroupData
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteLimits
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 收藏服务类
 *
 * 用于处理所有类型的收藏操作，包括世界、头像等
 */
class FavoriteService(
    private val favoriteApi: FavoriteApi,
) {

    // 收藏列表缓存，key为FavoriteGroupData的name
    private val _favoritesByGroup: MutableMap<FavoriteType, MutableStateFlow<Map<FavoriteGroupData, List<FavoriteData>>>> =
        mutableMapOf()

    // 收藏限制信息缓存
    private lateinit var _favoriteLimits: FavoriteLimits


    fun favoritesByGroup(favoriteType: FavoriteType): StateFlow<Map<FavoriteGroupData, List<FavoriteData>>> =
        _favoritesByGroup.getOrPut(favoriteType) { MutableStateFlow(mutableMapOf()) }


    init {
        CoroutineScope(Job()).launch(Dispatchers.IO) {
            loadFavoriteLimits()
        }
    }

    /**
     * 加载收藏限制信息
     */
    private suspend fun loadFavoriteLimits() {
        _favoriteLimits = favoriteApi.getFavoriteLimits()
    }

    /**
     * 获取指定类型的收藏组最大数量
     *
     * @param favoriteType 收藏类型
     * @return 该类型的收藏组最大数量，如未加载限制信息则返回默认值1
     */
    fun getMaxFavoriteGroups(favoriteType: FavoriteType): Int {
        val limits = _favoriteLimits
        return when (favoriteType) {
            FavoriteType.World -> limits.maxFavoriteGroups.world
            FavoriteType.Avatar -> limits.maxFavoriteGroups.avatar
            FavoriteType.Friend -> limits.maxFavoriteGroups.friend
        }
    }

    /**
     * 获取指定类型每组的最大收藏数量
     *
     * @param favoriteType 收藏类型
     * @return 该类型每组的最大收藏数量，如未加载限制信息则返回默认值100
     */
    fun getMaxFavoritesPerGroup(favoriteType: FavoriteType): Int {
        val limits = _favoriteLimits
        return when (favoriteType) {
            FavoriteType.World -> limits.maxFavoritesPerGroup.world
            FavoriteType.Avatar -> limits.maxFavoritesPerGroup.avatar
            FavoriteType.Friend -> limits.maxFavoritesPerGroup.friend
        }
    }

    suspend fun loadFavoriteByGroup(favoriteType: FavoriteType) = runCatching {
        val newFavoritesByGroup: MutableMap<FavoriteGroupData, List<FavoriteData>> = mutableMapOf()
        val newFavoritesMap = mutableMapOf<String, MutableList<FavoriteData>>()
        favoriteApi.fetchFavorite(favoriteType)
            .collect { favoriteDataList ->
                favoriteDataList.forEach { favoriteData ->
                    val tag = favoriteData.tags.first()
                    newFavoritesMap.getOrPut(tag) { mutableListOf() }.add(favoriteData)
                }
            }
        val groups = favoriteApi.getFavoriteGroupsByType(favoriteType)
        groups.forEach { group ->
            newFavoritesByGroup[group] = newFavoritesMap[group.name] ?: listOf()
        }
        _favoritesByGroup.getOrPut(favoriteType) { MutableStateFlow(mutableMapOf()) }.value = newFavoritesByGroup
    }.onFailure {
        SharedFlowCentre.toastText.emit(ToastText.Error(it.message ?: "Load Favorite By Group Failed"))
    }


    /**
     * 添加收藏
     *
     * @param favoriteId 收藏目标ID
     * @param favoriteType 收藏类型
     * @param groupName 收藏组名
     */
    suspend fun addFavorite(
        favoriteId: String,
        favoriteType: FavoriteType,
        groupName: String,
    ) = favoriteApi.addFavorite(
        favoriteId = favoriteId,
        favoriteType = favoriteType,
        tag = groupName
    )


    /**
     * 移除收藏
     *
     * @param id 收藏记录ID（注意：这是FavoriteData.id）
     */
    suspend fun removeFavorite(
        id: String,
    ) = favoriteApi.deleteFavorite(id)


    fun getFavoriteByFavoriteId(favoriteType: FavoriteType, favoriteId: String): FavoriteData? =
        favoritesByGroup(favoriteType).value.values.flatten().firstOrNull { it.favoriteId == favoriteId }

} 