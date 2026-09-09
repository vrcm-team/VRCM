package io.github.vrcmteam.vrcm.network.api.playermoderation.data

import kotlinx.serialization.Serializable

/** Server-backed override types that remain supported for player-specific moderation. */
enum class PlayerModerationType(val apiValue: String) {
    Block("block"),
    InteractOff("interactOff"),
    InteractOn("interactOn"),
    Mute("mute"),
    MuteChat("muteChat"),
    Unmute("unmute"),
    UnmuteChat("unmuteChat"),
    ;

    companion object {
        fun fromApiValue(value: String): PlayerModerationType? =
            entries.firstOrNull { it.apiValue == value }
    }
}

@Serializable
data class PlayerModerationData(
    val id: String,
    val targetUserId: String,
    val type: String,
)

@Serializable
internal data class PlayerModerationRequest(
    val moderated: String,
    val type: String,
)
