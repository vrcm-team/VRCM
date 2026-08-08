package io.github.vrcmteam.vrcm.presentation.screens.meetup.editor

import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.CropTransformCalculator
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.ImageSize
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCrop
import io.github.vrcmteam.vrcm.storage.meetup.MeetupOrientation
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class MeetupCropMapperTest {
    private val calculator = CropTransformCalculator()
    private val mapper = MeetupCropMapper(calculator)
    private val source = ImageSize(3000, 4000)
    private val portrait = MeetupOrientation.Portrait.referenceViewport
    private val landscape = MeetupOrientation.Landscape.referenceViewport

    @Test
    fun derivingLandscapePreservesFocusAndCover() {
        val current = MeetupCrop(centerOffsetX = .12f, centerOffsetY = -.08f, zoom = 2.2f)

        val derived = mapper.derive(source, portrait, landscape, current)

        val landscapeCover = calculator.zoomLimits(source, landscape, 0).cover
        assertTrue(derived.zoom >= landscapeCover)
        assertNear(.12f, derived.centerOffsetX)
        assertNear(-.08f, derived.centerOffsetY)
    }

    @Test
    fun derivedCropNeverFallsBelowCoverAndClampsImpossibleOffsets() {
        val portraitCover = calculator.zoomLimits(source, portrait, 0).cover
        val current = MeetupCrop(centerOffsetX = .12f, centerOffsetY = -.08f, zoom = portraitCover)

        val derived = mapper.derive(source, portrait, landscape, current)

        val landscapeCover = calculator.zoomLimits(source, landscape, 0).cover
        assertNear(landscapeCover, derived.zoom)
        // cover 缩放下横向已无可平移空间，偏移必须被 clamp 回 0。
        assertNear(0f, derived.centerOffsetX)
        assertNear(-.08f, derived.centerOffsetY)
    }

    @Test
    fun coverCropStartsCenteredAtViewportCover() {
        val crop = mapper.coverCrop(source, landscape)

        assertNear(calculator.zoomLimits(source, landscape, 0).cover, crop.zoom)
        assertNear(0f, crop.centerOffsetX)
        assertNear(0f, crop.centerOffsetY)
    }

    private fun assertNear(expected: Float, actual: Float, tolerance: Float = .02f) {
        assertTrue(
            abs(expected - actual) <= tolerance,
            "Expected $expected within ±$tolerance but was $actual",
        )
    }
}
