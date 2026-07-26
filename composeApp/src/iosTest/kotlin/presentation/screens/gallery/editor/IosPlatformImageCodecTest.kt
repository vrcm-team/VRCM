package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
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
import org.jetbrains.skia.impl.use
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataCreateMutable
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreGraphics.CGImageRelease
import platform.ImageIO.CGImageDestinationAddImage
import platform.ImageIO.CGImageDestinationCreateWithData
import platform.ImageIO.CGImageDestinationFinalize
import platform.ImageIO.CGImageSourceCreateImageAtIndex
import platform.ImageIO.CGImageSourceCreateWithData
import platform.posix.memcpy
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class IosPlatformImageCodecTest {
    private val codec = IosPlatformImageCodec()

    @Test
    fun localBitmapSizeMismatchReleasesOwnedPixels() {
        var activeBitmaps = 0

        val failure = assertFailsWith<IllegalStateException> {
            handoffLocalPlatformImageBitmap(
                createBitmap = {
                    activeBitmaps += 1
                    ImageBitmap(width = 3, height = 2)
                },
                releaseBitmap = { activeBitmaps -= 1 },
            ) { bitmap ->
                check(bitmap.width == 4 && bitmap.height == 2) {
                    "Decoded bitmap has unexpected dimensions"
                }
                bitmap
            }
        }

        assertEquals("Decoded bitmap has unexpected dimensions", failure.message)
        assertEquals(0, activeBitmaps)
    }

    @Test
    fun localBitmapFailuresReleasePixelsAndKeepIdentity() {
        listOf(
            CancellationException("cancelled"),
            IllegalArgumentException("recoverable"),
            AssertionError("fatal"),
        ).forEach { expected ->
            var activeBitmaps = 0

            val thrown = assertFailsWith<Throwable> {
                handoffLocalPlatformImageBitmap(
                    createBitmap = {
                        activeBitmaps += 1
                        ImageBitmap(width = 3, height = 2)
                    },
                    releaseBitmap = { activeBitmaps -= 1 },
                ) {
                    throw expected
                }
            }

            assertSame(expected, thrown)
            assertEquals(0, activeBitmaps)
        }
    }

    @Test
    fun localBitmapReleaseFailureIsSuppressedOnPrimaryFailure() {
        val primary = IllegalStateException("mismatch")
        val releaseFailure = IllegalArgumentException("release")

        val thrown = assertFailsWith<IllegalStateException> {
            handoffLocalPlatformImageBitmap(
                createBitmap = { ImageBitmap(width = 3, height = 2) },
                releaseBitmap = { throw releaseFailure },
            ) {
                throw primary
            }
        }

        assertSame(primary, thrown)
        assertEquals(listOf(releaseFailure), thrown.suppressedExceptions)
    }

    @Test
    fun localBitmapSuccessfulHandoffDoesNotReleasePixels() {
        val bitmap = ImageBitmap(width = 3, height = 2)
        var releaseCount = 0

        val result = handoffLocalPlatformImageBitmap(
            createBitmap = { bitmap },
            releaseBitmap = { releaseCount += 1 },
        ) { it }

        assertSame(bitmap, result)
        assertEquals(0, releaseCount)
    }

    @Test
    fun pngRoundTripPreservesDimensions() = runBlocking {
        val decoded = codec.decode(
            createEncodedImage(12, 7, EncodedImageFormat.PNG),
            DecodeRequest(maxDimension = 2_048, maxPixels = 16_000_000L),
        )

        assertEquals(ImageSize(12, 7), decoded.originalSize)
        assertEquals(12, decoded.bitmap.width)
        assertEquals(7, decoded.bitmap.height)
        assertTrue(codec.encodePng(decoded.bitmap).hasPngSignature())
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
            val bitmap = decoded.bitmap.asSkiaBitmap()
            val expectedSize = contract.orientedSize(rawSize)

            try {
                assertEquals(expectedSize, decoded.originalSize, "orientation=${contract.orientation}")
                assertEquals(expectedSize.width, bitmap.width, "orientation=${contract.orientation}")
                assertEquals(expectedSize.height, bitmap.height, "orientation=${contract.orientation}")
                contract.expectedCorners.forEach { expected ->
                    val (x, y) = expected.corner.samplePoint(expectedSize)
                    val actual = bitmap.getColor(x, y)
                    assertExifColorNear(
                        expected = expected.color,
                        actualRed = Color.getR(actual),
                        actualGreen = Color.getG(actual),
                        actualBlue = Color.getB(actual),
                        context = "orientation=${contract.orientation}, corner=${expected.corner}",
                    )
                }
            } finally {
                bitmap.close()
            }
        }
    }

    @Test
    fun heicsSequenceSourceTypeIsAccepted() = runBlocking {
        val source = ImageSize(12, 8)

        val decoded = codec.decode(
            createHeicsImage(source),
            DecodeRequest(maxDimension = 2_048, maxPixels = 16_000_000L),
        )

        assertEquals(source, decoded.originalSize)
        assertEquals(source.width, decoded.bitmap.width)
        assertEquals(source.height, decoded.bitmap.height)
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
    fun previewHonorsCustomPixelBudget() = runBlocking {
        val source = ImageSize(1_600, 1_200)
        val request = DecodeRequest(maxDimension = 2_048, maxPixels = 400_000L)

        val decoded = codec.decode(createEncodedImage(source, EncodedImageFormat.PNG), request)

        assertEquals(source, decoded.originalSize)
        assertTrue(maxOf(decoded.bitmap.width, decoded.bitmap.height) <= request.maxDimension)
        assertTrue(decoded.bitmap.width.toLong() * decoded.bitmap.height <= request.maxPixels)
    }

    @Test
    fun previewHonorsDefaultSixteenMegapixelBudget() = runBlocking {
        val source = ImageSize(4_100, 4_000)
        val request = DecodeRequest(maxDimension = 5_760, maxPixels = 16_000_000L)

        val decoded = codec.decode(createEncodedImage(source, EncodedImageFormat.PNG), request)

        assertEquals(source, decoded.originalSize)
        assertTrue(maxOf(decoded.bitmap.width, decoded.bitmap.height) <= request.maxDimension)
        assertTrue(decoded.bitmap.width.toLong() * decoded.bitmap.height <= request.maxPixels)
    }

    @Test
    fun cropRenderingUsesPlannerTransforms() = runBlocking {
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
    fun coreImageTransformConvertsSkewYFlipAndExtentOrigin() {
        val sourceToOutput = AffineTransform(
            scaleX = 2.0,
            skewX = 3.0,
            translateX = 5.0,
            skewY = 7.0,
            scaleY = 11.0,
            translateY = 13.0,
        )
        val sourceHeight = 17
        val outputHeight = 19

        val converted = sourceToOutput.toCoreImageTransform(sourceHeight, outputHeight)
        val sourcePoint = AffinePoint(31.0, 37.0)
        val expected = sourceToOutput.map(sourcePoint.x, sourcePoint.y)
        val coreImagePoint = converted.map(sourcePoint.x, sourceHeight - sourcePoint.y)

        assertEquals(expected.x, coreImagePoint.x, absoluteTolerance = 0.000_001)
        assertEquals(expected.y, outputHeight - coreImagePoint.y, absoluteTolerance = 0.000_001)
        assertEquals(-3.0, converted.skewX)
        assertEquals(-7.0, converted.skewY)

        val extentOrigin = AffinePoint(23.0, -29.0)
        val normalizedOrigin = coreImageExtentNormalization(extentOrigin.x, extentOrigin.y)
            .map(extentOrigin.x, extentOrigin.y)
        assertEquals(AffinePoint(0.0, 0.0), normalizedOrigin)
    }

    @Test
    fun uikitCropFallbackIsLimitedToIntermediateDecodeBudget() {
        assertTrue(canUseUIKitCropFallback(ImageSize(4_000, 4_000)))
        assertFalse(canUseUIKitCropFallback(ImageSize(16_000_001, 1)))
    }

    @Test
    fun emptyNativeDataBridgeAvoidsElementZero() {
        val data = byteArrayOf().toNSData()

        assertEquals(0uL, data.length)
        assertTrue(data.toByteArray().isEmpty())
    }

    @Test
    fun cropRenderingUsesPrintOutputContract() = runBlocking {
        val sourceSize = ImageSize(400, 300)
        val outputSize = ImageSize(1_920, 1_080)

        val rendered = codec.renderCrop(
            createFourColorImage(sourceSize, EncodedImageFormat.PNG),
            CropRenderRequest(sourceSize, CropTransform(), outputSize),
        )

        assertEquals(outputSize.width, rendered.width)
        assertEquals(outputSize.height, rendered.height)
        assertColorNear(Color.RED, rendered.asSkiaBitmap().getColor(240, 180), tolerance = 8)
        assertColorNear(Color.YELLOW, rendered.asSkiaBitmap().getColor(1_680, 900), tolerance = 8)
    }

    @Test
    fun exifSixCropRendersPixelsInNormalizedOrientation() = runBlocking {
        val rawSize = ImageSize(400, 300)
        val orientedSize = ImageSize(300, 400)
        val jpeg = createFourColorImage(rawSize, EncodedImageFormat.JPEG).withExifOrientation(6)
        val outputSize = ImageSize(150, 200)

        val rendered = codec.renderCrop(
            jpeg,
            CropRenderRequest(orientedSize, CropTransform(), outputSize),
        ).asSkiaBitmap()

        assertEquals(outputSize.width, rendered.width)
        assertEquals(outputSize.height, rendered.height)
        assertColorNear(Color.BLUE, rendered.getColor(20, 20), tolerance = 35)
        assertColorNear(Color.RED, rendered.getColor(129, 20), tolerance = 35)
        assertColorNear(Color.YELLOW, rendered.getColor(20, 179), tolerance = 35)
        assertColorNear(Color.GREEN, rendered.getColor(129, 179), tolerance = 35)
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
        assertTrue(direct.highContrastPixels >= legacy.highContrastPixels + 200, "$direct vs $legacy")
        assertTrue(direct.transitions >= legacy.transitions + 100, "$direct vs $legacy")
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

private fun createEncodedImage(
    size: ImageSize,
    format: EncodedImageFormat,
): ByteArray = Surface.makeRasterN32Premul(size.width, size.height).use { surface ->
    surface.canvas.clear(Color.RED)
    surface.makeImageSnapshot().use { image ->
        requireNotNull(image.encodeToData(format, 100)).use { data -> data.bytes }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun createHeicsImage(size: ImageSize): ByteArray {
    val pngData = createEncodedImage(size, EncodedImageFormat.PNG).toCFData()
    try {
        val source = requireNotNull(CGImageSourceCreateWithData(pngData, null))
        try {
            val image = requireNotNull(CGImageSourceCreateImageAtIndex(source, 0u, null))
            try {
                val output = requireNotNull(CFDataCreateMutable(null, 0))
                try {
                    val type = requireNotNull(
                        CFStringCreateWithCString(null, "public.heics", kCFStringEncodingUTF8),
                    )
                    try {
                        val destination = requireNotNull(
                            CGImageDestinationCreateWithData(output, type, 1u, null),
                        )
                        try {
                            CGImageDestinationAddImage(destination, image, null)
                            check(CGImageDestinationFinalize(destination))
                            return output.toByteArray()
                        } finally {
                            CFRelease(destination)
                        }
                    } finally {
                        CFRelease(type)
                    }
                } finally {
                    CFRelease(output)
                }
            } finally {
                CGImageRelease(image)
            }
        } finally {
            CFRelease(source)
        }
    } finally {
        CFRelease(pngData)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toCFData() = usePinned { pinned ->
    CFDataCreate(null, pinned.addressOf(0).reinterpret(), size.toLong())
        ?: error("Unable to create test image data")
}

@OptIn(ExperimentalForeignApi::class)
private fun platform.CoreFoundation.CFDataRef.toByteArray(): ByteArray {
    val length = CFDataGetLength(this)
    val source = CFDataGetBytePtr(this)
    return ByteArray(length.toInt()).also { result ->
        result.usePinned { pinned ->
            memcpy(pinned.addressOf(0), source, length.toULong())
        }
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

private fun legacyStripeStats(preview: ImageBitmap, request: CropRenderRequest): StripeStats {
    val plan = CropRenderPlanner().plan(request)
    val transform = plan.sourceToOutput
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
