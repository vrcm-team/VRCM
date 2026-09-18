package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.designsystem.AppPullToRefreshBox
import io.github.vrcmteam.vrcm.presentation.designsystem.AppPullToRefreshState
import io.github.vrcmteam.vrcm.presentation.designsystem.rememberAppPullToRefreshState
import kotlinx.coroutines.launch

/**
 * 下拉刷新Box组件
 */
@Composable
fun RefreshBox(
    modifier: Modifier = Modifier,
    refreshContainerOffsetY: Dp = 0.dp,
    isRefreshing: Boolean = true,
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

    AppPullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = pullToRefreshState,
        indicatorOffset = refreshContainerOffsetY,
        contentAlignment = Alignment.TopCenter,
        content = content,
    )
}
