package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.animation.*
import androidx.compose.animation.SharedTransitionScope.OverlayClip
import androidx.compose.animation.SharedTransitionScope.PlaceHolderSize
import androidx.compose.animation.SharedTransitionScope.PlaceHolderSize.Companion.contentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.ScreenTransition
import io.github.vrcmteam.vrcm.presentation.animations.DefaultBoundsTransform
import io.github.vrcmteam.vrcm.presentation.animations.DefaultScreenTransition
import io.github.vrcmteam.vrcm.presentation.animations.ParentClip

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ScreenSharedTransition(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    transitionSpec: AnimatedContentTransitionScope<Screen>.() -> ContentTransform = { DefaultScreenTransition },
) {
    SharedTransitionLayout(modifier) {
        ScreenTransition(
            navigator = navigator,
            modifier = modifier,
            transition = transitionSpec,
        ){ screen ->
            CompositionLocalProvider(
                LocalSharedTransitionScope provides this@SharedTransitionLayout,
                LocalAnimatedVisibilityScope provides this
            ) {
                screen.Content()
            }
        }
    }


}


@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope: ProvidableCompositionLocal<SharedTransitionScope> =
    staticCompositionLocalOf { error("SharedTransitionScope is not provided") }

val LocalAnimatedVisibilityScope: ProvidableCompositionLocal<AnimatedVisibilityScope> =
    staticCompositionLocalOf { error("AnimatedVisibilityScope is not provided") }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedElementBy(
    key: String,
    boundsTransform: BoundsTransform = DefaultBoundsTransform,
    placeHolderSize: PlaceHolderSize = contentSize,
    renderInOverlayDuringTransition: Boolean = true,
    zIndexInOverlay: Float = 0f,
    clipInOverlayDuringTransition: OverlayClip = ParentClip
): Modifier =
    with(LocalSharedTransitionScope.current) {
        this@sharedElementBy.sharedElement(
            state =  rememberSharedContentState(key),
            animatedVisibilityScope = LocalAnimatedVisibilityScope.current,
            boundsTransform = boundsTransform,
            placeHolderSize = placeHolderSize,
            renderInOverlayDuringTransition = renderInOverlayDuringTransition,
            zIndexInOverlay = zIndexInOverlay,
            clipInOverlayDuringTransition = clipInOverlayDuringTransition
        )
    }


@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedBoundsBy(
    key: String,
    boundsTransform: BoundsTransform = DefaultBoundsTransform,
    placeHolderSize: PlaceHolderSize = contentSize,
    renderInOverlayDuringTransition: Boolean = true,
    zIndexInOverlay: Float = 0f,
    clipInOverlayDuringTransition: OverlayClip = ParentClip
): Modifier =
    with(LocalSharedTransitionScope.current) {
        this@sharedBoundsBy.sharedBounds(
            sharedContentState =  rememberSharedContentState(key),
            animatedVisibilityScope = LocalAnimatedVisibilityScope.current,
            boundsTransform = boundsTransform,
            placeHolderSize = placeHolderSize,
            renderInOverlayDuringTransition = renderInOverlayDuringTransition,
            zIndexInOverlay = zIndexInOverlay,
            clipInOverlayDuringTransition = clipInOverlayDuringTransition
        )
    }


