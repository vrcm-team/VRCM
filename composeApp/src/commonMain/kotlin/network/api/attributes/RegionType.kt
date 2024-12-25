package io.github.vrcmteam.vrcm.network.api.attributes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class RegionType(val iconUrl: String) {
    @SerialName("us")
    Us("https://assets.vrchat.com/www/images/Region_US.png"),

    @SerialName("use")
    Use("https://assets.vrchat.com/www/images/Region_US.png"),

    @SerialName("eu")
    Eu("https://assets.vrchat.com/www/images/Region_EU.png"),

    @SerialName("jp")
    Jp("https://assets.vrchat.com/www/images/Region_JP.png"),

    /**
     * Sometimes it returns unknown when there is a public location.
     */
    @SerialName("unknown")
    Unknown("https://assets.vrchat.com/www/images/Region_US.png")
}