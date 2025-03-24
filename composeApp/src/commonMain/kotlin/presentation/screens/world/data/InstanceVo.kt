package io.github.vrcmteam.vrcm.presentation.screens.world.data

import androidx.compose.ui.graphics.vector.ImageVector
import cafe.adriel.voyager.core.lifecycle.JavaSerializable
import io.github.vrcmteam.vrcm.network.api.attributes.AccessType
import io.github.vrcmteam.vrcm.network.api.attributes.BlueprintType
import io.github.vrcmteam.vrcm.network.api.attributes.RegionType
import io.github.vrcmteam.vrcm.network.api.instances.data.InstanceData
import io.github.vrcmteam.vrcm.presentation.screens.home.data.HomeInstanceVo
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 表示单个世界实例的数据类
 */
data class InstanceVo(
    val id: String ,
    val instanceId: String = "",
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
    val owner: StateFlow<Owner?> = MutableStateFlow(null),
    val accessType: AccessType = AccessType.Public,
) : JavaSerializable {
    constructor(instance: InstanceData, owner: StateFlow<Owner?> = MutableStateFlow(null)) : this(
        id = instance.id,
        instanceId = instance.instanceId,
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
        ownerId = instance.ownerId,
        owner = owner,
        accessType = instance.accessType
    )

    constructor(instants: HomeInstanceVo) : this(
        id = instants.id,
        instanceId = instants.instanceId,
        instanceName = instants.name,
        currentUsers = instants.userCount.substringBefore("/").toIntOrNull(),
        regionType = instants.region,
        regionName = instants.region.name,
        isActive = true, // 如果是从InstantsVo创建，通常是活跃的实例
        ownerId = instants.owner?.id,
        owner = MutableStateFlow(instants.owner?.let {
            Owner(
                id = it.id,
                displayName = it.displayName,
                type = it.type
            )
        }),
        accessType = instants.accessType
    )

    data class Owner(
        val id: String,
        val displayName: String,
        val type: BlueprintType,
    ) {
        val iconVector: ImageVector get() = if (type == BlueprintType.User) AppIcons.Person else AppIcons.Groups
    }
} 