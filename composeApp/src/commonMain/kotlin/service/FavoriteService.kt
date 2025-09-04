package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.favorite.FavoriteApi
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteData
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteGroupData
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteLimits
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.storage.FavoriteLocalDao
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * 收藏服务类
 *
 * 用于处理所有类型的收藏操作，包括世界、头像等
 */
class FavoriteService(
    private val favoriteApi: FavoriteApi,
    private val favoriteLocalDao: FavoriteLocalDao,
) {

    // 收藏列表缓存，key为FavoriteGroupData的name
    private val _favoritesByGroup: MutableMap<FavoriteType, MutableStateFlow<Map<FavoriteGroupData, List<FavoriteData>>>> =
        mutableMapOf()

    // 收藏限制信息缓存
    private var _favoriteLimits: FavoriteLimits? = null

    init {
        CoroutineScope(Dispatchers.Default).launch{
            SharedFlowCentre.authed.collect {
                _favoritesByGroup.clear()
            }
        }
    }


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
        val limits = _favoriteLimits ?: return 1
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
        val limits = _favoriteLimits ?: return 100
        return when (favoriteType) {
            FavoriteType.World -> limits.maxFavoritesPerGroup.world
            FavoriteType.Avatar -> limits.maxFavoritesPerGroup.avatar
            FavoriteType.Friend -> limits.maxFavoritesPerGroup.friend
        }
    }

    private fun localGroupName(favoriteType: FavoriteType): String = "__local_${favoriteType.value}__"

    private fun localGroupOf(favoriteType: FavoriteType): FavoriteGroupData = FavoriteGroupData(
        id = "local-${favoriteType.value}",
        ownerId = "local",
        type = favoriteType.value,
        visibility = "public",
        displayName = "local",
        name = localGroupName(favoriteType),
        ownerDisplayName = "",
        tags = emptyList()
    )

    private fun toLocalFavoriteId(favoriteType: FavoriteType, favoriteId: String): String =
        "local|${favoriteType.value}|$favoriteId"

    fun parseLocalFavoriteId(id: String): Triple<Boolean, FavoriteType?, String?> {
        if (!id.startsWith("local|")) return Triple(false, null, null)
        val parts = id.split('|')
        if (parts.size != 3) return Triple(false, null, null)
        val type = when (parts[1]) {
            FavoriteType.World.value -> FavoriteType.World
            FavoriteType.Avatar.value -> FavoriteType.Avatar
            FavoriteType.Friend.value -> FavoriteType.Friend
            else -> return Triple(false, null, null)
        }
        return Triple(true, type, parts[2])
    }

    suspend fun loadFavoriteByGroup(favoriteType: FavoriteType) = runCatching {
        val newFavoritesMap = mutableMapOf<String, MutableList<FavoriteData>>()
        // 尝试加载远程收藏
        val remoteGroups = runCatching {
            favoriteApi.fetchFavorite(favoriteType)
                .collect { favoriteDataList ->
                    favoriteDataList.forEach { favoriteData ->
                        val tag = favoriteData.tags.firstOrNull()
                        if (tag != null) {
                            newFavoritesMap.getOrPut(tag) { mutableListOf() }.add(favoriteData)
                        }
                    }
                }
            favoriteApi.getFavoriteGroupsByType(favoriteType)
        }.getOrElse { emptyList() }

        // 本地收藏
        val localGroup = localGroupOf(favoriteType)
        val localIds = favoriteLocalDao.load(favoriteType)
        val localFavorites = localIds.map { fid ->
            FavoriteData(
                favoriteId = fid,
                id = toLocalFavoriteId(favoriteType, fid),
                tags = listOf(localGroup.name),
                type = favoriteType.value
            )
        }

        // 合并远程与本地
        _favoritesByGroup.getOrPut(favoriteType) { MutableStateFlow(mutableMapOf()) }.update {
            remoteGroups.associateWith { (newFavoritesMap[it.name] ?: listOf()) } + (localGroup to localFavorites)
        }
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
    ): FavoriteData {
        val localName = localGroupName(favoriteType)
        return if (groupName == localName) {
            val current = favoriteLocalDao.load(favoriteType)
            if (!current.contains(favoriteId)) {
                favoriteLocalDao.save(favoriteType, current + favoriteId)
            }
            FavoriteData(
                favoriteId = favoriteId,
                id = toLocalFavoriteId(favoriteType, favoriteId),
                tags = listOf(localName),
                type = favoriteType.value
            )
        } else {
            favoriteApi.addFavorite(
                favoriteId = favoriteId,
                favoriteType = favoriteType,
                tag = groupName
            )
        }
    }


    /**
     * 移除收藏
     *
     * @param id 收藏记录ID（注意：这是FavoriteData.id）
     */
    suspend fun removeFavorite(
        id: String,
    ) {
        val (isLocal, type, favoriteId) = parseLocalFavoriteId(id)
        if (isLocal && type != null && favoriteId != null) {
            val current = favoriteLocalDao.load(type)
            favoriteLocalDao.save(type, current.filterNot { it == favoriteId })
        } else {
            favoriteApi.deleteFavorite(id)
        }
    }


    fun getFavoriteByFavoriteId(favoriteType: FavoriteType, favoriteId: String): FavoriteData? =
        favoritesByGroup(favoriteType).value.values.flatten().firstOrNull { it.favoriteId == favoriteId }

} 