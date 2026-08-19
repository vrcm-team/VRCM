package io.github.vrcmteam.vrcm.presentation.screens.meetup.editor

import androidx.compose.ui.graphics.ImageBitmap
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.CropRenderRequest
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.CropTransformCalculator
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.DecodeRequest
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.DecodedImage
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.ImageSize
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PlatformImageCodec
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PrintImageFailure
import io.github.vrcmteam.vrcm.storage.meetup.MeetupOrientation
import io.github.vrcmteam.vrcm.storage.meetup.MeetupPhotoSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MeetupPhotoPreparerTest {
    private val calculator = CropTransformCalculator()

    @Test
    fun successfulPrepareStartsBothOrientationsAtCover() = runTest {
        val originalSize = ImageSize(3000, 4000)
        val preparer = MeetupPhotoPreparer(FakeCodec(decoded = originalSize), calculator)

        val prepared = preparer.prepare(
            source = MeetupPhotoSource.LocalAlbum,
            sourceId = null,
            sourceUrl = null,
            fileName = "photo.png",
            bytes = byteArrayOf(1, 2, 3),
        ).getOrThrow()

        assertEquals(3000, prepared.candidate.width)
        assertEquals(4000, prepared.candidate.height)
        val portrait = MeetupOrientation.Portrait.referenceViewport
        val landscape = MeetupOrientation.Landscape.referenceViewport
        assertEquals(
            calculator.zoomLimits(originalSize, portrait, 0).cover,
            prepared.candidate.portraitCrop.zoom,
        )
        assertEquals(
            calculator.zoomLimits(originalSize, landscape, 0).cover,
            prepared.candidate.landscapeCrop.zoom,
        )
    }

    @Test
    fun oversizedDimensionsFailWithoutThrowing() = runTest {
        val preparer = MeetupPhotoPreparer(
            FakeCodec(decoded = ImageSize(20_000, 6_000)),
            calculator,
        )

        val result = preparer.prepare(
            source = MeetupPhotoSource.LocalAlbum,
            sourceId = null,
            sourceUrl = null,
            fileName = "huge.png",
            bytes = byteArrayOf(1),
        )

        assertIs<PrintImageFailure.ImageDimensionsTooLarge>(result.exceptionOrNull())
    }

    @Test
    fun decodeFailureIsWrappedAsResultFailure() = runTest {
        val preparer = MeetupPhotoPreparer(
            FakeCodec(failure = IllegalStateException("broken image")),
            calculator,
        )

        val result = preparer.prepare(
            source = MeetupPhotoSource.VrchatGallery,
            sourceId = "file_1",
            sourceUrl = "https://example.test/image",
            fileName = "broken.png",
            bytes = byteArrayOf(1),
        )

        assertTrue(result.isFailure)
        assertIs<PrintImageFailure.DecodeFailed>(result.exceptionOrNull())
    }

    private class FakeCodec(
        private val decoded: ImageSize? = null,
        private val failure: Exception? = null,
    ) : PlatformImageCodec {
        override suspend fun decode(bytes: ByteArray, request: DecodeRequest): DecodedImage {
            failure?.let { throw it }
            return DecodedImage(
                bitmap = stubImageBitmap(4, 4),
                originalSize = checkNotNull(decoded),
            )
        }

        override suspend fun renderCrop(bytes: ByteArray, request: CropRenderRequest): ImageBitmap =
            error("renderCrop must not be used by the meetup preparer")

        override suspend fun encodePng(bitmap: ImageBitmap, maxBytes: Int): ByteArray =
            error("encodePng must not be used by the meetup preparer")
    }
}
