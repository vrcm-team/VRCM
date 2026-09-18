package io.github.vrcmteam.vrcm.presentation.screens.home

import io.github.vrcmteam.vrcm.presentation.designsystem.AppDrawerValue
import io.github.vrcmteam.vrcm.presentation.screens.favorites.FavoritesTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeShellStateTest {
    @Test
    fun initialClosedDrawerStateDoesNotCancelOpeningIntent() {
        val coordinator = HomeDrawerStateCoordinator()

        assertFalse(coordinator.shouldHide(AppDrawerValue.Closed))
        assertFalse(coordinator.shouldHide(AppDrawerValue.Open))
        assertTrue(coordinator.shouldHide(AppDrawerValue.Closed))
        assertFalse(coordinator.shouldHide(AppDrawerValue.Closed))
    }

    @Test
    fun rootDestinationSwitchKeepsHomeTabAndReportsReselection() {
        val state = HomeShellState()
        state.selectHomeTab(HomeTab.Activity)

        state.selectFavoritesTab(FavoritesTab.Group)
        assertFalse(state.selectDestination(HomeDestination.Favorites))
        assertEquals(HomeDestination.Favorites.ordinal, state.selectedDestinationIndex)
        assertEquals(HomeTab.Activity.ordinal, state.selectedHomeTabIndex)
        assertEquals(FavoritesTab.Group.ordinal, state.selectedFavoritesTabIndex)
        assertTrue(state.selectDestination(HomeDestination.Favorites))
    }

    @Test
    fun accountBoundaryClearsPersonalOverlays() {
        val state = HomeShellState()
        state.showDrawer()
        assertTrue(state.drawerVisible)

        state.clearOverlays()
        assertFalse(state.drawerVisible)
    }
}
