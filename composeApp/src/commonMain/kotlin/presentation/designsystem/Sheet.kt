package io.github.vrcmteam.vrcm.presentation.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** sheet 的停靠位：收起 / 半屏（medium detent）/ 全展开（large detent）。 */
enum class AppSheetValue { Hidden, PartiallyExpanded, Expanded }

/**
 * sheet 的状态。[skipPartiallyExpanded] 为 false 且内容高过半屏时，先停在半屏，可再往上拖到全展开。
 * [confirmValueChange] 返回 false 可以否决一次停靠位变化（例如保存中不允许下滑关闭）。
 */
@Stable
class AppSheetState(
    internal val skipPartiallyExpanded: Boolean = false,
    internal val confirmValueChange: (AppSheetValue) -> Boolean = { true },
) {
    var currentValue: AppSheetValue by mutableStateOf(AppSheetValue.Hidden)
        private set
    var targetValue: AppSheetValue by mutableStateOf(AppSheetValue.Hidden)
        private set

    val isVisible: Boolean get() = currentValue != AppSheetValue.Hidden
    val hasPartiallyExpandedState: Boolean get() = AppSheetValue.PartiallyExpanded in anchors

    /** sheet 顶边的 y（px）。 */
    internal val offset = Animatable(0f)
    internal var anchors: Map<AppSheetValue, Float> by mutableStateOf(emptyMap())
    internal var laidOut: Boolean by mutableStateOf(false)

    suspend fun show() = animateTo(if (hasPartiallyExpandedState) AppSheetValue.PartiallyExpanded else AppSheetValue.Expanded)

    suspend fun expand() = animateTo(AppSheetValue.Expanded)

    suspend fun partialExpand() = animateTo(AppSheetValue.PartiallyExpanded)

    suspend fun hide() = animateTo(AppSheetValue.Hidden)

    internal suspend fun animateTo(value: AppSheetValue, velocity: Float = 0f) {
        // targetValue 是"打算停在哪"：动画被打断（尺寸变了、用户又拖了一下）也不回退，后续重排按它接着走
        targetValue = value
        val target = anchors[value]
        if (target == null) {
            // 还没量过：没有可动画的位置，直接记下状态
            currentValue = value
            return
        }
        offset.animateTo(target, spring(dampingRatio = 0.92f, stiffness = 340f), initialVelocity = velocity)
        currentValue = value
    }

    /**
     * 尺寸变化后重算停靠位，然后（重新）滑向打算停的位置：
     * 这张 sheet 第一次量到尺寸时从屏幕外滑入；之后内容变高 / 变矮、窗口变化都只是换个落点继续走。
     */
    internal suspend fun updateAnchors(newAnchors: Map<AppSheetValue, Float>) {
        anchors = newAnchors
        if (!laidOut) {
            offset.snapTo(newAnchors.getValue(AppSheetValue.Hidden))
            laidOut = true
            targetValue = if (hasPartiallyExpandedState) AppSheetValue.PartiallyExpanded else AppSheetValue.Expanded
        }
        animateTo(if (targetValue in newAnchors) targetValue else AppSheetValue.Expanded)
    }

    /** sheet 离开组合：状态可能被调用方留着下次再用，回到"没出现过"。 */
    internal fun onDetached() {
        laidOut = false
        anchors = emptyMap()
        currentValue = AppSheetValue.Hidden
        targetValue = AppSheetValue.Hidden
    }

    internal fun closestAnchor(position: Float): AppSheetValue =
        anchors.minByOrNull { abs(it.value - position) }?.key ?: currentValue

    /** 松手后的去向：够快就按方向去下一个停靠位，否则去最近的；被 [confirmValueChange] 否决则回到原位。 */
    internal fun settleTarget(velocity: Float): AppSheetValue {
        val position = offset.value
        val ordered = anchors.entries.sortedBy { it.value }
        val candidate = when {
            velocity > FlingVelocityThreshold -> ordered.firstOrNull { it.value > position + 1f }?.key
            velocity < -FlingVelocityThreshold -> ordered.lastOrNull { it.value < position - 1f }?.key
            else -> null
        } ?: closestAnchor(position)
        return if (candidate == currentValue || confirmValueChange(candidate)) candidate else currentValue
    }

    private companion object {
        const val FlingVelocityThreshold = 1200f
    }
}

@Composable
fun rememberAppSheetState(
    skipPartiallyExpanded: Boolean = false,
    confirmValueChange: (AppSheetValue) -> Boolean = { true },
): AppSheetState {
    val currentConfirm by rememberUpdatedState(confirmValueChange)
    return remember(skipPartiallyExpanded) { AppSheetState(skipPartiallyExpanded) { currentConfirm(it) } }
}

/**
 * Sheet（HIG Sheets）：从底部升起的模态面板，顶部有 grabber，可下滑 / 点背景关闭；背后的页面被压暗。
 * 用户关闭时先滑出再回调 [onDismissRequest]；代码调 [AppSheetState.hide] 不触发回调。
 * 宽屏下限宽 640 dp 居中。面板内是"抬升"后的深色令牌（见 [AppColors.elevated]）。
 */
@Composable
fun AppSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: AppSheetState = rememberAppSheetState(),
    sheetGesturesEnabled: Boolean = true,
    containerColor: Color = AppTheme.colors.elevated().groupedBackground,
    dragHandle: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val currentOnDismiss by rememberUpdatedState(onDismissRequest)
    AppOverlay {
        val scope = rememberCoroutineScope()
        val density = LocalDensity.current
        val shape = RoundedCornerShape(topStart = AppRadius.xl, topEnd = AppRadius.xl)
        val requestDismiss: () -> Unit = {
            if (sheetState.confirmValueChange(AppSheetValue.Hidden)) {
                scope.launch {
                    sheetState.hide()
                    if (!sheetState.isVisible) currentOnDismiss()
                }
            }
        }
        val settle: (Float) -> Unit = { velocity ->
            scope.launch {
                sheetState.animateTo(sheetState.settleTarget(velocity), velocity)
                if (!sheetState.isVisible) currentOnDismiss()
            }
        }
        LocalAppOverlayBackHandler.current?.invoke(true, requestDismiss)

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val fullHeight = constraints.maxHeight.toFloat()
            var sheetHeight by remember { mutableIntStateOf(0) }
            val topGap = WindowInsets.statusBars.asPaddingValues(density).calculateTopPadding() + 12.dp

            DisposableEffect(sheetState) { onDispose { sheetState.onDetached() } }
            LaunchedEffect(fullHeight, sheetHeight, sheetState.skipPartiallyExpanded) {
                if (sheetHeight == 0) return@LaunchedEffect
                val expanded = fullHeight - sheetHeight
                val anchors = buildMap {
                    put(AppSheetValue.Hidden, fullHeight)
                    put(AppSheetValue.Expanded, expanded)
                    if (!sheetState.skipPartiallyExpanded && sheetHeight > fullHeight / 2f) {
                        put(AppSheetValue.PartiallyExpanded, fullHeight / 2f)
                    }
                }
                sheetState.updateAnchors(anchors)
            }

            val restingTop = sheetState.anchors[AppSheetValue.PartiallyExpanded]
                ?: sheetState.anchors[AppSheetValue.Expanded]
                ?: fullHeight
            val visibleFraction = if (!sheetState.laidOut || restingTop >= fullHeight) 0f else {
                ((fullHeight - sheetState.offset.value) / (fullHeight - restingTop)).coerceIn(0f, 1f)
            }
            val scrim = AppTheme.colors.scrim
            Box(
                Modifier
                    .fillMaxSize()
                    .background(scrim.copy(alpha = scrim.alpha * visibleFraction))
                    .pointerInput(Unit) { detectTapGestures { requestDismiss() } },
            )

            val minAnchor = sheetState.anchors[AppSheetValue.Expanded] ?: 0f
            val dragState = rememberDraggableState { delta ->
                scope.launch { sheetState.offset.snapTo((sheetState.offset.value + delta).coerceIn(minAnchor, fullHeight)) }
            }
            val nestedScroll = remember(sheetState, minAnchor, fullHeight) {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        // 往上滚：sheet 还没到顶就先把 sheet 拖上去，再轮到内容滚动
                        if (source != NestedScrollSource.UserInput || available.y >= 0f) return Offset.Zero
                        val current = sheetState.offset.value
                        val target = (current + available.y).coerceAtLeast(minAnchor)
                        if (target == current) return Offset.Zero
                        scope.launch { sheetState.offset.snapTo(target) }
                        return Offset(0f, target - current)
                    }

                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                        // 内容已经滚到顶还在往下拉：带着 sheet 一起下来
                        if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                        val current = sheetState.offset.value
                        scope.launch { sheetState.offset.snapTo((current + available.y).coerceAtMost(fullHeight)) }
                        return Offset(0f, available.y)
                    }

                    override suspend fun onPreFling(available: Velocity): Velocity {
                        val displaced = sheetState.anchors.values.none { abs(it - sheetState.offset.value) < 1f }
                        if (!displaced) return Velocity.Zero
                        settle(available.y)
                        return available
                    }
                }
            }

            Column(
                modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, if (sheetState.laidOut) sheetState.offset.value.roundToInt() else constraints.maxHeight) }
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight - topGap)
                    .onSizeChanged { sheetHeight = it.height }
                    .shadow(24.dp, shape, clip = false)
                    .clip(shape)
                    .background(containerColor)
                    .semantics { paneTitle = "Sheet" }
                    .pointerInput(Unit) {}
                    .then(
                        if (sheetGesturesEnabled) {
                            Modifier
                                .nestedScroll(nestedScroll)
                                .draggable(dragState, Orientation.Vertical, onDragStopped = { velocity -> settle(velocity) })
                        } else {
                            Modifier
                        }
                    )
                    .windowInsetsPadding(
                        WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom)
                    )
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            ) {
                CompositionLocalProvider(
                    LocalAppColors provides AppTheme.colors.elevated(),
                    LocalContentColor provides AppTheme.colors.label,
                    LocalGlassBackdrop provides null,
                ) {
                    if (dragHandle) {
                        Box(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(width = 36.dp, height = 5.dp).clip(CircleShape).background(AppTheme.colors.tertiaryLabel.copy(alpha = 0.5f)))
                        }
                    }
                    content()
                }
            }
        }
    }
}

/** [AppSheetAction] 是否处在 [AppSheetActionGroup] 里：在组里就不再各自带圆角与外边距。 */
private val LocalSheetActionGrouped = staticCompositionLocalOf { false }

/**
 * Sheet 里的一组动作（HIG Action sheets）：若干 [AppSheetAction] 合成一张圆角卡片，行与行之间是发丝线。
 * 分隔线就是卡片底色从行间缝隙里露出来，所以按条件增减动作行时不用自己管分隔线。
 */
@Composable
fun AppSheetActionGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalSheetActionGrouped provides true) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.page)
                .clip(AppShapes.m)
                .background(AppTheme.colors.separator),
            verticalArrangement = Arrangement.spacedBy(0.5.dp),
            content = content,
        )
    }
}

/**
 * Sheet 里的一行动作（HIG Action sheets）：整行可点，强调色文字居中；破坏性动作红字。
 * 放进 [AppSheetActionGroup] 时并入同一张卡片；单独使用时自己是一张圆角卡片。
 * 取消靠下滑 / 点背景，不另设取消按钮。
 */
@Composable
fun AppSheetAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: AppButtonRole = AppButtonRole.Default,
    content: @Composable RowScope.() -> Unit,
) {
    val c = AppTheme.colors
    val container = if (LocalSheetActionGrouped.current) {
        Modifier.fillMaxWidth()
    } else {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.page, vertical = 2.dp)
            .clip(AppShapes.m)
    }
    Row(
        modifier = modifier
            .then(container)
            .background(c.secondaryGroupedBackground)
            .clickable(interactionSource = null, indication = LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick)
            .enabledAlpha(enabled)
            .defaultMinSize(minHeight = 46.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProvideContentColor(if (role == AppButtonRole.Destructive) c.destructive else c.tint, AppTheme.type.body) { content() }
    }
}
