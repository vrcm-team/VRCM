package io.github.vrcmteam.vrcm.network.api.attributes

/**
 * Trust rank of a user.
 * @param value The value of the trust rank.
 * @param displayName The display name of the trust rank.
 * @see <a href="https://vrchatapi.github.io/tutorials/tags/">VRChatApi Tutorials Tags</a>
 */
enum class TrustRank(val value: String, val displayName: String) {

    TrustedUser("system_trust_veteran","Trusted"),

    KnownUser("system_trust_trusted","Known"),

    User("system_trust_known","User"),

    NewUser("system_trust_basic","New"),

    Visitor("system_probable_troll","Visitor");

    companion object {
        fun fromValue(value: String): TrustRank =
            entries.firstOrNull { it.value == value } ?: Visitor

        fun fromValue(tags: List<String>): TrustRank =
            entries.firstOrNull { tags.contains(it.value) } ?: Visitor
    }
}
