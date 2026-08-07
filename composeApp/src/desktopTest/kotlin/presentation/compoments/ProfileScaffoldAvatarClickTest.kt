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
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ProfileScaffoldAvatarClickTest {
    @Test
    fun profileCoverRequestUsesSharedImageCachePlaceholder() = runComposeUiTest {
        val requests = CopyOnWriteArrayList<ImageRequest>()
        val platformContext = PlatformContext.INSTANCE
        val imageLoader = ImageLoader.Builder(platformContext)
            .components {
                add(
                    Interceptor { chain ->
                        requests += chain.request
                        ErrorResult(
                            image = null,
                            request = chain.request,
                            throwable = IllegalStateException("Request captured by test"),
                        )
                    },
                )
            }
            .build()

        setContent {
            KoinApplication(
                application = {
                    modules(
                        module {
                            single<PlatformContext> { platformContext }
                            single<ImageLoader> { imageLoader }
                        },
                    )
                },
            ) {
                MaterialTheme {
                    Box(modifier = Modifier.size(width = 400.dp, height = 600.dp)) {
                        ProfileScaffold(
                            profileImageUrl = TargetImageUrl,
                            iconUrl = null,
                            sharedImageCacheKey = SourceImageCacheKey,
                            onReturn = {},
                        ) { _, _ -> Unit }
                    }
                }
            }
        }

        waitUntil(timeoutMillis = 5_000) {
            requests.any { it.data == TargetImageUrl }
        }
        val coverRequest = requests.first { it.data == TargetImageUrl }
        assertEquals(
            MemoryCache.Key(SourceImageCacheKey),
            coverRequest.placeholderMemoryCacheKey,
        )
    }

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

    private companion object {
        const val TargetImageUrl = "https://example.com/avatar.png"
        const val SourceImageCacheKey = "https://example.com/avatar-preview.png"
    }
}
