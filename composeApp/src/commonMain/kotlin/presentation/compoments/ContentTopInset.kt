package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.offset

/**
 * 内容区顶上被悬浮的顶栏盖住的高度（px）。内容铺到顶栏下面（顶栏是模糊玻璃，内容从它下面滚过），
 * 所以列表要把这段高度让出来。
 *
 * 主页的顶栏会随滚动收起，[current] 是个随时在变的量：只在测量 / 布局阶段读它，变化时只重新测量、不重组。
 * [expanded] 是顶栏完全显示时的高度，给下拉刷新指示器这类"栏一定是露着的"场合用。
 */
@Stable
class ContentTopInset(val current: () -> Int, val expanded: () -> Int) {
    companion object {
        val None = ContentTopInset(current = { 0 }, expanded = { 0 })
    }
}

/** 不在带悬浮顶栏的骨架里时是 [ContentTopInset.None]。 */
val LocalContentTopInset = staticCompositionLocalOf { ContentTopInset.None }

/** 列表的 contentPadding 再加上顶栏盖住的那一段：内容能滚到顶栏下面，停在顶上时又不被挡住。 */
@Composable
fun PaddingValues.withContentTopInset(): PaddingValues {
    val inset = LocalContentTopInset.current
    if (inset === ContentTopInset.None) return this
    val density = LocalDensity.current
    return remember(this, inset, density) { TopInsetPaddingValues(this, inset, density) }
}

/** 顶栏完全显示时盖住的高度，给不随收起变化的偏移用（下拉刷新指示器的位置）。 */
@Composable
fun expandedContentTopInset(): Dp {
    val inset = LocalContentTopInset.current
    return with(LocalDensity.current) { inset.expanded().toDp() }
}

/** 不是列表的内容（固定在列表上方的搜索框、筛选条）：整块让到顶栏下面。 */
@Composable
fun Modifier.contentTopInsetPadding(): Modifier {
    val inset = LocalContentTopInset.current
    if (inset === ContentTopInset.None) return this
    return layout { measurable, constraints ->
        val top = inset.current()
        val placeable = measurable.measure(constraints.offset(vertical = -top))
        layout(placeable.width, placeable.height + top) { placeable.place(0, top) }
    }
}

/** 顶部留白在 [calculateTopPadding] 里现读：LazyColumn 在测量时取它，所以收起动画只触发重新测量。 */
@Stable
private class TopInsetPaddingValues(
    private val base: PaddingValues,
    private val inset: ContentTopInset,
    private val density: Density,
) : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = base.calculateLeftPadding(layoutDirection)
    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = base.calculateRightPadding(layoutDirection)
    override fun calculateTopPadding(): Dp = base.calculateTopPadding() + with(density) { inset.current().toDp() }
    override fun calculateBottomPadding(): Dp = base.calculateBottomPadding()

    override fun equals(other: Any?): Boolean =
        other is TopInsetPaddingValues && other.base == base && other.inset === inset && other.density == density

    override fun hashCode(): Int = (base.hashCode() * 31 + inset.hashCode()) * 31 + density.hashCode()
}
