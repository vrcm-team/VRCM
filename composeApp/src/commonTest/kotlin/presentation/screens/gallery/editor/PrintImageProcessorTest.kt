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
import io.github.vrcmteam.vrcm.network.api.files.data.FileTagType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
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
                    maxPixels = PrintImageLimits.MAX_PREVIEW_PIXELS,
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
    fun downloadedPrintBorderIsRemovedBeforeEditing() = runBlocking {
        val source = SelectedImage("downloaded-print.png", byteArrayOf(1))
        val croppedBytes = pngHeader(width = 1_920, height = 1_080)
        val released = mutableListOf<ImageBitmap>()
        val codec = FakePlatformImageCodec(
            originalSize = ImageSize(2_048, 1_440),
            encodedBytes = croppedBytes,
            allowDecode = true,
            decodedBitmap = PreviewOwnershipTestBitmap,
        )
        val processor = DefaultPrintImageProcessor(
            codec = codec,
            releaseBitmap = released::add,
        )

        val preparedSource = processor.preparePrint(source).getOrThrow()

        assertContentEquals(croppedBytes, preparedSource.source.bytes)
        assertEquals(ImageSize(1_920, 1_080), preparedSource.prepared.originalSize)
        assertEquals(ImageSize(1_920, 1_080), codec.encodedSize)
        assertEquals(
            PixelRect(left = 64, top = 69, right = 1_984, bottom = 1_149),
            CropRenderPlanner().plan(codec.cropRequests.single()).visibleSourceBounds,
        )
        assertEquals(listOf<ImageBitmap>(PreviewOwnershipTestBitmap), released)
    }

    @Test
    fun disabledDownloadedPrintBorderCropKeepsOriginalForEditing() = runBlocking {
        val source = SelectedImage("downloaded-print.png", byteArrayOf(1))
        val released = mutableListOf<ImageBitmap>()
        val codec = FakePlatformImageCodec(
            originalSize = ImageSize(2_048, 1_440),
            allowDecode = true,
            decodedBitmap = PreviewOwnershipTestBitmap,
        )
        val processor = DefaultPrintImageProcessor(
            codec = codec,
            releaseBitmap = released::add,
        )

        val preparedSource = processor.preparePrint(
            source = source,
            cropDownloadedPrintBorder = false,
        ).getOrThrow()

        assertSame(source, preparedSource.source)
        assertSame(PreviewOwnershipTestBitmap, preparedSource.prepared.preview)
        assertEquals(ImageSize(2_048, 1_440), preparedSource.prepared.originalSize)
        assertEquals(emptyList(), codec.cropRequests)
        assertEquals(emptyList(), codec.encodeLimits)
        assertEquals(emptyList(), released)
    }

    @Test
    fun nonPrintDimensionsEnterTheEditorUnchanged() = runBlocking {
        val source = SelectedImage("regular-photo.png", byteArrayOf(1))
        val codec = FakePlatformImageCodec(
            originalSize = ImageSize(2_047, 1_440),
            allowDecode = true,
            decodedBitmap = PreviewOwnershipTestBitmap,
        )
        val processor = DefaultPrintImageProcessor(codec = codec)

        val preparedSource = processor.preparePrint(source).getOrThrow()

        assertSame(source, preparedSource.source)
        assertSame(PreviewOwnershipTestBitmap, preparedSource.prepared.preview)
        assertEquals(ImageSize(2_047, 1_440), preparedSource.prepared.originalSize)
        assertEquals(emptyList(), codec.cropRequests)
        assertEquals(emptyList(), codec.encodeLimits)
    }

    @Test
    fun downloadedPrintCropFailureReleasesDecodedPreview() = runBlocking {
        val failure = PrintImageFailure.RenderFailed(IllegalStateException("crop"))
        val released = mutableListOf<ImageBitmap>()
        val codec = FakePlatformImageCodec(
            originalSize = ImageSize(2_048, 1_440),
            allowDecode = true,
            decodedBitmap = PreviewOwnershipTestBitmap,
            renderFailure = failure,
        )
        val processor = DefaultPrintImageProcessor(
            codec = codec,
            releaseBitmap = released::add,
        )

        val result = processor.preparePrint(SelectedImage("downloaded-print.png", byteArrayOf(1)))

        assertSame(failure, result.exceptionOrNull())
        assertEquals(listOf<ImageBitmap>(PreviewOwnershipTestBitmap), released)
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
    fun avatarCoverCanvasUsesTheFull1920By1080Output() = runBlocking {
        val originalSize = ImageSize(4_000, 3_000)
        val codec = FakePlatformImageCodec(
            encodedBytes = pngHeader(width = 1_920, height = 1_080),
        )
        val processor = DefaultPrintImageProcessor(
            codec = codec,
            spec = AvatarCoverCanvasSpec,
        )

        val result = processor.render(
            source = SelectedImage("cover.jpg", byteArrayOf(1)),
            originalSize = originalSize,
            transform = CropTransform(),
        )

        assertTrue(result.isSuccess, result.exceptionOrNull()?.stackTraceToString())
        assertEquals(
            listOf(
                CropRenderRequest(
                    originalSize = originalSize,
                    transform = CropTransform(),
                    outputSize = ImageSize(1_920, 1_080),
                ),
            ),
            codec.cropRequests,
        )
        assertEquals(ImageSize(1_920, 1_080), codec.encodedSize)
    }

    @Test
    fun galleryCanvasUsesTheConfirmedFourByThreeOutput() = runBlocking {
        val originalSize = ImageSize(1_600, 900)
        val codec = FakePlatformImageCodec(
            encodedBytes = pngHeader(width = 2_000, height = 1_500),
        )
        val processor = DefaultPrintImageProcessor(
            codec = codec,
            spec = ImageEditorTarget.Gallery(FileTagType.Gallery).canvasSpec,
        )

        val result = processor.render(
            source = SelectedImage("gallery.png", byteArrayOf(1)),
            originalSize = originalSize,
            transform = CropTransform(),
            background = CanvasBackground.Transparent,
        )

        assertTrue(result.isSuccess, result.exceptionOrNull()?.stackTraceToString())
        assertEquals(
            listOf(
                CropRenderRequest(
                    originalSize = originalSize,
                    transform = CropTransform(),
                    outputSize = ImageSize(2_000, 1_500),
                ),
            ),
            codec.cropRequests,
        )
        assertEquals(ImageSize(2_000, 1_500), codec.encodedSize)
    }

    @Test
    fun squareGalleryCanvasUsesTheConfirmedSquareOutput() = runBlocking {
        val originalSize = ImageSize(1_600, 900)
        val codec = FakePlatformImageCodec(
            encodedBytes = pngHeader(width = 2_000, height = 2_000),
        )
        val processor = DefaultPrintImageProcessor(
            codec = codec,
            spec = ImageEditorTarget.Gallery(FileTagType.Icon).canvasSpec,
        )

        val result = processor.render(
            source = SelectedImage("icon.png", byteArrayOf(1)),
            originalSize = originalSize,
            transform = CropTransform(),
            background = CanvasBackground.Transparent,
        )

        assertTrue(result.isSuccess, result.exceptionOrNull()?.stackTraceToString())
        assertEquals(
            listOf(
                CropRenderRequest(
                    originalSize = originalSize,
                    transform = CropTransform(),
                    outputSize = ImageSize(2_000, 2_000),
                ),
            ),
            codec.cropRequests,
        )
        assertEquals(ImageSize(2_000, 2_000), codec.encodedSize)
    }

    @Test
    fun fullCanvasRenderCompositesInPlaceWithoutASecondOutputBitmap() = runBlocking {
        val originalSize = ImageSize(1_200, 800)
        val released = mutableListOf<ImageBitmap>()
        val codec = FakePlatformImageCodec(
            encodedBytes = pngHeader(width = originalSize.width, height = originalSize.height),
        )
        val processor = DefaultPrintImageProcessor(
            codec = codec,
            spec = PrintCanvasSpec(
                canvasWidth = originalSize.width,
                canvasHeight = originalSize.height,
                contentWidth = originalSize.width,
                contentHeight = originalSize.height,
                contentOffsetX = 0,
                contentOffsetY = 0,
            ),
            releaseBitmap = {
                released += it
                releasePlatformImageBitmap(it)
            },
        )

        val result = processor.render(
            source = SelectedImage("gallery.png", byteArrayOf(1)),
            originalSize = originalSize,
            transform = CropTransform(),
            background = CanvasBackground.White,
        )

        assertTrue(result.isSuccess, result.exceptionOrNull()?.stackTraceToString())
        assertEquals(1, released.size)
    }

    @Test
    fun galleryCategoriesUseTheConfirmedUploadCanvasesWithinTheRenderBudget() {
        val expected = mapOf(
            FileTagType.Gallery to ImageSize(2_000, 1_500),
            FileTagType.Icon to ImageSize(2_000, 2_000),
            FileTagType.Emoji to ImageSize(2_000, 2_000),
            FileTagType.Sticker to ImageSize(2_000, 2_000),
        )

        expected.forEach { (tagType, size) ->
            val spec = ImageEditorTarget.Gallery(tagType).canvasSpec
            assertEquals(size, ImageSize(spec.canvasWidth, spec.canvasHeight), tagType.value)
            assertTrue(
                spec.canvasWidth.toLong() * spec.canvasHeight <=
                        PrintImageLimits.MAX_EDITED_OUTPUT_PIXELS,
                tagType.value,
            )
        }
    }

    @Test
    fun vrcGalleryRenderingUsesTheVisibleSourceScaleOnlyOnce() = runBlocking {
        data class Case(
            val name: String,
            val originalSize: ImageSize,
            val transform: CropTransform,
            val expectedOutput: ImageSize,
        )

        val cases = listOf(
            Case(
                name = "landscape fit",
                originalSize = ImageSize(1_600, 900),
                transform = CropTransform(),
                expectedOutput = ImageSize(1_600, 1_600),
            ),
            Case(
                name = "landscape cover",
                originalSize = ImageSize(1_600, 900),
                transform = CropTransform(zoom = 16f / 9f),
                expectedOutput = ImageSize(900, 900),
            ),
            Case(
                name = "landscape high zoom",
                originalSize = ImageSize(1_600, 900),
                transform = CropTransform(zoom = 3f),
                expectedOutput = ImageSize(534, 534),
            ),
            Case(
                name = "rotated portrait fit",
                originalSize = ImageSize(900, 1_600),
                transform = CropTransform(quarterTurns = 1),
                expectedOutput = ImageSize(1_600, 1_600),
            ),
        )

        cases.forEach { case ->
            val codec = FakePlatformImageCodec(
                encodedBytes = pngHeader(case.expectedOutput.width, case.expectedOutput.height),
            )
            val processor = DefaultPrintImageProcessor(
                codec = codec,
                spec = SquareCanvasSpec,
                maxOutputBytes = PrintImageLimits.MAX_GALLERY_ENCODED_OUTPUT_BYTES,
                limitOutputToVisibleSource = true,
                shrinkOversizedOutput = true,
            )

            val result = processor.render(
                source = SelectedImage("icon.png", byteArrayOf(1)),
                originalSize = case.originalSize,
                transform = case.transform,
                background = CanvasBackground.Transparent,
            )

            assertTrue(result.isSuccess, "${case.name}: ${result.exceptionOrNull()?.stackTraceToString()}")
            assertEquals(case.expectedOutput, codec.cropRequests.single().outputSize, case.name)
        }
    }

    @Test
    fun vrcGalleryRenderingShrinksByTwentyFivePixelsWhenPngIsTooLarge() = runBlocking {
        var encodeAttempt = 0
        val codec = FakePlatformImageCodec(
            encode = { bitmap, _ ->
                encodeAttempt++
                if (encodeAttempt == 1) throw PrintImageFailure.EncodedOutputTooLarge
                pngHeader(bitmap.width, bitmap.height)
            },
        )
        val processor = DefaultPrintImageProcessor(
            codec = codec,
            spec = SquareCanvasSpec,
            maxOutputBytes = PrintImageLimits.MAX_GALLERY_ENCODED_OUTPUT_BYTES,
            limitOutputToVisibleSource = true,
            shrinkOversizedOutput = true,
        )

        val result = processor.render(
            source = SelectedImage("emoji.png", byteArrayOf(1)),
            originalSize = ImageSize(3_000, 3_000),
            transform = CropTransform(),
            background = CanvasBackground.Transparent,
        )

        assertTrue(result.isSuccess, result.exceptionOrNull()?.stackTraceToString())
        assertEquals(
            listOf(ImageSize(2_000, 2_000), ImageSize(1_975, 1_975)),
            codec.cropRequests.map(CropRenderRequest::outputSize),
        )
    }

    @Test
    fun encodedOutputOverTheBoundIsRejectedBeforeSubmission() = runBlocking {
        val maxOutputBytes = 24
        val codec = FakePlatformImageCodec(encodedBytes = ByteArray(maxOutputBytes + 1))
        val processor = DefaultPrintImageProcessor(
            codec = codec,
            maxOutputBytes = maxOutputBytes,
        )

        val result = processor.render(
            source = SelectedImage("photo.png", byteArrayOf(1)),
            originalSize = ImageSize(1_920, 1_080),
            transform = CropTransform(),
        )

        assertIs<PrintImageFailure.EncodedOutputTooLarge>(result.exceptionOrNull())
        assertEquals(listOf(maxOutputBytes), codec.encodeLimits)
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
    private val encode: ((ImageBitmap, Int) -> ByteArray)? = null,
) : PlatformImageCodec {
    val decodeRequests = mutableListOf<DecodeRequest>()
    val cropRequests = mutableListOf<CropRenderRequest>()
    val encodeLimits = mutableListOf<Int>()
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

    override suspend fun encodePng(bitmap: ImageBitmap, maxBytes: Int): ByteArray {
        encodeLimits += maxBytes
        encodedSize = ImageSize(bitmap.width, bitmap.height)
        encodedPixels = bitmap.toPixelMap()
        encodeFailure?.let { throw it }
        return encode?.invoke(bitmap, maxBytes) ?: encodedBytes
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
