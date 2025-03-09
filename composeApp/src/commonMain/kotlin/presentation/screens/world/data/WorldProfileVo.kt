package io.github.vrcmteam.vrcm.presentation.screens.world.data

import io.github.vrcmteam.vrcm.network.api.attributes.RegionType
import io.github.vrcmteam.vrcm.network.api.instances.data.InstanceData
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.presentation.screens.home.data.InstantsVo

data class WorldProfileVo(
    // 基本信息
    val worldId: String,
    val worldName: String,
    val worldImageUrl: String?,
    val thumbnailImageUrl: String? = null,
    val worldDescription: String,
    val authorID: String? = null,
    val authorName: String? = null,
    
    // 世界属性
    val capacity: Int? = null,
    val recommendedCapacity: Int? = null,
    val visits: Int? = null,
    val favorites: Int? = null,
    val heat: Int? = null,
    val popularity: Int? = null,
    val featured: Boolean? = null,
    val tags: List<String>? = null,
    val releaseStatus: String? = null,
    val version: Int? = null,
    
    // 时间信息
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val publicationDate: String? = null,
    
    // 实例信息
    val instanceID: String? = null,
    val currentUsers: Int? = null,
    val pcUsers: Int? = null,
    val androidUsers: Int? = null,
    val queueEnabled: Boolean? = null,
    val queueSize: Int? = null,
    val isActive: Boolean? = null,
    val isFull: Boolean? = null,
    val hasCapacity: Boolean? = null,
    val regionType: RegionType? = null,
    val regionName: String? = null
){
    constructor(world: WorldData, instance: InstanceData? = null):this(
        worldId = world.id,
        worldName = world.name,
        worldImageUrl = world.imageUrlThumbnail,
        thumbnailImageUrl = world.thumbnailImageUrl,
        worldDescription = world.description,
        authorID = world.authorId,
        authorName = world.authorName,
        capacity = world.capacity,
        recommendedCapacity = world.recommendedCapacity,
        visits = world.visits,
        favorites = world.favorites,
        heat = world.heat,
        popularity = world.popularity,
        featured = world.featured,
        tags = world.tags,
        releaseStatus = world.releaseStatus,
        version = world.version,
        createdAt = world.createdAt,
        updatedAt = world.updatedAt,
        publicationDate = world.publicationDate,
        
        // 实例数据，如果有的话
        instanceID = instance?.id,
        currentUsers = instance?.nUsers,
        pcUsers = instance?.platforms?.standaloneWindows,
        androidUsers = instance?.platforms?.android,
        queueEnabled = instance?.queueEnabled,
        queueSize = instance?.queueSize,
        isActive = instance?.active,
        isFull = instance?.full,
        hasCapacity = instance?.hasCapacityForYou,
        regionType = instance?.region,
        regionName = instance?.region?.name
    )
    
    constructor(instant: InstantsVo): this(
        worldId = instant.worldId,
        worldName = instant.worldName,
        worldImageUrl = instant.worldImageUrl,
        worldDescription = instant.worldDescription,
        authorID = instant.worldAuthorId,
        authorName = instant.worldAuthorName,
        
        // 尝试从userCount解析当前用户数和容量 (格式通常为"当前用户/总容量")
        currentUsers = instant.userCount.substringBefore("/").toIntOrNull(),
        capacity = instant.userCount.substringAfter("/", "0").toIntOrNull(),
        
        // 实例相关信息
        regionType = instant.region,
        regionName = instant.region.name,
        
        // 标签信息
        tags = instant.worldAuthorTag,
        
        // 实例相关
        instanceID = instant.owner?.id,
        
        // 基本属性默认值
        isActive = true, // 如果是从InstantsVo创建，通常是活跃的实例
        
        // 其他实例相关信息可能需要进一步完善
        // 以下属性在InstantsVo中不存在，设置为null
        recommendedCapacity = null, 
        visits = null,
        favorites = null,
        heat = null,
        popularity = null,
        featured = null,
        releaseStatus = null,
        version = null,
        createdAt = null,
        updatedAt = null,
        publicationDate = null,
        pcUsers = null,
        androidUsers = null,
        queueEnabled = null,
        queueSize = null,
        isFull = null,
        hasCapacity = null,
        thumbnailImageUrl = null
    )
}