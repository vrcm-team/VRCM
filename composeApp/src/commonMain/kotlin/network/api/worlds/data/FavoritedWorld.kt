package io.github.vrcmteam.vrcm.network.api.worlds.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 收藏的世界数据类
 * 直接映射API返回的JSON结构
 */
@Serializable
data class FavoritedWorld(
    // 作者信息
    val authorId: String? = "",          // 作者ID，可为空
    val authorName: String? = "",        // 作者名称，可为空
    
    // 世界基本信息
    val id: String,                      // 世界ID
    val name: String,                    // 世界名称
    val description: String? = null,     // 描述，可为空
    val capacity: Int? = 0,              // 最大容量，可为空
    val recommendedCapacity: Int? = null,// 推荐容量，可为空
    val releaseStatus: String? = "",     // 发布状态，可为空
    val imageUrl: String? = null,        // 图片URL，可为空
    val thumbnailImageUrl: String? = null,// 缩略图URL，可为空
    val organization: String? = null,    // 组织，可为空
    val version: Int? = 0,               // 版本号，可为空
    
    // 收藏和流行度信息
    val favoriteId: String,
    val favoriteGroup: String,
    val favorites: Int? = 0,             // 收藏数量，可为空
    val featured: Boolean? = false,      // 是否精选，可为空
    val heat: Int? = 0,                  // 热度，可为空
    val popularity: Int? = 0,            // 流行度，可为空
    val occupants: Int? = null,          // 当前在线人数，可为空
    val visits: Int? = null,             // 访问次数，可为空
    val tags: List<String> = emptyList(),// 标签列表，可为空时给空列表
    val isSecure: Boolean? = null,      // 是否安全，可为空
    // 时间相关
    @SerialName("created_at")
    val createdAt: String? = "",         // 创建时间，可为空
    @SerialName("updated_at")
    val updatedAt: String? = "",         // 更新时间，可为空
    val publicationDate: String? = null, // 发布日期，可为空
    val labsPublicationDate: String? = null, // 实验室发布日期，可为空
    
    // 资源相关
    val unityPackages: List<UnityPackage> = emptyList(), // Unity包列表，可为空时给空列表
    val udonProducts: List<String> = emptyList(),      // Udon产品列表，可为空时给空列表
    val urlList: List<String>? = emptyList(),           // URL列表，可为空
    
    // 内容设置
    val defaultContentSettings: Map<String, Boolean>? = emptyMap(), // 默认内容设置，可为空
    
    // 可选字段
    val previewYoutubeId: String? = null // YouTube预览ID，可为空
) 