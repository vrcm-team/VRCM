package io.github.vrcmteam.vrcm.presentation.screens.gallery

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PrintCanvasSpec
import kotlin.math.max
import kotlin.math.min

internal const val PrintBoundsTransitionDurationMillis = 500
internal const val PrintRevealTransitionDurationMillis = 500

@OptIn(ExperimentalSharedTransitionApi::class)
internal val PrintBoundsTransform = BoundsTransform { _, _ ->
    tween(durationMillis = PrintBoundsTransitionDurationMillis)
}

internal enum class PrintCanvasPlacement {
    FitCenter,
    CropTopCenter,
}

internal data class PrintContentInsets(
    val left: Int = 8,
    val top: Int = 5,
    val right: Int = 8,
    val bottom: Int = 4,
)

internal data class PrintDisplaySpec(
    val canvas: PrintCanvasSpec = PrintCanvasSpec(),
    val contentInsets: PrintContentInsets = PrintContentInsets(),
) {
    val sourceCropRect: Rect = Rect(
        left = (canvas.contentOffsetX + contentInsets.left).toFloat(),
        top = (canvas.contentOffsetY + contentInsets.top).toFloat(),
        right = (canvas.contentOffsetX + canvas.contentWidth - contentInsets.right).toFloat(),
        bottom = (canvas.contentOffsetY + canvas.contentHeight - contentInsets.bottom).toFloat(),
    )
}

internal data class PrintCropTransform(
    val scale: Float,
    val translationX: Float,
    val translationY: Float,
) {
    fun map(rect: Rect, bounds: Rect): Rect {
        val pivot = bounds.center
        return Rect(
            left = pivot.x + (rect.left - pivot.x) * scale + translationX,
            top = pivot.y + (rect.top - pivot.y) * scale + translationY,
            right = pivot.x + (rect.right - pivot.x) * scale + translationX,
            bottom = pivot.y + (rect.bottom - pivot.y) * scale + translationY,
        )
    }
}

internal object PrintDisplayGeometry {
    val spec = PrintDisplaySpec()

    fun canvasRect(bounds: Rect, placement: PrintCanvasPlacement): Rect {
        if (bounds.width <= 0f || bounds.height <= 0f) return bounds

        val canvas = spec.canvas
        val scale = when (placement) {
            PrintCanvasPlacement.FitCenter -> min(
                bounds.width / canvas.canvasWidth,
                bounds.height / canvas.canvasHeight,
            )

            PrintCanvasPlacement.CropTopCenter -> max(
                bounds.width / canvas.canvasWidth,
                bounds.height / canvas.canvasHeight,
            )
        }
        val width = canvas.canvasWidth * scale
        val height = canvas.canvasHeight * scale
        val left = bounds.center.x - width / 2f
        val top = when (placement) {
            PrintCanvasPlacement.FitCenter -> bounds.center.y - height / 2f
            PrintCanvasPlacement.CropTopCenter -> bounds.top
        }
        return Rect(left, top, left + width, top + height)
    }

    fun cropRect(bounds: Rect, placement: PrintCanvasPlacement): Rect {
        val canvasRect = canvasRect(bounds, placement)
        if (bounds.width <= 0f || bounds.height <= 0f) return bounds

        val scale = canvasRect.width / spec.canvas.canvasWidth
        val source = spec.sourceCropRect
        return Rect(
            left = canvasRect.left + source.left * scale,
            top = canvasRect.top + source.top * scale,
            right = canvasRect.left + source.right * scale,
            bottom = canvasRect.top + source.bottom * scale,
        )
    }

    fun revealedCropRect(
        bounds: Rect,
        placement: PrintCanvasPlacement,
        revealProgress: Float,
    ): Rect {
        val crop = cropRect(bounds, placement)
        val progress = revealProgress.coerceIn(0f, 1f)
        return Rect(
            left = lerp(crop.left, bounds.left, progress),
            top = lerp(crop.top, bounds.top, progress),
            right = lerp(crop.right, bounds.right, progress),
            bottom = lerp(crop.bottom, bounds.bottom, progress),
        )
    }

    fun cropToFillTransform(bounds: Rect, placement: PrintCanvasPlacement): PrintCropTransform {
        val crop = cropRect(bounds, placement)
        if (crop.width <= 0f || crop.height <= 0f) {
            return PrintCropTransform(scale = 1f, translationX = 0f, translationY = 0f)
        }

        val scale = max(bounds.width / crop.width, bounds.height / crop.height)
        return PrintCropTransform(
            scale = scale,
            translationX = (bounds.center.x - crop.center.x) * scale,
            translationY = (bounds.center.y - crop.center.y) * scale,
        )
    }

    private fun lerp(start: Float, end: Float, progress: Float): Float =
        start + (end - start) * progress
}

internal class PrintCropShape(
    private val placement: PrintCanvasPlacement,
    private val revealProgress: Float = 0f,
    private val cornerRadius: Dp = 0.dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val rect = PrintDisplayGeometry.revealedCropRect(
            bounds = Rect(0f, 0f, size.width, size.height),
            placement = placement,
            revealProgress = revealProgress,
        )
        if (cornerRadius == 0.dp) return Outline.Rectangle(rect)

        val radius = CornerRadius(with(density) { cornerRadius.toPx() })
        return Outline.Rounded(
            RoundRect(
                left = rect.left,
                top = rect.top,
                right = rect.right,
                bottom = rect.bottom,
                topLeftCornerRadius = radius,
                topRightCornerRadius = radius,
                bottomRightCornerRadius = radius,
                bottomLeftCornerRadius = radius,
            )
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
internal class PrintCropOverlayClip(
    private val placement: PrintCanvasPlacement,
    private val revealProgress: Float = 0f,
) : SharedTransitionScope.OverlayClip {
    override fun getClipPath(
        sharedContentState: SharedTransitionScope.SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Path = Path().apply {
        addRect(PrintDisplayGeometry.revealedCropRect(bounds, placement, revealProgress))
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
internal object NoOverlayClip : SharedTransitionScope.OverlayClip {
    override fun getClipPath(
        sharedContentState: SharedTransitionScope.SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Path? = null
}

internal val PrintThumbnailOverlayClip: SharedTransitionScope.OverlayClip = NoOverlayClip
