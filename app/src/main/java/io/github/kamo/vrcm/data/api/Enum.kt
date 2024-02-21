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
        fun fetchIconUrl(location: String): String? {
            val region = runCatching {
                location.split("region(")[1].removeSuffix(")")
            }.getOrNull()
            return when (region) {
                "us" -> Us.iconUrl
                "use" -> Use.iconUrl
                "eu" -> Eu.iconUrl
                "jp" -> Jp.iconUrl
                else -> Us.iconUrl
            }
        }
    }
}

enum class InstantsType(val typeName: String, val displayName: String) {
    Public("public","public"),

    GroupPublic("group","Group Public"),
    GroupPlus("group","Group+"),
    Group("group","Group"),

    FriendPlus("hidden","Friends+"),
    Friend("friends","Friends"),

    Private("private","Private");

    companion object {
        val Deserializer = JsonDeserializer { json, _, _ ->
            InstantsType.entries.firstOrNull { it.typeName == json.asString }?:json.asString
        }
        val Serializer = JsonSerializer<UserStatus> { src, _, _ ->
            JsonPrimitive(src.typeName)
        }
    }
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
    Instance("wrld_")
}