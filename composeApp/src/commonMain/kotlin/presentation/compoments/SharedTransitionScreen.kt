package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.animation.*
import androidx.compose.animation.SharedTransitionScope.*
import androidx.compose.animation.SharedTransitionScope.PlaceHolderSize.Companion.contentSize
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.ScaleToBounds
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.ScreenTransition
import cafe.adriel.voyager.transitions.ScreenTransitionContent
import io.github.vrcmteam.vrcm.presentation.animations.DefaultBoundsTransform
import io.github.vrcmteam.vrcm.presentation.animations.DefaultScreenTransition
import io.github.vrcmteam.vrcm.presentation.animations.ParentClip

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScreen(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    transitionSpec: AnimatedContentTransitionScope<Screen>.() -> ContentTransform = { DefaultScreenTransition },
    content: ScreenTransitionContent = { it.Content() }
) {
    SharedTransitionLayout(modifier) {
        ScreenTransition(
            navigator = navigator,
            modifier = modifier,
            transition = transitionSpec,
        ) { screen ->
            CompositionLocalProvider(
                LocalSharedTransitionScreenScope provides this@SharedTransitionLayout,
                LocalAnimatedVisibilityScope provides this
            ) {
                content(screen)
            }
        }
    }

}


@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScreenScope: ProvidableCompositionLocal<SharedTransitionScope> =
    staticCompositionLocalOf { error("SharedTransitionScope is not provided") }

/**
 * 用于区分多个同级页面共享元素错位问题:比如两个Page中key相同的元素会在滑动时错位
 */
val LocalSharedSuffixKey: ProvidableCompositionLocal<String> =
    staticCompositionLocalOf { "" }

val LocalAnimatedVisibilityScope: ProvidableCompositionLocal<AnimatedVisibilityScope> =
    staticCompositionLocalOf { error("AnimatedVisibilityScope is not provided") }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedElementBy(
    key: String,
    useSuffixKey: Boolean = true,
    sharedTransitionScope: SharedTransitionScope = LocalSharedTransitionScreenScope.current,
    animatedVisibilityScope: AnimatedVisibilityScope = LocalAnimatedVisibilityScope.current,
    boundsTransform: BoundsTransform = DefaultBoundsTransform,
    placeHolderSize: PlaceHolderSize = contentSize,
    renderInOverlayDuringTransition: Boolean = true,
    zIndexInOverlay: Float = 0f,
    clipInOverlayDuringTransition: OverlayClip = ParentClip,
): Modifier =
    with(sharedTransitionScope) {
        val suffixKey = LocalSharedSuffixKey.current
        this@sharedElementBy.sharedElement(
            state = rememberSharedContentState(if (!useSuffixKey || suffixKey.isBlank())key else "$key:$suffixKey"),
            animatedVisibilityScope = animatedVisibilityScope,
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
    useSuffixKey: Boolean = true,
    sharedTransitionScope: SharedTransitionScope = LocalSharedTransitionScreenScope.current,
    animatedVisibilityScope: AnimatedVisibilityScope = LocalAnimatedVisibilityScope.current,
    resizeMode: ResizeMode = ScaleToBounds(ContentScale.FillWidth, Center),
    boundsTransform: BoundsTransform = DefaultBoundsTransform,
    placeHolderSize: PlaceHolderSize = contentSize,
    renderInOverlayDuringTransition: Boolean = true,
    zIndexInOverlay: Float = 0f,
    clipInOverlayDuringTransition: OverlayClip = ParentClip,
): Modifier =
    with(sharedTransitionScope) {
        val suffixKey = LocalSharedSuffixKey.current
        this@sharedBoundsBy.sharedBounds(
            sharedContentState = rememberSharedContentState(if (!useSuffixKey || suffixKey.isBlank())key else "$key:$suffixKey"),
            animatedVisibilityScope = animatedVisibilityScope,
            resizeMode = resizeMode,
            boundsTransform = boundsTransform,
            placeHolderSize = placeHolderSize,
            renderInOverlayDuringTransition = renderInOverlayDuringTransition,
            zIndexInOverlay = zIndexInOverlay,
            clipInOverlayDuringTransition = clipInOverlayDuringTransition
        )
    }


