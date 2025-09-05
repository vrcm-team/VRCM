package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

@Composable
fun AnimatedDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    enter: EnterTransition = fadeIn() + slideInVertically { (it * 0.2).roundToInt() },
    exit: ExitTransition = fadeOut() + slideOutVertically { (it * 0.2).roundToInt() },
    content: @Composable () -> Unit
) {
    val transitionState = remember { MutableTransitionState(false) }
    LaunchedEffect(visible) { transitionState.targetState = visible }
    if (transitionState.currentState || !transitionState.isIdle) {
        Dialog(onDismissRequest = onDismissRequest, properties = properties) {
            AnimatedVisibility(
                modifier = Modifier,
                visibleState = transitionState,
                enter = enter,
                exit = exit
            ) {
                CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                    content()
                }
            }
        }
    }
}

@Composable
fun AnimatedDialogExample() {
    var isVisible by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedDialog(
            visible = isVisible,
            onDismissRequest = { isVisible = false }
        ) {
            Surface(
                modifier = Modifier.height(250.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Hello World")
                    Button(onClick = { isVisible = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("Click Me")
                    }
                }

            }
        }
        Button(modifier = Modifier.align(Alignment.Center), onClick = { isVisible = true }) {
            Text("Click Me")
        }
    }
}