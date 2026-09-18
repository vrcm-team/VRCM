package presentation.compoments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.designsystem.AppControlContext
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIconButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppShapes
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.designsystem.LocalAppControlContext
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons

@Composable
fun TopMenuBar(
    topBarHeight: Dp = 64.dp,
    sysTopPadding: Dp = getInsetPadding(WindowInsets::getTop),
    offsetDp: Dp,
    ratio: Float,
    color: Color = AppTheme.colors.secondaryGroupedBackground,
    onReturn: () -> Unit,
    onMenu: (() -> Unit)?,
    menuContentDescription: String = "MenuIcon",
    centerContent: @Composable RowScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    // image上滑反比例
    val inverseRatio = 1 - ratio
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .height(topBarHeight + sysTopPadding)
                .offset(y = offsetDp)
                .background(
                    color.copy(alpha = inverseRatio), AppShapes.m.copy(
                        topStart = CornerSize(0.dp),
                        topEnd = CornerSize(0.dp)
                    )
                )
                .padding(top = sysTopPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val rowScope = this
            // 返回 / 动作 / 更多都是玻璃圆钮：取样页面内容做高斯模糊（由所在页面提供 LocalGlassBackdrop），
            // 头图上、收起后的面板上都读得清，不需要随滚动换配色
            CompositionLocalProvider(LocalAppControlContext provides AppControlContext.NavBar) {
                AppIconButton(
                    modifier = Modifier
                        .padding(horizontal = 10.dp),
                    onClick = onReturn
                ) {
                    AppIcon(
                        imageVector = AppIcons.ArrowBackIosNew,
                        contentDescription = "ReturnIcon"
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    with(rowScope) { centerContent() }
                }
                Row(
                    modifier = Modifier.padding(end = if (onMenu == null) 10.dp else 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions()
                }
                onMenu?.let {
                    AppIconButton(
                        modifier = Modifier
                            .padding(horizontal = 10.dp),
                        onClick = it,
                    ) {
                        AppIcon(
                            imageVector = AppIcons.Menu,
                            contentDescription = menuContentDescription,
                        )
                    }
                }
            }
        }
    }
}
