package io.github.vrcmteam.vrcm.presentation.screens.gallery

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrintDisplayGeometryTest {
    @Test
    fun safeCropComesFromPrintCanvasContentRegion() {
        assertRectEquals(
            expected = Rect(72f, 74f, 1_976f, 1_145f),
            actual = PrintDisplayGeometry.spec.sourceCropRect,
        )
    }

    @Test
    fun fitCropUsesCenteredCanvasInPortraitBounds() {
        val crop = PrintDisplayGeometry.cropRect(
            bounds = Rect(0f, 0f, 1_080f, 1_440f),
        )

        assertRectEquals(
            expected = Rect(37.96875f, 379.33594f, 1_042.03125f, 944.1211f),
            actual = crop,
        )
    }

    @Test
    fun fitCropUsesCenteredCanvasInWideBounds() {
        val crop = PrintDisplayGeometry.cropRect(
            bounds = Rect(0f, 0f, 2_560f, 1_080f),
        )

        assertRectEquals(
            expected = Rect(566f, 55.5f, 1_994f, 858.75f),
            actual = crop,
        )
    }

    @Test
    fun fitCropUsesCenteredCanvasInSixteenByNineBounds() {
        val crop = PrintDisplayGeometry.cropRect(
            bounds = Rect(0f, 0f, 2_048f, 1_152f),
        )

        assertRectEquals(
            expected = Rect(262.4f, 59.2f, 1_785.6f, 916f),
            actual = crop,
        )
    }

    @Test
    fun targetPhotoRectIsSixteenByNineSafeRegionInsideFittedCanvas() {
        val bounds = Rect(0f, 0f, 240f, 160f)
        val canvas = PrintDisplayGeometry.canvasRect(bounds)

        val photo = PrintDisplayGeometry.targetPhotoRect(bounds)

        assertEquals(16f / 9f, photo.width / photo.height, 0.01f)
        assertTrue(photo.left >= canvas.left)
        assertTrue(photo.top >= canvas.top)
        assertTrue(photo.right <= canvas.right)
        assertTrue(photo.bottom <= canvas.bottom)
    }

    @Test
    fun safePhotoTransformMapsFittedRawPngCropToSharedBounds() {
        val bounds = Rect(0f, 0f, 160f, 90f)
        val fittedSafePhoto = PrintDisplayGeometry.targetPhotoRect(bounds)

        val transform = PrintDisplayGeometry.safePhotoToBoundsTransform(bounds)

        assertRectEquals(bounds, transform.map(fittedSafePhoto, bounds))
    }

    private fun assertRectEquals(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left, 0.01f)
        assertEquals(expected.top, actual.top, 0.01f)
        assertEquals(expected.right, actual.right, 0.01f)
        assertEquals(expected.bottom, actual.bottom, 0.01f)
    }
}
