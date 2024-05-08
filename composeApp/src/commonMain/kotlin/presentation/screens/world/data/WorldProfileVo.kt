package io.github.vrcmteam.vrcm.presentation.screens.world.data

import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData

data class WorldProfileVo(
    val worldId: String,
    val worldName: String,
    val worldImageUrl: String?,
    val worldDescription: String,
    ){
    constructor(world: WorldData):this(
        worldId = world.id,
        worldName = world.name,
        worldImageUrl = world.imageUrl,
        worldDescription = world.description
    )
}