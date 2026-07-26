package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CropTransformCalculatorTest {
    private val calculator = CropTransformCalculator()

    @Test
    fun landscapeImageUsesMinimumCoverScale() {
        val geometry = calculator.geometry(
            source = ImageSize(2400, 1080),
            viewport = ImageSize(1600, 900),
            transform = CropTransform(),
        )

        assertEquals(2000f, geometry.imageWidth, 0.01f)
        assertEquals(900f, geometry.imageHeight, 0.01f)
        assertEquals(0f, geometry.translationX, 0.01f)
        assertEquals(0f, geometry.translationY, 0.01f)
    }

    @Test
    fun portraitImageAlwaysCoversSixteenByNineViewport() {
        val geometry = calculator.geometry(
            source = ImageSize(1080, 1920),
            viewport = ImageSize(1600, 900),
            transform = CropTransform(),
        )

        assertTrue(geometry.imageWidth >= 1600f)
        assertTrue(geometry.imageHeight >= 900f)
    }

    @Test
    fun panIsClampedBeforeItCanRevealEmptyPixels() {
        val source = ImageSize(2400, 1080)
        val viewport = ImageSize(1600, 900)
        val updated = calculator.transform(
            source = source,
            viewport = viewport,
            current = CropTransform(),
            panX = 10_000f,
            panY = 10_000f,
            zoomChange = 1f,
        )
        val geometry = calculator.geometry(source, viewport, updated)

        assertEquals(0.125f, updated.centerOffsetX, 0.0001f)
        assertEquals(0f, updated.centerOffsetY, 0.0001f)
        assertTrue(abs(geometry.translationX) <= (geometry.imageWidth - viewport.width) / 2f)
        assertTrue(abs(geometry.translationY) <= (geometry.imageHeight - viewport.height) / 2f)
    }

    @Test
    fun oddQuarterTurnSwapsDimensionsAndPreservesCoverage() {
        val source = ImageSize(2400, 1080)
        val viewport = ImageSize(1600, 900)
        val rotated = calculator.rotate(source, viewport, CropTransform(), turns = 1)
        val geometry = calculator.geometry(source, viewport, rotated)

        assertEquals(1, rotated.quarterTurns)
        assertEquals(90f, geometry.rotationDegrees)
        assertEquals(1600f, geometry.imageWidth, 0.01f)
        assertTrue(geometry.imageHeight >= viewport.height)
    }

    @Test
    fun zoomAndFlipValuesAreNormalized() {
        val source = ImageSize(1920, 1080)
        val viewport = ImageSize(1600, 900)
        val zoomed = calculator.transform(
            source = source,
            viewport = viewport,
            current = CropTransform(),
            panX = 0f,
            panY = 0f,
            zoomChange = 100f,
        )
        val flipped = calculator.flipVertical(calculator.flipHorizontal(zoomed))
        val geometry = calculator.geometry(source, viewport, flipped)

        assertEquals(3f, flipped.zoom)
        assertEquals(-1f, geometry.scaleXSign)
        assertEquals(-1f, geometry.scaleYSign)
    }

    @Test
    fun normalizedOffsetsRemainStableWhenViewportResizes() {
        val transform = CropTransform(centerOffsetX = 0.1f, centerOffsetY = -0.05f, zoom = 2f)

        val large = calculator.geometry(ImageSize(1920, 1080), ImageSize(1600, 900), transform)
        val small = calculator.geometry(ImageSize(1920, 1080), ImageSize(800, 450), transform)

        assertEquals(large.translationX / 2f, small.translationX, 0.01f)
        assertEquals(large.translationY / 2f, small.translationY, 0.01f)
    }
}
