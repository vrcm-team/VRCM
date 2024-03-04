package io.github.vrcmteam.vrcm.network.api.attributes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class UserState(val value  : String) {
    @SerialName("offline")
    Offline("offline"),
    @SerialName("active")
    Active("active"),
    @SerialName("online")
    Online("online");

    companion object {
        fun fromValue(value: String): UserState =
            UserState.entries.firstOrNull { it.value == value }
                ?: error("Unexpected value '$value'")
    }

}
