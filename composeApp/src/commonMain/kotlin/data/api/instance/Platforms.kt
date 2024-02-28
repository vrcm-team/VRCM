package io.github.vrcmteam.vrcm.data.api.instance

import kotlinx.serialization.Serializable

@Serializable
data class Platforms(
    val android: Int,
    val standalonewindows: Int
)