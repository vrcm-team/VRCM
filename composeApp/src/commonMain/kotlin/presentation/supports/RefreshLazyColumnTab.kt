package io.github.vrcmteam.vrcm.presentation.supports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.tab.Tab
import io.github.vrcmteam.vrcm.presentation.compoments.RefreshBox
import kotlinx.coroutines.flow.collectLatest

/**
 * 用于保存刷新状态,和滑动距离状态的父类
 */
abstract class RefreshLazyColumnTab: Tab {

    /**
     * 刷新状态
     */
    private var isRefreshed = true

    /**
     * 滑动偏移量
     */
    private var itemScrollOffset = 0

    /**
     * 滑动tem索引
     */
    private var itemIndex = 0

    @Composable
    protected abstract fun doRefreshCall(): suspend () -> Unit

    @Composable
    protected abstract fun BoxContent(): Unit

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val doRefresh = doRefreshCall()
        RefreshBox(
            isStartRefresh =isRefreshed,
            doRefresh = {
                isRefreshed = false
                itemScrollOffset = 0
                itemIndex = 0
                doRefresh()
            }
        ){
            BoxContent()
        }
    }

    @Composable
     fun RememberLazyColumn(
        modifier: Modifier = Modifier,
        state: LazyListState = rememberLazyListState(itemIndex, itemScrollOffset),
        contentPadding: PaddingValues = PaddingValues(0.dp),
        verticalArrangement: Arrangement.Vertical = Arrangement.Top,
        horizontalAlignment: Alignment.Horizontal = Alignment.Start,
        content: LazyListScope.() -> Unit
    ) {
        LaunchedEffect(Unit){
            state.scrollToItem(itemIndex, itemScrollOffset)
        }
        LaunchedEffect(state){
            snapshotFlow { state.firstVisibleItemScrollOffset }
                .collectLatest{ itemScrollOffset = it }
        }
        LaunchedEffect(state){
            snapshotFlow { state.firstVisibleItemIndex }
                .collectLatest{ itemIndex = it }
        }
        LazyColumn(
            modifier = modifier,
            state = state,
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
        ){
            content()
        }
    }
}