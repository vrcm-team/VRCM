package io.github.vrcmteam.vrcm.network.api.attributes

enum class LocationType(val value: String) {
    /**
     * Synthetic grouping key for friends active on the website.
     */
    Web("web"),

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
    Traveling("traveling");

    companion object {
        fun fromValue(value: String): LocationType =
            when (value) {
                Offline.value -> Offline
                Private.value -> Private
                Traveling.value -> Traveling
                else -> Instance
            }
    }
}
