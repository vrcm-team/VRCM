package io.github.vrcmteam.vrcm.presentation.screens.world.data

import cafe.adriel.voyager.core.lifecycle.JavaSerializable
import io.github.vrcmteam.vrcm.network.api.instances.data.InstanceData
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.presentation.screens.home.data.HomeInstanceVo

data class WorldProfileVo(
    // 基本信息
    val worldId: String,
    val worldName: String = "",
    val worldImageUrl: String? = null,
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
    
    // 实例信息列表
    val instances: List<InstanceVo>,

    ): JavaSerializable {
    
    // 从WorldData构造，不包含实例信息
    constructor(world: WorldData, instancesList: List<InstanceVo> = listOf()): this(
        worldId = world.id,
        worldName = world.name,
        worldImageUrl = world.imageUrl,
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
        tags = world.tags.filter { it.startsWith("author_tag_") }
            .map { it.substringAfter("author_tag_") },
        releaseStatus = world.releaseStatus,
        version = world.version,
        createdAt = world.createdAt,
        updatedAt = world.updatedAt,
        publicationDate = world.publicationDate,
        instances = instancesList,
    )
    
    // 从单个InstanceData构造临时对象
    constructor(instance: InstanceData): this(
        worldId = instance.world.id,
        worldName = instance.world.name,
        worldImageUrl = instance.world.imageUrlThumbnail,
        thumbnailImageUrl = instance.world.thumbnailImageUrl,
        worldDescription = instance.world.description,
        authorID = instance.world.authorId,
        authorName = instance.world.authorName,
        capacity = instance.world.capacity,
        recommendedCapacity = instance.world.recommendedCapacity,
        visits = instance.world.visits,
        favorites = instance.world.favorites,
        heat = instance.world.heat,
        popularity = instance.world.popularity,
        featured = instance.world.featured,
        tags = instance.world.tags,
        releaseStatus = instance.world.releaseStatus,
        version = instance.world.version,
        createdAt = instance.world.createdAt,
        updatedAt = instance.world.updatedAt,
        publicationDate = instance.world.publicationDate,
        instances = mutableListOf(InstanceVo(instance)),
    )
    
    // 从InstantsVo构造临时对象
    constructor(instant: HomeInstanceVo): this(
        worldId = instant.worldId,
        worldName = instant.worldName,
        worldImageUrl = instant.worldImageUrl,
        worldDescription = instant.worldDescription,
        authorID = instant.worldAuthorId,
        authorName = instant.worldAuthorName,
        tags = instant.worldAuthorTag,
        instances = mutableListOf(InstanceVo(instant)),
    )
}