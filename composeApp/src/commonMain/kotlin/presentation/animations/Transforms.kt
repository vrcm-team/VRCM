package io.github.vrcmteam.vrcm.presentation.animations

import androidx.compose.animation.*
import androidx.compose.animation.SharedTransitionScope.OverlayClip
import androidx.compose.animation.SharedTransitionScope.SharedContentState
import androidx.compose.animation.core.*
import androidx.compose.animation.core.Spring.StiffnessMediumLow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
enum class SlideOrientation {
    Horizontal,
    Vertical,
}


val HomeToAuthAnimeTransition =
    fadeIn(tween(600, 300)) + slideIn(tween(600)) { IntOffset(0, (it.height * 0.2f).toInt()) } togetherWith
            fadeOut(tween(600))
val AuthAnimeToHomeTransition = fadeIn(tween(600)) togetherWith
        fadeOut(tween(600)) + slideOut(tween(600)) { IntOffset(0, (it.height * 0.2f).toInt()) }
val DefaultScreenTransition = (fadeIn(animationSpec = tween(220, delayMillis = 90)) +
        scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90)))
    .togetherWith(fadeOut(animationSpec = tween(90)))

private const val BoundsAnimationDurationMillis = 300

@OptIn(ExperimentalSharedTransitionApi::class)
val TextBoundsTransform = BoundsTransform { _, _ ->
    tween(
        durationMillis = BoundsAnimationDurationMillis,
        easing = FastOutSlowInEasing,
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
val IconBoundsTransform = BoundsTransform { _, _ ->
    tween(800)
}

@OptIn(ExperimentalSharedTransitionApi::class)
val DefaultBoundsTransform = BoundsTransform { _, _ -> DefaultSpring }

internal fun <T> defaultSpring(
    visibilityThreshold: T? = null,
): SpringSpec<T> = spring(
    stiffness = StiffnessMediumLow,
    visibilityThreshold = visibilityThreshold,
)

val DefaultSpring = defaultSpring(Rect.VisibilityThreshold)

@OptIn(ExperimentalSharedTransitionApi::class)
val ParentClip: OverlayClip =
    object : OverlayClip {
        override fun getClipPath(
            state: SharedContentState,
            bounds: Rect,
            layoutDirection: LayoutDirection,
            density: Density,
        ): Path? {
            return state.parentSharedContentState?.clipPathInOverlay
        }
    }

@OptIn(ExperimentalSharedTransitionApi::class)
val NoClip: OverlayClip =
    object : OverlayClip {
        override fun getClipPath(
            state: SharedContentState,
            bounds: Rect,
            layoutDirection: LayoutDirection,
            density: Density,
        ): Path? {
            return null
        }
    }


fun slideScreenTransition(
    isPop: Boolean,
    orientation: SlideOrientation = SlideOrientation.Vertical,
): ContentTransform {
    val initialOffset = if (isPop) ({ size: Int -> -size }) else ({ size: Int -> size })
    val targetOffset = if (isPop) ({ size: Int -> size }) else ({ size: Int -> -size })
    val animationIntSpec = tween<IntOffset>()
    val animationFloatSpec = tween<Float>()
    return when (orientation) {
        SlideOrientation.Horizontal -> slideInHorizontally(animationIntSpec, initialOffset) togetherWith
            horizontalScreenExitTransition(animationFloatSpec)

        SlideOrientation.Vertical ->
            slideInVertically(initialOffsetY = initialOffset) togetherWith scaleOut(
                animationFloatSpec,
                0.85f,
                TransformOrigin(0.5f, 0.3f)
            ) + fadeOut(animationFloatSpec)

    }
}

/**
 * 纯淡入淡出的转场：留给由共享元素承担主体运动的页面对。
 * 屏幕级再叠加缩放会和共享元素的形变打架，看起来像缩放了两次。
 */
fun fadeScreenTransition(): ContentTransform {
    val animationSpec = tween<Float>()
    return fadeIn(animationSpec) togetherWith fadeOut(animationSpec)
}

internal fun horizontalScreenExitTransition(
    animationSpec: FiniteAnimationSpec<Float>,
): ExitTransition = scaleOut(
    animationSpec = animationSpec,
    targetScale = 0.8f,
    transformOrigin = TransformOrigin(0.3f, 0.5f),
) + fadeOut(animationSpec = animationSpec)
