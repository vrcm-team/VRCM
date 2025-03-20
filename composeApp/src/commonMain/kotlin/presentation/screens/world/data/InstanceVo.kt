package io.github.vrcmteam.vrcm.presentation.screens.world.data

import androidx.compose.ui.graphics.vector.ImageVector
import cafe.adriel.voyager.core.lifecycle.JavaSerializable
import io.github.vrcmteam.vrcm.network.api.attributes.AccessType
import io.github.vrcmteam.vrcm.network.api.attributes.BlueprintType
import io.github.vrcmteam.vrcm.network.api.attributes.RegionType
import io.github.vrcmteam.vrcm.network.api.instances.data.InstanceData
import io.github.vrcmteam.vrcm.presentation.screens.home.data.HomeInstanceVo
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons

/**
 * 表示单个世界实例的数据类
 */
data class InstanceVo(
    val instanceId: String,
    val instanceName: String = "unknown",
    val currentUsers: Int? = null,
    val pcUsers: Int? = null,
    val androidUsers: Int? = null,
    val queueEnabled: Boolean? = null,
    val queueSize: Int? = null,
    val isActive: Boolean? = null,
    val isFull: Boolean? = null,
    val hasCapacity: Boolean? = null,
    val regionType: RegionType = RegionType.Us,
    val regionName: String = "unknown",
    val ownerId: String? = null,
    val owner: Owner? = null,
    val accessType: AccessType = AccessType.Public,
) : JavaSerializable {
    constructor(instance: InstanceData, owner: Owner? = null) : this(
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
        owner = owner,
        accessType = instance.accessType
    )

    constructor(instants: HomeInstanceVo) : this(
        instanceId = instants.id,
        instanceName = instants.name,
        currentUsers = instants.userCount.substringBefore("/").toIntOrNull(),
        regionType = instants.region,
        regionName = instants.region.name,
        isActive = true, // 如果是从InstantsVo创建，通常是活跃的实例
        owner = instants.owner?.let { Owner(
            id = it.id,
            displayName = it.displayName,
            type = it.type
        )},
        accessType = instants.accessType
    )
    data class Owner(
        val id: String,
        val displayName: String,
        val type: BlueprintType,
    ){
        val iconVector: ImageVector get() = if (type == BlueprintType.User) AppIcons.Person else AppIcons.Groups
    }
} 