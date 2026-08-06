package io.github.vrcmteam.vrcm.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppNavigatorTest {
    @Test
    fun rootRouteCannotBePopped() {
        val root = TestRoute("root")
        val navigator = AppNavigator(mutableStateListOf(root))

        assertFalse(navigator.canPop)
        assertFalse(navigator.pop())
        assertEquals(listOf(root), navigator.items)
    }

    @Test
    fun replaceAllRemovesPrivateNavigationHistory() {
        val navigator = AppNavigator(mutableStateListOf(TestRoute("home")))
        navigator.push(TestRoute("profile"))
        navigator.push(TestRoute("gallery"))

        val auth = TestRoute("auth")
        navigator.replaceAll(auth)

        assertEquals(listOf(auth), navigator.items)
        assertFalse(navigator.canPop)
    }

    @Test
    fun replacingTopRoutePreservesEarlierHistory() {
        val root = TestRoute("root")
        val navigator = AppNavigator(mutableStateListOf(root))
        navigator.push(TestRoute("loading"))

        val home = TestRoute("home")
        navigator.replace(home)

        assertEquals(listOf(root, home), navigator.items)
        assertTrue(navigator.canPop)
    }

    @Test
    fun pushingCurrentRouteDoesNotDuplicateNavigationEntry() {
        val root = TestRoute("root")
        val profile = TestRoute("profile")
        val navigator = AppNavigator(mutableStateListOf(root))

        navigator.push(profile)
        navigator.push(profile)

        assertEquals(listOf(root, profile), navigator.items)
    }
}

private data class TestRoute(val name: String) : AppRoute {
    @Composable
    override fun Content() = Unit
}
