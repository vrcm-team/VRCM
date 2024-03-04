package io.github.vrcmteam.vrcm.network.api.instance.data

import kotlinx.serialization.Serializable

@Serializable
data class Platforms(
    val android: Int,
    val standalonewindows: Int
)