package io.github.vrcmteam.vrcm.network.api.instances.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Platforms(
    val android: Int = 0,
    val ios: Int = 0,
    @SerialName("standalonewindows")
    val standaloneWindows: Int = 0,
)