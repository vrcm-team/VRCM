package io.github.vrcmteam.vrcm.presentation.animations

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.compoments.sharedBoundsBy
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalSharedTransitionApi::class)
class ScreenTransitionRenderingTest {
    @Test
    fun outgoingPageIsHiddenWhileSharedCoverTransitionRuns() = runComposeUiTest {
        mainClock.autoAdvance = false
        var showDetail by mutableStateOf(false)
        var outgoingPageIsComposed = false

        setContent {
            Box(
                modifier = Modifier
                    .size(RootWidth, RootHeight)
                    .testTag(RootTag)
                    .background(Color.Black),
            ) {
                SharedTransitionLayout {
                    AnimatedContent(
                        targetState = showDetail,
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopStart,
                        transitionSpec = {
                            EnterTransition.None togetherWith horizontalScreenExitTransition(
                                animationSpec = tween(ScreenExitDurationMillis),
                            )
                        },
                    ) { detail ->
                        if (!detail) {
                            DisposableEffect(Unit) {
                                outgoingPageIsComposed = true
                                onDispose { outgoingPageIsComposed = false }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(if (detail) Color.Transparent else Color.Red),
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(if (detail) Alignment.BottomEnd else Alignment.TopStart)
                                    .size(CoverSize)
                                    .sharedBoundsBy(
                                        key = SharedCoverKey,
                                        useSuffixKey = false,
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                        animatedVisibilityScope = this@AnimatedContent,
                                        boundsTransform = BoundsTransform { _, _ ->
                                            tween(SharedCoverDurationMillis)
                                        },
                                        clipInOverlayDuringTransition = NoClip,
                                    )
                                    .background(Color.Blue),
                            )
                        }
                    }
                }
            }
        }

        waitForIdle()
        runOnIdle { showDetail = true }
        waitForIdle()
        mainClock.advanceTimeBy(ProbeTimeMillis)
        waitForIdle()

        val frame = onNodeWithTag(RootTag).captureToImage()
        assertTrue(
            runOnIdle { outgoingPageIsComposed },
            "longer shared transition should still retain the outgoing page branch",
        )
        val background = frame.toPixelMap()[frame.width / 2, frame.height * 3 / 4]
        assertTrue(
            background.red < 0.05f && background.green < 0.05f && background.blue < 0.05f,
            "outgoing page should be hidden after its exit, but probe pixel was $background",
        )
    }

    private companion object {
        const val RootTag = "screen-transition-root"
        const val SharedCoverKey = "screen-transition-cover"
        const val ScreenExitDurationMillis = 100
        const val SharedCoverDurationMillis = 500
        const val ProbeTimeMillis = 150L
        val RootWidth = 240.dp
        val RootHeight = 160.dp
        val CoverSize = 40.dp
    }
}
