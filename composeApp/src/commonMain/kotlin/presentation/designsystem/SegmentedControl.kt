package io.github.vrcmteam.vrcm.presentation.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 分段控件（HIG Segmented controls）：多选一用它或菜单，不用单选按钮组。
 * 选中的浮起胶囊用弹簧滑到新位置；Reduce Motion 时瞬移。
 */
@Composable
fun AppSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    AppSegmentedControl(
        count = options.size,
        selectedIndex = selectedIndex,
        onSelect = onSelect,
        modifier = modifier,
        enabled = enabled,
    ) { index -> AppText(options[index], maxLines = 1) }
}

/** 插槽版：每段内容任意（图标、图标 + 文字）。 */
@Composable
fun AppSegmentedControl(
    count: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    segmentEnabled: (Int) -> Boolean = { true },
    segment: @Composable (index: Int) -> Unit,
) {
    SegmentedTrack(selectedIndex = selectedIndex, count = count, modifier = modifier.fillMaxWidth().height(36.dp)) {
        repeat(count) { index ->
            val selected = index == selectedIndex
            val segmentIsEnabled = enabled && segmentEnabled(index)
            Box(
                Modifier
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .semantics { this.selected = selected }
                    .clickable(interactionSource = null, indication = null, enabled = segmentIsEnabled, role = Role.Tab) { onSelect(index) }
                    .enabledAlpha(segmentIsEnabled)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                ProvideContentColor(
                    AppTheme.colors.label,
                    AppTheme.type.footnote.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                ) { segment(index) }
            }
        }
    }
}

/**
 * 分段行：子项是若干 [AppSegment]，各自声明是否选中（选中项淡入浮起胶囊）。
 * 选中序号不在一处、或每段的可用性不同的表单用这个；能给出选中序号时优先用 [AppSegmentedControl]（胶囊会滑动）。
 */
@Composable
fun AppSegmentedRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .width(IntrinsicSize.Min)
            .height(36.dp)
            .clip(CircleShape)
            .background(AppTheme.colors.fill)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun RowScope.AppSegment(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable () -> Unit,
) {
    val c = AppTheme.colors
    val motion = AppTheme.motion
    val pill = if (c.isDark) Color(0xFF636366) else Color.White
    val container by animateColorAsState(if (selected) pill else pill.copy(alpha = 0f), tween(motion.fastMs), label = "segmentContainer")
    Box(
        modifier
            .weight(1f)
            .fillMaxHeight()
            .then(if (selected) Modifier.shadow(1.dp, CircleShape, clip = false) else Modifier)
            .clip(CircleShape)
            .background(container)
            .semantics { this.selected = selected }
            .clickable(interactionSource = null, indication = null, enabled = enabled, role = Role.Tab, onClick = onClick)
            .enabledAlpha(enabled)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        ProvideContentColor(
            c.label,
            AppTheme.type.footnote.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            label,
        )
    }
}

// ---------- 标签页：页内的几个并列视图（配合 Pager），外观是同一套分段控件 ----------

private val LocalTabRowScrollable = compositionLocalOf { false }

/**
 * 页内标签：等分宽度的分段控件，[selectedTabIndex] 的浮起胶囊滑动过去。内容是若干 [AppTab]。
 * 整体占 48 dp 高（36 dp 轨道 + 上下各 6 dp）。
 */
@Composable
fun AppTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    tabs: @Composable () -> Unit,
) {
    Box(modifier.fillMaxWidth().padding(horizontal = AppSpacing.page, vertical = 6.dp)) {
        SegmentedTrack(selectedIndex = selectedTabIndex, count = null, modifier = Modifier.fillMaxWidth().height(36.dp), content = tabs)
    }
}

/** 标签多到一行放不下时：横向滚动的一排胶囊，选中项着色。 */
@Composable
fun AppScrollableTabRow(
    modifier: Modifier = Modifier,
    edgePadding: Dp = AppSpacing.page,
    tabs: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalTabRowScrollable provides true) {
        Row(
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = edgePadding, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { tabs() }
    }
}

@Composable
fun AppTab(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: @Composable () -> Unit,
) {
    val c = AppTheme.colors
    val scrollable = LocalTabRowScrollable.current
    val weight = if (selected) FontWeight.SemiBold else FontWeight.Medium
    Box(
        modifier
            .then(if (scrollable) Modifier.height(36.dp) else Modifier.fillMaxHeight())
            .clip(CircleShape)
            .then(if (scrollable) Modifier.background(if (selected) c.tint else c.fill) else Modifier)
            .semantics { this.selected = selected }
            .clickable(interactionSource = null, indication = null, enabled = enabled, role = Role.Tab, onClick = onClick)
            .enabledAlpha(enabled)
            .defaultMinSize(minWidth = 44.dp)
            .padding(horizontal = if (scrollable) 14.dp else 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        ProvideContentColor(
            if (scrollable && selected) c.onTint else c.label,
            AppTheme.type.footnote.copy(fontWeight = weight),
            text,
        )
    }
}

/**
 * 分段轨道：灰底胶囊里等分摆放子项，选中胶囊画在子项后面并用弹簧滑动。
 * [count] 为 null 时以实际子项数为准（标签页的子项由调用方发出）。
 */
@Composable
private fun SegmentedTrack(
    selectedIndex: Int,
    count: Int?,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val c = AppTheme.colors
    val motion = AppTheme.motion
    val indicator = remember { Animatable(selectedIndex.toFloat()) }
    LaunchedEffect(selectedIndex) {
        if (motion.reduced) {
            indicator.snapTo(selectedIndex.toFloat())
        } else {
            indicator.animateTo(selectedIndex.toFloat(), spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    val pill = if (c.isDark) Color(0xFF636366) else Color.White
    val pillShadow = Color.Black.copy(alpha = if (c.isDark) 0.3f else 0.12f)
    // 子项数在测量时才知道，绘制阶段读取
    val segments = remember { mutableIntStateOf(count ?: 0) }
    Layout(
        content = content,
        modifier = modifier
            .clip(CircleShape)
            .background(c.fill)
            .padding(3.dp)
            .drawBehind {
                val segmentCount = segments.intValue
                if (segmentCount <= 0) return@drawBehind
                val slot = size.width / segmentCount
                val left = indicator.value * slot
                val radius = CornerRadius(size.height / 2f)
                drawRoundRect(pillShadow, topLeft = Offset(left, 1.dp.toPx()), size = Size(slot, size.height), cornerRadius = radius)
                drawRoundRect(pill, topLeft = Offset(left, 0f), size = Size(slot, size.height), cornerRadius = radius)
            },
    ) { measurables, constraints ->
        segments.intValue = measurables.size
        if (measurables.isEmpty()) return@Layout layout(constraints.maxWidth, constraints.maxHeight) {}
        val slot = constraints.maxWidth / measurables.size
        val cell = Constraints.fixed(slot, constraints.maxHeight)
        val placeables = measurables.map { it.measure(cell) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { index, placeable -> placeable.placeRelative(index * slot, 0) }
        }
    }
}
