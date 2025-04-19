package io.github.vrcmteam.vrcm.network.api.files.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PlatformType(val value: String) {
    @SerialName("android")
    Android("android"),
    @SerialName("ios")
    Ios("ios"),
    @SerialName("standalonewindows")
    Windows("standalonewindows")
}