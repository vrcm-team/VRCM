package io.github.vrcmteam.vrcm.presentation.screens.home.data

import androidx.compose.ui.graphics.vector.ImageVector
import io.github.vrcmteam.vrcm.network.api.attributes.AccessType
import io.github.vrcmteam.vrcm.network.api.attributes.BlueprintType
import io.github.vrcmteam.vrcm.network.api.attributes.RegionType
import io.github.vrcmteam.vrcm.network.api.instances.data.InstanceData
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons

data class HomeInstanceVo(
    val id: String = "",
    val worldId: String = "",
    val worldName: String = "",
    val worldImageUrl: String? = null,
    val worldAuthorName: String = "",
    val worldAuthorId: String = "",
    val worldDescription: String = "",
    val worldAuthorTag: List<String> = emptyList(),
    val accessType: AccessType = AccessType.Public,
    val region: RegionType = RegionType.Us,
    val userCount: String = "",
    var owner: Owner? = null,
    val name: String = "",
) {
    constructor(instance: InstanceData) : this(
        id = instance.id,
        worldId = instance.world.id,
        worldName = instance.world.name,
        worldImageUrl = instance.world.imageUrl,
        worldAuthorName = instance.world.authorName,
        worldAuthorId = instance.world.authorId,
        worldDescription = instance.world.description,
        worldAuthorTag = instance.world.tags.filter { it.startsWith("author_tag_") }
            .map { it.substringAfter("author_tag_") },
        accessType = instance.accessType,
        region = instance.region,
        userCount = "${instance.userCount}/${instance.world.capacity}",
        name = instance.name
    )

    data class Owner(
        val id: String,
        val displayName: String,
        val type: BlueprintType,
    ){
        val iconVector: ImageVector get() = if (type == BlueprintType.User) AppIcons.Person else AppIcons.Groups
    }
}