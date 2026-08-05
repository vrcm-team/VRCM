package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.PlatformContext
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ProfileScaffoldAvatarClickTest {
    @Test
    fun avatarClickScrollsContentBeforeExpandingCover() = runComposeUiTest {
        mainClock.autoAdvance = false
        val outerScrollState = ScrollState(initial = 300)
        val innerScrollState = ScrollState(initial = 400)

        setContent {
            KoinApplication(
                application = {
                    modules(
                        module {
                            single<PlatformContext> { PlatformContext.INSTANCE }
                            single<ImageLoader> { ImageLoader.Builder(get<PlatformContext>()).build() }
                        },
                    )
                },
            ) {
                MaterialTheme {
                    Box(modifier = Modifier.size(width = 400.dp, height = 600.dp)) {
                        ProfileScaffold(
                            profileImageUrl = null,
                            iconUrl = null,
                            onReturn = {},
                            outerScrollState = outerScrollState,
                            innerScrollState = innerScrollState,
                        ) { _, _ ->
                            Spacer(modifier = Modifier.height(1_600.dp))
                        }
                    }
                }
            }
        }

        waitForIdle()
        val initialOuterPosition = runOnIdle { outerScrollState.value }
        val initialInnerPosition = runOnIdle { innerScrollState.value }
        assertTrue(initialOuterPosition > 0)
        assertTrue(initialInnerPosition > 0)

        onNodeWithContentDescription("UserIcon").performClick()

        var innerScrollMoved = false
        for (frame in 0 until 30) {
            mainClock.advanceTimeByFrame()
            waitForIdle()
            if (runOnIdle { innerScrollState.value < initialInnerPosition }) {
                innerScrollMoved = true
                assertEquals(initialOuterPosition, runOnIdle { outerScrollState.value })
                break
            }
        }
        assertTrue(innerScrollMoved)

        var framesAtTopBeforeCoverMoves = 0
        var coverScrollMoved = false
        var remainingFrames = 300
        while (!coverScrollMoved && remainingFrames-- > 0) {
            mainClock.advanceTimeByFrame()
            waitForIdle()
            val innerPosition = runOnIdle { innerScrollState.value }
            val outerPosition = runOnIdle { outerScrollState.value }
            if (outerPosition < initialOuterPosition) {
                assertEquals(
                    expected = 0,
                    actual = innerPosition,
                    message = "cover must not move before content reaches the top",
                )
                coverScrollMoved = true
            } else if (innerPosition == 0) {
                framesAtTopBeforeCoverMoves++
            }
        }
        assertTrue(coverScrollMoved)
        assertTrue(
            framesAtTopBeforeCoverMoves <= 2,
            "cover should start within two frames after content reaches the top, " +
                "but waited $framesAtTopBeforeCoverMoves frames",
        )

        mainClock.advanceTimeBy(10_000)
        waitForIdle()
        assertEquals(0, runOnIdle { innerScrollState.value })
        assertEquals(0, runOnIdle { outerScrollState.value })
    }
}
