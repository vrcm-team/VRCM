package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 下拉刷新Box组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshBox(
    modifier: Modifier = Modifier,
    refreshContainerOffsetY: Dp = 0.dp,
    isStartRefresh : Boolean = true,
    pullToRefreshState: PullToRefreshState = rememberPullToRefreshState(),
    doRefresh: suspend () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.onPrimary,
    contentColor: Color =  MaterialTheme.colorScheme.primary,
    content: @Composable BoxScope.() -> Unit
) {
    if (isStartRefresh) {
        LaunchedEffect(Unit){
            pullToRefreshState.startRefresh()
        }
    }
    val scaleFraction = if (pullToRefreshState.isRefreshing) 1f else
        LinearOutSlowInEasing.transform(pullToRefreshState.progress).coerceIn(0f, 1f)
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(Unit) {
            doRefresh()
            pullToRefreshState.endRefresh()
        }
    }
    Box(
        modifier.nestedScroll(pullToRefreshState.nestedScrollConnection)
    ) {
        content()
        PullToRefreshContainer(
            modifier = Modifier
                .offset(y = refreshContainerOffsetY)
                .graphicsLayer(scaleX = scaleFraction, scaleY = scaleFraction)
                .align(Alignment.TopCenter),
            containerColor =  containerColor,
            contentColor = contentColor,
            state = pullToRefreshState,
        )
    }
}
