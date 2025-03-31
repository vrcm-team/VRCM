package io.github.vrcmteam.vrcm.network.api.worlds.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 收藏的世界数据类
 * 继承了WorldData的基本属性，同时添加了与收藏相关的额外属性
 */
@Serializable
data class FavoritedWorld(
    // 继承自WorldData的基本属性
    val authorId: String,
    val authorName: String,
    val capacity: Int,
    val description: String,
    val name: String,
    val id: String,          // 世界ID
    val imageUrl: String,
    val thumbnailImageUrl: String,
    val releaseStatus: String,
    val organization: String,
    val tags: List<String>,
    val favorites: Int,
    val featured: Boolean,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
    val publicationDate: String,
    val labsPublicationDate: String,
    val unityPackages: List<UnityPackage>,
    val popularity: Int,
    val heat: Int,
    val occupants: Int,
    val version: Int,
    val urlList: List<String>,

    // 收藏特有的属性
    val favoriteId: String,           // 收藏记录的ID
    val favoriteGroup: String,        // 收藏组名称
    
    // 可选属性
    val visits: Int? = null,
    val recommendedCapacity: Int? = null,
    val udonProducts: List<String>? = null,
    val previewYoutubeId: String? = null,
    val namespace: String? = null,
    val instances: List<List<String>>? = null
) 