package presentation.compoments

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.ScreenTransition
import cafe.adriel.voyager.transitions.ScreenTransitionContent

@Composable
fun SelectableTransitionScreen(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    content: ScreenTransitionContent = { it.Content() },
    transition : AnimatedContentTransitionScope<Screen>.() -> ContentTransform,
) {
    ScreenTransition(
        navigator = navigator,
        modifier = modifier,
        transition = transition,
        content = content
    )
}