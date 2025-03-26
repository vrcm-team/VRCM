package io.github.vrcmteam.vrcm.network.api.favorite.data

import kotlinx.serialization.Serializable

/**
 * 收藏组类型限制数据
 * 
 * 用于表示每种类型的收藏组最大数量限制
 */
@Serializable
data class FavoriteGroupLimits(
    /**
     * 头像收藏组最大数量
     */
    val avatar: Int,
    
    /**
     * 好友收藏组最大数量
     */
    val friend: Int,
    
    /**
     * 世界收藏组最大数量
     */
    val world: Int
)

/**
 * 收藏限制数据
 * 
 * 用于表示用户的收藏限制信息
 */
@Serializable
data class FavoriteLimits(
    /**
     * 每种类型的最大收藏组数量
     */
    val maxFavoriteGroups: FavoriteGroupLimits,
    
    /**
     * 每种类型每组的最大收藏数量
     */
    val maxFavoritesPerGroup: FavoriteGroupLimits,
    
    /**
     * 默认最大收藏组数量
     */
    val defaultMaxFavoriteGroups: Int,
    
    /**
     * 默认每组最大收藏数量
     */
    val defaultMaxFavoritesPerGroup: Int
) 