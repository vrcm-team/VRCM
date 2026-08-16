package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CancellationException
import kotlin.math.min
import kotlin.math.roundToInt

interface PrintImageProcessor {
    suspend fun prepare(source: SelectedImage): Result<PreparedImage>

    suspend fun preparePrint(source: SelectedImage): Result<PreparedImageSource> =
        prepare(source).map { PreparedImageSource(source, it) }

    suspend fun render(
        source: SelectedImage,
        originalSize: ImageSize,
        transform: CropTransform,
        background: CanvasBackground = CanvasBackground.White,
    ): Result<ByteArray>
}

class DefaultPrintImageProcessor(
    private val codec: PlatformImageCodec,
    private val spec: PrintCanvasSpec = PrintCanvasSpec(),
    private val maxFileBytes: Int = PrintImageLimits.MAX_FILE_BYTES.toInt(),
    private val maxPixels: Long = PrintImageLimits.MAX_PIXELS,
    private val maxOutputBytes: Int = PrintImageLimits.MAX_ENCODED_OUTPUT_BYTES,
    private val limitOutputToVisibleSource: Boolean = false,
    private val shrinkOversizedOutput: Boolean = false,
    private val cropRenderPlanner: CropRenderPlanner = CropRenderPlanner(),
    private val releaseBitmap: (ImageBitmap) -> Unit = ::releasePlatformImageBitmap,
) : PrintImageProcessor {
    override suspend fun prepare(source: SelectedImage): Result<PreparedImage> =
        prepareSource(source, cropDownloadedPrintBorder = false).map(PreparedImageSource::prepared)

    override suspend fun preparePrint(source: SelectedImage): Result<PreparedImageSource> =
        prepareSource(source, cropDownloadedPrintBorder = true)

    private suspend fun prepareSource(
        source: SelectedImage,
        cropDownloadedPrintBorder: Boolean,
    ): Result<PreparedImageSource> = try {
        validateSource(source)
        val decoded = decodePreview(source.bytes)
        try {
            validateDimensions(decoded.originalSize)
        } catch (cause: CancellationException) {
            releaseAfterFailure(decoded.bitmap, cause)
            throw cause
        } catch (cause: Exception) {
            releaseAfterFailure(decoded.bitmap, cause)
            throw cause
        } catch (cause: Error) {
            releaseAfterFailure(decoded.bitmap, cause)
            throw cause
        }

        val preparedSource = if (
            cropDownloadedPrintBorder &&
            decoded.originalSize == DownloadedPrintCanvasSize
        ) {
            releaseBitmap(decoded.bitmap)
            cropDownloadedPrint(source, decoded.originalSize)
        } else {
            handoffOwnedBitmap(decoded.bitmap) {
                PreparedImageSource(
                    source = source,
                    prepared = PreparedImage(
                        preview = decoded.bitmap,
                        originalSize = decoded.originalSize,
                    ),
                )
            }
        }
        Result.success(preparedSource)
    } catch (cause: CancellationException) {
        throw cause
    } catch (failure: PrintImageFailure) {
        Result.failure(failure)
    } catch (cause: Exception) {
        Result.failure(PrintImageFailure.DecodeFailed(cause))
    }

    private suspend fun cropDownloadedPrint(
        source: SelectedImage,
        originalSize: ImageSize,
    ): PreparedImageSource {
        val cropped = renderCrop(
            bytes = source.bytes,
            originalSize = originalSize,
            transform = DownloadedPrintCanvasSpec.cropToContentTransform(),
            renderSpec = DownloadedPrintCanvasSpec,
        )
        return handoffOwnedBitmap(cropped) {
            val contentSize = DownloadedPrintCanvasSpec.contentSize
            if (ImageSize(cropped.width, cropped.height) != contentSize) {
                throw PrintImageFailure.RenderFailed(
                    IllegalStateException(
                        "Downloaded Print crop returned ${cropped.width}x${cropped.height}; " +
                                "expected ${contentSize.width}x${contentSize.height}",
                    ),
                )
            }
            val croppedBytes = encodePng(cropped)
            if (!hasExpectedPngHeader(croppedBytes, contentSize.width, contentSize.height)) {
                throw PrintImageFailure.EncodeFailed()
            }
            PreparedImageSource(
                source = source.copy(bytes = croppedBytes),
                prepared = PreparedImage(
                    preview = cropped,
                    originalSize = contentSize,
                ),
            )
        }
    }

    override suspend fun render(
        source: SelectedImage,
        originalSize: ImageSize,
        transform: CropTransform,
        background: CanvasBackground,
    ): Result<ByteArray> {
        return try {
            validateSource(source)
            validateDimensions(originalSize)
            var renderSpec = if (limitOutputToVisibleSource) {
                spec.fitWithinVisibleSource(originalSize, transform)
            } else {
                spec
            }
            var encodedBytes: ByteArray? = null
            while (encodedBytes == null) {
                try {
                    encodedBytes = renderWithSpec(
                        source.bytes,
                        originalSize,
                        transform,
                        background,
                        renderSpec,
                    )
                } catch (failure: PrintImageFailure.EncodedOutputTooLarge) {
                    if (!shrinkOversizedOutput) throw failure
                    renderSpec = renderSpec.shrinkLikeVrcx()
                        ?: throw failure
                }
            }
            Result.success(encodedBytes)
        } catch (cause: CancellationException) {
            throw cause
        } catch (failure: PrintImageFailure) {
            Result.failure(failure)
        } catch (cause: Exception) {
            Result.failure(PrintImageFailure.RenderFailed(cause))
        }
    }

    private suspend fun renderWithSpec(
        bytes: ByteArray,
        originalSize: ImageSize,
        transform: CropTransform,
        background: CanvasBackground,
        renderSpec: PrintCanvasSpec,
    ): ByteArray {
        val content = renderCrop(bytes, originalSize, transform, renderSpec)
        val encodedBytes = useOwnedBitmap(content) {
            if (
                content.width != renderSpec.contentWidth ||
                content.height != renderSpec.contentHeight
            ) {
                throw PrintImageFailure.RenderFailed(
                    IllegalStateException(
                        "Crop renderer returned ${content.width}x${content.height}; expected " +
                                "${renderSpec.contentWidth}x${renderSpec.contentHeight}",
                    ),
                )
            }
            if (renderSpec.isFullCanvas()) {
                compositeBackgroundInPlace(content, background)
                encodePng(content)
            } else {
                val output = renderFramedCanvas(content, background, renderSpec)
                useOwnedBitmap(output) {
                    encodePng(output)
                }
            }
        }
        if (!hasExpectedPngHeader(encodedBytes, renderSpec.canvasWidth, renderSpec.canvasHeight)) {
            throw PrintImageFailure.EncodeFailed()
        }
        return encodedBytes
    }

    private suspend fun decodePreview(bytes: ByteArray): DecodedImage = try {
        codec.decode(
            bytes,
            DecodeRequest(
                maxDimension = PREVIEW_MAX_DIMENSION,
                maxPixels = PrintImageLimits.MAX_PREVIEW_PIXELS,
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
        renderSpec: PrintCanvasSpec,
    ): ImageBitmap = try {
        codec.renderCrop(
            bytes,
            CropRenderRequest(
                originalSize = originalSize,
                transform = transform,
                outputSize = ImageSize(renderSpec.contentWidth, renderSpec.contentHeight),
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
        codec.encodePng(bitmap, maxOutputBytes).also { bytes ->
            if (bytes.size > maxOutputBytes) {
                throw PrintImageFailure.EncodedOutputTooLarge
            }
        }
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

    private fun compositeBackgroundInPlace(
        content: ImageBitmap,
        background: CanvasBackground,
    ) {
        if (background == CanvasBackground.Transparent) return
        try {
            Canvas(content).drawRect(
                rect = Rect(0f, 0f, content.width.toFloat(), content.height.toFloat()),
                paint = Paint().apply {
                    color = Color.White
                    blendMode = BlendMode.DstOver
                },
            )
        } catch (failure: PrintImageFailure) {
            throw failure
        } catch (cause: Exception) {
            throw PrintImageFailure.RenderFailed(cause)
        }
    }

    private fun renderFramedCanvas(
        content: ImageBitmap,
        background: CanvasBackground,
        renderSpec: PrintCanvasSpec,
    ): ImageBitmap = try {
        val output = ImageBitmap(
            width = renderSpec.canvasWidth,
            height = renderSpec.canvasHeight,
            hasAlpha = background == CanvasBackground.Transparent,
        )
        handoffOwnedBitmap(output) {
            val canvas = Canvas(output)
            canvas.drawRect(
                rect = Rect(
                    0f,
                    0f,
                    renderSpec.canvasWidth.toFloat(),
                    renderSpec.canvasHeight.toFloat(),
                ),
                paint = Paint().apply {
                    color = when (background) {
                        CanvasBackground.Transparent -> Color.Transparent
                        CanvasBackground.White -> Color.White
                    }
                    blendMode = BlendMode.Src
                },
            )
            canvas.drawImageRect(
                image = content,
                dstOffset = IntOffset(renderSpec.contentOffsetX, renderSpec.contentOffsetY),
                dstSize = IntSize(renderSpec.contentWidth, renderSpec.contentHeight),
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

    private fun PrintCanvasSpec.isFullCanvas(): Boolean =
        canvasWidth == contentWidth &&
                canvasHeight == contentHeight &&
                contentOffsetX == 0 &&
                contentOffsetY == 0

    private fun PrintCanvasSpec.fitWithinVisibleSource(
        originalSize: ImageSize,
        transform: CropTransform,
    ): PrintCanvasSpec {
        if (!isFullCanvas()) return this
        // These bounds already include pan, rotation, and zoom; only fit them to the target aspect.
        val visibleSourceBounds = cropRenderPlanner.plan(
            CropRenderRequest(
                originalSize = originalSize,
                transform = transform,
                outputSize = ImageSize(canvasWidth, canvasHeight),
            ),
        ).visibleSourceBounds
        val rotatedVisibleWidth: Int
        val rotatedVisibleHeight: Int
        if (transform.quarterTurns.mod(2) == 0) {
            rotatedVisibleWidth = visibleSourceBounds.width
            rotatedVisibleHeight = visibleSourceBounds.height
        } else {
            rotatedVisibleWidth = visibleSourceBounds.height
            rotatedVisibleHeight = visibleSourceBounds.width
        }
        val targetAspect = canvasWidth.toDouble() / canvasHeight
        val visibleAspect = rotatedVisibleWidth.toDouble() / rotatedVisibleHeight
        val uncappedWidth: Double
        val uncappedHeight: Double
        if (visibleAspect >= targetAspect) {
            uncappedWidth = rotatedVisibleWidth.toDouble()
            uncappedHeight = uncappedWidth / targetAspect
        } else {
            uncappedHeight = rotatedVisibleHeight.toDouble()
            uncappedWidth = uncappedHeight * targetAspect
        }
        val scale = minOf(
            1.0,
            canvasWidth / uncappedWidth,
            canvasHeight / uncappedHeight,
        )
        val width = (uncappedWidth * scale).roundToInt().coerceIn(1, canvasWidth)
        val height = (uncappedHeight * scale).roundToInt().coerceIn(1, canvasHeight)
        return copy(
            canvasWidth = width,
            canvasHeight = height,
            contentWidth = width,
            contentHeight = height,
        )
    }

    private fun PrintCanvasSpec.shrinkLikeVrcx(): PrintCanvasSpec? {
        if (!isFullCanvas() || canvasWidth <= VRCX_RESIZE_STEP || canvasHeight <= VRCX_RESIZE_STEP) {
            return null
        }
        val width: Int
        val height: Int
        if (canvasWidth > canvasHeight) {
            width = canvasWidth - VRCX_RESIZE_STEP
            height = (canvasHeight / (canvasWidth.toDouble() / width)).roundToInt()
        } else {
            height = canvasHeight - VRCX_RESIZE_STEP
            width = (canvasWidth / (canvasHeight.toDouble() / height)).roundToInt()
        }
        return copy(
            canvasWidth = width,
            canvasHeight = height,
            contentWidth = width,
            contentHeight = height,
        )
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
        const val VRCX_RESIZE_STEP = 25

        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
        val IHDR = byteArrayOf(0x49, 0x48, 0x44, 0x52)
    }
}

private val DownloadedPrintCanvasSpec = PrintCanvasSpec()
private val DownloadedPrintCanvasSize = ImageSize(
    width = DownloadedPrintCanvasSpec.canvasWidth,
    height = DownloadedPrintCanvasSpec.canvasHeight,
)

private val PrintCanvasSpec.contentSize: ImageSize
    get() = ImageSize(contentWidth, contentHeight)

private fun PrintCanvasSpec.cropToContentTransform(): CropTransform {
    // Map the known inner photo rectangle onto the crop output without duplicating its offsets.
    val fitScale = min(
        contentWidth.toFloat() / canvasWidth,
        contentHeight.toFloat() / canvasHeight,
    )
    val sourceCenterX = canvasWidth / 2f
    val sourceCenterY = canvasHeight / 2f
    val contentCenterX = contentOffsetX + contentWidth / 2f
    val contentCenterY = contentOffsetY + contentHeight / 2f
    return CropTransform(
        centerOffsetX = (sourceCenterX - contentCenterX) / contentWidth,
        centerOffsetY = (sourceCenterY - contentCenterY) / contentHeight,
        zoom = 1f / fitScale,
    )
}
