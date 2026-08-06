package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.extensions.enableIf
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.extensions.simpleClickable
import io.github.vrcmteam.vrcm.presentation.navigation.HandleBackNavigation

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
            val dialogTransition = updateTransition(
                targetState = dialogContent,
                label = "SharedTransitionDialogState",
            )
            val dialogTransitionProgress = sharedDialogTransitionProgress(dialogTransition)
            val dialogTransitionKey = sharedDialogTransitionKey(dialogTransition)
            // 监听返回键
            val closeDialog = {
                dialogContent?.close()
                dialogContent = null
            }
            HandleBackNavigation(enabled = dialogContent != null, onBack = closeDialog)
            CompositionLocalProvider(
                LocalSharedDialogTransitionProgress provides dialogTransitionProgress,
                LocalSharedDialogTransitionKey provides dialogTransitionKey,
            ) {
                content()
                dialogTransition.AnimatedContent(
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = { sharedDialogContentTransform() },
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
                                .simpleClickable(onClick = closeDialog)
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
}

internal fun sharedDialogTransitionDuration(
    initialState: SharedDialog?,
    targetState: SharedDialog?,
): Int = maxOf(
    initialState?.transitionDurationMillis ?: 0,
    targetState?.transitionDurationMillis ?: 0,
).coerceAtLeast(0)

internal fun AnimatedContentTransitionScope<SharedDialog?>.sharedDialogContentTransform(): ContentTransform =
    if (sharedDialogTransitionDuration(initialState, targetState) > 0) {
        EnterTransition.None togetherWith
            (ExitTransition.None + ExitTransition.KeepUntilTransitionsFinished)
    } else {
        fadeIn() togetherWith fadeOut()
    }

@Composable
internal fun sharedDialogTransitionProgress(
    transition: Transition<SharedDialog?>,
): Float {
    val progress by transition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = sharedDialogTransitionDuration(initialState, targetState),
                easing = LinearEasing,
            )
        },
        label = "SharedDialogTransitionProgress",
    ) { state ->
        if (state == null) 0f else 1f
    }
    return progress
}

internal fun sharedDialogTransitionKey(
    transition: Transition<SharedDialog?>,
): String? = transition.segment.targetState?.sharedTransitionKey
    ?: transition.segment.initialState?.sharedTransitionKey

val LocationDialogContent: ProvidableCompositionLocal<MutableState<SharedDialog?>> = compositionLocalOf {
    error("LocationDialogContent is not provided")
}

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionDialogScope: ProvidableCompositionLocal<SharedTransitionScope> =
    staticCompositionLocalOf { error("SharedTransitionScope is not provided") }

val LocalSharedDialogTransitionProgress: ProvidableCompositionLocal<Float> =
    compositionLocalOf { 1f }

val LocalSharedDialogTransitionKey: ProvidableCompositionLocal<String?> =
    compositionLocalOf { null }

interface SharedDialog {

    val transitionDurationMillis: Int
        get() = 0

    val sharedTransitionKey: String?
        get() = null

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
