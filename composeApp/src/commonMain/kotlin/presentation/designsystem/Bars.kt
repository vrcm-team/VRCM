package io.github.vrcmteam.vrcm.presentation.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

// ---------- 功能层：导航栏 / 标签栏 / 侧边导航（玻璃，内容从下面滚过）----------

/**
 * 透明导航栏（iOS 26 样式）：左返回 / 中标题 / 右动作，按钮自动是玻璃圆钮。
 * 栏本身不画底，只在背后铺一层滚动边缘效果（[scrollEdgeEffect]），内容从下面滚过时渐进模糊淡出而不是撞上按钮。
 * 标题居中，但绝不压到两侧按钮：两侧先量，标题只能用中间剩下的宽度——居中放不下就往空的一侧挪，再放不下就截断。
 */
@Composable
fun AppNavBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    edgeColor: Color = AppTheme.colors.groupedBackground,
) {
    Layout(
        modifier = modifier
            .fillMaxWidth()
            .scrollEdgeEffect(edgeColor)
            .windowInsetsPadding(windowInsets)
            .padding(horizontal = AppSpacing.m)
            .heightIn(min = AppSize.navBar),
        content = {
            CompositionLocalProvider(LocalAppControlContext provides AppControlContext.NavBar) {
                Row(
                    Modifier.layoutId("leading"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { navigationIcon() }
                Row(
                    Modifier.layoutId("trailing"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
            Box(Modifier.layoutId("title"), contentAlignment = Alignment.Center) {
                ProvideContentColor(AppTheme.colors.label, AppTheme.type.headline, title)
            }
        },
    ) { measurables, constraints ->
        val gap = 8.dp.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val leadingP = measurables.first { it.layoutId == "leading" }.measure(loose)
        val trailingP = measurables.first { it.layoutId == "trailing" }.measure(loose)
        val width = constraints.maxWidth
        val lw = leadingP.width
        val tw = trailingP.width
        // 标题可用区：两侧按钮之外各留一个间隙
        val freeStart = if (lw > 0) lw + gap else 0
        val freeEnd = width - (if (tw > 0) tw + gap else 0)
        val free = (freeEnd - freeStart).coerceAtLeast(0)
        val titleP = measurables.first { it.layoutId == "title" }.measure(loose.copy(maxWidth = free))
        val height = maxOf(constraints.minHeight, leadingP.height, trailingP.height, titleP.height)
        layout(width, height) {
            leadingP.placeRelative(0, (height - leadingP.height) / 2)
            trailingP.placeRelative(width - tw, (height - trailingP.height) / 2)
            val centered = (width - titleP.width) / 2
            val x = centered.coerceIn(freeStart, (freeEnd - titleP.width).coerceAtLeast(freeStart))
            titleP.placeRelative(x, (height - titleP.height) / 2)
        }
    }
}

/**
 * 浮动胶囊标签栏（HIG Tab bars：只导航不执行动作；不隐藏、不禁用）。
 * 选中底是一枚胶囊，用弹簧滑到新位置，路上先胀大再缩回；图标颜色渐变。系统「减少动态效果」时全部瞬切。
 * [item] 画单个标签的内容（图标 / 图标 + 单词标签），前景色已按选中态给好。
 */
@Composable
fun AppTabBar(
    itemCount: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemWidth: Dp = 72.dp,
    item: @Composable (index: Int, selected: Boolean) -> Unit,
) {
    val c = AppTheme.colors
    val motion = AppTheme.motion
    val density = LocalDensity.current
    val gap = TabBarItemGap
    // 选中底的位置：以标签序号为单位做弹簧插值，画在所有标签后面。记住起点，路上按进度让胶囊先胀大再缩回。
    val indicator = remember { Animatable(selectedIndex.toFloat()) }
    var from by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    LaunchedEffect(selectedIndex) {
        from = indicator.value // 连点时从半路接着走
        if (motion.reduced) {
            indicator.snapTo(selectedIndex.toFloat())
        } else {
            indicator.animateTo(selectedIndex.toFloat(), spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    val pill = if (c.isDark) c.label.copy(alpha = 0.14f) else c.systemBackground.copy(alpha = 0.8f)
    Row(
        modifier
            .glass(CircleShape)
            .height(AppSize.tabBar)
            .padding(TabBarPadding)
            .drawBehind {
                if (itemCount <= 0) return@drawBehind
                val slot = with(density) { itemWidth.toPx() }
                val gapPx = with(density) { gap.toPx() }
                val pos = indicator.value
                val total = selectedIndex - from
                val progress = if (abs(total) < 0.001f || motion.reduced) 1f else ((pos - from) / total).coerceIn(0f, 1f)
                // sin(π) 在浮点里略小于 0，负数开分数次方是 NaN → 先夹到 0
                val grow = sin(PI.toFloat() * progress).coerceIn(0f, 1f).pow(0.5f)
                val w = slot * (1f + 0.30f * grow)
                val h = size.height * (1f + 0.16f * grow)
                val cx = pos * (slot + gapPx) + slot / 2f
                drawRoundRect(pill, topLeft = Offset(cx - w / 2f, (size.height - h) / 2f), size = Size(w, h), cornerRadius = CornerRadius(h / 2f))
            },
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(itemCount) { index ->
            val selected = index == selectedIndex
            val foreground by animateColorAsState(
                if (selected) c.tint else c.secondaryLabel,
                if (motion.reduced) snap() else tween(motion.normalMs),
                label = "tabColor",
            )
            // 新选中时内容轻弹一下（1 → 1.12 → 1）
            val bounce = remember { Animatable(1f) }
            LaunchedEffect(selected) {
                if (selected && !motion.reduced) {
                    bounce.animateTo(1.12f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                    bounce.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
                } else {
                    bounce.snapTo(1f)
                }
            }
            Box(
                Modifier
                    .width(itemWidth)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .semantics { this.selected = selected }
                    .clickable(interactionSource = null, indication = null, role = Role.Tab) { onSelect(index) }
                    .scale(bounce.value),
                contentAlignment = Alignment.Center,
            ) {
                ProvideContentColor(foreground, AppTheme.type.caption2Emphasized) { item(index, selected) }
            }
        }
    }
}

private val TabBarPadding = 6.dp
private val TabBarItemGap = 2.dp

/** [AppTabBar] 要在 [availableWidth] 里放下 [itemCount] 项时，每项最多能有多宽（不超过 [preferred]）。 */
fun appTabBarItemWidth(availableWidth: Dp, itemCount: Int, preferred: Dp = 72.dp): Dp {
    if (itemCount <= 0) return preferred
    val chrome = TabBarPadding * 2 + TabBarItemGap * (itemCount - 1)
    return ((availableWidth - chrome) / itemCount).coerceIn(0.dp, preferred)
}

/**
 * 标签栏旁边的独立圆钮（iOS 26 标签栏右侧的搜索钮）：和标签栏同高、同一种玻璃，前景色同未选中的标签。
 * 标签栏只导航；全局都用得上的那一个动作放在这里，而不是挤进标签里。图标必须给 contentDescription。
 */
@Composable
fun AppTabBarAccessoryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .size(AppSize.tabBar)
            .glass(CircleShape)
            .clickable(
                interactionSource = null,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        ProvideContentColor(AppTheme.colors.secondaryLabel, content = content)
    }
}

/**
 * 宽屏的侧边导航：竖排的图标 + 单词标签，选中项是淡色底胶囊。只导航、不执行动作（HIG Tab bars / Sidebars）。
 */
@Composable
fun AppNavigationRail(
    modifier: Modifier = Modifier,
    containerColor: Color = AppTheme.colors.secondaryGroupedBackground,
    windowInsets: WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(containerColor)
            .windowInsetsPadding(windowInsets)
            .width(80.dp)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
fun AppNavigationRailItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: (@Composable () -> Unit)? = null,
) {
    val c = AppTheme.colors
    val motion = AppTheme.motion
    val animation = if (motion.reduced) snap<Color>() else tween(motion.normalMs)
    val foreground by animateColorAsState(if (selected) c.tint else c.secondaryLabel, animation, label = "railItemColor")
    val container by animateColorAsState(if (selected) c.tintSoft else Color.Transparent, animation, label = "railItemContainer")
    Column(
        modifier = modifier
            .width(68.dp)
            .clip(AppShapes.m)
            .background(container)
            .semantics { this.selected = selected }
            .clickable(interactionSource = null, indication = LocalIndication.current, enabled = enabled, role = Role.Tab, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ProvideContentColor(foreground, AppTheme.type.caption2Emphasized) {
            icon()
            label?.invoke()
        }
    }
}
