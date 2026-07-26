package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.impl.use
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDictionaryCreate
import platform.CoreFoundation.CFDictionaryGetValue
import platform.CoreFoundation.CFGetTypeID
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFNumberGetTypeID
import platform.CoreFoundation.CFNumberGetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringGetCString
import platform.CoreFoundation.CFStringGetLength
import platform.CoreFoundation.CFStringGetMaximumSizeForEncoding
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFNumberIntType
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreGraphics.CGAffineTransformMake
import platform.CoreGraphics.CGContextConcatCTM
import platform.CoreGraphics.CGContextSetInterpolationQuality
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreGraphics.kCGInterpolationHigh
import platform.CoreImage.CIContext
import platform.CoreImage.CIImage
import platform.CoreImage.createCGImage
import platform.ImageIO.CGImageSourceCopyPropertiesAtIndex
import platform.ImageIO.CGImageSourceCreateThumbnailAtIndex
import platform.ImageIO.CGImageSourceCreateWithData
import platform.ImageIO.CGImageSourceGetType
import platform.ImageIO.CGImageSourceRef
import platform.ImageIO.kCGImagePropertyOrientation
import platform.ImageIO.kCGImagePropertyPixelHeight
import platform.ImageIO.kCGImagePropertyPixelWidth
import platform.ImageIO.kCGImageSourceCreateThumbnailFromImageAlways
import platform.ImageIO.kCGImageSourceCreateThumbnailWithTransform
import platform.ImageIO.kCGImageSourceShouldCacheImmediately
import platform.ImageIO.kCGImageSourceThumbnailMaxPixelSize
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.posix.memcpy
import kotlin.math.ceil

@OptIn(ExperimentalForeignApi::class)
class IosPlatformImageCodec : PlatformImageCodec {
    private val coreImageContext: CIContext? = try {
        CIContext.contextWithOptions(null)
    } catch (_: NullPointerException) {
        // Kotlin/Native can surface a nil CIContext factory result as an NPE.
        null
    }

    override suspend fun decode(bytes: ByteArray, request: DecodeRequest): DecodedImage =
        withOwnedPlatformImageResult(
            dispatcher = Dispatchers.Default,
            ownedBitmap = DecodedImage::bitmap,
        ) {
            try {
                withImageSource(bytes) { source ->
                    val metadata = inspect(source)
                    val plannedSize = DecodeSizePlanner.plan(metadata.orientedSize, request)
                    val thumbnail = createThumbnail(
                        source,
                        conservativeThumbnailLongest(metadata.orientedSize, plannedSize, request),
                    )
                    try {
                        currentCoroutineContext().ensureActive()
                        handoffLocalPlatformImageBitmap(
                            createBitmap = { thumbnail.toImageBitmap() },
                        ) { bitmap ->
                            if (
                                bitmap.width > request.maxDimension ||
                                bitmap.height > request.maxDimension ||
                                bitmap.width.toLong() * bitmap.height > request.maxPixels
                            ) {
                                throw IllegalStateException(
                                    "ImageIO thumbnail ${bitmap.width}x${bitmap.height} exceeds $request",
                                )
                            }
                            DecodedImage(bitmap = bitmap, originalSize = metadata.orientedSize)
                        }
                    } finally {
                        CGImageRelease(thumbnail)
                    }
                }
            } catch (cause: CancellationException) {
                throw cause
            } catch (failure: PrintImageFailure) {
                throw failure
            } catch (cause: Exception) {
                throw PrintImageFailure.DecodeFailed(cause)
            }
        }

    override suspend fun renderCrop(bytes: ByteArray, request: CropRenderRequest): ImageBitmap =
        withOwnedPlatformImageResult(
            dispatcher = Dispatchers.Default,
            ownedBitmap = { it },
        ) {
            try {
                val metadata = withImageSource(bytes, ::inspect)
                currentCoroutineContext().ensureActive()
                if (metadata.orientedSize != request.originalSize) {
                    throw PrintImageFailure.RenderFailed(
                        IllegalArgumentException(
                            "Original size ${request.originalSize} does not match " +
                                    "image size ${metadata.orientedSize}",
                        ),
                    )
                }

                val plan = CropRenderPlanner().plan(request)
                currentCoroutineContext().ensureActive()
                when (val coreImage = renderCoreImage(bytes, metadata, plan)) {
                    is CoreImageRenderOutcome.Rendered -> coreImage.bitmap
                    CoreImageRenderOutcome.Unavailable -> {
                        currentCoroutineContext().ensureActive()
                        if (!canUseUIKitCropFallback(request.originalSize)) {
                            throw PrintImageFailure.RenderFailed(
                                IllegalStateException(
                                    "Core Image is unavailable and UIKit fallback source " +
                                            "${request.originalSize.width}x${request.originalSize.height} " +
                                            "exceeds the safe ${PrintImageLimits.MAX_INTERMEDIATE_DECODE_PIXELS}-pixel budget",
                                ),
                            )
                        }
                        renderUIKitFixedTarget(bytes, request, plan)
                    }
                }
            } catch (cause: CancellationException) {
                throw cause
            } catch (failure: PrintImageFailure) {
                throw failure
            } catch (cause: Exception) {
                throw PrintImageFailure.RenderFailed(cause)
            }
        }

    override suspend fun encodePng(bitmap: ImageBitmap): ByteArray =
        withContext(Dispatchers.Default) {
            try {
                Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
                    val data = image.encodeToData(EncodedImageFormat.PNG, 100)
                        ?: throw PrintImageFailure.EncodeFailed()
                    data.use {
                        it.bytes
                    }
                }
            } catch (cause: CancellationException) {
                throw cause
            } catch (failure: PrintImageFailure) {
                throw failure
            } catch (cause: Exception) {
                throw PrintImageFailure.EncodeFailed(cause)
            }
        }

    private inline fun <T> withImageSource(
        bytes: ByteArray,
        block: (CGImageSourceRef) -> T,
    ): T {
        val format = bytes.detectFormat() ?: throw PrintImageFailure.UnsupportedFormat()
        val data = bytes.toCFData()
        try {
            val source = CGImageSourceCreateWithData(data, null)
                ?: throw PrintImageFailure.UnsupportedFormat()
            try {
                val sourceType = CGImageSourceGetType(source)?.toKotlinString()
                if (!format.matchesSourceType(sourceType)) {
                    throw PrintImageFailure.UnsupportedFormat()
                }
                return block(source)
            } finally {
                CFRelease(source)
            }
        } finally {
            CFRelease(data)
        }
    }

    private fun inspect(source: CGImageSourceRef): ImageMetadata {
        val properties = CGImageSourceCopyPropertiesAtIndex(source, 0u, null)
            ?: throw PrintImageFailure.UnsupportedFormat()
        try {
            val rawWidth = properties.readInt(kCGImagePropertyPixelWidth)
                ?: throw PrintImageFailure.UnsupportedFormat()
            val rawHeight = properties.readInt(kCGImagePropertyPixelHeight)
                ?: throw PrintImageFailure.UnsupportedFormat()
            if (rawWidth <= 0 || rawHeight <= 0) {
                throw PrintImageFailure.UnsupportedFormat()
            }
            if (rawWidth.toLong() * rawHeight > PrintImageLimits.MAX_PIXELS) {
                throw PrintImageFailure.ImageDimensionsTooLarge
            }
            val orientation = properties.readInt(kCGImagePropertyOrientation)
                ?.takeIf { it in 1..8 }
                ?: 1
            val rawSize = ImageSize(rawWidth, rawHeight)
            val orientedSize = if (orientation in 5..8) {
                ImageSize(rawHeight, rawWidth)
            } else {
                rawSize
            }
            return ImageMetadata(orientedSize, orientation)
        } finally {
            CFRelease(properties)
        }
    }

    private fun createThumbnail(source: CGImageSourceRef, maxPixelSize: Int): CGImageRef = memScoped {
        val maxPixelValue = alloc<IntVar> { value = maxPixelSize }
        val maxPixelNumber = CFNumberCreate(null, kCFNumberIntType, maxPixelValue.ptr)
            ?: throw IllegalStateException("Unable to create thumbnail size option")
        try {
            val keys = allocArray<COpaquePointerVar>(4)
            keys[0] = kCGImageSourceCreateThumbnailFromImageAlways
            keys[1] = kCGImageSourceCreateThumbnailWithTransform
            keys[2] = kCGImageSourceThumbnailMaxPixelSize
            keys[3] = kCGImageSourceShouldCacheImmediately
            val values = allocArray<COpaquePointerVar>(4)
            values[0] = kCFBooleanTrue
            values[1] = kCFBooleanTrue
            values[2] = maxPixelNumber
            values[3] = kCFBooleanTrue
            val options = CFDictionaryCreate(
                allocator = null,
                keys = keys,
                values = values,
                numValues = 4,
                keyCallBacks = null,
                valueCallBacks = null,
            ) ?: throw IllegalStateException("Unable to create thumbnail options")
            try {
                CGImageSourceCreateThumbnailAtIndex(source, 0u, options)
                    ?: throw PrintImageFailure.UnsupportedFormat()
            } finally {
                CFRelease(options)
            }
        } finally {
            CFRelease(maxPixelNumber)
        }
    }

    private fun conservativeThumbnailLongest(
        source: ImageSize,
        planned: ImageSize,
        request: DecodeRequest,
    ): Int {
        var longest = maxOf(planned.width, planned.height)
        while (longest > 1) {
            val other = if (source.width >= source.height) {
                ceil(longest.toDouble() * source.height / source.width).toLong()
            } else {
                ceil(longest.toDouble() * source.width / source.height).toLong()
            }
            if (longest.toLong() * other <= request.maxPixels) break
            longest--
        }
        return longest
    }

    private suspend fun renderCoreImage(
        bytes: ByteArray,
        metadata: ImageMetadata,
        plan: CropRenderPlan,
    ): CoreImageRenderOutcome {
        val context = coreImageContext ?: return CoreImageRenderOutcome.Unavailable
        val sourceData = bytes.toNSData()
        val sourceImage = CIImage.imageWithData(sourceData)
            ?: return CoreImageRenderOutcome.Unavailable
        val orientedImage = sourceImage.imageByApplyingOrientation(metadata.orientation)
        val normalizedImage = orientedImage.extent.useContents {
            orientedImage.imageByApplyingTransform(
                coreImageExtentNormalization(origin.x, origin.y).toCGAffineTransform(),
            )
        }
        val transformed = normalizedImage.imageByApplyingTransform(
            plan.sourceToOutput.toCoreImageTransform(
                sourceHeight = metadata.orientedSize.height,
                outputHeight = plan.outputSize.height,
            ).toCGAffineTransform(),
        )
        val outputRect = CGRectMake(
            0.0,
            0.0,
            plan.outputSize.width.toDouble(),
            plan.outputSize.height.toDouble(),
        )
        val rendered = context.createCGImage(transformed, outputRect)
            ?: return CoreImageRenderOutcome.Unavailable
        try {
            currentCoroutineContext().ensureActive()
            return CoreImageRenderOutcome.Rendered(rendered.toImageBitmap())
        } finally {
            CGImageRelease(rendered)
        }
    }

    private fun AffineTransform.toCGAffineTransform() = CGAffineTransformMake(
        a = scaleX,
        b = skewY,
        c = skewX,
        d = scaleY,
        tx = translateX,
        ty = translateY,
    )

    // Simulator test processes may not provide a usable CIContext. This still rasterizes only
    // the requested fixed target and never creates a normalized full-source image.
    private suspend fun renderUIKitFixedTarget(
        bytes: ByteArray,
        request: CropRenderRequest,
        plan: CropRenderPlan,
    ): ImageBitmap {
        val source = UIImage.imageWithData(bytes.toNSData())
            ?: throw PrintImageFailure.UnsupportedFormat()
        UIGraphicsBeginImageContextWithOptions(
            CGSizeMake(
                request.outputSize.width.toDouble(),
                request.outputSize.height.toDouble(),
            ),
            false,
            1.0,
        )
        val output = try {
            val context = UIGraphicsGetCurrentContext()
                ?: throw IllegalStateException("Unable to create fixed output context")
            val transform = plan.sourceToOutput
            CGContextSetInterpolationQuality(context, kCGInterpolationHigh)
            CGContextConcatCTM(
                context,
                CGAffineTransformMake(
                    a = transform.scaleX,
                    b = transform.skewY,
                    c = transform.skewX,
                    d = transform.scaleY,
                    tx = transform.translateX,
                    ty = transform.translateY,
                ),
            )
            source.drawInRect(
                CGRectMake(
                    0.0,
                    0.0,
                    request.originalSize.width.toDouble(),
                    request.originalSize.height.toDouble(),
                ),
            )
            UIGraphicsGetImageFromCurrentImageContext()
                ?: throw IllegalStateException("Unable to snapshot fixed output image")
        } finally {
            UIGraphicsEndImageContext()
        }
        currentCoroutineContext().ensureActive()
        return handoffLocalPlatformImageBitmap(
            createBitmap = {
                UIImagePNGRepresentation(output)
                    ?.toByteArray()
                    ?.decodeToImageBitmap()
                    ?: throw IllegalStateException("Unable to encode fixed output image")
            },
        ) { bitmap ->
            check(bitmap.width == request.outputSize.width && bitmap.height == request.outputSize.height)
            bitmap
        }
    }

    private fun CGImageRef.toImageBitmap(): ImageBitmap {
        val expectedWidth = CGImageGetWidth(this).toInt()
        val expectedHeight = CGImageGetHeight(this).toInt()
        val png = UIImagePNGRepresentation(UIImage.imageWithCGImage(this))
            ?.toByteArray()
            ?: throw IllegalStateException("Unable to encode bounded CGImage")
        return handoffLocalPlatformImageBitmap(
            createBitmap = png::decodeToImageBitmap,
        ) { bitmap ->
            check(bitmap.width == expectedWidth && bitmap.height == expectedHeight)
            bitmap
        }
    }

    private fun ByteArray.toCFData() = usePinned { pinned ->
        CFDataCreate(
            allocator = null,
            bytes = pinned.addressOf(0).reinterpret(),
            length = size.toLong(),
        ) ?: throw PrintImageFailure.UnsupportedFormat()
    }

    private fun platform.CoreFoundation.CFDictionaryRef.readInt(
        key: platform.CoreFoundation.CFStringRef?,
    ): Int? {
        val value = CFDictionaryGetValue(this, key) ?: return null
        if (CFGetTypeID(value) != CFNumberGetTypeID()) return null
        return memScoped {
            val result = alloc<IntVar>()
            if (CFNumberGetValue(value.reinterpret(), kCFNumberIntType, result.ptr)) {
                result.value
            } else {
                null
            }
        }
    }

    private fun platform.CoreFoundation.CFStringRef.toKotlinString(): String = memScoped {
        val capacity = CFStringGetMaximumSizeForEncoding(
            CFStringGetLength(this@toKotlinString),
            kCFStringEncodingUTF8,
        ) + 1
        val buffer = allocArray<ByteVar>(capacity)
        if (!CFStringGetCString(this@toKotlinString, buffer, capacity, kCFStringEncodingUTF8)) {
            return@memScoped ""
        }
        buffer.toKString()
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

    private enum class ImageFormat {
        JPEG,
        PNG,
        HEIF;

        fun matchesSourceType(type: String?): Boolean = when (this) {
            JPEG -> type == "public.jpeg"
            PNG -> type == "public.png"
            HEIF -> type == "public.heic" || type == "public.heif" || type == "public.heics"
        }
    }

    private data class ImageMetadata(
        val orientedSize: ImageSize,
        val orientation: Int,
    )

    private sealed interface CoreImageRenderOutcome {
        data class Rendered(val bitmap: ImageBitmap) : CoreImageRenderOutcome
        data object Unavailable : CoreImageRenderOutcome
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

internal fun AffineTransform.toCoreImageTransform(
    sourceHeight: Int,
    outputHeight: Int,
): AffineTransform = AffineTransform(
    scaleX = scaleX,
    skewX = -skewX,
    translateX = skewX * sourceHeight + translateX,
    skewY = -skewY,
    scaleY = scaleY,
    translateY = outputHeight - scaleY * sourceHeight - translateY,
)

internal fun coreImageExtentNormalization(originX: Double, originY: Double): AffineTransform =
    AffineTransform(
        scaleX = 1.0,
        skewX = 0.0,
        translateX = -originX,
        skewY = 0.0,
        scaleY = 1.0,
        translateY = -originY,
    )

internal fun canUseUIKitCropFallback(originalSize: ImageSize): Boolean =
    originalSize.width.toLong() * originalSize.height <=
            PrintImageLimits.MAX_INTERMEDIATE_DECODE_PIXELS

@OptIn(ExperimentalForeignApi::class)
internal fun ByteArray.toNSData(): NSData = if (isEmpty()) {
    NSData.dataWithBytes(bytes = null, length = 0u)
} else {
    usePinned { pinned ->
        NSData.dataWithBytes(bytes = pinned.addressOf(0), length = size.toULong())
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    if (length == 0uL) return byteArrayOf()
    return ByteArray(length.toInt()).also { result ->
        result.usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytes, length)
        }
    }
}
