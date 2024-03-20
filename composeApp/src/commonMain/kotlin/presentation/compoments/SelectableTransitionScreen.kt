package presentation.compoments

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.ScreenTransition
import cafe.adriel.voyager.transitions.ScreenTransitionContent
import io.github.vrcmteam.vrcm.presentation.extensions.slideBack

@Composable
fun SelectableTransitionScreen(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    content: ScreenTransitionContent = { it.Content() },
    transition : AnimatedContentTransitionScope<Screen>.() -> ContentTransform,
) {
    ScreenTransition(
        navigator = navigator,
        modifier = modifier.slideBack(),
        transition = transition,
        content = content
    )
}