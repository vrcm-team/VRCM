package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.extensions.simpleClickable
import presentation.compoments.DialogShapeForSharedElement


@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AAlertDialog(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    SharedTransitionLayout(modifier = modifier.fillMaxSize()) {
        var dialogContent by LocationDialogContent.current
        CompositionLocalProvider(
            LocalSharedTransitionDialogScope provides this,
        ) {
            content()
            AnimatedContent(
                modifier = Modifier.fillMaxSize(),
                targetState = dialogContent,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "SharedTransitionDialog"
            ) { targetDialogContent ->
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ){
                    if (targetDialogContent == null) return@AnimatedContent
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .simpleClickable {
                                dialogContent = null
                                targetDialogContent.close()
                            }
                            .background(Color.Black.copy(alpha = 0.5f))
                    )
                    targetDialogContent.Content(
                        animatedVisibilityScope = this@AnimatedContent
                    )
                }

            }
        }

    }
}

val LocationDialogContent: ProvidableCompositionLocal<MutableState<SharedDialog?>> = compositionLocalOf {
    mutableStateOf(null)
}

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionDialogScope: ProvidableCompositionLocal<SharedTransitionScope> =
    staticCompositionLocalOf { error("SharedTransitionScope is not provided") }


interface SharedDialog {
    @Composable
    fun Content(animatedVisibilityScope: AnimatedVisibilityScope)

    fun close()

}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
inline fun SharedDialogContainer(
    key: String,
    animatedVisibilityScope: AnimatedVisibilityScope,
    background: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit
){
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .sharedBoundsBy(
                key = key + "Container",
                sharedTransitionScope = LocalSharedTransitionDialogScope.current,
                animatedVisibilityScope = animatedVisibilityScope,
                clipInOverlayDuringTransition = with(LocalSharedTransitionDialogScope.current) {
                    OverlayClip(DialogShapeForSharedElement)
                }
            )
            .background(background, DialogShapeForSharedElement)
            .clip(DialogShapeForSharedElement)
            ,
        content = content
    )
}