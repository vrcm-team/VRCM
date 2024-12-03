package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.animation.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ScreenSharedTransition(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    contentKey: (Screen) -> Any = { it.key },
    transitionSpec: AnimatedContentTransitionScope<Screen>.() -> ContentTransform,
    content: @Composable (Screen) -> Unit = {screen ->
        screen.Content()
    },
) {
    SharedTransitionLayout(modifier) {
        AnimatedContent(
            targetState = navigator.lastItem,
            transitionSpec = transitionSpec,
            contentAlignment = contentAlignment,
            contentKey = contentKey,
        ) { screen ->
            navigator.saveableState("transition", screen) {
                CompositionLocalProvider(
                    LocalSharedTransitionScope provides this@SharedTransitionLayout,
                    LocalAnimatedVisibilityScope provides this
                ){
                    content(screen)
                }
            }
        }
    }


}



@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope: ProvidableCompositionLocal<SharedTransitionScope> =
    staticCompositionLocalOf { error( "SharedTransitionScope is not provided") }

val LocalAnimatedVisibilityScope: ProvidableCompositionLocal<AnimatedVisibilityScope> =
    staticCompositionLocalOf { error( "AnimatedVisibilityScope is not provided") }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
inline fun Modifier.sharedElementBy(key: String): Modifier =
    with(LocalSharedTransitionScope.current){
        this@sharedElementBy.sharedElement(rememberSharedContentState(key),LocalAnimatedVisibilityScope.current)
    }
