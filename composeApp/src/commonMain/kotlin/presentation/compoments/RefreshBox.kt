package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 下拉刷新Box组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshBox(
    modifier: Modifier = Modifier,
    refreshContainerOffsetY: Dp = 0.dp,
    isRefreshing: Boolean = true,
    pullToRefreshState: PullToRefreshState = rememberPullToRefreshState(),
    doRefresh: suspend () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.onPrimary,
    contentColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable BoxScope.() -> Unit,
) {
    val scaleFraction = if (pullToRefreshState.isAnimating) 1f else
        LinearOutSlowInEasing.transform(pullToRefreshState.distanceFraction).coerceIn(0f, 1f)
    val scope = rememberCoroutineScope()

    val onRefresh: () -> Unit = {
        scope.launch {
            doRefresh()
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        contentAlignment = Alignment.TopCenter,
        state = pullToRefreshState,
        modifier = modifier,
        indicator = {
            Indicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .scale(scaleFraction)
                    .padding(top = refreshContainerOffsetY),
                isRefreshing = isRefreshing,
                color = contentColor,
                containerColor = containerColor,
                state = pullToRefreshState
            )
        },
        onRefresh = onRefresh,
        content = content
    )

}
