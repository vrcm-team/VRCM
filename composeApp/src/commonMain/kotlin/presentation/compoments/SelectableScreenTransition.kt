package presentation.compoments

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.ScreenTransition

@Composable
fun SelectableScreenTransition(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    transition : AnimatedContentTransitionScope<Screen>.() -> ContentTransform,
) {
    ScreenTransition(
        navigator = navigator,
        modifier = modifier,
        transition = transition
    )
}