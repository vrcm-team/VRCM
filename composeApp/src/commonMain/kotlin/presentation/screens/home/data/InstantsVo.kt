package io.github.vrcmteam.vrcm.presentation.screens.home.data

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import io.github.vrcmteam.vrcm.network.api.attributes.RegionType
import io.github.vrcmteam.vrcm.network.api.instances.data.InstanceData

data class InstantsVo(
    val worldId: String = "",
    val worldName: String = "",
    val worldImageUrl: String? = null,
    val worldAuthorName: String = "",
    val worldDescription: String = "",
    val worldAuthorTag: List<String> = emptyList(),
    val accessType: String = "",
    val region: RegionType = RegionType.Us,
    val userCount: String = "",
    val ownerName: MutableState<String?> = mutableStateOf(null),
){
    constructor(instance: InstanceData): this(
        worldId = instance.world.id,
        worldName = instance.world.name,
        worldImageUrl = instance.world.imageUrl,
        worldAuthorName = instance.world.authorName,
        worldDescription = instance.world.description,
        worldAuthorTag = instance.world.tags.filter { it.startsWith("author_tag_")  }.map { it.substringAfter("author_tag_") },
        accessType = instance.accessType.displayName,
        region = instance.region,
        userCount = "${instance.userCount}/${instance.world.capacity}",
    )
}