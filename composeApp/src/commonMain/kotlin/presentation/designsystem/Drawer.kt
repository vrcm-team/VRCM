package io.github.vrcmteam.vrcm.presentation.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class AppDrawerValue { Closed, Open }

/** 侧边抽屉的开合状态。[openFraction] 0 = 完全收起，1 = 完全展开。 */
@Stable
class AppDrawerState(initialValue: AppDrawerValue = AppDrawerValue.Closed) {
    internal val fraction = Animatable(if (initialValue == AppDrawerValue.Open) 1f else 0f)

    var currentValue: AppDrawerValue by mutableStateOf(initialValue)
        private set

    val openFraction: Float get() = fraction.value
    val isOpen: Boolean get() = currentValue == AppDrawerValue.Open
    val isClosed: Boolean get() = currentValue == AppDrawerValue.Closed
    val isAnimationRunning: Boolean get() = fraction.isRunning

    suspend fun open() = settle(AppDrawerValue.Open, 0f)

    suspend fun close() = settle(AppDrawerValue.Closed, 0f)

    internal suspend fun settle(target: AppDrawerValue, velocity: Float) {
        try {
            fraction.animateTo(
                targetValue = if (target == AppDrawerValue.Open) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.92f, stiffness = 380f),
                initialVelocity = velocity,
            )
        } finally {
            currentValue = if (fraction.value >= 0.5f) AppDrawerValue.Open else AppDrawerValue.Closed
        }
    }

    companion object {
        val Saver: Saver<AppDrawerState, AppDrawerValue> = Saver(save = { it.currentValue }, restore = { AppDrawerState(it) })
    }
}

@Composable
fun rememberAppDrawerState(initialValue: AppDrawerValue = AppDrawerValue.Closed): AppDrawerState =
    rememberSaveable(saver = AppDrawerState.Saver) { AppDrawerState(initialValue) }

/**
 * 侧边抽屉：从起始边滑入的面板，背后内容压暗；点压暗层或把面板拖回去即收起。
 * [gesturesEnabled] 为 false 时只能由代码开合。
 */
@Composable
fun AppModalDrawer(
    drawerContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    drawerState: AppDrawerState = rememberAppDrawerState(),
    gesturesEnabled: Boolean = true,
    scrimColor: Color = AppTheme.colors.scrim,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var drawerWidth by remember { mutableFloatStateOf(0f) }
    val dragState = rememberDraggableState { delta ->
        if (drawerWidth <= 0f) return@rememberDraggableState
        val signed = if (rtl) -delta else delta
        scope.launch { drawerState.fraction.snapTo((drawerState.fraction.value + signed / drawerWidth).coerceIn(0f, 1f)) }
    }
    Box(
        modifier
            .fillMaxSize()
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                enabled = gesturesEnabled,
                onDragStopped = { velocity ->
                    val signed = if (rtl) -velocity else velocity
                    val target = when {
                        signed > 800f -> AppDrawerValue.Open
                        signed < -800f -> AppDrawerValue.Closed
                        drawerState.fraction.value >= 0.5f -> AppDrawerValue.Open
                        else -> AppDrawerValue.Closed
                    }
                    drawerState.settle(target, if (drawerWidth > 0f) signed / drawerWidth else 0f)
                },
            ),
    ) {
        content()
        val openFraction = drawerState.openFraction
        if (openFraction > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(scrimColor.copy(alpha = scrimColor.alpha * openFraction))
                    .clickable(interactionSource = null, indication = null, enabled = gesturesEnabled) {
                        scope.launch { drawerState.close() }
                    },
            )
        }
        Box(
            Modifier
                .fillMaxHeight()
                // 完全收起时整块不画：面板虽然摆在屏幕外，投影仍会漏进来一条
                .graphicsLayer { alpha = if (drawerState.openFraction > 0f) 1f else 0f }
                // 宽度在测量阶段就拿到，收起时直接摆到屏幕外，首帧不会闪现
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    drawerWidth = placeable.width.toFloat()
                    layout(placeable.width, placeable.height) {
                        val hidden = (1f - drawerState.openFraction) * placeable.width
                        placeable.placeRelative(-hidden.roundToInt(), 0)
                    }
                },
        ) {
            drawerContent()
        }
    }
}

/** 抽屉面板：不透明的分组底色，外侧两角是大圆角。 */
@Composable
fun AppDrawerSheet(
    modifier: Modifier = Modifier,
    containerColor: Color = AppTheme.colors.secondarySystemBackground,
    contentColor: Color = AppTheme.colors.contentColorFor(containerColor).takeOrElse { LocalContentColor.current },
    windowInsets: WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(topEnd = AppRadius.xl, bottomEnd = AppRadius.xl)
    Column(
        modifier
            .fillMaxHeight()
            .shadow(24.dp, shape, clip = false)
            .clip(shape)
            .background(containerColor)
            .pointerInput(Unit) {}
            .windowInsetsPadding(windowInsets),
    ) {
        ProvideContentColor(contentColor) { content() }
    }
}
