package io.github.vrcmteam.vrcm.network.api.attributes

import cafe.adriel.voyager.core.lifecycle.JavaSerializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 用户状态
 */
@Serializable
enum class UserState(val value: String) : JavaSerializable {

    /**
     * 离线
     */
    @SerialName("offline")
    Offline("offline"),

    /**
     * 活跃 (如网站在线)
     */
    @SerialName("active")
    Active("active"),

    /**
     * 在线 (游戏中)
     */
    @SerialName("online")
    Online("online");

    companion object {
        fun fromValue(value: String): UserState =
            UserState.entries.firstOrNull { it.value == value }
                ?: error("Unexpected value '$value'")
    }

}
