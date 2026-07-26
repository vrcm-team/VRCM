package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Color
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.EncodedOrigin
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

class DesktopPlatformImageCodec : PlatformImageCodec {
    override suspend fun decode(bytes: ByteArray, request: DecodeRequest): DecodedImage =
        withOwnedPlatformImageResult(
            dispatcher = Dispatchers.Default,
            ownedBitmap = DecodedImage::bitmap,
        ) {
            require(request.maxDimension > 0) { "maxDimension must be positive" }
            require(request.maxPixels > 0) { "maxPixels must be positive" }
            mapDesktopImageFailure(DesktopFailureOperation.DECODE) {
                val coroutineContext = currentCoroutineContext().also { it.ensureActive() }
                val result = withCodec(bytes) { codec, metadata ->
                    ensureDesktopRasterCapability(metadata.format, metadata.orientedSize)
                    val target = DecodeSizePlanner.plan(metadata.orientedSize, request)
                    coroutineContext.ensureActive()
                    check(target.pixelCount() <= PrintImageLimits.MAX_INTERMEDIATE_DECODE_PIXELS) {
                        "Preview target exceeds the bounded raster limit"
                    }
                    val bitmap = when (metadata.format) {
                        EncodedImageFormat.JPEG,
                        EncodedImageFormat.PNG -> renderImageIoPreview(bytes, metadata, target)
                        EncodedImageFormat.HEIF -> when (
                            planDesktopPreviewRaster(metadata.orientedSize, target)
                        ) {
                            DesktopRasterStrategy.DIRECT_CODEC -> {
                                val rawTarget = if (metadata.origin.swapsWidthHeight()) {
                                    ImageSize(target.height, target.width)
                                } else {
                                    target
                                }
                                Bitmap().use { decoded ->
                                    check(
                                        decoded.allocPixels(
                                            ImageInfo.makeN32Premul(rawTarget.width, rawTarget.height),
                                        ),
                                    ) { "Unable to allocate bounded preview bitmap" }
                                    codec.readPixels(decoded)
                                    coroutineContext.ensureActive()
                                    orientPreview(decoded, metadata.origin, target)
                                }
                            }
                            DesktopRasterStrategy.BOUNDED_ENCODED_IMAGE ->
                                renderEncodedPreview(bytes, metadata, target)
                            DesktopRasterStrategy.REJECT_UNSAFE_SOURCE ->
                                throw PrintImageFailure.DesktopRegionDecodeUnavailable
                        }
                        else -> throw PrintImageFailure.UnsupportedFormat()
                    }
                    DecodedImage(
                        bitmap = bitmap,
                        originalSize = metadata.orientedSize,
                    )
                }
                result.copy(
                    bitmap = handoffDesktopImageBitmap(result.bitmap) {
                        coroutineContext.ensureActive()
                    },
                )
            }
        }

    override suspend fun renderCrop(bytes: ByteArray, request: CropRenderRequest): ImageBitmap =
        withOwnedPlatformImageResult(
            dispatcher = Dispatchers.Default,
            ownedBitmap = { it },
        ) {
            mapDesktopImageFailure(DesktopFailureOperation.RENDER) {
                val coroutineContext = currentCoroutineContext().also { it.ensureActive() }
                val metadata = withCodec(bytes) { _, metadata -> metadata }
                coroutineContext.ensureActive()
                if (metadata.orientedSize != request.originalSize) {
                    throw PrintImageFailure.RenderFailed(
                        IllegalArgumentException(
                            "Original size ${request.originalSize} does not match " +
                                    "image size ${metadata.orientedSize}",
                        ),
                    )
                }
                ensureDesktopRasterCapability(metadata.format, metadata.orientedSize)
                val plan = CropRenderPlanner().plan(request)
                check(plan.outputSize.pixelCount() <= PrintImageLimits.MAX_INTERMEDIATE_DECODE_PIXELS) {
                    "Crop target exceeds the bounded raster limit"
                }
                val rendered = when (metadata.format) {
                    EncodedImageFormat.JPEG,
                    EncodedImageFormat.PNG -> renderImageIoCrop(bytes, metadata, plan)
                    EncodedImageFormat.HEIF -> {
                        if (planDesktopCropRaster(metadata.orientedSize) ==
                            DesktopRasterStrategy.REJECT_UNSAFE_SOURCE
                        ) {
                            throw PrintImageFailure.DesktopRegionDecodeUnavailable
                        }
                        renderEncodedCrop(bytes, metadata, plan)
                    }
                    else -> throw PrintImageFailure.UnsupportedFormat()
                }
                handoffDesktopImageBitmap(rendered) {
                    coroutineContext.ensureActive()
                }
            }
        }

    override suspend fun encodePng(bitmap: ImageBitmap): ByteArray =
        withContext(Dispatchers.Default) {
            mapDesktopImageFailure(DesktopFailureOperation.ENCODE) {
                currentCoroutineContext().ensureActive()
                val result = Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
                    requireNotNull(image.encodeToData(EncodedImageFormat.PNG, 100)).use { data ->
                        data.bytes
                    }
                }
                currentCoroutineContext().ensureActive()
                result
            }
        }

    private fun orientPreview(
        decoded: Bitmap,
        origin: EncodedOrigin,
        target: ImageSize,
    ): ImageBitmap = Surface.makeRasterN32Premul(target.width, target.height).use { surface ->
        surface.canvas.clear(Color.TRANSPARENT)
        Image.makeFromBitmap(decoded).use { image ->
            surface.canvas.concat(origin.toOrientedAffine(target).toSkiaMatrix())
            surface.canvas.drawImage(image, 0f, 0f)
        }
        surface.makeImageSnapshot().use { snapshot ->
            snapshot.toOwnedComposeImageBitmap()
        }
    }

    private fun renderImageIoPreview(
        bytes: ByteArray,
        metadata: ImageMetadata,
        target: ImageSize,
    ): ImageBitmap {
        val rawRegion = PixelRect(0, 0, metadata.rawSize.width, metadata.rawSize.height)
        val subsampling = boundedSubsampling(rawRegion)
        val decoded = readImageIoRegion(bytes, metadata, rawRegion, subsampling)
        return renderImageIoRaster(
            decoded = decoded,
            localToOutput = localRasterToRaw(
                region = rawRegion,
                decodedSize = ImageSize(decoded.width, decoded.height),
                subsampling = subsampling,
            )
                .then(metadata.origin.toOrientedAffine(metadata.orientedSize))
                .then(
                    AffineTransform(
                        scaleX = target.width.toDouble() / metadata.orientedSize.width,
                        skewX = 0.0,
                        translateX = 0.0,
                        skewY = 0.0,
                        scaleY = target.height.toDouble() / metadata.orientedSize.height,
                        translateY = 0.0,
                    ),
                ),
            outputSize = target,
        )
    }

    private fun renderImageIoCrop(
        bytes: ByteArray,
        metadata: ImageMetadata,
        plan: CropRenderPlan,
    ): ImageBitmap {
        val readPlan = planCropRegionRead(metadata, plan.visibleSourceBounds)
        val decoded = readImageIoRegion(
            bytes = bytes,
            metadata = metadata,
            region = readPlan.region,
            subsampling = readPlan.subsampling,
        )
        return renderImageIoRaster(
            decoded = decoded,
            localToOutput = localRasterToRaw(
                region = readPlan.region,
                decodedSize = ImageSize(decoded.width, decoded.height),
                subsampling = readPlan.subsampling,
            )
                .then(metadata.origin.toOrientedAffine(metadata.orientedSize))
                .then(plan.sourceToOutput),
            outputSize = plan.outputSize,
        )
    }

    private fun renderImageIoRaster(
        decoded: BufferedImage,
        localToOutput: AffineTransform,
        outputSize: ImageSize,
    ): ImageBitmap = decoded.toOwnedSkiaBitmap().use { decodedBitmap ->
        Surface.makeRasterN32Premul(outputSize.width, outputSize.height).use { surface ->
            surface.canvas.clear(Color.TRANSPARENT)
            Image.makeFromBitmap(decodedBitmap).use { image ->
                surface.canvas.concat(localToOutput.toSkiaMatrix())
                surface.canvas.drawImageRect(
                    image,
                    Rect.makeWH(decoded.width.toFloat(), decoded.height.toFloat()),
                    Rect.makeWH(decoded.width.toFloat(), decoded.height.toFloat()),
                    SamplingMode.CATMULL_ROM,
                    null,
                    true,
                )
            }
            surface.makeImageSnapshot().use { snapshot ->
                snapshot.toOwnedComposeImageBitmap()
            }
        }
    }

    private fun readImageIoRegion(
        bytes: ByteArray,
        metadata: ImageMetadata,
        region: PixelRect,
        subsampling: Int,
    ): BufferedImage = withImageReader(bytes, metadata.format) { reader ->
        check(reader.getWidth(0) == metadata.rawSize.width)
        check(reader.getHeight(0) == metadata.rawSize.height)
        val param = reader.defaultReadParam.apply {
            sourceRegion = Rectangle(region.left, region.top, region.width, region.height)
            setSourceSubsampling(subsampling, subsampling, 0, 0)
        }
        reader.read(0, param).also { decoded ->
            check(decoded.width.toLong() * decoded.height <=
                    PrintImageLimits.MAX_INTERMEDIATE_DECODE_PIXELS
            ) { "ImageIO decoded raster exceeds the bounded raster limit" }
        }
    }

    private inline fun <T> withImageReader(
        bytes: ByteArray,
        format: EncodedImageFormat,
        block: (ImageReader) -> T,
    ): T = ByteArrayInputStream(bytes).use { source ->
        val input = ImageIO.createImageInputStream(source)
            ?: throw PrintImageFailure.UnsupportedFormat()
        input.use {
            val formatName = when (format) {
                EncodedImageFormat.JPEG -> "JPEG"
                EncodedImageFormat.PNG -> "PNG"
                else -> throw PrintImageFailure.UnsupportedFormat()
            }
            val readers = ImageIO.getImageReadersByFormatName(formatName)
            if (!readers.hasNext()) throw PrintImageFailure.UnsupportedFormat()
            val reader = readers.next()
            try {
                reader.setInput(input, true, true)
                block(reader)
            } finally {
                reader.dispose()
            }
        }
    }

    private fun planCropRegionRead(
        metadata: ImageMetadata,
        visibleOriented: PixelRect,
    ): ImageIoReadPlan {
        val orientedToRaw = metadata.origin
            .toOrientedAffine(metadata.orientedSize)
            .inverse()
        val rawCore = orientedToRaw.mapBounds(visibleOriented, metadata.rawSize)
        var halo = IMAGE_IO_INTERPOLATION_HALO
        while (true) {
            val region = rawCore.expand(halo, metadata.rawSize)
            val subsampling = boundedSubsampling(region)
            val requiredHalo = max(IMAGE_IO_INTERPOLATION_HALO, subsampling * 2)
            if (requiredHalo == halo) return ImageIoReadPlan(region, subsampling)
            halo = requiredHalo
        }
    }

    private fun renderEncodedPreview(
        bytes: ByteArray,
        metadata: ImageMetadata,
        target: ImageSize,
    ): ImageBitmap = Surface.makeRasterN32Premul(target.width, target.height).use { surface ->
        surface.canvas.clear(Color.TRANSPARENT)
        Image.makeFromEncoded(bytes).use { image ->
            // Encoded Skia images apply Codec.encodedOrigin before exposing their dimensions.
            check(image.width == metadata.orientedSize.width)
            check(image.height == metadata.orientedSize.height)
            surface.canvas.drawImageRect(
                image,
                Rect.makeWH(target.width.toFloat(), target.height.toFloat()),
            )
        }
        surface.makeImageSnapshot().use { snapshot ->
            snapshot.toOwnedComposeImageBitmap()
        }
    }

    private fun renderEncodedCrop(
        bytes: ByteArray,
        metadata: ImageMetadata,
        plan: CropRenderPlan,
    ): ImageBitmap = Surface.makeRasterN32Premul(
        plan.outputSize.width,
        plan.outputSize.height,
    ).use { surface ->
        surface.canvas.clear(Color.TRANSPARENT)
        Image.makeFromEncoded(bytes).use { image ->
            // The source is already origin-normalized, so the shared crop affine consumes it directly.
            check(image.width == metadata.orientedSize.width)
            check(image.height == metadata.orientedSize.height)
            val visible = plan.visibleSourceBounds
            val anchorX = ((visible.left.toDouble() + visible.right) / 2.0)
                .coerceIn(0.0, metadata.orientedSize.width.toDouble())
            val anchorY = ((visible.top.toDouble() + visible.bottom) / 2.0)
                .coerceIn(0.0, metadata.orientedSize.height.toDouble())
            val localToOutput = plan.sourceToOutput.rebaseForDesktopSkia(anchorX, anchorY)

            surface.canvas.concat(localToOutput.matrix)
            surface.canvas.drawImage(image, localToOutput.drawX, localToOutput.drawY)
        }
        surface.makeImageSnapshot().use { snapshot ->
            snapshot.toOwnedComposeImageBitmap()
        }
    }

    private inline fun <T> withCodec(
        bytes: ByteArray,
        block: (Codec, ImageMetadata) -> T,
    ): T = Data.makeFromBytes(bytes).use { data ->
        val codec = try {
            Codec.makeFromData(data)
        } catch (cause: IllegalArgumentException) {
            throw PrintImageFailure.UnsupportedFormat(cause)
        }
        codec.use {
            if (codec.encodedImageFormat !in SUPPORTED_FORMATS) {
                throw PrintImageFailure.UnsupportedFormat()
            }
            val rawWidth = codec.size.x
            val rawHeight = codec.size.y
            if (rawWidth <= 0 || rawHeight <= 0) {
                throw PrintImageFailure.UnsupportedFormat()
            }
            if (rawWidth.toLong() * rawHeight > PrintImageLimits.MAX_PIXELS) {
                throw PrintImageFailure.ImageDimensionsTooLarge
            }
            val origin = codec.encodedOrigin
            val rawSize = ImageSize(rawWidth, rawHeight)
            val orientedSize = if (origin.swapsWidthHeight()) {
                ImageSize(rawHeight, rawWidth)
            } else {
                rawSize
            }
            block(
                codec,
                ImageMetadata(
                    rawSize = rawSize,
                    orientedSize = orientedSize,
                    origin = origin,
                    format = codec.encodedImageFormat,
                ),
            )
        }
    }

    private data class ImageMetadata(
        val rawSize: ImageSize,
        val orientedSize: ImageSize,
        val origin: EncodedOrigin,
        val format: EncodedImageFormat,
    )

    private companion object {
        val SUPPORTED_FORMATS = setOf(
            EncodedImageFormat.JPEG,
            EncodedImageFormat.PNG,
            EncodedImageFormat.HEIF,
        )

        const val IMAGE_IO_INTERPOLATION_HALO = 2
    }
}

private data class ImageIoReadPlan(
    val region: PixelRect,
    val subsampling: Int,
)

private fun boundedSubsampling(region: PixelRect): Int {
    val limit = PrintImageLimits.MAX_INTERMEDIATE_DECODE_PIXELS
    val ratio = region.width.toLong() * region.height.toDouble() / limit
    var subsampling = max(1, ceil(sqrt(ratio)).toInt())
    while (
        ceilDiv(region.width, subsampling).toLong() *
        ceilDiv(region.height, subsampling) > limit
    ) {
        subsampling++
    }
    return subsampling
}

private fun ceilDiv(value: Int, divisor: Int): Int =
    ((value.toLong() + divisor - 1) / divisor).toInt()

internal fun localRasterToRaw(
    region: PixelRect,
    decodedSize: ImageSize,
    subsampling: Int,
): AffineTransform {
    require(subsampling > 0) { "subsampling must be positive" }
    check(decodedSize.width == ceilDiv(region.width, subsampling))
    check(decodedSize.height == ceilDiv(region.height, subsampling))
    // ImageIO samples raw pixel centers at region origin + i * subsampling + 0.5.
    val pixelCenterOffset = (1.0 - subsampling) / 2.0
    return AffineTransform(
        scaleX = subsampling.toDouble(),
        skewX = 0.0,
        translateX = region.left + pixelCenterOffset,
        skewY = 0.0,
        scaleY = subsampling.toDouble(),
        translateY = region.top + pixelCenterOffset,
    )
}

private fun AffineTransform.then(next: AffineTransform): AffineTransform = AffineTransform(
    scaleX = next.scaleX * scaleX + next.skewX * skewY,
    skewX = next.scaleX * skewX + next.skewX * scaleY,
    translateX = next.scaleX * translateX + next.skewX * translateY + next.translateX,
    skewY = next.skewY * scaleX + next.scaleY * skewY,
    scaleY = next.skewY * skewX + next.scaleY * scaleY,
    translateY = next.skewY * translateX + next.scaleY * translateY + next.translateY,
)

private fun AffineTransform.inverse(): AffineTransform {
    val determinant = scaleX * scaleY - skewX * skewY
    check(determinant != 0.0) { "Image orientation transform must be invertible" }
    val inverseScaleX = scaleY / determinant
    val inverseSkewX = -skewX / determinant
    val inverseSkewY = -skewY / determinant
    val inverseScaleY = scaleX / determinant
    return AffineTransform(
        scaleX = inverseScaleX,
        skewX = inverseSkewX,
        translateX = -inverseScaleX * translateX - inverseSkewX * translateY,
        skewY = inverseSkewY,
        scaleY = inverseScaleY,
        translateY = -inverseSkewY * translateX - inverseScaleY * translateY,
    )
}

private fun AffineTransform.mapBounds(
    bounds: PixelRect,
    limit: ImageSize,
): PixelRect {
    val corners = listOf(
        map(bounds.left.toDouble(), bounds.top.toDouble()),
        map(bounds.right.toDouble(), bounds.top.toDouble()),
        map(bounds.left.toDouble(), bounds.bottom.toDouble()),
        map(bounds.right.toDouble(), bounds.bottom.toDouble()),
    )
    val left = floor(corners.minOf(AffinePoint::x)).toInt().coerceIn(0, limit.width)
    val top = floor(corners.minOf(AffinePoint::y)).toInt().coerceIn(0, limit.height)
    val right = ceil(corners.maxOf(AffinePoint::x)).toInt().coerceIn(0, limit.width)
    val bottom = ceil(corners.maxOf(AffinePoint::y)).toInt().coerceIn(0, limit.height)
    return PixelRect(
        left = if (left == limit.width) left - 1 else left,
        top = if (top == limit.height) top - 1 else top,
        right = if (right <= left) (left + 1).coerceAtMost(limit.width) else right,
        bottom = if (bottom <= top) (top + 1).coerceAtMost(limit.height) else bottom,
    )
}

private fun PixelRect.expand(pixels: Int, limit: ImageSize): PixelRect = PixelRect(
    left = (left - pixels).coerceAtLeast(0),
    top = (top - pixels).coerceAtLeast(0),
    right = (right + pixels).coerceAtMost(limit.width),
    bottom = (bottom + pixels).coerceAtMost(limit.height),
)

private fun BufferedImage.toOwnedSkiaBitmap(): Bitmap {
    val argb = IntArray(width)
    val bgra = ByteArray(width * height * 4)
    var destination = 0
    repeat(height) { y ->
        getRGB(0, y, width, 1, argb, 0, width)
        repeat(width) { x ->
            val pixel = argb[x]
            val alpha = pixel ushr 24 and 0xFF
            val red = pixel ushr 16 and 0xFF
            val green = pixel ushr 8 and 0xFF
            val blue = pixel and 0xFF
            bgra[destination++] = premultiply(blue, alpha).toByte()
            bgra[destination++] = premultiply(green, alpha).toByte()
            bgra[destination++] = premultiply(red, alpha).toByte()
            bgra[destination++] = alpha.toByte()
        }
    }
    val bitmap = Bitmap()
    var handedOff = false
    try {
        check(
            bitmap.installPixels(
                ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.PREMUL),
                bgra,
                width * 4,
            ),
        ) { "Unable to install bounded ImageIO pixels" }
        bitmap.setImmutable()
        handedOff = true
        return bitmap
    } finally {
        if (!handedOff) bitmap.close()
    }
}

private fun premultiply(component: Int, alpha: Int): Int =
    (component * alpha + 127) / 255

private fun EncodedOrigin.toOrientedAffine(orientedSize: ImageSize): AffineTransform = when (this) {
    EncodedOrigin.TOP_LEFT -> AffineTransform(1.0, 0.0, 0.0, 0.0, 1.0, 0.0)
    EncodedOrigin.TOP_RIGHT -> AffineTransform(
        -1.0,
        0.0,
        orientedSize.width.toDouble(),
        0.0,
        1.0,
        0.0,
    )
    EncodedOrigin.BOTTOM_RIGHT -> AffineTransform(
        -1.0,
        0.0,
        orientedSize.width.toDouble(),
        0.0,
        -1.0,
        orientedSize.height.toDouble(),
    )
    EncodedOrigin.BOTTOM_LEFT -> AffineTransform(
        1.0,
        0.0,
        0.0,
        0.0,
        -1.0,
        orientedSize.height.toDouble(),
    )
    EncodedOrigin.LEFT_TOP -> AffineTransform(0.0, 1.0, 0.0, 1.0, 0.0, 0.0)
    EncodedOrigin.RIGHT_TOP -> AffineTransform(
        0.0,
        -1.0,
        orientedSize.width.toDouble(),
        1.0,
        0.0,
        0.0,
    )
    EncodedOrigin.RIGHT_BOTTOM -> AffineTransform(
        0.0,
        -1.0,
        orientedSize.width.toDouble(),
        -1.0,
        0.0,
        orientedSize.height.toDouble(),
    )
    EncodedOrigin.LEFT_BOTTOM -> AffineTransform(
        0.0,
        1.0,
        0.0,
        -1.0,
        0.0,
        orientedSize.height.toDouble(),
    )
    EncodedOrigin.UNUSED -> throw PrintImageFailure.UnsupportedFormat()
}

internal data class DesktopSkiaRebase(
    val matrix: Matrix33,
    val drawX: Float,
    val drawY: Float,
)

internal fun AffineTransform.rebaseForDesktopSkia(
    anchorX: Double,
    anchorY: Double,
): DesktopSkiaRebase {
    val floatAnchorX = anchorX.toFloat()
    val floatAnchorY = anchorY.toFloat()
    val canonicalAnchorX = floatAnchorX.toDouble()
    val canonicalAnchorY = floatAnchorY.toDouble()
    val rebased = copy(
        translateX = scaleX * canonicalAnchorX + skewX * canonicalAnchorY + translateX,
        translateY = skewY * canonicalAnchorX + scaleY * canonicalAnchorY + translateY,
    )
    return DesktopSkiaRebase(
        matrix = rebased.toSkiaMatrix(),
        drawX = -floatAnchorX,
        drawY = -floatAnchorY,
    )
}

private fun AffineTransform.toSkiaMatrix(): Matrix33 = Matrix33(
    scaleX.toFloat(),
    skewX.toFloat(),
    translateX.toFloat(),
    skewY.toFloat(),
    scaleY.toFloat(),
    translateY.toFloat(),
    0f,
    0f,
    1f,
)

// The returned ImageBitmap owns this Bitmap; every temporary Skia object remains caller-closed.
private fun Image.toOwnedComposeImageBitmap(): ImageBitmap =
    Bitmap.makeFromImage(this).asComposeImageBitmap()

internal inline fun handoffDesktopImageBitmap(
    bitmap: ImageBitmap,
    checkCancellation: () -> Unit,
): ImageBitmap = try {
    checkCancellation()
    bitmap
} catch (cause: CancellationException) {
    bitmap.asSkiaBitmap().close()
    throw cause
}

internal enum class DesktopRasterStrategy {
    DIRECT_CODEC,
    BOUNDED_ENCODED_IMAGE,
    REJECT_UNSAFE_SOURCE,
}

internal fun planDesktopPreviewRaster(
    source: ImageSize,
    target: ImageSize,
): DesktopRasterStrategy = when {
    source.pixelCount() > PrintImageLimits.MAX_INTERMEDIATE_DECODE_PIXELS ->
        DesktopRasterStrategy.REJECT_UNSAFE_SOURCE
    source == target -> DesktopRasterStrategy.DIRECT_CODEC
    else -> DesktopRasterStrategy.BOUNDED_ENCODED_IMAGE
}

internal fun planDesktopCropRaster(source: ImageSize): DesktopRasterStrategy =
    if (source.pixelCount() <= PrintImageLimits.MAX_INTERMEDIATE_DECODE_PIXELS) {
        DesktopRasterStrategy.BOUNDED_ENCODED_IMAGE
    } else {
        DesktopRasterStrategy.REJECT_UNSAFE_SOURCE
    }

private fun ImageSize.pixelCount(): Long = width.toLong() * height

internal fun ensureDesktopRasterCapability(
    format: EncodedImageFormat,
    source: ImageSize,
) {
    if (
        format == EncodedImageFormat.HEIF &&
        source.pixelCount() > PrintImageLimits.MAX_INTERMEDIATE_DECODE_PIXELS
    ) {
        throw PrintImageFailure.DesktopRegionDecodeUnavailable
    }
}

internal enum class DesktopFailureOperation {
    DECODE,
    RENDER,
    ENCODE,
}

internal inline fun <T> mapDesktopImageFailure(
    operation: DesktopFailureOperation,
    block: () -> T,
): T = try {
    block()
} catch (cause: CancellationException) {
    throw cause
} catch (failure: PrintImageFailure) {
    throw failure
} catch (cause: Exception) {
    throw when (operation) {
        DesktopFailureOperation.DECODE -> PrintImageFailure.DecodeFailed(cause)
        DesktopFailureOperation.RENDER -> PrintImageFailure.RenderFailed(cause)
        DesktopFailureOperation.ENCODE -> PrintImageFailure.EncodeFailed(cause)
    }
}
