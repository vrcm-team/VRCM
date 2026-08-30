package io.github.vrcmteam.vrcm.presentation.screens.home

import androidx.compose.material3.DrawerValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeShellStateTest {
    @Test
    fun initialClosedDrawerStateDoesNotCancelOpeningIntent() {
        val coordinator = HomeDrawerStateCoordinator()

        assertFalse(coordinator.shouldHide(DrawerValue.Closed))
        assertFalse(coordinator.shouldHide(DrawerValue.Open))
        assertTrue(coordinator.shouldHide(DrawerValue.Closed))
        assertFalse(coordinator.shouldHide(DrawerValue.Closed))
    }

    @Test
    fun rootDestinationSwitchKeepsHomeTabAndReportsReselection() {
        val state = HomeShellState()
        state.selectHomeTab(HomeTab.Activity)

        assertFalse(state.selectDestination(HomeDestination.Search))
        assertEquals(HomeDestination.Search.ordinal, state.selectedDestinationIndex)
        assertEquals(HomeTab.Activity.ordinal, state.selectedHomeTabIndex)
        assertTrue(state.selectDestination(HomeDestination.Search))
    }

    @Test
    fun accountBoundaryClearsPersonalOverlays() {
        val state = HomeShellState()
        state.showDrawer()
        assertTrue(state.drawerVisible)

        state.showSettings()
        assertFalse(state.drawerVisible)
        assertTrue(state.settingsVisible)

        state.clearOverlays()
        assertFalse(state.drawerVisible)
        assertFalse(state.settingsVisible)
    }
}
