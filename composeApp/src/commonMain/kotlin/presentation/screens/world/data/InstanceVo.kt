package io.github.vrcmteam.vrcm.presentation.screens.world.data

import io.github.vrcmteam.vrcm.network.api.attributes.RegionType
import io.github.vrcmteam.vrcm.network.api.instances.data.InstanceData
import io.github.vrcmteam.vrcm.presentation.screens.home.data.HomeInstanceVo

/**
 * 表示单个世界实例的数据类
 */
data class InstanceVo(
    val instanceId: String,
    val instanceName: String = "",
    val currentUsers: Int? = null,
    val pcUsers: Int? = null,
    val androidUsers: Int? = null,
    val queueEnabled: Boolean? = null,
    val queueSize: Int? = null,
    val isActive: Boolean? = null,
    val isFull: Boolean? = null,
    val hasCapacity: Boolean? = null,
    val regionType: RegionType? = null,
    val regionName: String? = null,
    val ownerId: String? = null,
    val ownerName: String? = null
) {
    constructor(instance: InstanceData) : this(
        instanceId = instance.id,
        instanceName = instance.name,
        currentUsers = instance.nUsers,
        pcUsers = instance.platforms.standaloneWindows,
        androidUsers = instance.platforms.android,
        queueEnabled = instance.queueEnabled,
        queueSize = instance.queueSize,
        isActive = instance.active,
        isFull = instance.full,
        hasCapacity = instance.hasCapacityForYou,
        regionType = instance.region,
        regionName = instance.region.name,
        ownerId = instance.ownerId
    )
    
    constructor(instants: HomeInstanceVo) : this(
        instanceId = instants.id,
        instanceName = instants.name,
        currentUsers = instants.userCount.substringBefore("/").toIntOrNull(),
        regionType = instants.region,
        regionName = instants.region.name,
        ownerId = instants.owner?.id,
        ownerName = instants.owner?.displayName,
        isActive = true // 如果是从InstantsVo创建，通常是活跃的实例
    )
} 