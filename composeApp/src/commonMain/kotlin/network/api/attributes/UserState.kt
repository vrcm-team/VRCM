package io.github.vrcmteam.vrcm.network.api.attributes

import cafe.adriel.voyager.core.lifecycle.JavaSerializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class UserState(val value: String) : JavaSerializable {
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
