package io.github.vrcmteam.vrcm.network.api.attributes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Visibility values accepted when updating an official favorite group. */
@Serializable
enum class FavoriteGroupVisibility(val value: String) {
    @SerialName("private")
    Private("private"),

    @SerialName("friends")
    Friends("friends"),

    @SerialName("public")
    Public("public");

    companion object {
        fun fromValue(value: String): FavoriteGroupVisibility? =
            entries.firstOrNull { it.value == value }
    }
}
