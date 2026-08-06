package io.github.vrcmteam.vrcm.presentation.screens.gallery

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VectorizedFiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.animations.DefaultSpring
import io.github.vrcmteam.vrcm.presentation.compoments.LocalSharedTransitionDialogScope
import io.github.vrcmteam.vrcm.presentation.compoments.sharedElementBy
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PrintCanvasSpec
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal const val PrintBoundsTransitionDurationMillis = 600
internal const val PrintRevealTransitionDurationMillis = 500
internal const val PrintTotalTransitionDurationMillis =
    PrintBoundsTransitionDurationMillis + PrintRevealTransitionDurationMillis
internal val PrintThumbnailCornerRadius = 12.dp
private const val NanosPerMillisecond = 1_000_000L

private val PrintRevealProgressSpring = TargetBasedAnimation(
    animationSpec = spring(
        stiffness = Spring.StiffnessMediumLow,
    ),
    typeConverter = Float.VectorConverter,
    initialValue = 0f,
    targetValue = 1f,
)

@OptIn(ExperimentalSharedTransitionApi::class)
internal val PrintBoundsTransform = BoundsTransform { initialBounds, targetBounds ->
    val isClosing = initialBounds.width * initialBounds.height >
        targetBounds.width * targetBounds.height
    DefaultSpring.withDelay(
        delayMillis = if (isClosing) PrintRevealTransitionDurationMillis else 0,
    )
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

    fun targetPhotoRect(bounds: Rect): Rect =
        cropRect(bounds)

    fun safePhotoToBoundsTransform(bounds: Rect): PrintCropTransform =
        cropToFillTransform(bounds)

    fun photoBoundsProgress(transitionProgress: Float): Float =
        springProgress(
            phaseProgress(
                transitionProgress = transitionProgress,
                delayMillis = 0,
                durationMillis = PrintBoundsTransitionDurationMillis,
            )
        )

    fun canvasRevealProgress(transitionProgress: Float): Float =
        springProgress(
            phaseProgress(
                transitionProgress = transitionProgress,
                delayMillis = PrintBoundsTransitionDurationMillis,
                durationMillis = PrintRevealTransitionDurationMillis,
            )
        )

    fun revealedCanvasRect(bounds: Rect, transitionProgress: Float): Rect {
        val photo = targetPhotoRect(bounds)
        val progress = canvasRevealProgress(transitionProgress)
        return Rect(
            left = lerp(photo.left, bounds.left, progress),
            top = lerp(photo.top, bounds.top, progress),
            right = lerp(photo.right, bounds.right, progress),
            bottom = lerp(photo.bottom, bounds.bottom, progress),
        )
    }

    fun canvasFramePath(bounds: Rect, transitionProgress: Float): Path {
        val photo = targetPhotoRect(bounds)
        val revealedCanvas = revealedCanvasRect(bounds, transitionProgress)
        return Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(revealedCanvas)
            addRect(photo)
        }
    }

    fun canvasRect(bounds: Rect): Rect {
        if (bounds.width <= 0f || bounds.height <= 0f) return bounds

        val canvas = spec.canvas
        val scale = min(
            bounds.width / canvas.canvasWidth,
            bounds.height / canvas.canvasHeight,
        )
        val width = canvas.canvasWidth * scale
        val height = canvas.canvasHeight * scale
        val left = bounds.center.x - width / 2f
        val top = bounds.center.y - height / 2f
        return Rect(left, top, left + width, top + height)
    }

    fun cropRect(bounds: Rect): Rect {
        val canvasRect = canvasRect(bounds)
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

    private fun cropToFillTransform(bounds: Rect): PrintCropTransform {
        val crop = cropRect(bounds)
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

    private fun phaseProgress(
        transitionProgress: Float,
        delayMillis: Int,
        durationMillis: Int,
    ): Float {
        val elapsedMillis = transitionProgress.coerceIn(0f, 1f) *
            PrintTotalTransitionDurationMillis
        return ((elapsedMillis - delayMillis) / durationMillis).coerceIn(0f, 1f)
    }

    private fun springProgress(linearProgress: Float): Float =
        PrintRevealProgressSpring.getValueFromNanos(
            (PrintRevealProgressSpring.durationNanos * linearProgress.coerceIn(0f, 1f)).toLong()
        )

    private fun lerp(start: Float, end: Float, progress: Float): Float =
        start + (end - start) * progress
}

internal fun Modifier.printSafePhotoToBounds(): Modifier = graphicsLayer {
    val transform = PrintDisplayGeometry.safePhotoToBoundsTransform(
        Rect(0f, 0f, size.width, size.height)
    )
    scaleX = transform.scale
    scaleY = transform.scale
    translationX = transform.translationX
    translationY = transform.translationY
}

/** Places its single child in the fitted raw Print canvas and requires bounded constraints. */
@Composable
internal fun PrintTargetCanvasLayout(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        content = content,
        modifier = modifier,
    ) { measurables, constraints ->
        constraints.requireBounded("PrintTargetCanvasLayout")
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val canvasRect = PrintDisplayGeometry.canvasRect(
            Rect(0f, 0f, width.toFloat(), height.toFloat())
        )
        val canvasWidth = canvasRect.width.roundToInt().coerceAtLeast(0)
        val canvasHeight = canvasRect.height.roundToInt().coerceAtLeast(0)
        val placeable = measurables.single().measure(
            Constraints.fixed(canvasWidth, canvasHeight)
        )
        layout(width, height) {
            placeable.place(
                x = canvasRect.left.roundToInt(),
                y = canvasRect.top.roundToInt(),
            )
        }
    }
}

/** Places its single child in the safe 16:9 photo region and requires bounded constraints. */
@Composable
internal fun PrintTargetPhotoLayout(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        content = content,
        modifier = modifier,
    ) { measurables, constraints ->
        constraints.requireBounded("PrintTargetPhotoLayout")
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val photoRect = PrintDisplayGeometry.targetPhotoRect(
            Rect(0f, 0f, width.toFloat(), height.toFloat())
        )
        val photoWidth = photoRect.width.roundToInt().coerceAtLeast(0)
        val photoHeight = photoRect.height.roundToInt().coerceAtLeast(0)
        val placeable = measurables.single().measure(
            Constraints.fixed(photoWidth, photoHeight)
        )
        layout(width, height) {
            placeable.place(
                x = photoRect.left.roundToInt(),
                y = photoRect.top.roundToInt(),
            )
        }
    }
}

@Composable
internal fun PrintCanvasBackground(
    progress: Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawPath(
                path = PrintDisplayGeometry.canvasFramePath(
                    bounds = Rect(0f, 0f, size.width, size.height),
                    transitionProgress = progress,
                ),
                color = Color.White,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    val framePath = PrintDisplayGeometry.canvasFramePath(
                        bounds = Rect(0f, 0f, size.width, size.height),
                        transitionProgress = progress,
                    )
                    clipPath(framePath) {
                        this@drawWithContent.drawContent()
                    }
                },
            content = content,
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun PrintSharedPhoto(
    key: String,
    progress: Float,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val sharedTransitionScope = LocalSharedTransitionDialogScope.current
    val photoBoundsProgress = PrintDisplayGeometry.photoBoundsProgress(progress).coerceIn(0f, 1f)
    val cornerRadius = PrintThumbnailCornerRadius * (1f - photoBoundsProgress)
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .sharedElementBy(
                key = key,
                useSuffixKey = false,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = PrintBoundsTransform,
                renderInOverlayDuringTransition = true,
                clipInOverlayDuringTransition = with(sharedTransitionScope) {
                    OverlayClip(shape)
                },
            )
            .clip(shape),
        content = content,
    )
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

private fun Constraints.requireBounded(layoutName: String) {
    require(hasBoundedWidth && hasBoundedHeight) {
        "$layoutName requires bounded width and height"
    }
}

private fun <T> FiniteAnimationSpec<T>.withDelay(delayMillis: Int): FiniteAnimationSpec<T> {
    if (delayMillis == 0) return this
    return DelayedFiniteAnimationSpec(
        delayNanos = delayMillis * NanosPerMillisecond,
        delegate = this,
    )
}

private class DelayedFiniteAnimationSpec<T>(
    private val delayNanos: Long,
    private val delegate: FiniteAnimationSpec<T>,
) : FiniteAnimationSpec<T> {
    override fun <V : AnimationVector> vectorize(
        converter: TwoWayConverter<T, V>,
    ): VectorizedFiniteAnimationSpec<V> = DelayedVectorizedFiniteAnimationSpec(
        delayNanos = delayNanos,
        delegate = delegate.vectorize(converter),
    )
}

private class DelayedVectorizedFiniteAnimationSpec<V : AnimationVector>(
    private val delayNanos: Long,
    private val delegate: VectorizedFiniteAnimationSpec<V>,
) : VectorizedFiniteAnimationSpec<V> {
    override fun getDurationNanos(
        initialValue: V,
        targetValue: V,
        initialVelocity: V,
    ): Long = delayNanos + delegate.getDurationNanos(initialValue, targetValue, initialVelocity)

    override fun getValueFromNanos(
        playTimeNanos: Long,
        initialValue: V,
        targetValue: V,
        initialVelocity: V,
    ): V = if (playTimeNanos < delayNanos) {
        initialValue
    } else {
        delegate.getValueFromNanos(
            playTimeNanos = playTimeNanos - delayNanos,
            initialValue = initialValue,
            targetValue = targetValue,
            initialVelocity = initialVelocity,
        )
    }

    override fun getVelocityFromNanos(
        playTimeNanos: Long,
        initialValue: V,
        targetValue: V,
        initialVelocity: V,
    ): V = delegate.getVelocityFromNanos(
        playTimeNanos = (playTimeNanos - delayNanos).coerceAtLeast(0L),
        initialValue = initialValue,
        targetValue = targetValue,
        initialVelocity = initialVelocity,
    )
}
