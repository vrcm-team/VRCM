package io.github.kamo.vrcm.data.api

import com.google.gson.TypeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

@JsonAdapter(UserStatus.Adapter::class)
enum class UserStatus(val typeName: String) {
    Active("active"),
    JoinMe("join me"),
    AskMe("ask me"),
    Busy("busy"),
    Offline("offline");

    companion object{
        fun fromValue(value: String):UserStatus =
            entries.firstOrNull { it.typeName == value }?: error("Unexpected value '$value'")
    }

    class Adapter: TypeAdapter<UserStatus>() {
        override fun write(out: JsonWriter, value: UserStatus) {
            out.value(value.typeName)
        }

        override fun read(`in`: JsonReader): UserStatus {
            val value: String = `in`.nextString()
            return UserStatus.fromValue(value)
        }
    }

}

@JsonAdapter(UserState.Adapter::class)
enum class UserState(val typeName:String){
    Offline("offline"),
    Active("active"),
    Online("online");
    companion object{
        fun fromValue(value: String):UserState =
            UserState.entries.firstOrNull { it.typeName == value }?: error("Unexpected value '$value'")
    }

    class Adapter: TypeAdapter<UserState>() {
        override fun write(out: JsonWriter, value: UserState) {
            out.value(value.typeName)
        }

        override fun read(`in`: JsonReader): UserState {
            val value: String = `in`.nextString()
            return UserState.fromValue(value)
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

    /**
     * is Traveling
     */
    Traveling("traveling")
}