package io.github.vrcmteam.vrcm.network.api.favorite.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 添加收藏请求
 *
 * 用于发送添加收藏的请求，包括收藏类型、收藏ID和标签
 */
@Serializable
data class AddFavoriteRequest(
    /**
     * 收藏类型
     * 可能的值: world, friend, avatar
     */
    @SerialName("type")
    val type: String,
    
    /**
     * 收藏目标ID
     * 根据type的不同，可以是AvatarID、WorldID或UserID
     */
    @SerialName("favoriteId")
    val favoriteId: String,
    
    /**
     * 标签列表
     * 对于世界，可以是worlds1到worlds4
     * 对于好友，可以是group_0到group_3
     * 对于头像，可以是avatars1到avatars4
     */
    @SerialName("tags")
    val tags: List<String>
) 