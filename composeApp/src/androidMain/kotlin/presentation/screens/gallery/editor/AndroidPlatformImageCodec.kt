package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

class AndroidPlatformImageCodec : PlatformImageCodec {
    override suspend fun decode(bytes: ByteArray, request: DecodeRequest): DecodedImage =
        withOwnedPlatformImageResult(
            dispatcher = Dispatchers.IO,
            ownedBitmap = DecodedImage::bitmap,
        ) {
            require(request.maxDimension > 0) { "maxDimension must be positive" }
            require(request.maxPixels > 0) { "maxPixels must be positive" }
            val coroutineContext = currentCoroutineContext().also { it.ensureActive() }
            mapAndroidImageFailure(AndroidFailureOperation.DECODE) {
                val metadata = inspect(bytes)
                coroutineContext.ensureActive()
                val target = DecodeSizePlanner.plan(metadata.orientedSize, request)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = calculatePreviewSampleSize(
                        metadata.rawSize,
                        metadata.orientation,
                        target,
                        request,
                    )
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                coroutineContext.ensureActive()
                var ownedBitmap: Bitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    ?: throw PrintImageFailure.UnsupportedFormat()
                try {
                    coroutineContext.ensureActive()
                    ownedBitmap = applyOrientation(
                        requireNotNull(ownedBitmap),
                        metadata.orientation,
                    )
                    coroutineContext.ensureActive()
                    ownedBitmap = scaleDown(requireNotNull(ownedBitmap), target)
                    coroutineContext.ensureActive()
                    val bitmap = handoffAndroidImageBitmap(
                        requireNotNull(ownedBitmap).asImageBitmap(),
                    ) {
                        coroutineContext.ensureActive()
                    }
                    ownedBitmap = null
                    DecodedImage(
                        bitmap = bitmap,
                        originalSize = metadata.orientedSize,
                    )
                } finally {
                    ownedBitmap?.recycleIfNeeded()
                }
            }
        }

    override suspend fun renderCrop(bytes: ByteArray, request: CropRenderRequest): ImageBitmap =
        withOwnedPlatformImageResult(
            dispatcher = Dispatchers.IO,
            ownedBitmap = { it },
        ) {
            val coroutineContext = currentCoroutineContext().also { it.ensureActive() }
            mapAndroidImageFailure(AndroidFailureOperation.RENDER) {
                val format = bytes.detectFormat() ?: throw PrintImageFailure.UnsupportedFormat()
                if (format == ImageFormat.HEIF && Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                    throw PrintImageFailure.UnsupportedFormat()
                }
                val metadata = inspect(bytes, format)
                coroutineContext.ensureActive()
                if (metadata.orientedSize != request.originalSize) {
                    throw PrintImageFailure.RenderFailed(
                        IllegalArgumentException(
                            "Original size ${request.originalSize} does not match " +
                                    "image size ${metadata.orientedSize}",
                        ),
                    )
                }
                val plan = CropRenderPlanner().plan(request)
                coroutineContext.ensureActive()
                var rendered: Bitmap? = if (metadata.format == ImageFormat.HEIF) {
                    renderHeifCrop(bytes, metadata, plan)
                } else {
                    renderRegionCrop(bytes, metadata, plan)
                }
                try {
                    coroutineContext.ensureActive()
                    val bitmap = handoffAndroidImageBitmap(
                        requireNotNull(rendered).asImageBitmap(),
                    ) {
                        coroutineContext.ensureActive()
                    }
                    rendered = null
                    bitmap
                } finally {
                    rendered?.recycleIfNeeded()
                }
            }
        }

    override suspend fun encodePng(bitmap: ImageBitmap): ByteArray =
        withContext(Dispatchers.IO) {
            val coroutineContext = currentCoroutineContext().also { it.ensureActive() }
            mapAndroidImageFailure(AndroidFailureOperation.ENCODE) {
                val bytes = ByteArrayOutputStream().use { output ->
                    if (!bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)) {
                        throw PrintImageFailure.EncodeFailed()
                    }
                    output.toByteArray()
                }
                coroutineContext.ensureActive()
                bytes
            }
        }

    private fun inspect(bytes: ByteArray, knownFormat: ImageFormat? = null): ImageMetadata {
        val format = knownFormat ?: bytes.detectFormat() ?: throw PrintImageFailure.UnsupportedFormat()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val rawWidth = bounds.outWidth
        val rawHeight = bounds.outHeight
        if (rawWidth <= 0 || rawHeight <= 0) {
            throw PrintImageFailure.UnsupportedFormat()
        }
        if (rawWidth.toLong() * rawHeight > PrintImageLimits.MAX_PIXELS) {
            throw PrintImageFailure.ImageDimensionsTooLarge
        }

        val orientation = readOrientation(bytes)
        val rawSize = ImageSize(rawWidth, rawHeight)
        val orientedSize = if (orientation.swapsDimensions()) {
            ImageSize(rawHeight, rawWidth)
        } else {
            rawSize
        }
        return ImageMetadata(format, rawSize, orientedSize, orientation)
    }

    private fun readOrientation(bytes: ByteArray): Int {
        return readAndroidImageOrientation {
            ByteArrayInputStream(bytes).use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }
    }

    private fun applyOrientation(source: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    setRotate(90f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    setRotate(-90f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            }
        }
        if (matrix.isIdentity) return source
        return Bitmap.createBitmap(
            source,
            0,
            0,
            source.width,
            source.height,
            matrix,
            true,
        ).also {
            if (it !== source) source.recycleIfNeeded()
        }
    }

    private fun scaleDown(source: Bitmap, target: ImageSize): Bitmap {
        if (source.width <= target.width && source.height <= target.height) return source
        val outputSize = if (source.width >= target.width && source.height >= target.height) {
            target
        } else {
            val scale = min(
                target.width.toDouble() / source.width,
                target.height.toDouble() / source.height,
            ).coerceAtMost(1.0)
            ImageSize(
                width = (source.width * scale).roundToInt().coerceAtLeast(1),
                height = (source.height * scale).roundToInt().coerceAtLeast(1),
            )
        }
        return Bitmap.createScaledBitmap(
            source,
            outputSize.width,
            outputSize.height,
            true,
        ).also {
            if (it !== source) source.recycleIfNeeded()
        }
    }

    @Suppress("DEPRECATION")
    private fun renderRegionCrop(
        bytes: ByteArray,
        metadata: ImageMetadata,
        plan: CropRenderPlan,
    ): Bitmap {
        val decoder = BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false)
        var regionBitmap: Bitmap? = null
        try {
            val rawToOriented = rawToOriented(metadata.rawSize, metadata.orientation)
            val sampleSize = cropSampleSize(plan.sourceToOutput)
            val rawBounds = rawRegionBounds(
                orientedBounds = plan.visibleSourceBounds,
                orientedToRaw = rawToOriented.inverse(),
                rawSize = metadata.rawSize,
                halo = sampleSize,
            )
            regionBitmap = decoder.decodeRegion(
                rawBounds.toAndroidRect(),
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            ) ?: throw IllegalStateException("Unable to decode image crop")
            val regionToRaw = AffineTransform(
                scaleX = rawBounds.width.toDouble() / regionBitmap.width,
                skewX = 0.0,
                translateX = rawBounds.left.toDouble(),
                skewY = 0.0,
                scaleY = rawBounds.height.toDouble() / regionBitmap.height,
                translateY = rawBounds.top.toDouble(),
            )
            val regionToOutput = plan.sourceToOutput
                .compose(rawToOriented)
                .compose(regionToRaw)
            return drawCrop(regionBitmap, regionToOutput, plan.outputSize)
        } finally {
            regionBitmap?.recycle()
            decoder.recycle()
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun renderHeifCrop(
        bytes: ByteArray,
        metadata: ImageMetadata,
        plan: CropRenderPlan,
    ): Bitmap {
        val sample = cropSampleSize(plan.sourceToOutput)
        val orientedBounds = plan.visibleSourceBounds.expanded(metadata.orientedSize, sample)
        // Crop coordinates follow the post-target normalized image. This bounds the returned
        // bitmap allocation; ImageDecoder's internal peak decode strategy remains platform-owned.
        val targetFullWidth = ceilDiv(metadata.orientedSize.width, sample)
        val targetFullHeight = ceilDiv(metadata.orientedSize.height, sample)
        val targetCrop = Rect(
            floor(orientedBounds.left.toDouble() * targetFullWidth / metadata.orientedSize.width)
                .toInt()
                .coerceIn(0, targetFullWidth - 1),
            floor(orientedBounds.top.toDouble() * targetFullHeight / metadata.orientedSize.height)
                .toInt()
                .coerceIn(0, targetFullHeight - 1),
            ceil(orientedBounds.right.toDouble() * targetFullWidth / metadata.orientedSize.width)
                .toInt()
                .coerceIn(1, targetFullWidth),
            ceil(orientedBounds.bottom.toDouble() * targetFullHeight / metadata.orientedSize.height)
                .toInt()
                .coerceIn(1, targetFullHeight),
        )
        var crop: Bitmap? = null
        try {
            crop = ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(ByteBuffer.wrap(bytes)),
            ) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSize(targetFullWidth, targetFullHeight)
                decoder.crop = targetCrop
            }
            val orientedLeft = targetCrop.left.toDouble() *
                    metadata.orientedSize.width / targetFullWidth
            val orientedTop = targetCrop.top.toDouble() *
                    metadata.orientedSize.height / targetFullHeight
            val orientedWidth = targetCrop.width().toDouble() *
                    metadata.orientedSize.width / targetFullWidth
            val orientedHeight = targetCrop.height().toDouble() *
                    metadata.orientedSize.height / targetFullHeight
            val cropToOriented = AffineTransform(
                scaleX = orientedWidth / crop.width,
                skewX = 0.0,
                translateX = orientedLeft,
                skewY = 0.0,
                scaleY = orientedHeight / crop.height,
                translateY = orientedTop,
            )
            return drawCrop(crop, plan.sourceToOutput.compose(cropToOriented), plan.outputSize)
        } finally {
            crop?.recycle()
        }
    }

    private fun drawCrop(source: Bitmap, transform: AffineTransform, outputSize: ImageSize): Bitmap {
        val output = Bitmap.createBitmap(
            outputSize.width,
            outputSize.height,
            Bitmap.Config.ARGB_8888,
        )
        var handedOff = false
        try {
            val matrix = Matrix().apply {
                setValues(
                    floatArrayOf(
                        transform.scaleX.toFloat(),
                        transform.skewX.toFloat(),
                        transform.translateX.toFloat(),
                        transform.skewY.toFloat(),
                        transform.scaleY.toFloat(),
                        transform.translateY.toFloat(),
                        0f,
                        0f,
                        1f,
                    ),
                )
            }
            val paint = Paint(
                Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG,
            )
            Canvas(output).drawBitmap(source, matrix, paint)
            handedOff = true
            return output
        } finally {
            if (!handedOff) output.recycleIfNeeded()
        }
    }

    private fun rawRegionBounds(
        orientedBounds: PixelRect,
        orientedToRaw: AffineTransform,
        rawSize: ImageSize,
        halo: Int,
    ): PixelRect {
        val corners = listOf(
            orientedToRaw.map(orientedBounds.left.toDouble(), orientedBounds.top.toDouble()),
            orientedToRaw.map(orientedBounds.right.toDouble(), orientedBounds.top.toDouble()),
            orientedToRaw.map(orientedBounds.left.toDouble(), orientedBounds.bottom.toDouble()),
            orientedToRaw.map(orientedBounds.right.toDouble(), orientedBounds.bottom.toDouble()),
        )
        return PixelRect(
            left = (floor(corners.minOf { it.x }).toLong() - halo)
                .coerceAtLeast(0L)
                .toInt(),
            top = (floor(corners.minOf { it.y }).toLong() - halo)
                .coerceAtLeast(0L)
                .toInt(),
            right = (ceil(corners.maxOf { it.x }).toLong() + halo)
                .coerceAtMost(rawSize.width.toLong())
                .toInt(),
            bottom = (ceil(corners.maxOf { it.y }).toLong() + halo)
                .coerceAtMost(rawSize.height.toLong())
                .toInt(),
        )
    }

    private fun rawToOriented(rawSize: ImageSize, orientation: Int): AffineTransform =
        when (orientation) {
            2 -> AffineTransform(-1.0, 0.0, rawSize.width.toDouble(), 0.0, 1.0, 0.0)
            3 -> AffineTransform(
                -1.0,
                0.0,
                rawSize.width.toDouble(),
                0.0,
                -1.0,
                rawSize.height.toDouble(),
            )
            4 -> AffineTransform(1.0, 0.0, 0.0, 0.0, -1.0, rawSize.height.toDouble())
            5 -> AffineTransform(0.0, 1.0, 0.0, 1.0, 0.0, 0.0)
            6 -> AffineTransform(0.0, -1.0, rawSize.height.toDouble(), 1.0, 0.0, 0.0)
            7 -> AffineTransform(
                0.0,
                -1.0,
                rawSize.height.toDouble(),
                -1.0,
                0.0,
                rawSize.width.toDouble(),
            )
            8 -> AffineTransform(0.0, 1.0, 0.0, -1.0, 0.0, rawSize.width.toDouble())
            else -> IDENTITY_AFFINE
        }

    private fun ByteArray.detectFormat(): ImageFormat? = when {
        hasPrefix(JPEG_SIGNATURE) -> ImageFormat.JPEG
        hasPrefix(PNG_SIGNATURE) -> ImageFormat.PNG
        hasSupportedHeifBrand() -> ImageFormat.HEIF
        else -> null
    }

    private fun ByteArray.hasPrefix(signature: ByteArray): Boolean =
        size >= signature.size && signature.indices.all { this[it] == signature[it] }

    private fun ByteArray.hasSupportedHeifBrand(): Boolean {
        if (size < 12 || copyOfRange(4, 8).decodeToString() != "ftyp") return false
        var offset = 8
        val limit = minOf(size, 64)
        while (offset + 4 <= limit) {
            if (copyOfRange(offset, offset + 4).decodeToString() in HEIF_BRANDS) return true
            offset += 4
        }
        return false
    }

    private companion object {
        val JPEG_SIGNATURE = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
        val HEIF_BRANDS = setOf(
            "heic",
            "heix",
            "hevc",
            "hevx",
            "heim",
            "heis",
            "mif1",
            "msf1",
        )
    }
}

private enum class ImageFormat {
    JPEG,
    PNG,
    HEIF,
}

private data class ImageMetadata(
    val format: ImageFormat,
    val rawSize: ImageSize,
    val orientedSize: ImageSize,
    val orientation: Int,
)

internal inline fun handoffAndroidImageBitmap(
    bitmap: ImageBitmap,
    checkCancellation: () -> Unit,
): ImageBitmap = try {
    checkCancellation()
    bitmap
} catch (cause: CancellationException) {
    releaseAndroidImageBitmapAfterFailure(bitmap, cause)
    throw cause
} catch (cause: Exception) {
    releaseAndroidImageBitmapAfterFailure(bitmap, cause)
    throw cause
} catch (cause: Error) {
    releaseAndroidImageBitmapAfterFailure(bitmap, cause)
    throw cause
}

internal inline fun readAndroidImageOrientation(reader: () -> Int): Int {
    val orientation = try {
        reader()
    } catch (cause: CancellationException) {
        throw cause
    } catch (_: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }
    return orientation.takeIf { it in 1..8 } ?: ExifInterface.ORIENTATION_NORMAL
}

private fun releaseAndroidImageBitmapAfterFailure(
    bitmap: ImageBitmap,
    primaryFailure: Throwable,
) {
    try {
        releasePlatformImageBitmap(bitmap)
    } catch (releaseFailure: CancellationException) {
        primaryFailure.addAndroidReleaseFailure(releaseFailure)
    } catch (releaseFailure: Exception) {
        primaryFailure.addAndroidReleaseFailure(releaseFailure)
    } catch (releaseFailure: Error) {
        primaryFailure.addAndroidReleaseFailure(releaseFailure)
    }
}

private fun Throwable.addAndroidReleaseFailure(releaseFailure: Throwable) {
    if (releaseFailure !== this) addSuppressed(releaseFailure)
}

internal enum class AndroidFailureOperation {
    DECODE,
    RENDER,
    ENCODE,
}

internal inline fun <T> mapAndroidImageFailure(
    operation: AndroidFailureOperation,
    block: () -> T,
): T = try {
    block()
} catch (cause: CancellationException) {
    throw cause
} catch (failure: PrintImageFailure) {
    throw failure
} catch (cause: Exception) {
    throw when (operation) {
        AndroidFailureOperation.DECODE -> PrintImageFailure.DecodeFailed(cause)
        AndroidFailureOperation.RENDER -> PrintImageFailure.RenderFailed(cause)
        AndroidFailureOperation.ENCODE -> PrintImageFailure.EncodeFailed(cause)
    }
}

private fun Bitmap.recycleIfNeeded() {
    if (!isRecycled) recycle()
}

internal fun calculatePreviewSampleSize(
    rawSize: ImageSize,
    orientation: Int,
    target: ImageSize,
    request: DecodeRequest,
): Int {
    fun sizeAt(sample: Int): ImageSize {
        val sampledRawWidth = ceilDiv(rawSize.width, sample)
        val sampledRawHeight = ceilDiv(rawSize.height, sample)
        return if (orientation.swapsDimensions()) {
            ImageSize(sampledRawHeight, sampledRawWidth)
        } else {
            ImageSize(sampledRawWidth, sampledRawHeight)
        }
    }

    var sample = 1
    var sampledSize = sizeAt(sample)
    while (sampledSize.width.toLong() * sampledSize.height > request.maxPixels) {
        check(sample <= Int.MAX_VALUE / 2) { "Unable to calculate a bounded sample size" }
        sample *= 2
        sampledSize = sizeAt(sample)
    }

    while (sample <= Int.MAX_VALUE / 2) {
        val nextSample = sample * 2
        val nextSize = sizeAt(nextSample)
        if (nextSize.width < target.width || nextSize.height < target.height) break
        sample = nextSample
    }
    return sample
}

private fun cropSampleSize(transform: AffineTransform): Int {
    val sourceToOutputScale = hypot(transform.scaleX, transform.skewY)
    var sample = 1
    while (sample <= Int.MAX_VALUE / 2 && sample.toDouble() * 2 * sourceToOutputScale <= 1.0) {
        sample *= 2
    }
    return sample
}

private fun Int.swapsDimensions(): Boolean = this in 5..8

private fun ceilDiv(value: Int, divisor: Int): Int =
    ((value.toLong() + divisor - 1) / divisor).toInt()

private fun PixelRect.toAndroidRect(): Rect = Rect(left, top, right, bottom)

private fun PixelRect.expanded(size: ImageSize, pixels: Int): PixelRect = PixelRect(
    left = (left.toLong() - pixels).coerceAtLeast(0L).toInt(),
    top = (top.toLong() - pixels).coerceAtLeast(0L).toInt(),
    right = (right.toLong() + pixels).coerceAtMost(size.width.toLong()).toInt(),
    bottom = (bottom.toLong() + pixels).coerceAtMost(size.height.toLong()).toInt(),
)

private fun AffineTransform.compose(right: AffineTransform): AffineTransform = AffineTransform(
    scaleX = scaleX * right.scaleX + skewX * right.skewY,
    skewX = scaleX * right.skewX + skewX * right.scaleY,
    translateX = scaleX * right.translateX + skewX * right.translateY + translateX,
    skewY = skewY * right.scaleX + scaleY * right.skewY,
    scaleY = skewY * right.skewX + scaleY * right.scaleY,
    translateY = skewY * right.translateX + scaleY * right.translateY + translateY,
)

private fun AffineTransform.inverse(): AffineTransform {
    val determinant = scaleX * scaleY - skewX * skewY
    require(determinant != 0.0) { "Transform is not invertible" }
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

private val IDENTITY_AFFINE = AffineTransform(1.0, 0.0, 0.0, 0.0, 1.0, 0.0)
