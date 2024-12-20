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
import cafe.adriel.voyager.core.stack.StackEvent
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideOrientation


val HomeToAuthAnimeTransition =
    fadeIn(tween(600, 300)) + slideIn(tween(600)) { IntOffset(0, (it.height * 0.2f).toInt()) } togetherWith
            fadeOut(tween(600))
val AuthAnimeToHomeTransition = fadeIn(tween(600)) togetherWith
        fadeOut(tween(600)) + slideOut(tween(600)) { IntOffset(0, (it.height * 0.2f).toInt()) }
val DefaultScreenTransition = (fadeIn(animationSpec = tween(220, delayMillis = 90)) +
        scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90)))
    .togetherWith(fadeOut(animationSpec = tween(90)))

private const val BoundsAnimationDurationMillis = 500

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalAnimationSpecApi::class)
val TextBoundsTransform = BoundsTransform { initialBounds, targetBounds ->
    keyframes {
        durationMillis = BoundsAnimationDurationMillis
        initialBounds at 0 using ArcMode.ArcBelow using FastOutSlowInEasing
        targetBounds at BoundsAnimationDurationMillis
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
val IconBoundsTransform = BoundsTransform { _, _ ->
    tween(800)
}

@OptIn(ExperimentalSharedTransitionApi::class)
val DefaultBoundsTransform = BoundsTransform { _, _ -> DefaultSpring }

val DefaultSpring = spring(
    stiffness = StiffnessMediumLow,
    visibilityThreshold = Rect.VisibilityThreshold
)

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


fun slideScreenTransition(
    navigator: Navigator,
    orientation: SlideOrientation = SlideOrientation.Vertical,
): ContentTransform {
    val initialOffset = when (navigator.lastEvent) {
        StackEvent.Pop -> { size: Int -> -size }
        else -> { size: Int -> size }
    }
    val targetOffset = when (navigator.lastEvent) {
        StackEvent.Pop -> { size: Int -> size }
        else -> { size: Int -> -size }
    }
    val animationIntSpec = tween<IntOffset>()
    val animationFloatSpec = tween<Float>()
    return when (orientation) {
        SlideOrientation.Horizontal -> slideInHorizontally(animationIntSpec, initialOffset) togetherWith scaleOut(
            animationFloatSpec,
            0.8f,
            TransformOrigin(0.3f, 0.5f)
        ) + fadeOut(animationFloatSpec, 0.8f)

        SlideOrientation.Vertical ->
            slideInVertically(initialOffsetY= initialOffset)  togetherWith slideOutVertically(targetOffsetY = targetOffset)

    }
}