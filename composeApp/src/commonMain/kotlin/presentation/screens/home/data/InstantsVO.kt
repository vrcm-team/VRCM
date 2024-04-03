package io.github.vrcmteam.vrcm.presentation.screens.home.data

import io.github.vrcmteam.vrcm.network.api.attributes.CountryIcon
import io.github.vrcmteam.vrcm.network.api.instances.data.InstanceData

data class InstantsVO(
    val worldId: String = "",
    val worldName: String = "",
    val worldImageUrl: String? = null,
    val worldDescription: String = "",
    val accessType: String = "",
    val regionIconUrl: String? = null,
    val userCount: String = "",
){
    constructor(instance: InstanceData): this(
        worldId = instance.world.id,
        worldName = instance.world.name,
        worldImageUrl = instance.world.imageUrl,
        worldDescription = instance.world.description,
        accessType = instance.accessType.displayName,
        regionIconUrl = CountryIcon.fetchIconUrl(instance.region),
        userCount = "${instance.userCount}/${instance.world.capacity}",
    )
}