package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import androidx.compose.ui.graphics.ImageBitmap

data class ImageSize(
    val width: Int,
    val height: Int,
)

data class DecodeRequest(
    val maxDimension: Int,
    val maxPixels: Long,
)

data class CropTransform(
    val centerOffsetX: Float = 0f,
    val centerOffsetY: Float = 0f,
    val zoom: Float = 1f,
    val quarterTurns: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
)

data class CropRenderRequest(
    val originalSize: ImageSize,
    val transform: CropTransform,
    val outputSize: ImageSize,
)

data class AffinePoint(
    val x: Double,
    val y: Double,
)

data class AffineTransform(
    val scaleX: Double,
    val skewX: Double,
    val translateX: Double,
    val skewY: Double,
    val scaleY: Double,
    val translateY: Double,
) {
    fun map(x: Double, y: Double): AffinePoint = AffinePoint(
        x = scaleX * x + skewX * y + translateX,
        y = skewY * x + scaleY * y + translateY,
    )
}

data class PixelRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

data class CropRenderPlan(
    val sourceToOutput: AffineTransform,
    val visibleSourceBounds: PixelRect,
    val outputSize: ImageSize,
)

data class RenderGeometry(
    val imageWidth: Float,
    val imageHeight: Float,
    val translationX: Float,
    val translationY: Float,
    val rotationDegrees: Float,
    val scaleXSign: Float,
    val scaleYSign: Float,
)

data class SelectedImage(
    val fileName: String,
    val bytes: ByteArray,
)

data class DecodedImage(
    val bitmap: ImageBitmap,
    val originalSize: ImageSize,
)

data class PreparedImage(
    val preview: ImageBitmap,
    val originalSize: ImageSize,
)

data class PrintCanvasSpec(
    val canvasWidth: Int = 2_048,
    val canvasHeight: Int = 1_440,
    val contentWidth: Int = 1_920,
    val contentHeight: Int = 1_080,
    val contentOffsetX: Int = 64,
    val contentOffsetY: Int = 69,
)

object PrintImageLimits {
    const val MAX_FILE_BYTES: Long = 50L * 1024 * 1024
    const val MAX_PIXELS: Long = 100_000_000L
    const val MAX_INTERMEDIATE_DECODE_PIXELS: Long = 16_000_000L
}

sealed class PrintImageFailure(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    data object FileTooLarge : PrintImageFailure("Selected image exceeds 50 MiB")
    data object ImageDimensionsTooLarge : PrintImageFailure("Selected image exceeds 100 megapixels")
    data object DesktopRegionDecodeUnavailable : PrintImageFailure(
        "Desktop cannot safely region-decode this large image format",
    )
    class UnsupportedFormat(cause: Throwable? = null) :
        PrintImageFailure("Unsupported image format", cause)

    class DecodeFailed(cause: Throwable) : PrintImageFailure("Image decode failed", cause)
    class RenderFailed(cause: Throwable) : PrintImageFailure("Image render failed", cause)
    class EncodeFailed(cause: Throwable? = null) : PrintImageFailure("PNG encode failed", cause)
}
