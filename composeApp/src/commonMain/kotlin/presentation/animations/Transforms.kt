package io.github.vrcmteam.vrcm.presentation.animations

import androidx.compose.animation.*
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import cafe.adriel.voyager.core.stack.StackEvent
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideOrientation


val homeToAuthAnimeTransition = fadeIn(tween(600,300)) + slideIn(tween(600)) { IntOffset(0, (it.height * 0.2f).toInt()) } togetherWith
        fadeOut(tween(600))
val authAnimeToHomeTransition = fadeIn(tween(600)) togetherWith
        fadeOut(tween(600)) + slideOut(tween(600)) { IntOffset(0, (it.height * 0.2f).toInt()) }

fun slideScreenTransition(
    navigator: Navigator,
    orientation: SlideOrientation = SlideOrientation.Horizontal,
    inAnimationSpec: FiniteAnimationSpec<IntOffset> = tween(),
    outAnimationSpec: FiniteAnimationSpec<Float> = tween()
): ContentTransform {
    val initialOffset = when (navigator.lastEvent) {
        StackEvent.Pop -> { size: Int -> -size }
        else -> { size: Int -> size }
    }

     return when (orientation) {
        SlideOrientation.Horizontal ->
            slideInHorizontally(inAnimationSpec, initialOffset)  togetherWith
                      scaleOut(outAnimationSpec, 0.8f, TransformOrigin(0.3f, 0.5f)) + fadeOut(outAnimationSpec,0.8f)

        SlideOrientation.Vertical ->
            slideInVertically(inAnimationSpec, initialOffset) togetherWith
                     scaleOut(outAnimationSpec,0.8f, TransformOrigin(0.5f, 0.3f)) + fadeOut(outAnimationSpec,0.8f)
    }
}
