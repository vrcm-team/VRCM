package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CropRenderPlannerTest {
    private val calculator = CropTransformCalculator()
    private val planner = CropRenderPlanner(calculator)

    @Test
    fun resetLandscapeCropMapsCenterAndVisibleSourceBounds() {
        val plan = planner.plan(
            CropRenderRequest(
                originalSize = ImageSize(2_400, 1_080),
                transform = CropTransform(),
                outputSize = ImageSize(1_920, 1_080),
            ),
        )

        assertPointEquals(AffinePoint(960.0, 540.0), plan.sourceToOutput.map(1_200.0, 540.0))
        assertEquals(PixelRect(240, 0, 2_160, 1_080), plan.visibleSourceBounds)
        assertEquals(ImageSize(1_920, 1_080), plan.outputSize)
    }

    @Test
    fun highZoomSelectsOnlySmallSourceRegion() {
        val source = ImageSize(6_000, 12_000)
        val bounds = planner.plan(
            CropRenderRequest(
                originalSize = source,
                transform = CropTransform(zoom = 3f, centerOffsetY = 0.25f),
                outputSize = ImageSize(1_920, 1_080),
            ),
        ).visibleSourceBounds

        assertTrue(bounds.width <= 2_100)
        assertTrue(bounds.height <= 1_200)
        assertInsideSource(bounds, source)
    }

    @Test
    fun negativeQuarterTurnsAreEquivalentToPositiveNormalizedTurns() {
        val source = ImageSize(4_032, 3_024)
        val output = ImageSize(1_920, 1_080)
        val negative = planner.plan(
            CropRenderRequest(
                originalSize = source,
                transform = CropTransform(
                    centerOffsetX = 0.1f,
                    centerOffsetY = -0.2f,
                    zoom = 1.6f,
                    quarterTurns = -1,
                    flipHorizontal = true,
                ),
                outputSize = output,
            ),
        )
        val positive = planner.plan(
            CropRenderRequest(
                originalSize = source,
                transform = CropTransform(
                    centerOffsetX = 0.1f,
                    centerOffsetY = -0.2f,
                    zoom = 1.6f,
                    quarterTurns = 3,
                    flipHorizontal = true,
                ),
                outputSize = output,
            ),
        )

        assertEquals(positive, negative)
    }

    @Test
    fun rotatedPannedAndFlippedCropMapsCenterUsingCalculatorTranslation() {
        val source = ImageSize(4_000, 3_000)
        val output = ImageSize(1_920, 1_080)
        val transform = CropTransform(
            centerOffsetX = 0.2f,
            centerOffsetY = -0.15f,
            zoom = 1.8f,
            quarterTurns = 1,
            flipHorizontal = true,
            flipVertical = true,
        )
        val geometry = calculator.geometry(source, output, transform)

        val plan = planner.plan(CropRenderRequest(source, transform, output))
        val expectedBounds = visibleSourceBoundsFromReturnedAffine(
            transform = plan.sourceToOutput,
            source = source,
            output = output,
        )

        assertPointEquals(
            expected = AffinePoint(
                x = output.width / 2.0 + geometry.translationX.toDouble(),
                y = output.height / 2.0 + geometry.translationY.toDouble(),
            ),
            actual = plan.sourceToOutput.map(source.width / 2.0, source.height / 2.0),
        )
        assertEquals(PixelRect(1_390, 333, 2_329, 2_000), expectedBounds)
        assertEquals(expectedBounds, plan.visibleSourceBounds)
        assertInsideSource(plan.visibleSourceBounds, source)
    }

    @Test
    fun affineMappingMatchesPreviewTransformOrderForCornersAndCenter() {
        val source = ImageSize(3_840, 2_160)
        val output = ImageSize(1_920, 1_080)
        val transform = CropTransform(
            centerOffsetX = -0.18f,
            centerOffsetY = 0.12f,
            zoom = 1.7f,
            quarterTurns = 3,
            flipHorizontal = true,
            flipVertical = false,
        )
        val geometry = calculator.geometry(source, output, transform)
        val plan = planner.plan(CropRenderRequest(source, transform, output))
        val points = listOf(
            AffinePoint(0.0, 0.0),
            AffinePoint(source.width.toDouble(), 0.0),
            AffinePoint(0.0, source.height.toDouble()),
            AffinePoint(source.width.toDouble(), source.height.toDouble()),
            AffinePoint(source.width / 2.0, source.height / 2.0),
        )

        points.forEach { point ->
            assertPointEquals(
                expected = mapLikePreview(point, source, output, transform, geometry),
                actual = plan.sourceToOutput.map(point.x, point.y),
            )
        }
    }

    @Test
    fun extremeLegalPanIsClampedToNonEmptyInBoundsRegion() {
        val source = ImageSize(6_000, 4_000)
        val output = ImageSize(1_920, 1_080)
        val transform = CropTransform(
            centerOffsetX = Float.MAX_VALUE,
            centerOffsetY = -Float.MAX_VALUE,
            zoom = 2.5f,
            quarterTurns = 1,
            flipVertical = true,
        )
        val geometry = calculator.geometry(source, output, transform)
        val maxTranslationX = (geometry.imageWidth - output.width) / 2f
        val maxTranslationY = (geometry.imageHeight - output.height) / 2f

        val plan = planner.plan(CropRenderRequest(source, transform, output))
        val expectedBounds = visibleSourceBoundsFromReturnedAffine(
            transform = plan.sourceToOutput,
            source = source,
            output = output,
        )

        assertEquals(maxTranslationX, geometry.translationX, 0.01f)
        assertEquals(-maxTranslationY, geometry.translationY, 0.01f)
        assertPointEquals(
            expected = AffinePoint(
                x = output.width / 2.0 + geometry.translationX.toDouble(),
                y = output.height / 2.0 + geometry.translationY.toDouble(),
            ),
            actual = plan.sourceToOutput.map(source.width / 2.0, source.height / 2.0),
        )
        assertEquals(PixelRect(5_100, 0, 6_000, 1_600), expectedBounds)
        assertEquals(expectedBounds, plan.visibleSourceBounds)
        assertInsideSource(plan.visibleSourceBounds, source)
        assertTrue(plan.visibleSourceBounds.width > 0)
        assertTrue(plan.visibleSourceBounds.height > 0)
    }

    @Test
    fun centeredQuarterTurnKeepsExclusiveRightConservativeForDoubleInverse() {
        val source = ImageSize(6_000, 4_000)
        val output = ImageSize(1_920, 1_080)
        val plan = planner.plan(
            CropRenderRequest(
                originalSize = source,
                transform = CropTransform(quarterTurns = 1),
                outputSize = output,
            ),
        )

        assertEquals(PixelRect(1_875, 0, 4_125, 4_000), plan.visibleSourceBounds)
        val exclusiveRightCorner = inverseUsingDouble(
            plan.sourceToOutput,
            AffinePoint(0.0, output.height.toDouble()),
        )
        assertEquals(4_125.0, exclusiveRightCorner.x, 0.000_001)
        assertTrue(exclusiveRightCorner.x <= plan.visibleSourceBounds.right)
        assertOutputSamplesCoveredByBounds(plan, source, output)
    }

    @Test
    fun maximumLegalPixelCountKeepsDoubleInverseCornersCovered() {
        val source = ImageSize(100_000_000, 1)
        val output = ImageSize(1_920, 1_080)
        val geometry = calculator.geometry(source, output, CropTransform())
        val plan = planner.plan(CropRenderRequest(source, CropTransform(), output))

        assertPointEquals(
            expected = AffinePoint(
                x = output.width / 2.0 + geometry.translationX.toDouble(),
                y = output.height / 2.0 + geometry.translationY.toDouble(),
            ),
            actual = plan.sourceToOutput.map(source.width / 2.0, source.height / 2.0),
            tolerance = 0.000_001,
        )
        assertEquals(
            visibleSourceBoundsFromReturnedAffine(plan.sourceToOutput, source, output),
            plan.visibleSourceBounds,
        )
        assertOutputSamplesCoveredByBounds(plan, source, output)
        assertInsideSource(plan.visibleSourceBounds, source)
    }

    @Test
    fun everyQuarterTurnAndFlipCombinationConservativelyCoversOutputSamples() {
        val source = ImageSize(3_973, 2_819)
        val output = ImageSize(1_920, 1_080)

        for (turns in 0..3) {
            for (flipHorizontal in listOf(false, true)) {
                for (flipVertical in listOf(false, true)) {
                    val plan = planner.plan(
                        CropRenderRequest(
                            originalSize = source,
                            transform = CropTransform(
                                centerOffsetX = 0.07f,
                                centerOffsetY = -0.04f,
                                zoom = 1.7f,
                                quarterTurns = turns,
                                flipHorizontal = flipHorizontal,
                                flipVertical = flipVertical,
                            ),
                            outputSize = output,
                        ),
                    )

                    assertEquals(
                        visibleSourceBoundsFromReturnedAffine(plan.sourceToOutput, source, output),
                        plan.visibleSourceBounds,
                        "turns=$turns, flipHorizontal=$flipHorizontal, flipVertical=$flipVertical",
                    )
                    assertOutputSamplesCoveredByBounds(plan, source, output)
                    assertInsideSource(plan.visibleSourceBounds, source)
                }
            }
        }
    }

    @Test
    fun continuousAffineCenterParityAllowsHalfPixelRoundingAndFloatEpsilon() {
        val source = ImageSize(3_973, 2_819)
        val output = ImageSize(1_920, 1_080)
        val transform = CropTransform(
            centerOffsetX = 0.07f,
            centerOffsetY = -0.04f,
            zoom = 1.37f,
            quarterTurns = 1,
            flipHorizontal = true,
        )
        val geometry = calculator.geometry(source, output, transform)
        val plan = planner.plan(CropRenderRequest(source, transform, output))
        val roundedPreviewCenter = mapCenterLikeRoundedPreview(output, transform, geometry)
        val affineCenter = plan.sourceToOutput.map(source.width / 2.0, source.height / 2.0)

        assertEquals(roundedPreviewCenter.x, affineCenter.x, PREVIEW_ROUNDING_TOLERANCE)
        assertEquals(roundedPreviewCenter.y, affineCenter.y, PREVIEW_ROUNDING_TOLERANCE)
    }

    private fun mapLikePreview(
        point: AffinePoint,
        source: ImageSize,
        output: ImageSize,
        transform: CropTransform,
        geometry: RenderGeometry,
    ): AffinePoint {
        val turns = ((transform.quarterTurns % 4) + 4) % 4
        val unrotatedWidth = if (turns % 2 == 0) {
            geometry.imageWidth.toDouble()
        } else {
            geometry.imageHeight.toDouble()
        }
        val unrotatedHeight = if (turns % 2 == 0) {
            geometry.imageHeight.toDouble()
        } else {
            geometry.imageWidth.toDouble()
        }
        val flippedX = (point.x / source.width - 0.5) *
                unrotatedWidth * geometry.scaleXSign.toDouble()
        val flippedY = (point.y / source.height - 0.5) *
                unrotatedHeight * geometry.scaleYSign.toDouble()
        val rotated = when (turns) {
            0 -> AffinePoint(flippedX, flippedY)
            1 -> AffinePoint(-flippedY, flippedX)
            2 -> AffinePoint(-flippedX, -flippedY)
            else -> AffinePoint(flippedY, -flippedX)
        }
        return AffinePoint(
            x = output.width / 2.0 + geometry.translationX.toDouble() + rotated.x,
            y = output.height / 2.0 + geometry.translationY.toDouble() + rotated.y,
        )
    }

    private fun visibleSourceBoundsFromReturnedAffine(
        transform: AffineTransform,
        source: ImageSize,
        output: ImageSize,
    ): PixelRect {
        val sourceCorners = outputCorners(output).map { point -> inverseUsingDouble(transform, point) }
        return PixelRect(
            left = floor(sourceCorners.minOf { it.x }).toInt().coerceIn(0, source.width),
            top = floor(sourceCorners.minOf { it.y }).toInt().coerceIn(0, source.height),
            right = ceil(sourceCorners.maxOf { it.x }).toInt().coerceIn(0, source.width),
            bottom = ceil(sourceCorners.maxOf { it.y }).toInt().coerceIn(0, source.height),
        )
    }

    private fun assertOutputSamplesCoveredByBounds(
        plan: CropRenderPlan,
        source: ImageSize,
        output: ImageSize,
    ) {
        val bounds = plan.visibleSourceBounds
        val outputSamples = outputCorners(output) +
                AffinePoint(output.width / 2.0, output.height / 2.0)
        outputSamples
            .map { point -> inverseUsingDouble(plan.sourceToOutput, point) }
            .forEach { point ->
                val clampedPoint = AffinePoint(
                    x = point.x.coerceIn(0.0, source.width.toDouble()),
                    y = point.y.coerceIn(0.0, source.height.toDouble()),
                )
                assertTrue(
                    actual = clampedPoint.x >= bounds.left && clampedPoint.x <= bounds.right,
                    message = "Inverse x=${clampedPoint.x} is outside $bounds",
                )
                assertTrue(
                    actual = clampedPoint.y >= bounds.top && clampedPoint.y <= bounds.bottom,
                    message = "Inverse y=${clampedPoint.y} is outside $bounds",
                )
            }
    }

    private fun inverseUsingDouble(
        transform: AffineTransform,
        point: AffinePoint,
    ): AffinePoint {
        val scaleX = transform.scaleX
        val skewX = transform.skewX
        val translateX = transform.translateX
        val skewY = transform.skewY
        val scaleY = transform.scaleY
        val translateY = transform.translateY
        val determinant = scaleX * scaleY - skewX * skewY
        val translatedX = point.x - translateX
        val translatedY = point.y - translateY
        return AffinePoint(
            x = (scaleY * translatedX - skewX * translatedY) / determinant,
            y = (-skewY * translatedX + scaleX * translatedY) / determinant,
        )
    }

    private fun outputCorners(output: ImageSize): List<AffinePoint> = listOf(
        AffinePoint(0.0, 0.0),
        AffinePoint(output.width.toDouble(), 0.0),
        AffinePoint(0.0, output.height.toDouble()),
        AffinePoint(output.width.toDouble(), output.height.toDouble()),
    )

    private fun mapCenterLikeRoundedPreview(
        output: ImageSize,
        transform: CropTransform,
        geometry: RenderGeometry,
    ): AffinePoint {
        val turns = ((transform.quarterTurns % 4) + 4) % 4
        val unrotatedWidth = if (turns % 2 == 0) geometry.imageWidth else geometry.imageHeight
        val unrotatedHeight = if (turns % 2 == 0) geometry.imageHeight else geometry.imageWidth
        val localX = (-unrotatedWidth / 2f).roundToInt() + unrotatedWidth.roundToInt() / 2f
        val localY = (-unrotatedHeight / 2f).roundToInt() + unrotatedHeight.roundToInt() / 2f
        val flippedX = localX.toDouble() * geometry.scaleXSign.toDouble()
        val flippedY = localY.toDouble() * geometry.scaleYSign.toDouble()
        val rotated = when (turns) {
            0 -> AffinePoint(flippedX, flippedY)
            1 -> AffinePoint(-flippedY, flippedX)
            2 -> AffinePoint(-flippedX, -flippedY)
            else -> AffinePoint(flippedY, -flippedX)
        }
        return AffinePoint(
            x = output.width / 2.0 + geometry.translationX.toDouble() + rotated.x,
            y = output.height / 2.0 + geometry.translationY.toDouble() + rotated.y,
        )
    }

    private fun assertInsideSource(bounds: PixelRect, source: ImageSize) {
        assertTrue(bounds.left >= 0)
        assertTrue(bounds.top >= 0)
        assertTrue(bounds.right <= source.width)
        assertTrue(bounds.bottom <= source.height)
        assertTrue(bounds.right > bounds.left)
        assertTrue(bounds.bottom > bounds.top)
    }

    private fun assertPointEquals(
        expected: AffinePoint,
        actual: AffinePoint,
        tolerance: Double = 0.01,
    ) {
        assertEquals(expected.x, actual.x, tolerance)
        assertEquals(expected.y, actual.y, tolerance)
    }

    private companion object {
        const val PREVIEW_ROUNDING_TOLERANCE = 0.501
    }
}
