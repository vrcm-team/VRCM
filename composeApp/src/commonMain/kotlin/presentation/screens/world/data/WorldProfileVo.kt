package io.github.vrcmteam.vrcm.presentation.screens.world.data

import androidx.compose.runtime.mutableStateListOf
import cafe.adriel.voyager.core.lifecycle.JavaSerializable
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.presentation.screens.home.data.HomeInstanceVo

data class WorldProfileVo(
    // 基本信息
    val worldId: String,
    val worldName: String = "",
    val worldImageUrl: String? = null,
    val thumbnailImageUrl: String? = null,
    val worldDescription: String = "",
    val authorID: String? = null,
    val authorName: String? = null,
    
    // 世界属性
    val capacity: Int = 0,
    val recommendedCapacity: Int = 0,
    val visits: Int = 0,
    val favorites: Int = 0,
    val heat: Int = 0,
    val popularity: Int = 0,
    val privateOccupants: Int = 0,
    val publicOccupants: Int = 0,
    val featured: Boolean? = null,
    val tags: List<String>? = null,
    val releaseStatus: String? = null,
    val version: Int? = null,
    
    // 时间信息
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val publicationDate: String? = null,
    
    // 实例信息列表
    val instances: List<InstanceVo> = mutableStateListOf(),

    ): JavaSerializable {
    
    // 从WorldData构造，不包含实例信息
    constructor(world: WorldData, instancesList: List<InstanceVo> = mutableStateListOf()): this(
        worldId = world.id,
        worldName = world.name,
        worldImageUrl = world.imageUrl,
        thumbnailImageUrl = world.thumbnailImageUrl,
        worldDescription = world.description.orEmpty(),
        authorID = world.authorId,
        authorName = world.authorName,
        capacity = world.capacity,
        recommendedCapacity = world.recommendedCapacity,
        visits = world.visits?: 0,
        favorites = world.favorites?: 0,
        heat = world.heat,
        popularity = world.popularity,
        privateOccupants = world.privateOccupants?: 0,
        publicOccupants = world.publicOccupants?: 0,
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