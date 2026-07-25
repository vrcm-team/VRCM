package io.github.vrcmteam.vrcm.presentation.screens.gallery

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals

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
            placement = PrintCanvasPlacement.FitCenter,
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
            placement = PrintCanvasPlacement.FitCenter,
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
            placement = PrintCanvasPlacement.FitCenter,
        )

        assertRectEquals(
            expected = Rect(262.4f, 59.2f, 1_785.6f, 916f),
            actual = crop,
        )
    }

    @Test
    fun thumbnailTransformMapsAllCropEdgesToContainerEdges() {
        val bounds = Rect(0f, 0f, 2_048f, 1_152f)
        val crop = PrintDisplayGeometry.cropRect(
            bounds = bounds,
            placement = PrintCanvasPlacement.CropTopCenter,
        )
        val transform = PrintDisplayGeometry.cropToFillTransform(
            bounds = bounds,
            placement = PrintCanvasPlacement.CropTopCenter,
        )

        assertRectEquals(bounds, transform.map(crop, bounds))
    }

    @Test
    fun revealProgressUsesSameCropAndFullBoundsInBothDirections() {
        val bounds = Rect(0f, 0f, 1_080f, 1_440f)
        val crop = PrintDisplayGeometry.cropRect(bounds, PrintCanvasPlacement.FitCenter)

        assertRectEquals(
            crop,
            PrintDisplayGeometry.revealedCropRect(bounds, PrintCanvasPlacement.FitCenter, 0f),
        )
        assertRectEquals(
            bounds,
            PrintDisplayGeometry.revealedCropRect(bounds, PrintCanvasPlacement.FitCenter, 1f),
        )
    }

    private fun assertRectEquals(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left, 0.01f)
        assertEquals(expected.top, actual.top, 0.01f)
        assertEquals(expected.right, actual.right, 0.01f)
        assertEquals(expected.bottom, actual.bottom, 0.01f)
    }
}
