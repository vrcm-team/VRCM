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
import cafe.adriel.voyager.core.lifecycle.JavaSerializable
import io.github.vrcmteam.vrcm.presentation.extensions.LocalOnBackHook
import io.github.vrcmteam.vrcm.presentation.extensions.enableIf
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.extensions.simpleClickable

// 为了解决安卓序列化问题, 不能写成rememberSaveable
private val DialogContentMap = mutableMapOf<String, MutableState<SharedDialog?>>()

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionDialog(
    modifier: Modifier = Modifier,
    key: String,
    content: @Composable () -> Unit,
) {
    SharedTransitionLayout(modifier = modifier.fillMaxSize()) {
        val dialogContentState = DialogContentMap.getOrPut(key) { mutableStateOf(null) }
        CompositionLocalProvider(
            LocationDialogContent provides dialogContentState,
            LocalSharedTransitionDialogScope provides this,
        ) {
            var dialogContent by LocationDialogContent.current
            // 监听返回键
            var onBackHook by LocalOnBackHook.current
            onBackHook = {
                dialogContent?.close()
                dialogContent == null
            }

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
                ) {
                    if (targetDialogContent == null) return@AnimatedContent

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .simpleClickable {
                                dialogContent = null
                                targetDialogContent.close()
                            }
                            .background(Color.Black.copy(alpha = 0.6f))
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
    error("LocationDialogContent is not provided")
}

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionDialogScope: ProvidableCompositionLocal<SharedTransitionScope> =
    staticCompositionLocalOf { error("SharedTransitionScope is not provided") }


interface SharedDialog : JavaSerializable {

    @Composable
    fun Content(animatedVisibilityScope: AnimatedVisibilityScope)

    fun close() = Unit

}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
inline fun SharedDialogContainer(
    key: String = "",
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    background: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = getInsetPadding(16, WindowInsets::getTop) + 16.dp,
                bottom = getInsetPadding(16, WindowInsets::getBottom) + 16.dp,
            )
            .enableIf(animatedVisibilityScope != null) {
                sharedBoundsBy(
                    key = key + "Container",
                    sharedTransitionScope = LocalSharedTransitionDialogScope.current,
                    animatedVisibilityScope = animatedVisibilityScope!!,
                    clipInOverlayDuringTransition = with(LocalSharedTransitionDialogScope.current) {
                        OverlayClip(DialogShapeForSharedElement)
                    }
                )
            }
            .background(background, DialogShapeForSharedElement)
            .clip(DialogShapeForSharedElement),
        content = content
    )
}