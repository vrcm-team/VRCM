package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [PixelBitmapRegionDecoderShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidPlatformImageCodecTest {
    private val codec = AndroidPlatformImageCodec()

    @Test
    fun orientationReaderPreservesCancellationIdentity() {
        val cancellation = CancellationException("cancelled")

        val thrown = assertFailsWith<CancellationException> {
            readAndroidImageOrientation { throw cancellation }
        }

        assertSame(cancellation, thrown)
    }

    @Test
    fun orientationReaderPreservesFatalErrorIdentity() {
        val fatal = AssertionError("fatal")

        val thrown = assertFailsWith<AssertionError> {
            readAndroidImageOrientation { throw fatal }
        }

        assertSame(fatal, thrown)
    }

    @Test
    fun orientationReaderFallsBackOnlyForRecoverableExceptions() {
        assertEquals(1, readAndroidImageOrientation { throw IllegalArgumentException("exif") })
        assertEquals(1, readAndroidImageOrientation { 0 })
        assertEquals(1, readAndroidImageOrientation { 9 })
        assertEquals(6, readAndroidImageOrientation { 6 })
    }

    @Test
    fun cancellationsAreNotMappedToRecoverableImageFailures() {
        AndroidFailureOperation.entries.forEach { operation ->
            val cancellation = CancellationException("cancelled-$operation")

            val thrown = assertFailsWith<CancellationException> {
                mapAndroidImageFailure(operation) { throw cancellation }
            }

            assertSame(cancellation, thrown)
        }
    }

    @Test
    fun fatalErrorsAreNotMappedToRecoverableImageFailures() {
        AndroidFailureOperation.entries.forEach { operation ->
            val fatal = AssertionError("fatal-$operation")

            val thrown = assertFailsWith<AssertionError> {
                mapAndroidImageFailure(operation) { throw fatal }
            }

            assertSame(fatal, thrown)
        }
    }

    @Test
    fun cancelledImageBitmapHandoffRecyclesOwnedPixels() {
        val cancellation = CancellationException("cancelled")
        val androidBitmap = Bitmap.createBitmap(12, 7, Bitmap.Config.ARGB_8888)

        val thrown = assertFailsWith<CancellationException> {
            handoffAndroidImageBitmap(androidBitmap.asImageBitmap()) { throw cancellation }
        }

        assertSame(cancellation, thrown)
        assertTrue(androidBitmap.isRecycled)
    }

    @Test
    fun promptCancellationAfterWorkerCompletionRecyclesOwnedPixels() {
        val workerDispatcher = QueuedTestDispatcher()
        val cancellation = CancellationException("cancelled")
        val androidBitmap = Bitmap.createBitmap(12, 7, Bitmap.Config.ARGB_8888)

        val result = cancelAfterWorkerCompletion(workerDispatcher, cancellation) {
            withOwnedPlatformImageResult(
                dispatcher = workerDispatcher,
                ownedBitmap = { it },
            ) {
                androidBitmap.asImageBitmap()
            }
        }

        assertIs<CancellationException>(result.exceptionOrNull())
        assertTrue(androidBitmap.isRecycled)
    }

    @Test
    fun pngRoundTripPreservesDimensions() = runBlocking {
        val png = createEncodedBitmap(12, 7, Bitmap.CompressFormat.PNG)
        assertDecodableBounds(png, "image/png")
        val decoded = codec.decode(
            png,
            DecodeRequest(2_048, PrintImageLimits.MAX_INTERMEDIATE_DECODE_PIXELS),
        )

        assertEquals(ImageSize(12, 7), decoded.originalSize)
        assertEquals(12, decoded.bitmap.width)
        assertEquals(7, decoded.bitmap.height)

        val encoded = codec.encodePng(decoded.bitmap)
        assertTrue(encoded.hasPngSignature())
    }

    @Test
    fun exifOrientationsNormalizePreviewDimensionsAndPixels() = runBlocking {
        val rawSize = EXIF_ORIENTATION_FIXTURE_SIZE
        val rawJpeg = createFourColorJpeg(rawSize)

        EXIF_ORIENTATION_CONTRACTS.forEach { contract ->
            val jpeg = rawJpeg.withExifOrientation(contract.orientation)
            assertDecodableBounds(jpeg, "image/jpeg")
            val decoded = codec.decode(
                jpeg,
                DecodeRequest(maxDimension = 2_048, maxPixels = 16_000_000L),
            )
            val bitmap = decoded.bitmap.asAndroidBitmap()
            val expectedSize = contract.orientedSize(rawSize)

            try {
                assertEquals(expectedSize, decoded.originalSize, "orientation=${contract.orientation}")
                assertEquals(expectedSize.width, bitmap.width, "orientation=${contract.orientation}")
                assertEquals(expectedSize.height, bitmap.height, "orientation=${contract.orientation}")
                contract.expectedCorners.forEach { expected ->
                    val (x, y) = expected.corner.samplePoint(expectedSize)
                    val actual = bitmap.getPixel(x, y)
                    assertExifColorNear(
                        expected = expected.color,
                        actualRed = Color.red(actual),
                        actualGreen = Color.green(actual),
                        actualBlue = Color.blue(actual),
                        context = "orientation=${contract.orientation}, corner=${expected.corner}",
                    )
                }
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test
    fun malformedBytesAreRejected() = runBlocking {
        assertFailsWith<PrintImageFailure.UnsupportedFormat> {
            codec.decode(
                byteArrayOf(1, 2, 3),
                DecodeRequest(maxDimension = 2_048, maxPixels = 16_000_000L),
            )
        }
        Unit
    }

    @Test
    fun previewIsBoundedByDefaultPixelBudget() = runBlocking {
        val png = createEncodedBitmap(4_100, 4_000, Bitmap.CompressFormat.PNG)

        val decoded = codec.decode(
            png,
            DecodeRequest(maxDimension = 5_760, maxPixels = 16_000_000L),
        )
        val bitmap = decoded.bitmap.asAndroidBitmap()

        assertEquals(ImageSize(4_100, 4_000), decoded.originalSize)
        assertTrue(maxOf(bitmap.width, bitmap.height) <= 5_760)
        assertTrue(bitmap.width.toLong() * bitmap.height <= 16_000_000L)
    }

    @Test
    fun previewSampleKeepsClosestPowerOfTwoAbovePlannedSize() {
        val source = ImageSize(6_000, 1_000)
        val request = DecodeRequest(maxDimension = 2_048, maxPixels = 16_000_000L)
        val target = DecodeSizePlanner.plan(source, request)

        val sample = calculatePreviewSampleSize(
            rawSize = source,
            orientation = 1,
            target = target,
            request = request,
        )
        val sampledSize = ImageSize(
            width = (source.width + sample - 1) / sample,
            height = (source.height + sample - 1) / sample,
        )

        assertEquals(ImageSize(2_048, 341), target)
        assertEquals(2, sample)
        assertEquals(ImageSize(3_000, 500), sampledSize)
        assertTrue(sampledSize.width >= target.width)
        assertTrue(sampledSize.height >= target.height)
        assertTrue(sampledSize.width.toLong() * sampledSize.height <= request.maxPixels)
    }

    @Test
    fun previewSampleDoesNotUndershootTargetForSmallResize() {
        val source = ImageSize(2_050, 2_050)
        val request = DecodeRequest(maxDimension = 2_048, maxPixels = 16_000_000L)
        val target = DecodeSizePlanner.plan(source, request)

        val sample = calculatePreviewSampleSize(source, orientation = 1, target, request)

        assertEquals(ImageSize(2_048, 2_048), target)
        assertEquals(1, sample)
    }

    @Test
    fun previewScalesLargerIntermediateToPlannedTarget() = runBlocking {
        val source = ImageSize(6_000, 1_000)
        val request = DecodeRequest(maxDimension = 2_048, maxPixels = 16_000_000L)
        val target = DecodeSizePlanner.plan(source, request)
        val png = createEncodedBitmap(source.width, source.height, Bitmap.CompressFormat.PNG)

        val decoded = codec.decode(png, request).bitmap.asAndroidBitmap()

        assertEquals(target.width, decoded.width)
        assertEquals(target.height, decoded.height)
        decoded.recycle()
    }

    @Test
    fun previewHonorsRequestPixelBudget() = runBlocking {
        val png = createEncodedBitmap(1_600, 1_200, Bitmap.CompressFormat.PNG)
        val request = DecodeRequest(maxDimension = 2_048, maxPixels = 400_000L)

        val decoded = codec.decode(png, request)
        val bitmap = decoded.bitmap.asAndroidBitmap()

        assertEquals(ImageSize(1_600, 1_200), decoded.originalSize)
        assertTrue(bitmap.width.toLong() * bitmap.height <= request.maxPixels)
        assertTrue(maxOf(bitmap.width, bitmap.height) <= request.maxDimension)
    }

    @Test
    fun cropRenderingUsesResetZoomAndPanSourceRegions() = runBlocking {
        val sourceSize = ImageSize(400, 300)
        val outputSize = ImageSize(200, 100)
        val png = createGradientPng(sourceSize)
        val transforms = listOf(
            CropTransform(),
            CropTransform(zoom = 2f),
            CropTransform(centerOffsetX = 0.35f, centerOffsetY = -0.25f, zoom = 2f),
        )

        transforms.forEach { transform ->
            val request = CropRenderRequest(sourceSize, transform, outputSize)
            val rendered = codec.renderCrop(png, request).asAndroidBitmap()

            assertEquals(outputSize.width, rendered.width)
            assertEquals(outputSize.height, rendered.height)
            listOf(
                12 to 12,
                outputSize.width / 2 to outputSize.height / 2,
                outputSize.width - 13 to outputSize.height - 13,
            ).forEach { (x, y) ->
                val expected = expectedGradientAtOutput(request, x, y)
                assertColorNear(expected, rendered.getPixel(x, y), tolerance = 5)
            }
            rendered.recycle()
        }
    }

    @Test
    fun exifSixCropRendersPixelsInNormalizedOrientation() = runBlocking {
        val rawSize = ImageSize(400, 300)
        val orientedSize = ImageSize(300, 400)
        val jpeg = createFourColorJpeg(rawSize).withExifOrientation(6)
        val outputSize = ImageSize(150, 200)

        val rendered = codec.renderCrop(
            jpeg,
            CropRenderRequest(orientedSize, CropTransform(), outputSize),
        ).asAndroidBitmap()

        assertEquals(outputSize.width, rendered.width)
        assertEquals(outputSize.height, rendered.height)
        assertColorNear(Color.BLUE, rendered.getPixel(20, 20), tolerance = 35)
        assertColorNear(Color.RED, rendered.getPixel(129, 20), tolerance = 35)
        assertColorNear(Color.YELLOW, rendered.getPixel(20, 179), tolerance = 35)
        assertColorNear(Color.GREEN, rendered.getPixel(129, 179), tolerance = 35)
        rendered.recycle()
    }

    @Test
    fun sampledOddJpegCropKeepsEdgesOpaqueAndAligned() = runBlocking {
        val sourceSize = ImageSize(803, 603)
        val outputSize = ImageSize(201, 151)
        val jpeg = createGradientJpeg(sourceSize)
        val request = CropRenderRequest(
            originalSize = sourceSize,
            transform = CropTransform(zoom = 1.5f),
            outputSize = outputSize,
        )

        val rendered = codec.renderCrop(jpeg, request).asAndroidBitmap()
        val visible = CropRenderPlanner().plan(request).visibleSourceBounds
        val decodedRegion = requireNotNull(PixelBitmapRegionDecoderShadow.lastRect)
        val sampleSize = requireNotNull(PixelBitmapRegionDecoderShadow.lastSampleSize)
        val samples = listOf(
            0 to 0,
            outputSize.width / 2 to 0,
            outputSize.width - 1 to 0,
            0 to outputSize.height / 2,
            outputSize.width / 2 to outputSize.height / 2,
            outputSize.width - 1 to outputSize.height / 2,
            0 to outputSize.height - 1,
            outputSize.width / 2 to outputSize.height - 1,
            outputSize.width - 1 to outputSize.height - 1,
        )

        samples.forEach { (x, y) ->
            val actual = rendered.getPixel(x, y)
            assertEquals(255, Color.alpha(actual), "Expected opaque pixel at $x,$y")
            assertColorNear(expectedGradientAtOutput(request, x, y), actual, tolerance = 20)
        }
        assertEquals(2, sampleSize)
        assertTrue(decodedRegion.left <= (visible.left - sampleSize).coerceAtLeast(0))
        assertTrue(decodedRegion.top <= (visible.top - sampleSize).coerceAtLeast(0))
        assertTrue(decodedRegion.right >= (visible.right + sampleSize).coerceAtMost(sourceSize.width))
        assertTrue(decodedRegion.bottom >= (visible.bottom + sampleSize).coerceAtMost(sourceSize.height))
        rendered.recycle()
    }

    @Test
    fun highZoomCropPreservesSourceStripeDetail() = runBlocking {
        val sourceSize = ImageSize(2_160, 4_320)
        val png = createStripedPng(sourceSize)
        val request = CropRenderRequest(
            originalSize = sourceSize,
            transform = CropTransform(zoom = 3f),
            outputSize = ImageSize(1_920, 1_080),
        )

        val rendered = codec.renderCrop(png, request).asAndroidBitmap()
        val direct = stripeStats(rendered)
        val preview = decodeLegacyFullPreview(png)
        val legacyOutput = renderFromFullPreview(preview, request)
        val legacy = stripeStats(legacyOutput)

        // A 2048-edge full-image preview collapses these two-source-pixel stripes before zooming.
        assertTrue(
            direct.highContrastPixels >= rendered.width * 3 / 5,
            "Expected high-contrast stripe pixels, got $direct",
        )
        assertTrue(
            direct.transitions >= 250,
            "Expected preserved stripe transitions, got $direct",
        )
        assertTrue(direct.highContrastPixels >= legacy.highContrastPixels + 200, "$direct vs $legacy")
        assertTrue(direct.transitions >= legacy.transitions + 100, "$direct vs $legacy")

        rendered.recycle()
        preview.recycle()
        legacyOutput.recycle()
    }

    @Test
    fun malformedCropSourceIsRejectedWithTypedFailure() = runBlocking {
        assertFailsWith<PrintImageFailure.UnsupportedFormat> {
            codec.renderCrop(
                byteArrayOf(1, 2, 3),
                CropRenderRequest(ImageSize(12, 7), CropTransform(), ImageSize(20, 10)),
            )
        }
        Unit
    }

    @Test
    fun cropRejectsOriginalSizeMismatch() = runBlocking {
        val png = createEncodedBitmap(40, 30, Bitmap.CompressFormat.PNG)

        assertFailsWith<PrintImageFailure.RenderFailed> {
            codec.renderCrop(
                png,
                CropRenderRequest(ImageSize(41, 30), CropTransform(), ImageSize(20, 10)),
            )
        }
        Unit
    }
}

private fun assertDecodableBounds(bytes: ByteArray, expectedMimeType: String) {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    assertTrue(
        options.outWidth > 0 && options.outHeight > 0,
        "Expected positive bounds, mime=${options.outMimeType}, bytes=${bytes.size}",
    )
    if (options.outMimeType != null) {
        assertEquals(expectedMimeType, options.outMimeType)
    }
}

private fun createEncodedBitmap(
    width: Int,
    height: Int,
    format: Bitmap.CompressFormat,
): ByteArray {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.RED)
    }
    return ByteArrayOutputStream().use { output ->
        check(bitmap.compress(format, 100, output))
        bitmap.recycle()
        output.toByteArray()
    }
}

private fun createGradientPng(size: ImageSize): ByteArray = createPng(size) { x, y ->
    gradientColor(x.toDouble(), y.toDouble(), size)
}

private fun createGradientJpeg(size: ImageSize): ByteArray = createEncodedPixels(
    size,
    Bitmap.CompressFormat.JPEG,
) { x, y ->
    gradientColor(x.toDouble(), y.toDouble(), size)
}

private fun createFourColorJpeg(size: ImageSize): ByteArray = createEncodedPixels(
    size,
    Bitmap.CompressFormat.JPEG,
) { x, y ->
    exifFixtureColorAt(x, y, size).let { Color.rgb(it.red, it.green, it.blue) }
}

private fun createStripedPng(size: ImageSize): ByteArray = createPng(size) { x, _ ->
    if (x / 2 % 2 == 0) Color.BLACK else Color.WHITE
}

private inline fun createPng(size: ImageSize, colorAt: (Int, Int) -> Int): ByteArray {
    return createEncodedPixels(size, Bitmap.CompressFormat.PNG, colorAt)
}

private inline fun createEncodedPixels(
    size: ImageSize,
    format: Bitmap.CompressFormat,
    colorAt: (Int, Int) -> Int,
): ByteArray {
    val pixels = IntArray(size.width * size.height)
    for (y in 0 until size.height) {
        for (x in 0 until size.width) {
            pixels[y * size.width + x] = colorAt(x, y)
        }
    }
    val bitmap = Bitmap.createBitmap(pixels, size.width, size.height, Bitmap.Config.ARGB_8888)
    return ByteArrayOutputStream().use { output ->
        check(bitmap.compress(format, 100, output))
        bitmap.recycle()
        output.toByteArray()
    }
}

private fun expectedGradientAtOutput(
    request: CropRenderRequest,
    outputX: Int,
    outputY: Int,
): Int {
    val transform = CropRenderPlanner().plan(request).sourceToOutput
    val determinant = transform.scaleX * transform.scaleY - transform.skewX * transform.skewY
    val translatedX = outputX + 0.5 - transform.translateX
    val translatedY = outputY + 0.5 - transform.translateY
    val sourceX = (transform.scaleY * translatedX - transform.skewX * translatedY) / determinant
    val sourceY = (-transform.skewY * translatedX + transform.scaleX * translatedY) / determinant
    return gradientColor(sourceX - 0.5, sourceY - 0.5, request.originalSize)
}

private fun gradientColor(x: Double, y: Double, size: ImageSize): Int {
    val red = (x.coerceIn(0.0, (size.width - 1).toDouble()) * 255 / (size.width - 1))
        .toInt()
    val green = (y.coerceIn(0.0, (size.height - 1).toDouble()) * 255 / (size.height - 1))
        .toInt()
    return Color.rgb(red, green, 127)
}

private fun assertColorNear(expected: Int, actual: Int, tolerance: Int) {
    assertTrue(
        abs(Color.red(expected) - Color.red(actual)) <= tolerance,
        "red channel: expected=${Color.red(expected)}, actual=${Color.red(actual)}, " +
                "alpha=${Color.alpha(actual)}, rgb=${Color.red(actual)}/${Color.green(actual)}/" +
                "${Color.blue(actual)}",
    )
    assertTrue(
        abs(Color.green(expected) - Color.green(actual)) <= tolerance,
        "green channel: expected=${Color.green(expected)}, actual=${Color.green(actual)}",
    )
    assertTrue(
        abs(Color.blue(expected) - Color.blue(actual)) <= tolerance,
        "blue channel: expected=${Color.blue(expected)}, actual=${Color.blue(actual)}",
    )
}

private fun renderFromFullPreview(preview: Bitmap, request: CropRenderRequest): Bitmap {
    val plan = CropRenderPlanner().plan(request)
    val sourcePerPreviewX = request.originalSize.width.toDouble() / preview.width
    val sourcePerPreviewY = request.originalSize.height.toDouble() / preview.height
    val transform = plan.sourceToOutput
    val matrix = Matrix().apply {
        setValues(
            floatArrayOf(
                (transform.scaleX * sourcePerPreviewX).toFloat(),
                (transform.skewX * sourcePerPreviewY).toFloat(),
                transform.translateX.toFloat(),
                (transform.skewY * sourcePerPreviewX).toFloat(),
                (transform.scaleY * sourcePerPreviewY).toFloat(),
                transform.translateY.toFloat(),
                0f,
                0f,
                1f,
            ),
        )
    }
    return Bitmap.createBitmap(
        request.outputSize.width,
        request.outputSize.height,
        Bitmap.Config.ARGB_8888,
    ).also { output ->
        Canvas(output).drawBitmap(preview, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
    }
}

private fun decodeLegacyFullPreview(bytes: ByteArray): Bitmap {
    val sampled = BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply {
            inSampleSize = 2
            inPreferredConfig = Bitmap.Config.ARGB_8888
        },
    ) ?: error("Unable to decode legacy preview fixture")
    return Bitmap.createScaledBitmap(sampled, 1_024, 2_048, true).also {
        if (it !== sampled) sampled.recycle()
    }
}

private fun stripeStats(bitmap: Bitmap): StripeStats {
    val luminance = IntArray(bitmap.width) { x -> Color.red(bitmap.getPixel(x, bitmap.height / 2)) }
    val contrastStates = luminance.asSequence().mapNotNull { value ->
        when {
            value <= 64 -> false
            value >= 191 -> true
            else -> null
        }
    }.toList()
    return StripeStats(
        highContrastPixels = luminance.count { it <= 32 || it >= 223 },
        transitions = contrastStates.zipWithNext().count { (left, right) -> left != right },
        range = luminance.min()..luminance.max(),
    )
}

private data class StripeStats(
    val highContrastPixels: Int,
    val transitions: Int,
    val range: IntRange,
)

private fun ByteArray.hasPngSignature(): Boolean {
    val signature = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )
    return size >= signature.size && signature.indices.all { this[it] == signature[it] }
}

@Implements(BitmapRegionDecoder::class)
class PixelBitmapRegionDecoderShadow {
    private lateinit var source: Bitmap

    @Implementation
    fun decodeRegion(rect: Rect, options: BitmapFactory.Options): Bitmap {
        lastRect = Rect(rect)
        lastSampleSize = options.inSampleSize.coerceAtLeast(1)
        val cropped = Bitmap.createBitmap(source, rect.left, rect.top, rect.width(), rect.height())
        val sample = options.inSampleSize.coerceAtLeast(1)
        if (sample == 1) return cropped
        return Bitmap.createScaledBitmap(
            cropped,
            (rect.width() + sample - 1) / sample,
            (rect.height() + sample - 1) / sample,
            true,
        ).also { cropped.recycle() }
    }

    @Implementation
    fun recycle() {
        source.recycle()
    }

    companion object {
        var lastRect: Rect? = null
            private set
        var lastSampleSize: Int? = null
            private set

        @JvmStatic
        @Implementation
        fun newInstance(
            data: ByteArray,
            offset: Int,
            length: Int,
            @Suppress("UNUSED_PARAMETER") isShareable: Boolean,
        ): BitmapRegionDecoder {
            lastRect = null
            lastSampleSize = null
            val decoder = Shadow.newInstanceOf(BitmapRegionDecoder::class.java)
            Shadow.extract<PixelBitmapRegionDecoderShadow>(decoder).source =
                BitmapFactory.decodeByteArray(data, offset, length)
                    ?: throw IllegalArgumentException("Unable to decode test image")
            return decoder
        }
    }
}
