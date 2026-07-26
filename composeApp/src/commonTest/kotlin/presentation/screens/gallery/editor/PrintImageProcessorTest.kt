package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.toPixelMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

abstract class PrintImageProcessorContractTest {
    @Test
    fun oversizedEncodedFileFailsBeforeDecode() = runBlocking {
        val codec = FakePlatformImageCodec()
        val processor = DefaultPrintImageProcessor(
            codec = codec,
            maxFileBytes = 4,
        )

        val result = processor.prepare(SelectedImage("large.jpg", ByteArray(5)))

        assertIs<PrintImageFailure.FileTooLarge>(result.exceptionOrNull())
        assertEquals(emptyList(), codec.decodeRequests)
    }

    @Test
    fun previewDecodeCancellationPropagatesUnchanged() = runBlocking {
        val cancellation = CancellationException("cancelled")
        val codec = FakePlatformImageCodec(decodeFailure = cancellation)
        val processor = DefaultPrintImageProcessor(codec = codec)

        val thrown = assertFailsWith<CancellationException> {
            processor.prepare(SelectedImage("photo.png", byteArrayOf(1)))
        }

        assertSame(cancellation, thrown)
    }

    @Test
    fun prepareUsesBoundedRequestAndRejectsOversizedPixelDimensions() = runBlocking {
        val codec = FakePlatformImageCodec(
            originalSize = ImageSize(10_001, 10_001),
            allowDecode = true,
        )
        val processor = DefaultPrintImageProcessor(codec = codec)

        val result = processor.prepare(SelectedImage("huge.png", byteArrayOf(1)))

        assertIs<PrintImageFailure.ImageDimensionsTooLarge>(result.exceptionOrNull())
        assertEquals(
            listOf(
                DecodeRequest(
                    maxDimension = 2_048,
                    maxPixels = PrintImageLimits.MAX_INTERMEDIATE_DECODE_PIXELS,
                ),
            ),
            codec.decodeRequests,
        )
    }

    @Test
    fun prepareValidationFailureReleasesDecodedPreview() = runBlocking {
        val released = mutableListOf<ImageBitmap>()
        val codec = FakePlatformImageCodec(
            originalSize = ImageSize(10_001, 10_001),
            allowDecode = true,
            decodedBitmap = PreviewOwnershipTestBitmap,
        )
        val processor = DefaultPrintImageProcessor(
            codec = codec,
            releaseBitmap = released::add,
        )

        val result = processor.prepare(SelectedImage("huge.png", byteArrayOf(1)))

        assertIs<PrintImageFailure.ImageDimensionsTooLarge>(result.exceptionOrNull())
        assertEquals(listOf<ImageBitmap>(PreviewOwnershipTestBitmap), released)
    }

    @Test
    fun successfulPrepareTransfersPreviewWithoutReleasingIt() = runBlocking {
        val released = mutableListOf<ImageBitmap>()
        val codec = FakePlatformImageCodec(
            allowDecode = true,
            decodedBitmap = PreviewOwnershipTestBitmap,
        )
        val processor = DefaultPrintImageProcessor(
            codec = codec,
            releaseBitmap = released::add,
        )

        val prepared = processor.prepare(SelectedImage("photo.png", byteArrayOf(1))).getOrThrow()

        assertSame(PreviewOwnershipTestBitmap, prepared.preview)
        assertEquals(emptyList(), released)
    }

    @Test
    fun renderUsesPlatformCropWithoutPreviewDecodeAndBuildsPrintCanvas() = runBlocking {
        val originalSize = ImageSize(6_000, 4_000)
        val transform = CropTransform(
            centerOffsetX = 0.2f,
            centerOffsetY = -0.15f,
            zoom = 1.4f,
            quarterTurns = 1,
            flipHorizontal = true,
        )
        val codec = FakePlatformImageCodec(
            renderColor = Color(red = 1f, green = 0f, blue = 0f, alpha = 0.5f),
        )
        val processor = DefaultPrintImageProcessor(codec = codec)

        val result = processor.render(
            source = SelectedImage("photo.jpg", byteArrayOf(1)),
            originalSize = originalSize,
            transform = transform,
        )

        assertTrue(result.isSuccess, result.exceptionOrNull()?.stackTraceToString())
        assertEquals(emptyList(), codec.decodeRequests)
        assertEquals(
            listOf(
                CropRenderRequest(
                    originalSize = originalSize,
                    transform = transform,
                    outputSize = ImageSize(1_920, 1_080),
                ),
            ),
            codec.cropRequests,
        )
        assertEquals(ImageSize(2_048, 1_440), codec.encodedSize)
        val pixels = requireNotNull(codec.encodedPixels)
        listOf(
            pixels[64, 69],
            pixels[1_983, 69],
            pixels[64, 1_148],
            pixels[1_983, 1_148],
        ).forEach { pixel ->
            assertEquals(1f, pixel.alpha, 0.01f)
            assertEquals(1f, pixel.red, 0.01f)
            assertEquals(0.5f, pixel.green, 0.02f)
            assertEquals(0.5f, pixel.blue, 0.02f)
        }

        for (y in 0 until 1_440) {
            for (x in 0 until 2_048) {
                val isContent = x in 64 until 1_984 && y in 69 until 1_149
                if (!isContent) {
                    assertEquals(Color.White, pixels[x, y], "Expected white background at ($x, $y)")
                }
            }
        }
    }

    @Test
    fun invalidPngSignatureIsRejected() = runBlocking {
        val codec = FakePlatformImageCodec(encodedBytes = byteArrayOf(1, 2, 3))
        val processor = DefaultPrintImageProcessor(codec = codec)

        val result = processor.render(
            source = SelectedImage("photo.png", byteArrayOf(1)),
            originalSize = ImageSize(1_920, 1_080),
            transform = CropTransform(),
        )

        assertIs<PrintImageFailure.EncodeFailed>(result.exceptionOrNull())
        Unit
    }

    @Test
    fun wrongPngDimensionsAreRejected() = runBlocking {
        val codec = FakePlatformImageCodec(encodedBytes = pngHeader(width = 100, height = 100))
        val processor = DefaultPrintImageProcessor(codec = codec)

        val result = processor.render(
            source = SelectedImage("photo.png", byteArrayOf(1)),
            originalSize = ImageSize(1_920, 1_080),
            transform = CropTransform(),
        )

        assertIs<PrintImageFailure.EncodeFailed>(result.exceptionOrNull())
        Unit
    }

    @Test
    fun cropCancellationPropagatesUnchanged() = runBlocking {
        val cancellation = CancellationException("cancelled")
        val codec = FakePlatformImageCodec(renderFailure = cancellation)
        val processor = DefaultPrintImageProcessor(codec = codec)

        val thrown = assertFailsWith<CancellationException> {
            processor.render(
                source = SelectedImage("photo.png", byteArrayOf(1)),
                originalSize = ImageSize(1_920, 1_080),
                transform = CropTransform(),
            )
        }

        assertTrue(thrown === cancellation)
    }

    @Test
    fun fatalCropFailureIsNotCapturedInResult() = runBlocking {
        val fatal = AssertionError("fatal")
        val codec = FakePlatformImageCodec(renderFailure = fatal)
        val processor = DefaultPrintImageProcessor(codec = codec)

        val thrown = assertFailsWith<AssertionError> {
            processor.render(
                source = SelectedImage("photo.png", byteArrayOf(1)),
                originalSize = ImageSize(1_920, 1_080),
                transform = CropTransform(),
            )
        }

        assertTrue(thrown === fatal)
    }

    @Test
    fun canvasFailureReleasesAllocatedOutputAndContent() = runBlocking {
        val released = mutableListOf<ImageBitmap>()
        val codec = FakePlatformImageCodec(renderedBitmap = InvalidContentBitmap)
        val processor = DefaultPrintImageProcessor(
            codec = codec,
            releaseBitmap = released::add,
        )

        val result = processor.render(
            source = SelectedImage("photo.png", byteArrayOf(1)),
            originalSize = ImageSize(1_920, 1_080),
            transform = CropTransform(),
        )

        assertIs<PrintImageFailure.RenderFailed>(result.exceptionOrNull())
        assertEquals(2, released.size)
        assertEquals(ImageSize(2_048, 1_440), ImageSize(released.first().width, released.first().height))
        assertSame(InvalidContentBitmap, released.last())
    }

    @Test
    fun releaseFailureDoesNotReplaceEncodeCancellation() = runBlocking {
        val cancellation = CancellationException("cancelled")
        val releaseFailure = IllegalStateException("release")
        val codec = FakePlatformImageCodec(encodeFailure = cancellation)
        val processor = DefaultPrintImageProcessor(
            codec = codec,
            releaseBitmap = { bitmap ->
                releasePlatformImageBitmap(bitmap)
                throw releaseFailure
            },
        )

        val thrown = assertFailsWith<CancellationException> {
            processor.render(
                source = SelectedImage("photo.png", byteArrayOf(1)),
                originalSize = ImageSize(1_920, 1_080),
                transform = CropTransform(),
            )
        }

        assertSame(cancellation, thrown)
        assertTrue(releaseFailure in thrown.suppressedExceptions)
    }

    @Test
    fun releaseFailureDoesNotReplaceFatalEncodeError() = runBlocking {
        val fatal = AssertionError("fatal")
        val releaseFailure = IllegalStateException("release")
        val codec = FakePlatformImageCodec(encodeFailure = fatal)
        val processor = DefaultPrintImageProcessor(
            codec = codec,
            releaseBitmap = { bitmap ->
                releasePlatformImageBitmap(bitmap)
                throw releaseFailure
            },
        )

        val thrown = assertFailsWith<AssertionError> {
            processor.render(
                source = SelectedImage("photo.png", byteArrayOf(1)),
                originalSize = ImageSize(1_920, 1_080),
                transform = CropTransform(),
            )
        }

        assertSame(fatal, thrown)
        assertTrue(releaseFailure in thrown.suppressedExceptions)
    }
}

private class FakePlatformImageCodec(
    private val originalSize: ImageSize = ImageSize(1_920, 1_080),
    private val encodedBytes: ByteArray = pngHeader(2_048, 1_440),
    private val allowDecode: Boolean = false,
    private val decodedBitmap: ImageBitmap? = null,
    private val decodeFailure: Throwable? = null,
    private val renderFailure: Throwable? = null,
    private val renderedBitmap: ImageBitmap? = null,
    private val renderColor: Color = Color.Red,
    private val encodeFailure: Throwable? = null,
) : PlatformImageCodec {
    val decodeRequests = mutableListOf<DecodeRequest>()
    val cropRequests = mutableListOf<CropRenderRequest>()
    var encodedSize: ImageSize? = null
    var encodedPixels: PixelMap? = null

    override suspend fun decode(bytes: ByteArray, request: DecodeRequest): DecodedImage {
        decodeFailure?.let { throw it }
        check(allowDecode) { "Final rendering must not use preview decode" }
        decodeRequests += request
        return DecodedImage(decodedBitmap ?: solidBitmap(16, 9, Color.Red), originalSize)
    }

    override suspend fun renderCrop(bytes: ByteArray, request: CropRenderRequest): ImageBitmap {
        renderFailure?.let { throw it }
        cropRequests += request
        return renderedBitmap
            ?: solidBitmap(request.outputSize.width, request.outputSize.height, renderColor)
    }

    override suspend fun encodePng(bitmap: ImageBitmap): ByteArray {
        encodedSize = ImageSize(bitmap.width, bitmap.height)
        encodedPixels = bitmap.toPixelMap()
        encodeFailure?.let { throw it }
        return encodedBytes
    }
}

private data object PreviewOwnershipTestBitmap : ImageBitmap {
    override val width: Int = 16
    override val height: Int = 9
    override val colorSpace: ColorSpace = ColorSpaces.Srgb
    override val hasAlpha: Boolean = true
    override val config: ImageBitmapConfig = ImageBitmapConfig.Argb8888

    override fun readPixels(
        buffer: IntArray,
        startX: Int,
        startY: Int,
        width: Int,
        height: Int,
        bufferOffset: Int,
        stride: Int,
    ) = Unit

    override fun prepareToDraw() = Unit
}

private data object InvalidContentBitmap : ImageBitmap {
    override val width: Int = 1_920
    override val height: Int = 1_080
    override val colorSpace: ColorSpace = ColorSpaces.Srgb
    override val hasAlpha: Boolean = true
    override val config: ImageBitmapConfig = ImageBitmapConfig.Argb8888

    override fun readPixels(
        buffer: IntArray,
        startX: Int,
        startY: Int,
        width: Int,
        height: Int,
        bufferOffset: Int,
        stride: Int,
    ) = throw IllegalStateException("Canvas cannot draw this test bitmap")

    override fun prepareToDraw() = Unit
}

private fun solidBitmap(width: Int, height: Int, color: Color): ImageBitmap =
    ImageBitmap(width, height, hasAlpha = true).also { bitmap ->
        Canvas(bitmap).drawRect(
            rect = Rect(0f, 0f, width.toFloat(), height.toFloat()),
            paint = Paint().apply { this.color = color },
        )
    }

private fun pngHeader(width: Int, height: Int): ByteArray = byteArrayOf(
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
