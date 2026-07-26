package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.impl.Stats
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DesktopPlatformImageCodecTest {
    private val codec = DesktopPlatformImageCodec()

    @Test
    fun previewStrategyRejectsUnsafeLazyDecodeBeforeRasterization() {
        val source = ImageSize(4_001, 4_000)
        val target = DecodeSizePlanner.plan(
            source,
            DecodeRequest(maxDimension = 2_048, maxPixels = 4_000_000L),
        )

        assertEquals(
            DesktopRasterStrategy.REJECT_UNSAFE_SOURCE,
            planDesktopPreviewRaster(source, target),
        )
    }

    @Test
    fun previewStrategyRejectsUnsafeExactSizeDecode() {
        val source = ImageSize(10_000, 10_000)

        assertEquals(
            DesktopRasterStrategy.REJECT_UNSAFE_SOURCE,
            planDesktopPreviewRaster(source, source),
        )
    }

    @Test
    fun previewStrategyUsesStablePathsWithoutDecoderExceptions() {
        val safeSource = ImageSize(1_600, 1_200)

        assertEquals(
            DesktopRasterStrategy.DIRECT_CODEC,
            planDesktopPreviewRaster(safeSource, safeSource),
        )
        assertEquals(
            DesktopRasterStrategy.BOUNDED_ENCODED_IMAGE,
            planDesktopPreviewRaster(safeSource, ImageSize(800, 600)),
        )
    }

    @Test
    fun cropStrategyRejectsUnsafeLazyDecodeBeforeRasterization() {
        assertEquals(
            DesktopRasterStrategy.REJECT_UNSAFE_SOURCE,
            planDesktopCropRaster(ImageSize(50_000_000, 2)),
        )
        assertEquals(
            DesktopRasterStrategy.BOUNDED_ENCODED_IMAGE,
            planDesktopCropRaster(ImageSize(4_000, 4_000)),
        )
    }

    @Test
    fun unsafeHeifUsesTypedDesktopCapabilityFailureWithoutRejectingJpegOrPng() {
        val unsafeSource = ImageSize(5_000, 4_000)

        assertFailsWith<PrintImageFailure.DesktopRegionDecodeUnavailable> {
            ensureDesktopRasterCapability(EncodedImageFormat.HEIF, unsafeSource)
        }
        ensureDesktopRasterCapability(EncodedImageFormat.JPEG, unsafeSource)
        ensureDesktopRasterCapability(EncodedImageFormat.PNG, unsafeSource)
        ensureDesktopRasterCapability(EncodedImageFormat.HEIF, ImageSize(4_000, 4_000))
    }

    @Test
    fun nonDivisibleImageIoSubsamplingKeepsHighZoomEdgeOnReaderGrid() {
        val region = PixelRect(left = 101, top = 53, right = 5_102, bottom = 4_054)
        val decodedSize = ImageSize(width = 2_501, height = 2_001)
        val subsampling = 2
        val transform = localRasterToRaw(region, decodedSize, subsampling)

        val first = transform.map(0.5, 0.5)
        val second = transform.map(1.5, 1.5)
        assertEquals(region.left + 0.5, first.x, absoluteTolerance = 0.0)
        assertEquals(region.top + 0.5, first.y, absoluteTolerance = 0.0)
        assertEquals(2.0, second.x - first.x, absoluteTolerance = 0.0)
        assertEquals(2.0, second.y - first.y, absoluteTolerance = 0.0)

        val localEdgeX = decodedSize.width - 0.5
        val expectedRawEdgeX = region.left +
                (decodedSize.width - 1.0) * subsampling + 0.5
        val highZoom = 32.0
        assertEquals(
            expected = expectedRawEdgeX * highZoom,
            actual = transform.map(localEdgeX, 0.5).x * highZoom,
            absoluteTolerance = 0.0,
        )
    }

    @Test
    fun nonDivisibleImageIoCropMapsSampleToRawPixelCenter() = runBlocking {
        val sourceSize = ImageSize(4_001, 4_001)
        val outputSize = ImageSize(4_000, 4_000)
        val lineX = 2_000
        val request = CropRenderRequest(
            originalSize = sourceSize,
            transform = CropTransform(),
            outputSize = outputSize,
        )
        val expectedCenter = CropRenderPlanner()
            .plan(request)
            .sourceToOutput
            .map(lineX + 0.5, sourceSize.height / 2.0)
            .x

        val rendered = codec.renderCrop(
            createVerticalLinePng(sourceSize, lineX),
            request,
        ).asSkiaBitmap()

        try {
            val actualCenter = horizontalLuminanceCentroid(
                bitmap = rendered,
                y = rendered.height / 2,
                centerX = expectedCenter,
            )
            assertEquals(expectedCenter, actualCenter, absoluteTolerance = 0.1)
        } finally {
            rendered.close()
        }
    }

    @Test
    fun skiaRebaseUsesOneFloatAnchorForMatrixAndDrawTranslation() {
        val anchorX = 50_000_003.0
        val anchorY = 1.0
        val transform = AffineTransform(
            scaleX = 2_048.0,
            skewX = 0.0,
            translateX = -2_048.0 * anchorX + 1_024.0,
            skewY = 0.0,
            scaleY = 1_024.0,
            translateY = -1_024.0 * anchorY + 512.0,
        )

        val rebased = transform.rebaseForDesktopSkia(anchorX, anchorY)
        val floatAnchorX = anchorX.toFloat()
        val floatAnchorY = anchorY.toFloat()
        val canonicalMapped = transform.map(floatAnchorX.toDouble(), floatAnchorY.toDouble())

        assertEquals(-floatAnchorX, rebased.drawX)
        assertEquals(-floatAnchorY, rebased.drawY)
        assertEquals(canonicalMapped.x.toFloat(), rebased.matrix.mat[2])
        assertEquals(canonicalMapped.y.toFloat(), rebased.matrix.mat[5])
        assertTrue(
            transform.map(anchorX, anchorY).x.toFloat() != rebased.matrix.mat[2],
            "Fixture must expose a Float-anchor error amplified beyond output pixels",
        )
    }

    @Test
    fun fatalErrorsAreNotMappedToRecoverableImageFailures() {
        DesktopFailureOperation.entries.forEach { operation ->
            val fatal = AssertionError("fatal-$operation")

            val thrown = assertFailsWith<AssertionError> {
                mapDesktopImageFailure(operation) { throw fatal }
            }

            assertSame(fatal, thrown)
        }
    }

    @Test
    fun cancelledImageBitmapHandoffClosesOwnedPixels() {
        val cancellation = CancellationException("cancelled")
        val previousEnabled = Stats.enabled
        Stats.enabled = true
        Stats.allocated.clear()
        try {
            val bitmap = createOwnedImageBitmap()

            val thrown = assertFailsWith<CancellationException> {
                handoffDesktopImageBitmap(bitmap) { throw cancellation }
            }

            assertSame(cancellation, thrown)
            assertEquals(0, Stats.allocated["Bitmap"] ?: 0)
        } finally {
            Stats.allocated.clear()
            Stats.enabled = previousEnabled
        }
    }

    @Test
    fun successfulImageBitmapHandoffKeepsOwnedPixelsOpen() {
        val previousEnabled = Stats.enabled
        Stats.enabled = true
        Stats.allocated.clear()
        try {
            val bitmap = createOwnedImageBitmap()

            assertSame(bitmap, handoffDesktopImageBitmap(bitmap) {})
            assertEquals(1, Stats.allocated["Bitmap"] ?: 0)

            bitmap.asSkiaBitmap().close()
            assertEquals(0, Stats.allocated["Bitmap"] ?: 0)
        } finally {
            Stats.allocated.clear()
            Stats.enabled = previousEnabled
        }
    }

    @Test
    fun promptCancellationAfterWorkerCompletionClosesOwnedPixels() {
        val previousEnabled = Stats.enabled
        Stats.enabled = true
        Stats.allocated.clear()
        try {
            val workerDispatcher = QueuedTestDispatcher()
            val cancellation = CancellationException("cancelled")
            val bitmap = createOwnedImageBitmap()

            val result = cancelAfterWorkerCompletion(workerDispatcher, cancellation) {
                withOwnedPlatformImageResult(
                    dispatcher = workerDispatcher,
                    ownedBitmap = { it },
                ) {
                    bitmap
                }
            }

            assertIs<CancellationException>(result.exceptionOrNull())
            assertEquals(0, Stats.allocated["Bitmap"] ?: 0)
        } finally {
            Stats.allocated.clear()
            Stats.enabled = previousEnabled
        }
    }

    @Test
    fun pngRoundTripPreservesDimensions() = runBlocking {
        val decoded = codec.decode(
            createEncodedImage(12, 7, EncodedImageFormat.PNG),
            DecodeRequest(2_048, PrintImageLimits.MAX_INTERMEDIATE_DECODE_PIXELS),
        )

        assertEquals(ImageSize(12, 7), decoded.originalSize)
        assertEquals(12, decoded.bitmap.width)
        assertEquals(7, decoded.bitmap.height)

        val encoded = codec.encodePng(decoded.bitmap)
        assertTrue(encoded.hasPngSignature())
        Image.makeFromEncoded(encoded).use { image ->
            assertEquals(12, image.width)
            assertEquals(7, image.height)
        }
    }

    @Test
    fun previewHonorsEdgeAndPixelBudgets() = runBlocking {
        val source = ImageSize(1_600, 1_200)
        val request = DecodeRequest(maxDimension = 700, maxPixels = 300_000L)

        val decoded = codec.decode(createEncodedImage(source, EncodedImageFormat.PNG), request)

        assertEquals(source, decoded.originalSize)
        assertEquals(DecodeSizePlanner.plan(source, request).width, decoded.bitmap.width)
        assertEquals(DecodeSizePlanner.plan(source, request).height, decoded.bitmap.height)
        assertTrue(maxOf(decoded.bitmap.width, decoded.bitmap.height) <= request.maxDimension)
        assertTrue(decoded.bitmap.width.toLong() * decoded.bitmap.height <= request.maxPixels)
    }

    @Test
    fun largeJpegAndPngPreviewHonorBudgetsWithoutRejectingSourceDimensions() = runBlocking {
        val request = DecodeRequest(maxDimension = 2_048, maxPixels = 3_000_000L)
        val target = DecodeSizePlanner.plan(LARGE_IMAGE_SIZE, request)

        largeStripedFixtures.forEach { (format, bytes) ->
            val decoded = codec.decode(bytes, request)
            val bitmap = decoded.bitmap.asSkiaBitmap()
            try {
                assertEquals(LARGE_IMAGE_SIZE, decoded.originalSize, "format=$format")
                assertEquals(target.width, bitmap.width, "format=$format")
                assertEquals(target.height, bitmap.height, "format=$format")
                assertTrue(maxOf(bitmap.width, bitmap.height) <= request.maxDimension)
                assertTrue(bitmap.width.toLong() * bitmap.height <= request.maxPixels)
            } finally {
                bitmap.close()
            }
        }
    }

    @Test
    fun largeJpegAndPngHighZoomCropKeepsSourceDetailAtFixedOutput() = runBlocking {
        val request = CropRenderRequest(
            originalSize = LARGE_IMAGE_SIZE,
            transform = CropTransform(
                centerOffsetX = 0.35f,
                centerOffsetY = -0.2f,
                zoom = 4f,
            ),
            outputSize = ImageSize(1_920, 1_080),
        )

        largeStripedFixtures.forEach { (format, bytes) ->
            val rendered = codec.renderCrop(bytes, request).asSkiaBitmap()
            try {
                assertEquals(request.outputSize.width, rendered.width, "format=$format")
                assertEquals(request.outputSize.height, rendered.height, "format=$format")
                val detail = horizontalContrastStats(rendered, rendered.height / 2)
                assertTrue(
                    detail.highContrastPixels >= rendered.width * 4 / 5,
                    "Expected high-contrast source detail for $format, got $detail",
                )
                assertTrue(
                    detail.transitions >= 200,
                    "Expected source-level stripe transitions for $format, got $detail",
                )
            } finally {
                rendered.close()
            }
        }
    }

    @Test
    fun cropRenderingUsesPlannerTransformsAndFixedOutput() = runBlocking {
        val sourceSize = ImageSize(400, 300)
        val outputSize = ImageSize(320, 180)
        val png = createFourColorImage(sourceSize, EncodedImageFormat.PNG)
        val transforms = listOf(
            CropTransform(),
            CropTransform(zoom = 2f),
            CropTransform(centerOffsetX = 0.3f, centerOffsetY = -0.2f, zoom = 2f),
            CropTransform(quarterTurns = 1),
            CropTransform(flipHorizontal = true),
            CropTransform(flipVertical = true),
            CropTransform(
                centerOffsetX = 0.15f,
                centerOffsetY = -0.1f,
                zoom = 1.6f,
                quarterTurns = 3,
                flipHorizontal = true,
                flipVertical = true,
            ),
        )

        transforms.forEach { transform ->
            val request = CropRenderRequest(sourceSize, transform, outputSize)
            val rendered = codec.renderCrop(png, request).asSkiaBitmap()

            assertEquals(outputSize.width, rendered.width)
            assertEquals(outputSize.height, rendered.height)
            listOf(
                outputSize.width / 4 to outputSize.height / 4,
                outputSize.width * 3 / 4 to outputSize.height / 4,
                outputSize.width / 4 to outputSize.height * 3 / 4,
                outputSize.width * 3 / 4 to outputSize.height * 3 / 4,
            ).forEach { (x, y) ->
                assertColorNear(
                    expectedFourColorAtOutput(request, x, y),
                    rendered.getColor(x, y),
                    tolerance = 8,
                )
            }
        }
    }

    @Test
    fun exifOrientationsNormalizePreviewDimensionsAndPixels() = runBlocking {
        val rawSize = EXIF_ORIENTATION_FIXTURE_SIZE
        val rawJpeg = createFourColorImage(rawSize, EncodedImageFormat.JPEG)

        EXIF_ORIENTATION_CONTRACTS.forEach { contract ->
            val decoded = codec.decode(
                rawJpeg.withExifOrientation(contract.orientation),
                DecodeRequest(maxDimension = 2_048, maxPixels = 16_000_000L),
            )
            val preview = decoded.bitmap.asSkiaBitmap()
            val expectedSize = contract.orientedSize(rawSize)

            try {
                assertEquals(expectedSize, decoded.originalSize, "orientation=${contract.orientation}")
                assertEquals(expectedSize.width, preview.width, "orientation=${contract.orientation}")
                assertEquals(expectedSize.height, preview.height, "orientation=${contract.orientation}")
                contract.expectedCorners.forEach { expected ->
                    val (x, y) = expected.corner.samplePoint(expectedSize)
                    val actual = preview.getColor(x, y)
                    assertExifColorNear(
                        expected = expected.color,
                        actualRed = Color.getR(actual),
                        actualGreen = Color.getG(actual),
                        actualBlue = Color.getB(actual),
                        context = "orientation=${contract.orientation}, corner=${expected.corner}",
                    )
                }
            } finally {
                preview.close()
            }
        }
    }

    @Test
    fun exifSixCropNormalizesPixelDirection() = runBlocking {
        val rawSize = ImageSize(400, 300)
        val orientedSize = ImageSize(300, 400)
        val jpeg = createFourColorImage(rawSize, EncodedImageFormat.JPEG).withExifOrientation(6)
        val rendered = codec.renderCrop(
            jpeg,
            CropRenderRequest(orientedSize, CropTransform(), ImageSize(150, 200)),
        ).asSkiaBitmap()

        assertColorNear(Color.BLUE, rendered.getColor(20, 20), tolerance = 35)
        assertColorNear(Color.RED, rendered.getColor(129, 20), tolerance = 35)
        assertColorNear(Color.YELLOW, rendered.getColor(20, 179), tolerance = 35)
        assertColorNear(Color.GREEN, rendered.getColor(129, 179), tolerance = 35)
    }

    @Test
    fun exifOrientationsMapOrientedCropRegionsBackToRawPixels() = runBlocking {
        val rawSize = ImageSize(400, 300)
        val rawJpeg = createFourColorImage(rawSize, EncodedImageFormat.JPEG)
        val outputSize = ImageSize(180, 120)

        EXIF_ORIENTATION_CONTRACTS.forEach { contract ->
            val orientedSize = contract.orientedSize(rawSize)
            val request = CropRenderRequest(
                originalSize = orientedSize,
                transform = CropTransform(
                    centerOffsetX = 0.22f,
                    centerOffsetY = -0.17f,
                    zoom = 1.8f,
                ),
                outputSize = outputSize,
            )
            val rendered = codec.renderCrop(
                rawJpeg.withExifOrientation(contract.orientation),
                request,
            ).asSkiaBitmap()

            try {
                listOf(
                    20 to 20,
                    159 to 20,
                    20 to 99,
                    159 to 99,
                ).forEach { (x, y) ->
                    assertColorNear(
                        expected = expectedRawColorAtOutput(
                            request = request,
                            rawSize = rawSize,
                            exifOrientation = contract.orientation,
                            outputX = x,
                            outputY = y,
                        ),
                        actual = rendered.getColor(x, y),
                        tolerance = 35,
                    )
                }
            } finally {
                rendered.close()
            }
        }
    }

    @Test
    fun highZoomCropPreservesMoreStripeDetailThanBoundedFullPreview() = runBlocking {
        val sourceSize = ImageSize(2_160, 4_320)
        val png = createStripedPng(sourceSize)
        val request = CropRenderRequest(
            originalSize = sourceSize,
            transform = CropTransform(zoom = 3f),
            outputSize = ImageSize(1_920, 1_080),
        )

        val rendered = codec.renderCrop(png, request)
        val direct = stripeStats(rendered.asSkiaBitmap())
        val preview = codec.decode(
            png,
            DecodeRequest(2_048, PrintImageLimits.MAX_INTERMEDIATE_DECODE_PIXELS),
        ).bitmap
        val legacy = legacyStripeStats(preview, request)

        assertTrue(
            direct.highContrastPixels >= rendered.width * 3 / 5,
            "Expected preserved high-contrast pixels, got $direct",
        )
        assertTrue(direct.transitions >= 250, "Expected preserved transitions, got $direct")
        assertTrue(direct.transitions >= legacy.transitions + 25, "$direct vs $legacy")
    }

    @Test
    fun repeatedDecodeAndRenderCloseTemporarySkiaResources() = runBlocking {
        val source = ImageSize(80, 60)
        val png = createFourColorImage(source, EncodedImageFormat.PNG)
        val request = CropRenderRequest(source, CropTransform(zoom = 1.5f), ImageSize(40, 30))
        val previousEnabled = Stats.enabled
        Stats.enabled = true
        Stats.allocated.clear()
        try {
            repeat(12) {
                val preview = codec.decode(
                    png,
                    DecodeRequest(maxDimension = 50, maxPixels = 2_000L),
                ).bitmap
                val crop = codec.renderCrop(png, request)

                assertColorNear(Color.RED, preview.asSkiaBitmap().getColor(5, 5), tolerance = 8)
                assertEquals(request.outputSize.width, crop.width)
                preview.asSkiaBitmap().close()
                crop.asSkiaBitmap().close()
            }

            listOf("Data", "Codec", "Image", "Surface", "Bitmap").forEach { resource ->
                assertEquals(0, Stats.allocated[resource] ?: 0, "$resource ownership imbalance")
            }
        } finally {
            Stats.allocated.clear()
            Stats.enabled = previousEnabled
        }
    }

    @Test
    fun returnedBitmapOwnsPixelsAfterSnapshotCloses() = runBlocking {
        val source = ImageSize(80, 60)
        val png = createFourColorImage(source, EncodedImageFormat.PNG)
        val previousEnabled = Stats.enabled
        Stats.enabled = true
        Stats.allocated.clear()
        try {
            val rendered = codec.renderCrop(
                png,
                CropRenderRequest(source, CropTransform(), ImageSize(40, 30)),
            )

            assertColorNear(Color.RED, rendered.asSkiaBitmap().getColor(5, 5), tolerance = 8)
            assertEquals(0, Stats.allocated["Image"] ?: 0, "snapshot must be closed")
            assertEquals(0, Stats.allocated["Surface"] ?: 0, "surface must be closed")
            assertEquals(1, Stats.allocated["Bitmap"] ?: 0, "only returned pixels stay owned")

            rendered.asSkiaBitmap().close()
            assertEquals(0, Stats.allocated["Bitmap"] ?: 0)
        } finally {
            Stats.allocated.clear()
            Stats.enabled = previousEnabled
        }
    }

    @Test
    fun malformedBytesAreRejectedForDecodeAndCrop() = runBlocking {
        val bytes = byteArrayOf(1, 2, 3)

        assertFailsWith<PrintImageFailure.UnsupportedFormat> {
            codec.decode(bytes, DecodeRequest(maxDimension = 2_048, maxPixels = 16_000_000L))
        }
        assertFailsWith<PrintImageFailure.UnsupportedFormat> {
            codec.renderCrop(
                bytes,
                CropRenderRequest(ImageSize(12, 7), CropTransform(), ImageSize(20, 10)),
            )
        }
        Unit
    }

    @Test
    fun cropRejectsOriginalSizeMismatch() = runBlocking {
        val png = createEncodedImage(40, 30, EncodedImageFormat.PNG)

        assertFailsWith<PrintImageFailure.RenderFailed> {
            codec.renderCrop(
                png,
                CropRenderRequest(ImageSize(41, 30), CropTransform(), ImageSize(20, 10)),
            )
        }
        Unit
    }
}

private fun createEncodedImage(
    width: Int,
    height: Int,
    format: EncodedImageFormat,
): ByteArray = createEncodedImage(ImageSize(width, height), format)

private fun createOwnedImageBitmap(): ImageBitmap =
    Surface.makeRasterN32Premul(2, 2).use { surface ->
        surface.makeImageSnapshot().use { image ->
            Bitmap.makeFromImage(image).asComposeImageBitmap()
        }
    }

private fun createEncodedImage(
    size: ImageSize,
    format: EncodedImageFormat,
): ByteArray = Surface.makeRasterN32Premul(size.width, size.height).use { surface ->
    surface.canvas.clear(Color.RED)
    surface.makeImageSnapshot().use { image ->
        requireNotNull(image.encodeToData(format, 100)).use { data -> data.bytes }
    }
}

private fun createFourColorImage(
    size: ImageSize,
    format: EncodedImageFormat,
): ByteArray = Surface.makeRasterN32Premul(size.width, size.height).use { surface ->
    Paint().use { paint ->
        val halfWidth = size.width / 2f
        val halfHeight = size.height / 2f
        listOf(
            Rect.makeXYWH(0f, 0f, halfWidth, halfHeight) to Color.RED,
            Rect.makeXYWH(halfWidth, 0f, halfWidth, halfHeight) to Color.GREEN,
            Rect.makeXYWH(0f, halfHeight, halfWidth, halfHeight) to Color.BLUE,
            Rect.makeXYWH(halfWidth, halfHeight, halfWidth, halfHeight) to Color.YELLOW,
        ).forEach { (rect, color) ->
            paint.color = color
            surface.canvas.drawRect(rect, paint)
        }
    }
    surface.makeImageSnapshot().use { image ->
        requireNotNull(image.encodeToData(format, 100)).use { data -> data.bytes }
    }
}

private fun createStripedPng(size: ImageSize): ByteArray =
    Surface.makeRasterN32Premul(size.width, size.height).use { surface ->
        Paint().use { paint ->
            var x = 0
            while (x < size.width) {
                paint.color = if (x / 2 % 2 == 0) Color.BLACK else Color.WHITE
                surface.canvas.drawRect(
                    Rect.makeXYWH(x.toFloat(), 0f, 2f, size.height.toFloat()),
                    paint,
                )
                x += 2
            }
        }
        surface.makeImageSnapshot().use { image ->
            requireNotNull(image.encodeToData(EncodedImageFormat.PNG, 100)).use { data -> data.bytes }
        }
    }

private val LARGE_IMAGE_SIZE = ImageSize(5_000, 4_000)

private val largeStripedFixtures: Map<EncodedImageFormat, ByteArray> by lazy {
    listOf(EncodedImageFormat.JPEG, EncodedImageFormat.PNG).associateWith { format ->
        createLargeStripedImage(LARGE_IMAGE_SIZE, format)
    }
}

private fun createLargeStripedImage(
    size: ImageSize,
    format: EncodedImageFormat,
): ByteArray {
    val image = BufferedImage(size.width, size.height, BufferedImage.TYPE_BYTE_GRAY)
    val pixels = (image.raster.dataBuffer as DataBufferByte).data
    val row = ByteArray(size.width) { x ->
        if (x / 4 % 2 == 0) 0 else 0xFF.toByte()
    }
    repeat(size.height) { y ->
        row.copyInto(pixels, destinationOffset = y * size.width)
    }
    return ByteArrayOutputStream().use { output ->
        val formatName = when (format) {
            EncodedImageFormat.JPEG -> "jpeg"
            EncodedImageFormat.PNG -> "png"
            else -> error("Unsupported large fixture format: $format")
        }
        check(ImageIO.write(image, formatName, output))
        output.toByteArray()
    }
}

private fun createVerticalLinePng(size: ImageSize, lineX: Int): ByteArray {
    val image = BufferedImage(size.width, size.height, BufferedImage.TYPE_BYTE_GRAY)
    val pixels = (image.raster.dataBuffer as DataBufferByte).data
    repeat(size.height) { y ->
        pixels[y * size.width + lineX] = 0xFF.toByte()
    }
    return ByteArrayOutputStream().use { output ->
        check(ImageIO.write(image, "png", output))
        output.toByteArray()
    }
}

private fun horizontalLuminanceCentroid(
    bitmap: Bitmap,
    y: Int,
    centerX: Double,
): Double {
    val left = floor(centerX).toInt() - 4
    val right = ceil(centerX).toInt() + 4
    var weightedPosition = 0.0
    var totalLuminance = 0.0
    for (x in left..right) {
        val luminance = Color.getR(bitmap.getColor(x, y)).toDouble()
        weightedPosition += (x + 0.5) * luminance
        totalLuminance += luminance
    }
    check(totalLuminance > 0.0) { "Expected the sampled source line in the rendered crop" }
    return weightedPosition / totalLuminance
}

private data class HorizontalContrastStats(
    val highContrastPixels: Int,
    val transitions: Int,
)

private fun horizontalContrastStats(bitmap: Bitmap, y: Int): HorizontalContrastStats {
    var highContrastPixels = 0
    var transitions = 0
    var previousDark: Boolean? = null
    repeat(bitmap.width) { x ->
        val red = Color.getR(bitmap.getColor(x, y))
        if (red <= 40 || red >= 215) highContrastPixels++
        val dark = red < 128
        if (previousDark != null && previousDark != dark) transitions++
        previousDark = dark
    }
    return HorizontalContrastStats(highContrastPixels, transitions)
}

private fun expectedFourColorAtOutput(
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
    return when {
        sourceX < request.originalSize.width / 2.0 &&
                sourceY < request.originalSize.height / 2.0 -> Color.RED
        sourceX >= request.originalSize.width / 2.0 &&
                sourceY < request.originalSize.height / 2.0 -> Color.GREEN
        sourceX < request.originalSize.width / 2.0 -> Color.BLUE
        else -> Color.YELLOW
    }
}

private fun expectedRawColorAtOutput(
    request: CropRenderRequest,
    rawSize: ImageSize,
    exifOrientation: Int,
    outputX: Int,
    outputY: Int,
): Int {
    val transform = CropRenderPlanner().plan(request).sourceToOutput
    val determinant = transform.scaleX * transform.scaleY - transform.skewX * transform.skewY
    val translatedX = outputX + 0.5 - transform.translateX
    val translatedY = outputY + 0.5 - transform.translateY
    val orientedX = (transform.scaleY * translatedX - transform.skewX * translatedY) /
            determinant
    val orientedY = (-transform.skewY * translatedX + transform.scaleX * translatedY) /
            determinant
    val (rawX, rawY) = when (exifOrientation) {
        1 -> orientedX to orientedY
        2 -> rawSize.width - orientedX to orientedY
        3 -> rawSize.width - orientedX to rawSize.height - orientedY
        4 -> orientedX to rawSize.height - orientedY
        5 -> orientedY to orientedX
        6 -> orientedY to rawSize.height - orientedX
        7 -> rawSize.width - orientedY to rawSize.height - orientedX
        8 -> rawSize.width - orientedY to orientedX
        else -> error("Unsupported EXIF orientation: $exifOrientation")
    }
    return when {
        rawX < rawSize.width / 2.0 && rawY < rawSize.height / 2.0 -> Color.RED
        rawX >= rawSize.width / 2.0 && rawY < rawSize.height / 2.0 -> Color.GREEN
        rawX < rawSize.width / 2.0 -> Color.BLUE
        else -> Color.YELLOW
    }
}

private fun legacyStripeStats(preview: ImageBitmap, request: CropRenderRequest): StripeStats {
    val transform = CropRenderPlanner().plan(request).sourceToOutput
    val previewBitmap = preview.asSkiaBitmap()
    val sourcePerPreviewX = request.originalSize.width.toDouble() / previewBitmap.width
    val sourcePerPreviewY = request.originalSize.height.toDouble() / previewBitmap.height
    return Surface.makeRasterN32Premul(request.outputSize.width, request.outputSize.height).use { surface ->
        surface.canvas.clear(Color.TRANSPARENT)
        Image.makeFromBitmap(previewBitmap).use { previewImage ->
            surface.canvas.concat(
                Matrix33(
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
            surface.canvas.drawImage(previewImage, 0f, 0f)
        }
        surface.makeImageSnapshot().use { snapshot ->
            Bitmap.makeFromImage(snapshot).use(::stripeStats)
        }
    }
}

private fun stripeStats(bitmap: Bitmap): StripeStats {
    val luminance = IntArray(bitmap.width) { x -> Color.getR(bitmap.getColor(x, bitmap.height / 2)) }
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

private fun assertColorNear(expected: Int, actual: Int, tolerance: Int) {
    assertTrue(
        abs(Color.getR(expected) - Color.getR(actual)) <= tolerance,
        "red: expected=${Color.getR(expected)}, actual=${Color.getR(actual)}, color=$actual",
    )
    assertTrue(
        abs(Color.getG(expected) - Color.getG(actual)) <= tolerance,
        "green: expected=${Color.getG(expected)}, actual=${Color.getG(actual)}, color=$actual",
    )
    assertTrue(
        abs(Color.getB(expected) - Color.getB(actual)) <= tolerance,
        "blue: expected=${Color.getB(expected)}, actual=${Color.getB(actual)}, color=$actual",
    )
}

private fun ByteArray.hasPngSignature(): Boolean {
    val signature = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )
    return size >= signature.size && signature.indices.all { this[it] == signature[it] }
}
