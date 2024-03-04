package io.github.vrcmteam.vrcm.network.api.attributes

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
