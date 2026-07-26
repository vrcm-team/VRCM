package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CancellationException

interface PrintImageProcessor {
    suspend fun prepare(source: SelectedImage): Result<PreparedImage>

    suspend fun render(
        source: SelectedImage,
        originalSize: ImageSize,
        transform: CropTransform,
    ): Result<ByteArray>
}

class DefaultPrintImageProcessor(
    private val codec: PlatformImageCodec,
    private val spec: PrintCanvasSpec = PrintCanvasSpec(),
    private val maxFileBytes: Int = PrintImageLimits.MAX_FILE_BYTES.toInt(),
    private val maxPixels: Long = PrintImageLimits.MAX_PIXELS,
    private val releaseBitmap: (ImageBitmap) -> Unit = ::releasePlatformImageBitmap,
) : PrintImageProcessor {
    override suspend fun prepare(source: SelectedImage): Result<PreparedImage> = try {
        validateSource(source)
        val decoded = decodePreview(source.bytes)
        val prepared = handoffOwnedBitmap(decoded.bitmap) {
            validateDimensions(decoded.originalSize)
            PreparedImage(
                preview = decoded.bitmap,
                originalSize = decoded.originalSize,
            )
        }
        Result.success(
            prepared,
        )
    } catch (cause: CancellationException) {
        throw cause
    } catch (failure: PrintImageFailure) {
        Result.failure(failure)
    } catch (cause: Exception) {
        Result.failure(PrintImageFailure.DecodeFailed(cause))
    }

    override suspend fun render(
        source: SelectedImage,
        originalSize: ImageSize,
        transform: CropTransform,
    ): Result<ByteArray> = try {
        validateSource(source)
        validateDimensions(originalSize)
        val content = renderCrop(source.bytes, originalSize, transform)
        val bytes = useOwnedBitmap(content) {
            if (content.width != spec.contentWidth || content.height != spec.contentHeight) {
                throw PrintImageFailure.RenderFailed(
                    IllegalStateException(
                        "Crop renderer returned ${content.width}x${content.height}; expected " +
                                "${spec.contentWidth}x${spec.contentHeight}",
                    ),
                )
            }
            val output = renderCanvas(content)
            useOwnedBitmap(output) {
                encodePng(output)
            }
        }
        if (!hasExpectedPngHeader(bytes, spec.canvasWidth, spec.canvasHeight)) {
            throw PrintImageFailure.EncodeFailed()
        }
        Result.success(bytes)
    } catch (cause: CancellationException) {
        throw cause
    } catch (failure: PrintImageFailure) {
        Result.failure(failure)
    } catch (cause: Exception) {
        Result.failure(PrintImageFailure.RenderFailed(cause))
    }

    private suspend fun decodePreview(bytes: ByteArray): DecodedImage = try {
        codec.decode(
            bytes,
            DecodeRequest(
                maxDimension = PREVIEW_MAX_DIMENSION,
                maxPixels = PrintImageLimits.MAX_INTERMEDIATE_DECODE_PIXELS,
            ),
        )
    } catch (cause: CancellationException) {
        throw cause
    } catch (failure: PrintImageFailure) {
        throw failure
    } catch (cause: Exception) {
        throw PrintImageFailure.DecodeFailed(cause)
    }

    private suspend fun renderCrop(
        bytes: ByteArray,
        originalSize: ImageSize,
        transform: CropTransform,
    ): ImageBitmap = try {
        codec.renderCrop(
            bytes,
            CropRenderRequest(
                originalSize = originalSize,
                transform = transform,
                outputSize = ImageSize(spec.contentWidth, spec.contentHeight),
            ),
        )
    } catch (cause: CancellationException) {
        throw cause
    } catch (failure: PrintImageFailure) {
        throw failure
    } catch (cause: Exception) {
        throw PrintImageFailure.RenderFailed(cause)
    }

    private suspend fun encodePng(bitmap: ImageBitmap): ByteArray = try {
        codec.encodePng(bitmap)
    } catch (cause: CancellationException) {
        throw cause
    } catch (failure: PrintImageFailure) {
        throw failure
    } catch (cause: Exception) {
        throw PrintImageFailure.EncodeFailed(cause)
    }

    private suspend inline fun <T> useOwnedBitmap(
        bitmap: ImageBitmap,
        block: suspend (ImageBitmap) -> T,
    ): T {
        val result = try {
            block(bitmap)
        } catch (cause: CancellationException) {
            releaseAfterFailure(bitmap, cause)
            throw cause
        } catch (cause: Exception) {
            releaseAfterFailure(bitmap, cause)
            throw cause
        } catch (cause: Error) {
            releaseAfterFailure(bitmap, cause)
            throw cause
        }
        releaseBitmap(bitmap)
        return result
    }

    private fun releaseAfterFailure(bitmap: ImageBitmap, primaryFailure: Throwable) {
        try {
            releaseBitmap(bitmap)
        } catch (releaseFailure: CancellationException) {
            primaryFailure.addReleaseFailure(releaseFailure)
        } catch (releaseFailure: Exception) {
            primaryFailure.addReleaseFailure(releaseFailure)
        } catch (releaseFailure: Error) {
            primaryFailure.addReleaseFailure(releaseFailure)
        }
    }

    private fun Throwable.addReleaseFailure(releaseFailure: Throwable) {
        if (releaseFailure !== this) addSuppressed(releaseFailure)
    }

    private inline fun <T> handoffOwnedBitmap(
        bitmap: ImageBitmap,
        block: (ImageBitmap) -> T,
    ): T = try {
        block(bitmap)
    } catch (cause: CancellationException) {
        releaseAfterFailure(bitmap, cause)
        throw cause
    } catch (cause: Exception) {
        releaseAfterFailure(bitmap, cause)
        throw cause
    } catch (cause: Error) {
        releaseAfterFailure(bitmap, cause)
        throw cause
    }

    private fun renderCanvas(content: ImageBitmap): ImageBitmap = try {
        val output = ImageBitmap(
            width = spec.canvasWidth,
            height = spec.canvasHeight,
            hasAlpha = false,
        )
        handoffOwnedBitmap(output) {
            val canvas = Canvas(output)
            canvas.drawRect(
                rect = Rect(0f, 0f, spec.canvasWidth.toFloat(), spec.canvasHeight.toFloat()),
                paint = Paint().apply { color = Color.White },
            )
            canvas.drawImageRect(
                image = content,
                dstOffset = IntOffset(spec.contentOffsetX, spec.contentOffsetY),
                dstSize = IntSize(spec.contentWidth, spec.contentHeight),
                paint = Paint(),
            )
            output
        }
    } catch (failure: PrintImageFailure) {
        throw failure
    } catch (cause: Exception) {
        throw PrintImageFailure.RenderFailed(cause)
    }

    private fun validateSource(source: SelectedImage) {
        if (source.bytes.size > maxFileBytes) {
            throw PrintImageFailure.FileTooLarge
        }
    }

    private fun validateDimensions(size: ImageSize) {
        if (size.width <= 0 || size.height <= 0) {
            throw PrintImageFailure.DecodeFailed(IllegalArgumentException("Invalid image dimensions"))
        }
        if (size.width.toLong() * size.height > maxPixels) {
            throw PrintImageFailure.ImageDimensionsTooLarge
        }
    }

    private fun hasExpectedPngHeader(bytes: ByteArray, width: Int, height: Int): Boolean {
        if (bytes.size < PNG_HEADER_SIZE) return false
        if (!PNG_SIGNATURE.indices.all { bytes[it] == PNG_SIGNATURE[it] }) return false
        if (!IHDR.indices.all { bytes[PNG_CHUNK_TYPE_OFFSET + it] == IHDR[it] }) return false
        return bytes.readBigEndianInt(PNG_WIDTH_OFFSET) == width &&
                bytes.readBigEndianInt(PNG_HEIGHT_OFFSET) == height
    }

    private fun ByteArray.readBigEndianInt(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 24) or
                ((this[offset + 1].toInt() and 0xFF) shl 16) or
                ((this[offset + 2].toInt() and 0xFF) shl 8) or
                (this[offset + 3].toInt() and 0xFF)

    private companion object {
        const val PREVIEW_MAX_DIMENSION = 2_048
        const val PNG_HEADER_SIZE = 24
        const val PNG_CHUNK_TYPE_OFFSET = 12
        const val PNG_WIDTH_OFFSET = 16
        const val PNG_HEIGHT_OFFSET = 20

        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
        val IHDR = byteArrayOf(0x49, 0x48, 0x44, 0x52)
    }
}
