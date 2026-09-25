package io.github.vrcmteam.vrcm.presentation.screens.favorites

internal enum class FavoritesTab(val favoriteModelTabIndex: Int?) {
    Player(favoriteModelTabIndex = 0),
    World(favoriteModelTabIndex = 1),
    Avatar(favoriteModelTabIndex = 2),
    Group(favoriteModelTabIndex = null),
}
