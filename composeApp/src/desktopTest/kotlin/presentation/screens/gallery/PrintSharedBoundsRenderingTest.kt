package io.github.vrcmteam.vrcm.presentation.screens.gallery

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.compoments.LocalSharedTransitionDialogScope
import io.github.vrcmteam.vrcm.presentation.compoments.SharedDialog
import io.github.vrcmteam.vrcm.presentation.compoments.sharedDialogContentTransform
import io.github.vrcmteam.vrcm.presentation.compoments.sharedDialogTransitionKey
import io.github.vrcmteam.vrcm.presentation.compoments.sharedDialogTransitionProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalSharedTransitionApi::class)
class PrintSharedBoundsRenderingTest {
    @Test
    fun targetCanvasLayoutBoundsGestureSurfaceToFittedCanvas() = runComposeUiTest {
        var rootBounds: Rect? = null
        var canvasBounds: Rect? = null

        setContent {
            Box(
                modifier = Modifier
                    .size(RootWidth, RootHeight)
                    .onGloballyPositioned { rootBounds = it.boundsInRoot() },
            ) {
                PrintTargetCanvasLayout(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { canvasBounds = it.boundsInRoot() },
                    )
                }
            }
        }

        waitForIdle()
        val root = requireNotNull(rootBounds)
        val actual = requireNotNull(canvasBounds)
        val expected = PrintDisplayGeometry.canvasRect(root)
        assertEquals(expected.left, actual.left, 1f)
        assertEquals(expected.top, actual.top, 1f)
        assertEquals(expected.right, actual.right, 1f)
        assertEquals(expected.bottom, actual.bottom, 1f)
        assertTrue(actual.width < root.width || actual.height < root.height)
    }

    @Test
    fun settledTargetKeepsFullPrintCanvasDetailsOutsidePhotoRegion() = runComposeUiTest {
        setContent {
            Box(
                modifier = Modifier
                    .size(RootWidth, RootHeight)
                    .testTag(RootTag)
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                PrintTargetCanvasLayout(
                    modifier = Modifier.size(PreviewWidth, PreviewHeight),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        PrintCanvasBackground(
                            progress = 1f,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            PatternedPrint(modifier = Modifier.fillMaxSize())
                        }
                        PrintTargetPhotoLayout(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clipToBounds(),
                            ) {
                                PatternedPrint(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .printSafePhotoToBounds(),
                                )
                            }
                        }
                    }
                }
            }
        }

        waitForIdle()
        val image = onNodeWithTag(RootTag).captureToImage()
        val canvasDetailPixelCount = image.canvasDetailPixelCount()
        assertTrue(
            canvasDetailPixelCount >= MinimumCanvasDetailPixels,
            "settled Print preview must retain full-canvas details outside the photo region, " +
                "but only $canvasDetailPixelCount detail pixels were visible",
        )
    }

    @Test
    fun canvasInformationLayerDoesNotDrawInsidePhotoRegion() = runComposeUiTest {
        setContent {
            Box(
                modifier = Modifier
                    .size(CanvasTestWidth, CanvasTestHeight)
                    .testTag(RootTag),
            ) {
                PrintCanvasBackground(
                    progress = 1f,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    PatternedPrint(modifier = Modifier.fillMaxSize())
                }
            }
        }

        waitForIdle()
        val image = onNodeWithTag(RootTag).captureToImage()
        val photoRect = PrintDisplayGeometry.targetPhotoRect(
            Rect(0f, 0f, image.width.toFloat(), image.height.toFloat())
        )
        assertEquals(
            0,
            image.redPixelCountInside(photoRect),
            "Print canvas information layer must leave the shared photo region empty",
        )
    }

    @Test
    fun canvasInformationLayerWaitsForPhotoBoundsPhase() = runComposeUiTest {
        setContent {
            Box(
                modifier = Modifier
                    .size(CanvasTestWidth, CanvasTestHeight)
                    .testTag(RootTag),
            ) {
                PrintCanvasBackground(
                    progress = 0.25f,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    PatternedPrint(modifier = Modifier.fillMaxSize())
                }
            }
        }

        waitForIdle()
        val image = onNodeWithTag(RootTag).captureToImage()
        assertEquals(
            0,
            image.visibleCanvasDetailPixelCount(),
            "Print canvas information must stay hidden while the shared photo is enlarging",
        )
    }

    @Test
    fun canvasInformationLayerWaitsUntilPhotoBoundsTransitionFinishes() = runComposeUiTest {
        mainClock.autoAdvance = false
        var dialogContent by mutableStateOf<SharedDialog?>(null)

        setContent {
            val dialogTransition = updateTransition(
                targetState = dialogContent,
                label = "PrintPhaseTimingTransition",
            )
            Box(
                modifier = Modifier
                    .size(CanvasTestWidth, CanvasTestHeight)
                    .testTag(RootTag),
            ) {
                PrintCanvasBackground(
                    progress = sharedDialogTransitionProgress(dialogTransition),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    PatternedPrint(modifier = Modifier.fillMaxSize())
                }
            }
        }

        waitForIdle()
        runOnIdle { dialogContent = TestPrintDialog }
        waitForIdle()
        mainClock.advanceTimeBy(400L)
        waitForIdle()

        val image = onNodeWithTag(RootTag).captureToImage()
        assertEquals(
            0,
            image.visibleCanvasDetailPixelCount(),
            "Print canvas information must remain hidden until the bounds transition finishes",
        )
    }

    @Test
    fun thumbnailCropDoesNotOvershootSettledFitPreviewDuringSharedTransition() = runComposeUiTest {
        mainClock.autoAdvance = false
        var dialogContent by mutableStateOf<SharedDialog?>(null)
        var observedRevealProgress = Float.NaN
        var observedSharedTransitionActive = false

        setContent {
            Box(
                modifier = Modifier
                    .size(RootWidth, RootHeight)
                    .testTag(RootTag)
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                SharedTransitionLayout {
                    val dialogTransition = updateTransition(
                        targetState = dialogContent,
                        label = "PatternedPrintDialogTransition",
                    )
                    val revealProgress = sharedDialogTransitionProgress(dialogTransition)
                    val sharedKey = sharedDialogTransitionKey(dialogTransition)
                    val sharedTransitionActive = isTransitionActive
                    SideEffect {
                        observedRevealProgress = revealProgress
                        observedSharedTransitionActive = sharedTransitionActive
                    }

                    CompositionLocalProvider(
                        LocalSharedTransitionDialogScope provides this@SharedTransitionLayout,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier.size(ThumbnailWidth, ThumbnailHeight),
                            ) {
                                AnimatedVisibility(
                                    visible = dialogContent == null,
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    PrintSharedPhoto(
                                        key = SharedKey,
                                        progress = if (sharedKey == SharedKey) revealProgress else 0f,
                                        animatedVisibilityScope = this@AnimatedVisibility,
                                        modifier = Modifier.fillMaxSize(),
                                    ) {
                                        PatternedPrint(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .printSafePhotoToBounds(),
                                        )
                                    }
                                }
                            }

                            dialogTransition.AnimatedContent(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                                transitionSpec = { sharedDialogContentTransform() },
                            ) { previewDialog ->
                                if (previewDialog != null) {
                                    PrintTargetCanvasLayout(
                                        modifier = Modifier.size(PreviewWidth, PreviewHeight),
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            PrintCanvasBackground(
                                                progress = revealProgress,
                                                modifier = Modifier.fillMaxSize(),
                                            ) {
                                                PatternedPrint(modifier = Modifier.fillMaxSize())
                                            }
                                            PrintTargetPhotoLayout(modifier = Modifier.fillMaxSize()) {
                                                PrintSharedPhoto(
                                                    key = SharedKey,
                                                    progress = revealProgress,
                                                    animatedVisibilityScope = this@AnimatedContent,
                                                    modifier = Modifier.fillMaxSize(),
                                                ) {
                                                    PatternedPrint(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .printSafePhotoToBounds(),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        waitForIdle()
        runOnIdle { dialogContent = TestPrintDialog }
        waitForIdle()

        val transitionProbeTimeMillis =
            PrintBoundsTransitionDurationMillis.toLong() * TransitionProbeNumerator /
                TransitionProbeDenominator
        mainClock.advanceTimeBy(transitionProbeTimeMillis)
        waitForIdle()
        val (probeRevealProgress, probeSharedTransitionActive) = runOnIdle {
            observedRevealProgress to observedSharedTransitionActive
        }
        assertTrue(
            probeRevealProgress > 0f && probeRevealProgress < 1f,
            "probe must capture an active reveal transition, but progress was " +
                "$probeRevealProgress at ${transitionProbeTimeMillis}ms",
        )
        assertTrue(
            probeSharedTransitionActive,
            "shared transition must be active at the ${transitionProbeTimeMillis}ms probe",
        )
        val transitionImage = onNodeWithTag(RootTag).captureToImage()

        mainClock.advanceTimeBy(
            PrintBoundsTransitionDurationMillis.toLong() - transitionProbeTimeMillis + 100L
        )
        waitForIdle()
        val settledContentBounds = findPatternContentBounds(
            image = onNodeWithTag(RootTag).captureToImage(),
            frame = "settled Fit preview content",
        )
        val transitionContentBounds = findPatternContentBounds(
            image = transitionImage,
            frame = "${transitionProbeTimeMillis}ms transition content",
        )
        val sizeWithinTarget =
            transitionContentBounds.width <= settledContentBounds.width + PixelTolerance &&
                transitionContentBounds.height <= settledContentBounds.height + PixelTolerance
        val edgesWithinTarget = settledContentBounds.contains(
            other = transitionContentBounds,
            tolerance = PixelTolerance,
        )

        assertTrue(
            sizeWithinTarget && edgesWithinTarget,
            "thumbnail-only crop content exceeded the settled Fit preview content: " +
                "probe=${transitionProbeTimeMillis}ms, progress=$probeRevealProgress, " +
                "sharedActive=$probeSharedTransitionActive, " +
                "target=$settledContentBounds, transition=$transitionContentBounds, " +
                "sizeWithinTarget=$sizeWithinTarget, edgesWithinTarget=$edgesWithinTarget",
        )
    }

    @Test
    fun thumbnailEdgesMatchOverlayAtTransitionStart() = runComposeUiTest {
        mainClock.autoAdvance = false
        var dialogContent by mutableStateOf<SharedDialog?>(null)

        setContent {
            Box(
                modifier = Modifier
                    .size(RootWidth, RootHeight)
                    .testTag(RootTag)
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                SharedTransitionLayout {
                    val dialogTransition = updateTransition(
                        targetState = dialogContent,
                        label = "FirstFramePrintDialogTransition",
                    )
                    val revealProgress = sharedDialogTransitionProgress(dialogTransition)
                    val sharedKey = sharedDialogTransitionKey(dialogTransition)
                    CompositionLocalProvider(
                        LocalSharedTransitionDialogScope provides this@SharedTransitionLayout,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier.size(ThumbnailWidth, ThumbnailHeight),
                            ) {
                                AnimatedVisibility(
                                    visible = dialogContent == null,
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    PrintSharedPhoto(
                                        key = SharedKey,
                                        progress = if (sharedKey == SharedKey) revealProgress else 0f,
                                        animatedVisibilityScope = this@AnimatedVisibility,
                                        modifier = Modifier.fillMaxSize(),
                                    ) {
                                        PatternedPrint(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .printSafePhotoToBounds(),
                                        )
                                    }
                                }
                            }

                            dialogTransition.AnimatedContent(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                                transitionSpec = { sharedDialogContentTransform() },
                            ) { previewDialog ->
                                if (previewDialog != null) {
                                    PrintTargetCanvasLayout(
                                        modifier = Modifier.size(PreviewWidth, PreviewHeight),
                                    ) {
                                        PrintTargetPhotoLayout(modifier = Modifier.fillMaxSize()) {
                                            PrintSharedPhoto(
                                                key = SharedKey,
                                                progress = revealProgress,
                                                animatedVisibilityScope = this@AnimatedContent,
                                                modifier = Modifier.fillMaxSize(),
                                            ) {
                                                PatternedPrint(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .printSafePhotoToBounds(),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        waitForIdle()
        val settledThumbnail = onNodeWithTag(RootTag).captureToImage()

        runOnIdle { dialogContent = TestPrintDialog }
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
    fun sharedPhotoRemainsVisibleWhenExitStarts() = runComposeUiTest {
        mainClock.autoAdvance = false
        var dialogContent by mutableStateOf<SharedDialog?>(null)

        setContent {
            Box(
                modifier = Modifier
                    .size(RootWidth, RootHeight)
                    .testTag(RootTag)
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                SharedTransitionLayout {
                    val dialogTransition = updateTransition(
                        targetState = dialogContent,
                        label = "VisiblePrintExitTransition",
                    )
                    val revealProgress = sharedDialogTransitionProgress(dialogTransition)
                    val sharedKey = sharedDialogTransitionKey(dialogTransition)
                    CompositionLocalProvider(
                        LocalSharedTransitionDialogScope provides this@SharedTransitionLayout,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopStart,
                        ) {
                            AnimatedVisibility(
                                visible = dialogContent == null,
                                modifier = Modifier.size(ThumbnailWidth, ThumbnailHeight),
                            ) {
                                PrintSharedPhoto(
                                    key = SharedKey,
                                    progress = if (sharedKey == SharedKey) revealProgress else 0f,
                                    animatedVisibilityScope = this@AnimatedVisibility,
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(PrintColor),
                                    )
                                }
                            }

                            dialogTransition.AnimatedContent(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                                transitionSpec = { sharedDialogContentTransform() },
                            ) { previewDialog ->
                                if (previewDialog != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(PrintCanvasColor),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        PrintSharedPhoto(
                                            key = SharedKey,
                                            progress = revealProgress,
                                            animatedVisibilityScope = this@AnimatedContent,
                                            modifier = Modifier.size(PreviewWidth, PreviewHeight),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(PrintColor),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        waitForIdle()
        runOnIdle { dialogContent = TestPrintDialog }
        waitForIdle()
        mainClock.advanceTimeBy(PrintTotalTransitionDurationMillis.toLong() + 100L)
        waitForIdle()

        runOnIdle { dialogContent = null }
        waitForIdle()
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        waitForIdle()

        findPatternContentBounds(
            image = onNodeWithTag(RootTag).captureToImage(),
            frame = "first exit frame",
        )
    }

    @Test
    fun revealProgressReversesWithoutSnapping() = runComposeUiTest {
        mainClock.autoAdvance = false
        var dialogContent by mutableStateOf<SharedDialog?>(null)
        var observedProgress = Float.NaN
        var observedSharedKey: String? = null

        setContent {
            val dialogTransition = updateTransition(
                targetState = dialogContent,
                label = "TestSharedDialogReversal",
            )
            val revealProgress = sharedDialogTransitionProgress(dialogTransition)
            val sharedKey = sharedDialogTransitionKey(dialogTransition)
            SideEffect {
                observedProgress = revealProgress
                observedSharedKey = sharedKey
            }
            dialogTransition.AnimatedContent(
                transitionSpec = { sharedDialogContentTransform() },
            ) { previewDialog ->
                if (previewDialog != null) {
                    Box(
                        modifier = Modifier
                            .size(PreviewWidth, PreviewHeight)
                            .background(PrintColor),
                    )
                }
            }
        }

        waitForIdle()
        runOnIdle { dialogContent = TestPrintDialog }
        waitForIdle()
        mainClock.advanceTimeBy(200L)
        waitForIdle()

        val progressBeforeReverse = runOnIdle { observedProgress }
        assertTrue(
            progressBeforeReverse > 0f && progressBeforeReverse < 1f,
            "entry should be in progress before reversal, but was $progressBeforeReverse",
        )
        assertEquals(TestPrintDialog.sharedTransitionKey, runOnIdle { observedSharedKey })

        runOnIdle { dialogContent = null }
        waitForIdle()
        val progressAtReverse = runOnIdle { observedProgress }
        assertEquals(progressBeforeReverse, progressAtReverse, ProgressTolerance)
        assertEquals(TestPrintDialog.sharedTransitionKey, runOnIdle { observedSharedKey })

        mainClock.advanceTimeBy(100L)
        waitForIdle()
        val reversingProgress = runOnIdle { observedProgress }
        assertTrue(
            reversingProgress > 0f && reversingProgress < progressBeforeReverse,
            "reversal should continue toward zero without snapping, but was $reversingProgress",
        )
        assertEquals(TestPrintDialog.sharedTransitionKey, runOnIdle { observedSharedKey })

        mainClock.advanceTimeBy(PrintRevealTransitionDurationMillis.toLong())
        waitForIdle()
        assertEquals(0f, runOnIdle { observedProgress }, ProgressTolerance)
    }

    private fun findPatternContentBounds(image: ImageBitmap, frame: String): PixelBounds {
        val pixels = image.toPixelMap()
        var left = image.width
        var top = image.height
        var right = -1
        var bottom = -1
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val color = pixels[x, y]
                if (
                    color.red > 0.05f &&
                    color.red > color.green * 1.5f &&
                    color.red > color.blue * 1.5f
                ) {
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        assertTrue(right >= left && bottom >= top, "$frame should contain red content pixels")
        return PixelBounds(left, top, right, bottom)
    }

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

    private fun ImageBitmap.canvasDetailPixelCount(minimumBlue: Float = 0.8f): Int {
        val pixels = toPixelMap()
        var count = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = pixels[x, y]
                if (
                    color.blue > minimumBlue &&
                    color.red < 0.2f &&
                    color.green < 0.2f
                ) {
                    count += 1
                }
            }
        }
        return count
    }

    private fun ImageBitmap.visibleCanvasDetailPixelCount(): Int {
        val pixels = toPixelMap()
        var count = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = pixels[x, y]
                if (
                    color.blue - color.red > 0.05f &&
                    color.blue - color.green > 0.05f
                ) {
                    count += 1
                }
            }
        }
        return count
    }

    private fun ImageBitmap.redPixelCountInside(bounds: Rect): Int {
        val pixels = toPixelMap()
        var count = 0
        val left = bounds.left.toInt().coerceIn(0, width)
        val top = bounds.top.toInt().coerceIn(0, height)
        val right = bounds.right.toInt().coerceIn(left, width)
        val bottom = bounds.bottom.toInt().coerceIn(top, height)
        for (y in top until bottom) {
            for (x in left until right) {
                val color = pixels[x, y]
                if (
                    color.red > 0.8f &&
                    color.green < 0.2f &&
                    color.blue < 0.2f
                ) {
                    count += 1
                }
            }
        }
        return count
    }

    private companion object {
        const val RootTag = "print-transition-root"
        const val SharedKey = "print-transition-image"
        const val ProgressTolerance = 0.05f
        const val PixelTolerance = 1
        const val TransitionProbeNumerator = 2L
        const val TransitionProbeDenominator = 5L
        const val MinimumCanvasDetailPixels = 1_000
        val RootWidth = 240.dp
        val RootHeight = 160.dp
        val ThumbnailWidth = 160.dp
        val ThumbnailHeight = 90.dp
        val PreviewWidth = 220.dp
        val PreviewHeight = 140.dp
        val CanvasTestWidth = 204.8.dp
        val CanvasTestHeight = 144.dp
        val PrintColor = Color.Red
        val PrintCanvasColor = Color.Blue
    }

    private data class PixelBounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        val width: Int = right - left + 1
        val height: Int = bottom - top + 1

        fun contains(other: PixelBounds, tolerance: Int): Boolean =
            other.left >= left - tolerance &&
                other.top >= top - tolerance &&
                other.right <= right + tolerance &&
                other.bottom <= bottom + tolerance
    }

    @Composable
    private fun PatternedPrint(
        modifier: Modifier,
    ) {
        Canvas(modifier = modifier) {
            val bounds = Rect(0f, 0f, size.width, size.height)
            val canvas = PrintDisplayGeometry.canvasRect(bounds)
            drawRect(
                color = PrintCanvasColor,
                topLeft = Offset(canvas.left, canvas.top),
                size = Size(canvas.width, canvas.height),
            )
            val content = PrintDisplayGeometry.cropRect(bounds)
            drawRect(
                color = PrintColor,
                topLeft = Offset(content.left, content.top),
                size = Size(content.width, content.height),
            )
        }
    }
}

private object TestPrintDialog : SharedDialog {
    override val transitionDurationMillis: Int = PrintTotalTransitionDurationMillis
    override val sharedTransitionKey: String = "print-transition-image"

    @Composable
    override fun Content(animatedVisibilityScope: AnimatedVisibilityScope) = Unit
}
