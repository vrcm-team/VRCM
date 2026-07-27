package io.github.vrcmteam.vrcm.presentation.screens.gallery

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.compoments.sharedBoundsBy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalSharedTransitionApi::class)
class PrintSharedBoundsRenderingTest {
    @Test
    fun thumbnailEdgesMatchOverlayAtTransitionStart() = runComposeUiTest {
        assertSame(NoOverlayClip, PrintThumbnailOverlayClip)
        mainClock.autoAdvance = false
        var showPreview by mutableStateOf(false)

        setContent {
            Box(
                modifier = Modifier
                    .size(RootWidth, RootHeight)
                    .testTag(RootTag)
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                SharedTransitionLayout {
                    AnimatedContent(
                        targetState = showPreview,
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                        transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                    ) { preview ->
                        if (preview) {
                            Box(
                                modifier = Modifier
                                    .size(PreviewWidth, PreviewHeight)
                                    .sharedBoundsBy(
                                        key = SharedKey,
                                        useSuffixKey = false,
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                        animatedVisibilityScope = this@AnimatedContent,
                                        boundsTransform = PrintBoundsTransform,
                                        clipInOverlayDuringTransition = PrintCropOverlayClip(
                                            placement = PrintCanvasPlacement.FitCenter,
                                        ),
                                    )
                                    .clip(PrintCropShape(PrintCanvasPlacement.FitCenter))
                                    .background(PrintColor),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(ThumbnailWidth, ThumbnailHeight)
                                    .sharedBoundsBy(
                                        key = SharedKey,
                                        useSuffixKey = false,
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                        animatedVisibilityScope = this@AnimatedContent,
                                        boundsTransform = PrintBoundsTransform,
                                        clipInOverlayDuringTransition = PrintThumbnailOverlayClip,
                                    )
                                    .graphicsLayer {
                                        val transform = PrintDisplayGeometry.cropToFillTransform(
                                            bounds = Rect(0f, 0f, size.width, size.height),
                                            placement = PrintCanvasPlacement.CropTopCenter,
                                        )
                                        scaleX = transform.scale
                                        scaleY = transform.scale
                                        translationX = transform.translationX
                                        translationY = transform.translationY
                                    }
                                    .clip(PrintCropShape(PrintCanvasPlacement.CropTopCenter))
                                    .background(PrintColor),
                            )
                        }
                    }
                }
            }
        }

        waitForIdle()
        val settledThumbnail = onNodeWithTag(RootTag).captureToImage()

        runOnIdle { showPreview = true }
        waitForIdle()
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        waitForIdle()
        val overlayFirstFrame = onNodeWithTag(RootTag).captureToImage()

        edgeProbePoints(settledThumbnail).forEach { (x, y) ->
            assertPrintPixel(settledThumbnail, x, y, "settled thumbnail")
            assertPrintPixel(overlayFirstFrame, x, y, "overlay first frame")
        }
    }

    @Test
    fun revealProgressAndVisibleBoundsShrinkDuringExit() = runComposeUiTest {
        mainClock.autoAdvance = false
        var showPreview by mutableStateOf(false)
        var observedProgress = Float.NaN

        setContent {
            Box(
                modifier = Modifier
                    .size(RootWidth, RootHeight)
                    .testTag(RootTag)
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = showPreview,
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                    transitionSpec = {
                        // Retain the outgoing branch while the independently driven crop animation runs.
                        fadeIn(tween(durationMillis = 1)) togetherWith
                            fadeOut(
                                animationSpec = tween(durationMillis = ExitRetentionMillis),
                                targetAlpha = RetainedExitAlpha,
                            )
                    },
                ) { preview ->
                    if (preview) {
                        val revealProgress = rememberPrintCropRevealProgress(
                            animatedVisibilityScope = this@AnimatedContent,
                            enabled = true,
                        )
                        SideEffect { observedProgress = revealProgress }
                        Box(
                            modifier = Modifier
                                .size(PreviewWidth, PreviewHeight)
                                .clip(
                                    PrintCropShape(
                                        placement = PrintCanvasPlacement.FitCenter,
                                        revealProgress = revealProgress,
                                    )
                                )
                                .background(PrintColor),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(PreviewWidth, PreviewHeight)
                                .clip(
                                    PrintCropShape(
                                        placement = PrintCanvasPlacement.FitCenter,
                                        revealProgress = 0f,
                                    )
                                )
                                .background(PrintColor),
                        )
                    }
                }
            }
        }

        waitForIdle()
        runOnIdle { showPreview = true }
        waitForIdle()
        mainClock.advanceTimeBy(
            PrintBoundsTransitionDurationMillis.toLong() +
                PrintRevealTransitionDurationMillis + 100L
        )
        waitForIdle()

        assertEquals(1f, runOnIdle { observedProgress }, ProgressTolerance)
        val fullImage = onNodeWithTag(RootTag).captureToImage()

        runOnIdle { showPreview = false }
        waitForIdle()
        mainClock.advanceTimeBy(PrintRevealTransitionDurationMillis / 2L)
        waitForIdle()

        val midpointProgress = runOnIdle { observedProgress }
        assertTrue(
            midpointProgress > 0f && midpointProgress < 1f,
            "exit midpoint should be partially cropped, but progress was $midpointProgress",
        )
        val previewBounds = Rect(0f, 0f, PreviewWidth.value, PreviewHeight.value)
        val midpointBounds = PrintDisplayGeometry.revealedCropRect(
            bounds = previewBounds,
            placement = PrintCanvasPlacement.FitCenter,
            revealProgress = midpointProgress,
        )
        val sourceCrop = PrintDisplayGeometry.cropRect(
            bounds = previewBounds,
            placement = PrintCanvasPlacement.FitCenter,
        )
        assertTrue(midpointBounds.left > 0f && midpointBounds.left < sourceCrop.left)
        assertTrue(midpointBounds.top > 0f && midpointBounds.top < sourceCrop.top)
        val midpointImage = onNodeWithTag(RootTag).captureToImage()

        mainClock.advanceTimeBy(PrintRevealTransitionDurationMillis / 2L)
        waitForIdle()
        val collapsedProgress = runOnIdle { observedProgress }
        assertEquals(0f, collapsedProgress, ProgressTolerance)
        val collapsedImage = onNodeWithTag(RootTag).captureToImage()
        assertRevealBounds(
            full = findPrintBounds(fullImage, "fully revealed preview"),
            midpoint = findPrintBounds(midpointImage, "exit midpoint"),
            collapsed = findPrintBounds(collapsedImage, "collapsed exit preview"),
            midpointProgress = midpointProgress,
        )
    }

    private fun assertRevealBounds(
        full: PixelBounds,
        midpoint: PixelBounds,
        collapsed: PixelBounds,
        midpointProgress: Float,
    ) {
        assertTrue(midpoint.left > full.left && midpoint.left < collapsed.left)
        assertTrue(midpoint.top > full.top && midpoint.top < collapsed.top)
        assertTrue(midpoint.right < full.right && midpoint.right > collapsed.right)
        assertTrue(midpoint.bottom < full.bottom && midpoint.bottom > collapsed.bottom)

        assertEquals(lerp(collapsed.left, full.left, midpointProgress), midpoint.left.toFloat(), 2f)
        assertEquals(lerp(collapsed.top, full.top, midpointProgress), midpoint.top.toFloat(), 2f)
        assertEquals(lerp(collapsed.right, full.right, midpointProgress), midpoint.right.toFloat(), 2f)
        assertEquals(lerp(collapsed.bottom, full.bottom, midpointProgress), midpoint.bottom.toFloat(), 2f)
    }

    private fun findPrintBounds(image: ImageBitmap, frame: String): PixelBounds {
        val pixels = image.toPixelMap()
        var left = image.width
        var top = image.height
        var right = -1
        var bottom = -1
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val color = pixels[x, y]
                if (color.red > 0.8f && color.green < 0.2f && color.blue < 0.2f) {
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        assertTrue(right >= left && bottom >= top, "$frame should contain visible print pixels")
        return PixelBounds(left, top, right, bottom)
    }

    private fun lerp(start: Int, end: Int, progress: Float): Float =
        start + (end - start) * progress

    private fun edgeProbePoints(image: ImageBitmap): List<Pair<Int, Int>> {
        val thumbnailWidth = image.width * 2 / 3
        val thumbnailHeight = image.height * 9 / 16
        val left = (image.width - thumbnailWidth) / 2
        val top = (image.height - thumbnailHeight) / 2
        val right = left + thumbnailWidth - 1
        val bottom = top + thumbnailHeight - 1
        return listOf(
            left to image.height / 2,
            right to image.height / 2,
            image.width / 2 to top,
            image.width / 2 to bottom,
        )
    }

    private fun assertPrintPixel(image: ImageBitmap, x: Int, y: Int, frame: String) {
        val color = image.toPixelMap()[x, y]
        assertTrue(
            color.red > 0.8f && color.green < 0.2f && color.blue < 0.2f,
            "$frame should keep the print visible at ($x, $y), but was $color",
        )
    }

    private companion object {
        const val RootTag = "print-transition-root"
        const val SharedKey = "print-transition-image"
        const val ExitRetentionMillis = 600
        const val RetainedExitAlpha = 0.99f
        const val ProgressTolerance = 0.05f
        val RootWidth = 240.dp
        val RootHeight = 160.dp
        val ThumbnailWidth = 160.dp
        val ThumbnailHeight = 90.dp
        val PreviewWidth = 220.dp
        val PreviewHeight = 140.dp
        val PrintColor = Color.Red
    }

    private data class PixelBounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )
}
