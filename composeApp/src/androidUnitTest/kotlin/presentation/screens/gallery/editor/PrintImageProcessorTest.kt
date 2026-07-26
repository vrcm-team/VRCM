package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrintImageProcessorTest : PrintImageProcessorContractTest() {
    @Test
    fun successfulRenderRecyclesOwnedContentAndOutputAfterEncoding() = runBlocking {
        val codec = TrackingPlatformImageCodec()
        try {
            val result = DefaultPrintImageProcessor(codec).render(
                source = SelectedImage("photo.png", byteArrayOf(1)),
                originalSize = ImageSize(1_920, 1_080),
                transform = CropTransform(),
            )

            assertTrue(result.isSuccess, result.exceptionOrNull()?.stackTraceToString())
            assertTrue(codec.outputWasOpenDuringEncode)
            assertTrue(codec.content.isRecycled, "renderCrop content must be recycled")
            assertTrue(requireNotNull(codec.output).isRecycled, "print canvas must be recycled")
        } finally {
            codec.recycleRemaining()
        }
    }

    @Test
    fun encodeFailureRecyclesOwnedContentAndOutput() = runBlocking {
        val codec = TrackingPlatformImageCodec(encodeFailure = IllegalStateException("encode"))
        try {
            val result = DefaultPrintImageProcessor(codec).render(
                source = SelectedImage("photo.png", byteArrayOf(1)),
                originalSize = ImageSize(1_920, 1_080),
                transform = CropTransform(),
            )

            assertIs<PrintImageFailure.EncodeFailed>(result.exceptionOrNull())
            assertTrue(codec.outputWasOpenDuringEncode)
            assertTrue(codec.content.isRecycled, "renderCrop content must be recycled")
            assertTrue(requireNotNull(codec.output).isRecycled, "print canvas must be recycled")
        } finally {
            codec.recycleRemaining()
        }
    }

    @Test
    fun encodeCancellationKeepsIdentityAndRecyclesOwnedBitmaps() = runBlocking {
        val cancellation = CancellationException("cancelled")
        val codec = TrackingPlatformImageCodec(encodeFailure = cancellation)
        try {
            val thrown = assertFailsWith<CancellationException> {
                DefaultPrintImageProcessor(codec).render(
                    source = SelectedImage("photo.png", byteArrayOf(1)),
                    originalSize = ImageSize(1_920, 1_080),
                    transform = CropTransform(),
                )
            }

            assertSame(cancellation, thrown)
            assertTrue(codec.outputWasOpenDuringEncode)
            assertTrue(codec.content.isRecycled, "renderCrop content must be recycled")
            assertTrue(requireNotNull(codec.output).isRecycled, "print canvas must be recycled")
        } finally {
            codec.recycleRemaining()
        }
    }

    private class TrackingPlatformImageCodec(
        private val encodeFailure: Throwable? = null,
    ) : PlatformImageCodec {
        val content: Bitmap = Bitmap.createBitmap(1_920, 1_080, Bitmap.Config.ARGB_8888)
        var output: Bitmap? = null
        var outputWasOpenDuringEncode: Boolean = false

        override suspend fun decode(bytes: ByteArray, request: DecodeRequest): DecodedImage =
            error("Final rendering must not decode")

        override suspend fun renderCrop(bytes: ByteArray, request: CropRenderRequest): ImageBitmap =
            content.asImageBitmap()

        override suspend fun encodePng(bitmap: ImageBitmap): ByteArray {
            output = bitmap.asAndroidBitmap()
            outputWasOpenDuringEncode = !requireNotNull(output).isRecycled
            encodeFailure?.let { throw it }
            return pngHeader(2_048, 1_440)
        }

        fun recycleRemaining() {
            if (!content.isRecycled) content.recycle()
            output?.let { if (!it.isRecycled) it.recycle() }
        }
    }

    private companion object {
        fun pngHeader(width: Int, height: Int): ByteArray = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D,
            0x49, 0x48, 0x44, 0x52,
            (width ushr 24).toByte(),
            (width ushr 16).toByte(),
            (width ushr 8).toByte(),
            width.toByte(),
            (height ushr 24).toByte(),
            (height ushr 16).toByte(),
            (height ushr 8).toByte(),
            height.toByte(),
        )
    }
}
