package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.adaptive.LocalAppContentSize
import io.github.vrcmteam.vrcm.presentation.navigation.AppDetailRoute
import io.github.vrcmteam.vrcm.presentation.navigation.AppListRoute
import io.github.vrcmteam.vrcm.presentation.navigation.AppNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.AppRoute
import io.github.vrcmteam.vrcm.presentation.navigation.BackNavigationPolicy
import io.github.vrcmteam.vrcm.presentation.navigation.LocalBackNavigationPolicy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalSharedTransitionApi::class)
class SharedTransitionScreenAdaptiveTest {
    @Test
    fun fullWindowSharedTransitionSettlesInSinglePaneNavigation() = runComposeUiTest {
        mainClock.autoAdvance = false
        val navigator = AppNavigator(
            mutableStateListOf<AppRoute>(SinglePaneSourceRoute),
        )
        var sharedTransitionScope: SharedTransitionScope? = null

        setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalBackNavigationPolicy provides BackNavigationPolicy(),
                ) {
                    SharedTransitionScreen(
                        navigator = navigator,
                        content = { route ->
                            val currentSharedTransitionScope = LocalSharedTransitionScreenScope.current
                            SideEffect {
                                sharedTransitionScope = currentSharedTransitionScope
                            }
                            Box(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .align(
                                            if (route == SinglePaneSourceRoute) {
                                                Alignment.TopStart
                                            } else {
                                                Alignment.BottomEnd
                                            },
                                        )
                                        .size(if (route == SinglePaneSourceRoute) 48.dp else 160.dp)
                                        .sharedBoundsBy(
                                            key = SinglePaneSharedKey,
                                            useSuffixKey = false,
                                            boundsTransform = BoundsTransform { _, _ ->
                                                tween(SinglePaneTransitionDurationMillis)
                                            },
                                        )
                                        .testTag(
                                            if (route == SinglePaneSourceRoute) SourceTag else TargetTag,
                                        )
                                        .clickable {
                                            if (route == SinglePaneSourceRoute) {
                                                navigator.push(SinglePaneTargetRoute)
                                            }
                                        },
                                )
                            }
                        },
                    )
                }
            }
        }

        waitForIdle()
        onNodeWithTag(SourceTag).performClick()
        mainClock.advanceTimeByFrame()
        waitForIdle()

        assertTrue(sharedTransitionScope?.isTransitionActive == true)

        mainClock.advanceTimeBy(SinglePaneTransitionDurationMillis.toLong() + 1_000L)
        waitForIdle()

        assertFalse(
            sharedTransitionScope?.isTransitionActive == true,
            "a unique shared element must leave the overlay after single-pane navigation settles",
        )
        onNodeWithTag(TargetTag).fetchSemanticsNode()
    }

    @Test
    fun listAndDetailReturnToSinglePaneWhenContentWidthBecomesCompact() = runComposeUiTest {
        val navigator = AppNavigator(
            mutableStateListOf<AppRoute>(TestListRoute, TestDetailRoute),
        )
        var contentSize by mutableStateOf(DpSize(1_200.dp, 800.dp))

        setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalBackNavigationPolicy provides BackNavigationPolicy(),
                    LocalAppContentSize provides contentSize,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        SharedTransitionScreen(
                            navigator = navigator,
                            content = { route ->
                                Text(
                                    text = "${route.key}:" +
                                        (LocalSharedTransitionScreenScope.current != null),
                                )
                            },
                        )
                    }
                }
            }
        }

        waitForIdle()

        listOf(TestListRoute, TestDetailRoute).forEach { route ->
            onNodeWithText("${route.key}:false").fetchSemanticsNode()
        }

        runOnUiThread {
            contentSize = DpSize(360.dp, 800.dp)
        }
        waitForIdle()

        assertTrue(
            onAllNodesWithText("${TestListRoute.key}:false").fetchSemanticsNodes().isEmpty()
        )
        onNodeWithText("${TestDetailRoute.key}:true").fetchSemanticsNode()
    }

    @Test
    fun fullWindowEntryKeepsScreenSharedTransitionScope() = runComposeUiTest {
        val navigator = AppNavigator(
            mutableStateListOf<AppRoute>(TestFullWindowRoute),
        )

        setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalBackNavigationPolicy provides BackNavigationPolicy(),
                ) {
                    SharedTransitionScreen(
                        navigator = navigator,
                        content = { route ->
                            Text(
                                text = "${route.key}:" +
                                    (LocalSharedTransitionScreenScope.current != null),
                            )
                        },
                    )
                }
            }
        }

        waitForIdle()

        onNodeWithText("${TestFullWindowRoute.key}:true").fetchSemanticsNode()
    }

    private object TestListRoute : AppListRoute {
        override val key: String = "adaptive-test-list"

        @Composable
        override fun Content() = Unit
    }

    private object TestDetailRoute : AppDetailRoute {
        override val key: String = "adaptive-test-detail"

        @Composable
        override fun Content() = Unit
    }

    private object TestFullWindowRoute : AppRoute {
        override val key: String = "adaptive-test-full-window"

        @Composable
        override fun Content() = Unit
    }

    private object SinglePaneSourceRoute : AppRoute {
        override val key: String = "single-pane-source"

        @Composable
        override fun Content() = Unit
    }

    private object SinglePaneTargetRoute : AppRoute {
        override val key: String = "single-pane-target"

        @Composable
        override fun Content() = Unit
    }

    private companion object {
        const val SinglePaneSharedKey = "single-pane-shared-element"
        const val SinglePaneTransitionDurationMillis = 400
        const val SourceTag = "single-pane-source-element"
        const val TargetTag = "single-pane-target-element"
    }
}
