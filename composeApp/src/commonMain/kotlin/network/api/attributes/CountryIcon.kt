package io.github.vrcmteam.vrcm.network.api.attributes

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