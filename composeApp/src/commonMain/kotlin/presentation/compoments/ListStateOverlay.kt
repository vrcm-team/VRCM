package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.designsystem.AppActivityIndicator
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonStyle
import io.github.vrcmteam.vrcm.presentation.designsystem.AppShapes
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSurface
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings

/**
 * 列表的状态层，叠在列表上面。加载与失败怎么显示，全应用只有这一套：
 * - 没有内容：正在加载 → 居中的活动指示器；加载失败 → 居中说明 + 重试；都不是 → 居中的空态说明；
 * - 有内容：刷新是安静的（用户下拉发起的那一次由 [RefreshBox] 的指示器回应）；刷新失败 → 底部横幅 + 重试，内容照常显示。
 *
 * [errorMessage] 非空表示这一次加载 / 刷新失败了，[onRetry] 是重试。
 * [bottomPadding] 是横幅离容器底的距离：列表铺到悬浮的标签栏 / 系统导航条下面时把它们的高度算进来。
 */
@Composable
fun BoxScope.ListStateOverlay(
    isEmpty: Boolean,
    isLoading: Boolean,
    emptyMessage: String? = null,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
    bottomPadding: Dp = 12.dp,
) {
    when {
        isEmpty && isLoading -> AppActivityIndicator(Modifier.align(Alignment.Center))
        isEmpty && errorMessage != null -> ListStateMessage(errorMessage, onRetry)
        isEmpty -> if (emptyMessage != null) ListStateMessage(emptyMessage, onRetry = null)
        errorMessage != null -> ListErrorBanner(errorMessage, bottomPadding, onRetry)
    }
}

@Composable
private fun BoxScope.ListStateMessage(message: String, onRetry: (() -> Unit)?) {
    Column(
        Modifier.align(Alignment.Center).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppText(message, color = AppTheme.colors.secondaryLabel, textAlign = TextAlign.Center)
        if (onRetry != null) AppButton(onClick = onRetry, style = AppButtonStyle.Plain) { AppText(strings.retry) }
    }
}

@Composable
private fun BoxScope.ListErrorBanner(message: String, bottomPadding: Dp, onRetry: () -> Unit) {
    AppSurface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = bottomPadding),
        color = AppTheme.colors.destructiveSoft,
        contentColor = AppTheme.colors.onDestructiveSoft,
        shape = AppShapes.m,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(message, Modifier.weight(1f), style = AppTheme.type.subheadline)
            AppButton(onClick = onRetry, style = AppButtonStyle.Plain) { AppText(strings.retry) }
        }
    }
}
