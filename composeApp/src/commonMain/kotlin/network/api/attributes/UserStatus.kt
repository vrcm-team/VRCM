package io.github.vrcmteam.vrcm.network.api.attributes

import cafe.adriel.voyager.core.lifecycle.JavaSerializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class UserStatus(val value: String) : JavaSerializable {
    @SerialName("active")
    Active("active"),

    @SerialName("join me")
    JoinMe("join me"),

    @SerialName("ask me")
    AskMe("ask me"),

    @SerialName("busy")
    Busy("busy"),

    @SerialName("offline")
    Offline("offline");

    companion object {
        fun fromValue(value: String): UserStatus =
            entries.firstOrNull { it.value == value } ?: error("Unexpected value '$value'")
    }
}