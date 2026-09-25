package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.presentation.designsystem.AppPullToRefreshBox
import io.github.vrcmteam.vrcm.presentation.designsystem.AppPullToRefreshState
import io.github.vrcmteam.vrcm.presentation.designsystem.rememberAppPullToRefreshState
import kotlinx.coroutines.launch

/**
 * 下拉刷新Box组件
 *
 * 应用里刷新列表的唯一入口：触屏下拉；桌面端鼠标没法下拉，由刷新快捷键（[SharedFlowCentre.refreshRequest]）
 * 让当前显示着的列表刷新自己。
 *
 * 内容铺在悬浮顶栏下面时，指示器自己让到顶栏下方（下拉时顶栏一定是露着的）；
 * [refreshContainerOffsetY] 只需要给列表自己的顶部留白。
 */
@Composable
fun RefreshBox(
    modifier: Modifier = Modifier,
    refreshContainerOffsetY: Dp = 0.dp,
    isRefreshing: Boolean = true,
    enabled: Boolean = true,
    pullToRefreshState: AppPullToRefreshState = rememberAppPullToRefreshState(),
    doRefresh: suspend () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()

    val onRefresh: () -> Unit = {
        scope.launch {
            doRefresh()
        }
    }

    LaunchedEffect(pullToRefreshState) {
        SharedFlowCentre.refreshRequest.collect { pullToRefreshState.refresh() }
    }
    // 告诉悬浮顶栏内容正被拉着：这期间往回推要先把内容推回原位，顶栏不能抢先收起
    val topInset = LocalContentTopInset.current
    LaunchedEffect(pullToRefreshState, topInset) {
        var reported = false
        try {
            snapshotFlow { pullToRefreshState.distanceFraction > 0f }.collect { pulled ->
                if (pulled != reported) {
                    reported = pulled
                    topInset.onContentPulledChange(pulled)
                }
            }
        } finally {
            if (reported) topInset.onContentPulledChange(false)
        }
    }

    AppPullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        enabled = enabled,
        state = pullToRefreshState,
        indicatorOffset = refreshContainerOffsetY + expandedContentTopInset(),
        contentAlignment = Alignment.TopCenter,
        content = content,
    )
}
