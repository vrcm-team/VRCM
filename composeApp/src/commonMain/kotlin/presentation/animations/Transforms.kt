package io.github.vrcmteam.vrcm.presentation.animations

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import cafe.adriel.voyager.core.stack.StackEvent
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.TabNavigator
import cafe.adriel.voyager.transitions.SlideOrientation


val homeToAuthAnimeTransition = fadeIn(tween(600,300)) + slideIn(tween(600)) { IntOffset(0, (it.height * 0.2f).toInt()) } togetherWith
        fadeOut(tween(600))
val authAnimeToHomeTransition = fadeIn(tween(600)) togetherWith
        fadeOut(tween(600)) + slideOut(tween(600)) { IntOffset(0, (it.height * 0.2f).toInt()) }

fun slideScreenTransition(
    navigator: Navigator,
    orientation: SlideOrientation = SlideOrientation.Horizontal,
    animationSpec: FiniteAnimationSpec<IntOffset> = spring(
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntOffset.VisibilityThreshold
    )
): ContentTransform {
    val (initialOffset, targetOffset) = when (navigator.lastEvent) {
        StackEvent.Pop -> ({ size: Int -> -size }) to ({ size: Int -> size })
        else -> ({ size: Int -> size }) to ({ size: Int -> -size })
    }

     return when (orientation) {
        SlideOrientation.Horizontal ->
            slideInHorizontally(animationSpec, initialOffset) togetherWith
                    slideOutHorizontally(animationSpec, targetOffset)

        SlideOrientation.Vertical ->
            slideInVertically(animationSpec, initialOffset) togetherWith
                    slideOutVertically(animationSpec, targetOffset)
    }
}

fun slideTabTransition(
    tabNavigator: TabNavigator,
    orientation: SlideOrientation = SlideOrientation.Horizontal,
    animationSpec: FiniteAnimationSpec<IntOffset> = spring(
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntOffset.VisibilityThreshold
    )
): ContentTransform {
//    val (initialOffset, targetOffset) = when (navigator.lastEvent) {
//        StackEvent.Pop -> ({ size: Int -> -size }) to ({ size: Int -> size })
//        else -> ({ size: Int -> size }) to ({ size: Int -> -size })
//    }
//
//    return when (orientation) {
//        SlideOrientation.Horizontal ->
//            slideInHorizontally(animationSpec, initialOffset) togetherWith
//                    slideOutHorizontally(animationSpec, targetOffset)
//
//        SlideOrientation.Vertical ->
//            slideInVertically(animationSpec, initialOffset) togetherWith
//                    slideOutVertically(animationSpec, targetOffset)
//    }
    TODO()
}