package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import kotlin.test.Test
import kotlin.test.assertEquals

class CropTransformCalculatorTest {
    private val calculator = CropTransformCalculator()

    @Test
    fun landscapeImageFitsInsideViewportWithoutCropping() {
        val geometry = calculator.geometry(
            source = ImageSize(2400, 1080),
            viewport = ImageSize(1600, 900),
            transform = CropTransform(),
        )

        assertEquals(1600f, geometry.imageWidth, 0.01f)
        assertEquals(720f, geometry.imageHeight, 0.01f)
        assertEquals(0f, geometry.translationX, 0.01f)
        assertEquals(0f, geometry.translationY, 0.01f)
    }

    @Test
    fun portraitImageFitsInsideViewportWithoutCropping() {
        val geometry = calculator.geometry(
            source = ImageSize(1080, 1920),
            viewport = ImageSize(1600, 900),
            transform = CropTransform(),
        )

        assertEquals(506.25f, geometry.imageWidth, 0.01f)
        assertEquals(900f, geometry.imageHeight, 0.01f)
    }

    @Test
    fun panAtFitZoomCannotMoveImageAwayFromCenter() {
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

        assertEquals(0f, updated.centerOffsetX, 0.0001f)
        assertEquals(0f, updated.centerOffsetY, 0.0001f)
        assertEquals(0f, geometry.translationX, 0.01f)
        assertEquals(0f, geometry.translationY, 0.01f)
    }

    @Test
    fun oddQuarterTurnSwapsDimensionsAndPreservesFit() {
        val source = ImageSize(2400, 1080)
        val viewport = ImageSize(1600, 900)
        val rotated = calculator.rotate(source, viewport, CropTransform(), turns = 1)
        val geometry = calculator.geometry(source, viewport, rotated)

        assertEquals(1, rotated.quarterTurns)
        assertEquals(90f, geometry.rotationDegrees)
        assertEquals(405f, geometry.imageWidth, 0.01f)
        assertEquals(900f, geometry.imageHeight, 0.01f)
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
