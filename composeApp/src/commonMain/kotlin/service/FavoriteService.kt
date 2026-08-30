package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.favorite.FavoriteApi
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteData
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteGroupData
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteLimits
import io.github.vrcmteam.vrcm.storage.FavoriteLocalDao
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class FavoriteGroupCache {
    private val flows = FavoriteType.entries.associateWith {
        MutableStateFlow<Map<FavoriteGroupData, List<FavoriteData>>>(emptyMap())
    }

    fun flow(type: FavoriteType): StateFlow<Map<FavoriteGroupData, List<FavoriteData>>> =
        flows.getValue(type)

    fun replace(type: FavoriteType, favorites: Map<FavoriteGroupData, List<FavoriteData>>) {
        flows.getValue(type).value = favorites
    }

    fun clear() {
        flows.values.forEach { it.value = emptyMap() }
    }
}

/**
 * 收藏服务类
 *
 * 用于处理所有类型的收藏操作，包括世界、头像等
 */
class FavoriteService(
    private val favoriteApi: FavoriteApi,
    private val favoriteLocalDao: FavoriteLocalDao,
) {

    private val favoritesByGroupCache = FavoriteGroupCache()
    private var favoritesOwnerUserId: String? = SharedFlowCentre.currentSession.value?.token?.userId
    private var favoritesOwnerToken: io.github.vrcmteam.vrcm.core.shared.AccountSessionToken? =
        SharedFlowCentre.currentSession.value?.token
    private val cacheMutex = Mutex()
    private val requestGenerations = mutableMapOf<FavoriteType, Long>()

    // 收藏限制信息缓存
    private var _favoriteLimits: FavoriteLimits? = null

    init {
        CoroutineScope(Dispatchers.Default).launch {
            SharedFlowCentre.currentSession.collect { session ->
                val nextUserId = session?.token?.userId
                cacheMutex.withLock {
                    val nextToken = session?.token
                    if (favoritesOwnerToken != nextToken) {
                        if (favoritesOwnerUserId != nextUserId) favoritesByGroupCache.clear()
                        favoritesOwnerUserId = nextUserId
                        favoritesOwnerToken = nextToken
                        FavoriteType.entries.forEach { type ->
                            requestGenerations[type] = (requestGenerations[type] ?: 0L) + 1L
                        }
                    }
                }
            }
        }
    }


    fun favoritesByGroup(favoriteType: FavoriteType): StateFlow<Map<FavoriteGroupData, List<FavoriteData>>> =
        favoritesByGroupCache.flow(favoriteType)


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

    suspend fun loadFavoriteByGroup(favoriteType: FavoriteType): Result<Unit> {
        val result = runCatching {
            val sessionToken = cacheMutex.withLock {
                val currentToken = SharedFlowCentre.currentSession.value?.token
                    ?: error("No authenticated session")
                if (favoritesOwnerToken?.userId != currentToken.userId) {
                    favoritesByGroupCache.clear()
                }
                favoritesOwnerUserId = currentToken.userId
                favoritesOwnerToken = currentToken
                requestGenerations[favoriteType] = (requestGenerations[favoriteType] ?: 0L) + 1L
                currentToken to requestGenerations.getValue(favoriteType)
            }
            val token = sessionToken.first
            val generation = sessionToken.second
            val newFavoritesMap = mutableMapOf<String, MutableList<FavoriteData>>()
            favoriteApi.fetchFavorite(favoriteType)
                .toCollection(mutableListOf())
                .flatten()
                .forEach { favoriteData ->
                    val tag = favoriteData.tags.firstOrNull() ?: return@forEach
                    newFavoritesMap.getOrPut(tag) { mutableListOf() }.add(favoriteData)
                }

            val remoteGroups = favoriteApi.getFavoriteGroupsByType(favoriteType)

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

            cacheMutex.withLock {
                val currentToken = SharedFlowCentre.currentSession.value?.token
                if (currentToken == token && favoritesOwnerToken == token &&
                    requestGenerations[favoriteType] == generation
                ) {
                    favoritesByGroupCache.replace(
                        favoriteType,
                        remoteGroups.associateWith { newFavoritesMap[it.name] ?: listOf() } +
                            (localGroup to localFavorites),
                    )
                }
            }
        }
        result.exceptionOrNull()?.let { error ->
            if (error is CancellationException) throw error
        }
        return result
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
