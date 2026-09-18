package io.github.vrcmteam.vrcm.presentation.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 两层模型的页面骨架：内容层在下（玻璃的取样源，含页面底色），功能层（导航栏 / 标签栏 / 浮动按钮）浮在上面取样它。
 * [bannerHost] 是页面自己的短反馈横幅位，浮在底部。
 * 内容拿到的 [PaddingValues] 是功能层占掉的高度；把它交给列表的 contentPadding，内容就会从玻璃下面滚过去。
 */
@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bannerHost: @Composable () -> Unit = {},
    containerColor: Color = AppTheme.colors.groupedBackground,
    contentColor: Color = AppTheme.colors.contentColorFor(containerColor).takeOrElse { LocalContentColor.current },
    contentWindowInsets: WindowInsets = WindowInsets.systemBars,
    backdrop: GlassBackdrop = rememberGlassBackdrop(),
    content: @Composable (PaddingValues) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val insets = contentWindowInsets.asPaddingValues(LocalDensity.current)
    CompositionLocalProvider(
        LocalGlassBackdrop provides backdrop,
        LocalContentColor provides contentColor,
    ) {
        SubcomposeLayout(modifier.fillMaxSize().background(containerColor)) { constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val topPlaceables = subcompose(ScaffoldSlot.TopBar, topBar).map { it.measure(loose) }
            val bottomPlaceables = subcompose(ScaffoldSlot.BottomBar, bottomBar).map { it.measure(loose) }
            val fabPlaceables = subcompose(ScaffoldSlot.Fab, floatingActionButton).map { it.measure(loose) }
            val bannerPlaceables = subcompose(ScaffoldSlot.Banner, bannerHost).map { it.measure(loose) }
            val topHeight = topPlaceables.maxOfOrNull { it.height } ?: 0
            val bottomHeight = bottomPlaceables.maxOfOrNull { it.height } ?: 0
            val padding = PaddingValues(
                start = insets.calculateStartPadding(layoutDirection),
                end = insets.calculateEndPadding(layoutDirection),
                top = if (topHeight > 0) topHeight.toDp() else insets.calculateTopPadding(),
                bottom = if (bottomHeight > 0) bottomHeight.toDp() else insets.calculateBottomPadding(),
            )
            val contentPlaceables = subcompose(ScaffoldSlot.Content) {
                // 底色画在取样源里面，玻璃模糊到的才是"页面"而不是透明
                Box(Modifier.fillMaxSize().glassBackdropSource(backdrop).background(containerColor)) { content(padding) }
            }.map { it.measure(constraints) }
            layout(constraints.maxWidth, constraints.maxHeight) {
                contentPlaceables.forEach { it.place(0, 0) }
                topPlaceables.forEach { it.place(0, 0) }
                bottomPlaceables.forEach { it.place(0, constraints.maxHeight - it.height) }
                val fabSpacing = 16.dp.roundToPx()
                val fabBottom = maxOf(bottomHeight, insets.calculateBottomPadding().roundToPx()) + fabSpacing
                fabPlaceables.forEach {
                    it.placeRelative(constraints.maxWidth - it.width - fabSpacing, constraints.maxHeight - it.height - fabBottom)
                }
                // 横幅浮在底部功能层之上、水平居中
                val bannerBottom = maxOf(bottomHeight, insets.calculateBottomPadding().roundToPx())
                bannerPlaceables.forEach {
                    it.place((constraints.maxWidth - it.width) / 2, constraints.maxHeight - it.height - bannerBottom)
                }
            }
        }
    }
}

private enum class ScaffoldSlot { TopBar, BottomBar, Fab, Banner, Content }
