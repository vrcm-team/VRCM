package io.github.vrcmteam.vrcm.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
enum class UserStatus(val value: String) {
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

enum class AccessType(val value: String, val displayName: String) {
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


enum class LocationType(val value: String) {
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

enum class TrustRank(val value: String, val displayName: String) {

    TrustedUser("system_trust_veteran","Trusted"),

    KnownUser("system_trust_trusted","Known"),

    User("system_trust_known","User"),

    NewUser("system_trust_basic","New"),

    Visitor("system_probable_troll","Visitor");

    companion object {
        fun fromValue(value: String): TrustRank =
            TrustRank.entries.firstOrNull { it.value == value } ?: Visitor
    }
}

enum class AuthType(val typeName: String) {
    Email("emailOtp"),

    TFA("otp"),

    TTFA("totp");

}

