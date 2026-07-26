package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DecodeSizePlannerTest {
    @Test
    fun squareImageIsBoundedByPixelCount() {
        val result = DecodeSizePlanner.plan(
            source = ImageSize(10_000, 10_000),
            request = DecodeRequest(maxDimension = 5_760, maxPixels = 16_000_000L),
        )

        assertEquals(ImageSize(4_000, 4_000), result)
    }

    @Test
    fun landscapeImageIsBoundedByLongestEdge() {
        val result = DecodeSizePlanner.plan(
            source = ImageSize(8_000, 4_000),
            request = DecodeRequest(maxDimension = 2_048, maxPixels = 16_000_000L),
        )

        assertEquals(ImageSize(2_048, 1_024), result)
    }

    @Test
    fun portraitImageIsBoundedByLongestEdge() {
        val result = DecodeSizePlanner.plan(
            source = ImageSize(4_000, 8_000),
            request = DecodeRequest(maxDimension = 2_048, maxPixels = 16_000_000L),
        )

        assertEquals(ImageSize(1_024, 2_048), result)
    }

    @Test
    fun imageSmallerThanBoundsIsNotUpscaled() {
        val result = DecodeSizePlanner.plan(
            source = ImageSize(640, 480),
            request = DecodeRequest(maxDimension = 2_048, maxPixels = 16_000_000L),
        )

        assertEquals(ImageSize(640, 480), result)
    }

    @Test
    fun singlePixelBudgetProducesSinglePixelImage() {
        val result = DecodeSizePlanner.plan(
            source = ImageSize(100, 100),
            request = DecodeRequest(maxDimension = 100, maxPixels = 1L),
        )

        assertEquals(ImageSize(1, 1), result)
    }

    @Test
    fun singlePixelSourceIsPreserved() {
        val result = DecodeSizePlanner.plan(
            source = ImageSize(1, 1),
            request = DecodeRequest(maxDimension = Int.MAX_VALUE, maxPixels = Long.MAX_VALUE),
        )

        assertEquals(ImageSize(1, 1), result)
    }

    @Test
    fun maximumIntegerDimensionsDoNotOverflow() {
        val source = ImageSize(Int.MAX_VALUE, Int.MAX_VALUE)

        val result = DecodeSizePlanner.plan(
            source = source,
            request = DecodeRequest(maxDimension = Int.MAX_VALUE, maxPixels = Long.MAX_VALUE),
        )

        assertEquals(source, result)
        assertEquals(4_611_686_014_132_420_609L, result.width.toLong() * result.height)
    }

    @Test
    fun extremeAspectRatiosKeepBothDimensionsPositive() {
        val landscape = DecodeSizePlanner.plan(
            source = ImageSize(Int.MAX_VALUE, 1),
            request = DecodeRequest(maxDimension = Int.MAX_VALUE, maxPixels = 2L),
        )
        val portrait = DecodeSizePlanner.plan(
            source = ImageSize(1, Int.MAX_VALUE),
            request = DecodeRequest(maxDimension = Int.MAX_VALUE, maxPixels = 2L),
        )

        assertEquals(ImageSize(2, 1), landscape)
        assertEquals(ImageSize(1, 2), portrait)
    }

    @Test
    fun everyPixelBudgetChoosesLargestFeasibleLongestEdge() {
        val source = ImageSize(7, 5)
        val longestEdgeLimit = 7

        for (maxPixels in 1L..source.width.toLong() * source.height) {
            val result = DecodeSizePlanner.plan(
                source = source,
                request = DecodeRequest(longestEdgeLimit, maxPixels),
            )
            val resultLongest = maxOf(result.width, result.height)

            assertTrue(result.width.toLong() * result.height <= maxPixels)
            assertTrue(resultLongest <= longestEdgeLimit)
            if (resultLongest < longestEdgeLimit) {
                val nextLongest = resultLongest + 1
                val nextHeight =
                    (nextLongest.toLong() * source.height / source.width).toInt().coerceAtLeast(1)
                assertTrue(nextLongest.toLong() * nextHeight > maxPixels)
            }
        }
    }

    @Test
    fun irregularImageSatisfiesBothBoundsAndPreservesAspectRatio() {
        val source = ImageSize(9_999, 7_777)
        val request = DecodeRequest(maxDimension = 5_760, maxPixels = 16_000_000L)

        val result = DecodeSizePlanner.plan(source, request)

        assertTrue(maxOf(result.width, result.height) <= request.maxDimension)
        assertTrue(result.width.toLong() * result.height <= request.maxPixels)
        val sourceRatio = source.width.toDouble() / source.height
        val resultRatio = result.width.toDouble() / result.height
        assertTrue(abs(sourceRatio - resultRatio) <= 0.001)
    }

    @Test
    fun nonPositiveSourceWidthIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            DecodeSizePlanner.plan(ImageSize(0, 480), DecodeRequest(2_048, 16_000_000L))
        }
    }

    @Test
    fun nonPositiveSourceHeightIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            DecodeSizePlanner.plan(ImageSize(640, -1), DecodeRequest(2_048, 16_000_000L))
        }
    }

    @Test
    fun nonPositiveMaximumDimensionIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            DecodeSizePlanner.plan(ImageSize(640, 480), DecodeRequest(0, 16_000_000L))
        }
    }

    @Test
    fun nonPositiveMaximumPixelCountIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            DecodeSizePlanner.plan(ImageSize(640, 480), DecodeRequest(2_048, 0L))
        }
    }

}
