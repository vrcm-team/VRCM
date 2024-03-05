package io.github.vrcmteam.vrcm.network.api.instances.data

import kotlinx.serialization.Serializable

@Serializable
data class Platforms(
    val android: Int,
    val standalonewindows: Int
)