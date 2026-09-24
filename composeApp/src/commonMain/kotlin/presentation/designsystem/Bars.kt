package io.github.vrcmteam.vrcm.presentation.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
    GlassTabBar(vertical = false, itemCount, selectedIndex, onSelect, modifier, itemWidth, item)
}

/**
 * 宽屏的侧边标签栏：把 [AppTabBar] 竖过来贴在页面前缘（visionOS 标签栏的放法），同一种玻璃胶囊、选中胶囊和动效。
 * 每项与横排标签同高、宽 [itemWidth]；全局动作（搜索）照样用 [AppTabBarAccessoryButton] 单独放在旁边。
 */
@Composable
fun AppSideTabBar(
    itemCount: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemWidth: Dp = 72.dp,
    item: @Composable (index: Int, selected: Boolean) -> Unit,
) {
    GlassTabBar(vertical = true, itemCount, selectedIndex, onSelect, modifier, itemWidth, item)
}

@Composable
private fun GlassTabBar(
    vertical: Boolean,
    itemCount: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
    itemWidth: Dp,
    item: @Composable (index: Int, selected: Boolean) -> Unit,
) {
    val c = AppTheme.colors
    val motion = AppTheme.motion
    val density = LocalDensity.current
    val gap = TabBarItemGap
    // 竖排时每项与横排标签同高：两种栏里的标签内容是同一个尺寸
    val itemHeight = AppSize.tabBar - TabBarPadding * 2
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
    // 选中胶囊要和玻璃拉开对比：浅色玻璃透出下面的模糊内容时，胶囊是更亮的一块；
    // 没有可模糊的背景（侧边栏）、降低透明度或平台不支持模糊时，玻璃退成近乎不透明的白，亮胶囊会看不见，改用灰色填充
    val glassBlurs = rememberGlassSpec(backdropAvailable = LocalGlassBackdrop.current != null).blurEnabled
    val pill = when {
        c.isDark -> c.label.copy(alpha = 0.14f)
        glassBlurs -> c.systemBackground.copy(alpha = 0.8f)
        else -> c.fill
    }
    val barModifier = modifier
        .glass(TabBarShape)
        .then(if (vertical) Modifier.width(itemWidth + TabBarPadding * 2) else Modifier.height(AppSize.tabBar))
        .padding(TabBarPadding)
        .drawBehind {
            if (itemCount <= 0) return@drawBehind
            // 沿排列方向的一格：横排是标签宽，竖排是标签高
            val slot = with(density) { (if (vertical) itemHeight else itemWidth).toPx() }
            val gapPx = with(density) { gap.toPx() }
            val pos = indicator.value
            val total = selectedIndex - from
            val progress = if (abs(total) < 0.001f || motion.reduced) 1f else ((pos - from) / total).coerceIn(0f, 1f)
            // sin(π) 在浮点里略小于 0，负数开分数次方是 NaN → 先夹到 0
            val grow = sin(PI.toFloat() * progress).coerceIn(0f, 1f).pow(0.5f)
            // 沿排列方向胀 30%，另一个方向胀 16%
            val along = slot * (1f + 0.30f * grow)
            val across = (if (vertical) size.width else size.height) * (1f + 0.16f * grow)
            val center = pos * (slot + gapPx) + slot / 2f
            val pillSize = if (vertical) Size(across, along) else Size(along, across)
            val topLeft = if (vertical) {
                Offset((size.width - across) / 2f, center - along / 2f)
            } else {
                Offset(center - along / 2f, (size.height - across) / 2f)
            }
            drawRoundRect(pill, topLeft, pillSize, CornerRadius(minOf(pillSize.width, pillSize.height) / 2f))
        }
    val itemModifier = if (vertical) Modifier.height(itemHeight).fillMaxWidth() else Modifier.width(itemWidth).fillMaxHeight()
    val items: @Composable () -> Unit = {
        repeat(itemCount) { index ->
            TabBarItem(selected = index == selectedIndex, onClick = { onSelect(index) }, modifier = itemModifier) { selected ->
                item(index, selected)
            }
        }
    }
    if (vertical) {
        Column(barModifier, verticalArrangement = Arrangement.spacedBy(gap), horizontalAlignment = Alignment.CenterHorizontally) {
            items()
        }
    } else {
        Row(barModifier, horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
            items()
        }
    }
}

/** 标签栏里的一项：前景色随选中渐变，新选中时内容轻弹一下（1 → 1.12 → 1）。 */
@Composable
private fun TabBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable (selected: Boolean) -> Unit,
) {
    val c = AppTheme.colors
    val motion = AppTheme.motion
    val foreground by animateColorAsState(
        if (selected) c.tint else c.secondaryLabel,
        if (motion.reduced) snap() else tween(motion.normalMs),
        label = "tabColor",
    )
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
        modifier
            .clip(CircleShape)
            .semantics { this.selected = selected }
            .clickable(interactionSource = null, indication = null, role = Role.Tab, onClick = onClick)
            .scale(bounce.value),
        contentAlignment = Alignment.Center,
    ) {
        ProvideContentColor(foreground, AppTheme.type.caption2Emphasized) { content(selected) }
    }
}

private val TabBarPadding = 6.dp
private val TabBarItemGap = 2.dp

/**
 * 栏的圆角 = 选中胶囊的半径 + 内边距：选中胶囊走到两端时与栏的圆角同心。
 * 横排时正好是整条胶囊；竖排时栏比选中胶囊宽，不能用胶囊形，否则两端的弧度对不上。
 */
private val TabBarShape = RoundedCornerShape(AppSize.tabBar / 2)

/** 每项宽 [itemWidth] 的 [AppSideTabBar] 整条有多宽。 */
fun appSideTabBarWidth(itemWidth: Dp = 72.dp): Dp = itemWidth + TabBarPadding * 2

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
