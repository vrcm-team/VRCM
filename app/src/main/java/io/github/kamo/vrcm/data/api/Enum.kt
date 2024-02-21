package io.github.kamo.vrcm.data.api

import com.google.gson.JsonDeserializer
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer

enum class UserStatus(val typeName: String) {
    Online("active"),
    JoinMe("join me"),
    AskMe("ask me"),
    Busy("busy"),
    Offline("offline");

    companion object {
        val Deserializer = JsonDeserializer { json, _, _ ->
            entries.first { it.typeName == json.asString }
        }
        val Serializer = JsonSerializer<UserStatus> { src, _, _ ->
            JsonPrimitive(src.typeName)
        }
    }
}

enum class CountryIcon(val iconUrl: String) {
    Us("https://assets.vrchat.com/www/images/Region_US.png"),
    Use("https://assets.vrchat.com/www/images/Region_US.png"),
    Eu("https://assets.vrchat.com/www/images/Region_EU.png"),
    Jp("https://assets.vrchat.com/www/images/Region_JP.png");

    companion object {
        fun fetchIconUrl(region: String): String = (CountryIcon.entries.firstOrNull {
            it.name.lowercase() == region
        } ?: Us).iconUrl

    }
}

enum class AccessType(val typeName: String, val displayName: String) {
    Public("public", "Public"),

    GroupPublic("public", "Group Public"),
    GroupPlus("plus", "Group+"),
    GroupMembers("members", "Group"),
    Group("group", "Group"),

    FriendPlus("hidden", "Friends+"),
    Friend("friends", "Friends"),

    InvitePlus("canRequestInvite", "Invite"),
    Invite("!canRequestInvite", "Invite"),
    Private("private", "Private"),

}


enum class LocationType(val typeName: String) {
    /**
     * Friends Active on the Website
     */
    Offline("offline"),

    /**
     * Friends in Private Worlds
     */
    Private("private"),

    /**
     * by Location Instance
     */
    Instance("wrld_"),

    Traveling("traveling")
}