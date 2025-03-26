package io.github.vrcmteam.vrcm.network.api.favorite.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 收藏组数据类
 * 
 * 用于表示用户的收藏组信息
 */
@Serializable
data class FavoriteGroupData(
    /**
     * 收藏组ID
     */
    val id: String,
    
    /**
     * 所有者ID，必须是UserID格式
     */
    @SerialName("ownerId")
    val ownerId: String,
    
    /**
     * 收藏类型，可能的值：world, friend, avatar
     */
    val type: String,
    
    /**
     * 可见性，可能的值：private, friends, public
     */
    val visibility: String,
    
    /**
     * 显示名称
     */
    @SerialName("displayName")
    val displayName: String,
    
    /**
     * 名称
     */
    val name: String,
    
    /**
     * 所有者显示名称
     */
    @SerialName("ownerDisplayName")
    val ownerDisplayName: String,
    
    /**
     * 标签列表
     */
    val tags: List<String>
) 