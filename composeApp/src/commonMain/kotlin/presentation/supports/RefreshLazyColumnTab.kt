package io.github.vrcmteam.vrcm.presentation.supports

import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.tab.Tab
import io.github.vrcmteam.vrcm.presentation.compoments.RefreshBox
import kotlinx.coroutines.flow.collectLatest

/**
 * 用于保存刷新状态,和LazyColumn滑动距离状态的父类
 * 子类需要是单例，否则无法保存属性状态
 */
abstract class RefreshLazyColumnTab : Tab {

    /**
     * 刷新状态
     */
    protected var isRefreshed = true

    /**
     * 滑动偏移量
     */
    private var itemScrollOffset = 0

    /**
     * 滑动tem索引
     */
    private var itemIndex = 0

    /**
     * 返回顶部开关
     * 放在单例对象里保存避免MutableState对象被误序列化产生报错
     */
    private val toTopSwitchState: MutableState<Boolean>
        get() = ToTopSwitchList.getOrPut(key) { mutableStateOf(false) }


    /**
     * 初始化与创建刷新回调
     */
    @Composable
    protected abstract fun initAndCreateRefreshCall(): suspend () -> Unit

    /**
     * 返回顶部
     */
    fun toTop(): Unit = toTopSwitchState.let { it.value = !it.value }


    /**
     * 刷新组件内的内容
     */
    @Composable
    protected abstract fun BoxContent()

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val doRefresh = initAndCreateRefreshCall()
        RefreshBox(
            isStartRefresh = isRefreshed,
            doRefresh = {
                isRefreshed = false
                itemScrollOffset = 0
                itemIndex = 0
                doRefresh()
            }
        ) {
            BoxContent()
        }
    }


    @Composable
    fun RememberLazyColumn(
        modifier: Modifier = Modifier,
        state: LazyListState = rememberLazyListState(),
        contentPadding: PaddingValues = PaddingValues(0.dp),
        verticalArrangement: Arrangement.Vertical = Arrangement.Top,
        horizontalAlignment: Alignment.Horizontal = Alignment.Start,
        content: LazyListScope.() -> Unit
    ) {
        LaunchedEffect(toTopSwitchState.value) {
            if (state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0) return@LaunchedEffect
            // 计算每一个item的高度加上间距
            var targetScrollOffset = state.layoutInfo.visibleItemsInfo.last().size + state.layoutInfo.mainAxisItemSpacing.toFloat()
            // 乘以已经滚动完的Item数量
            targetScrollOffset *= state.firstVisibleItemIndex
            // 加上已经最后一个没有滚完的偏移量
            targetScrollOffset += state.firstVisibleItemScrollOffset
            // TODO() durationMillis根据划过的item数量动态调整,如果划过很多item，durationMillis应该变快
            state.animateScrollBy(-targetScrollOffset, tween(durationMillis = 2000))
        }
        LaunchedEffect(Unit) {
            state.scrollToItem(itemIndex, itemScrollOffset)
        }
        LaunchedEffect(state) {
            snapshotFlow { state.firstVisibleItemScrollOffset }
                .collectLatest { itemScrollOffset = it }
        }
        LaunchedEffect(state) {
            snapshotFlow { state.firstVisibleItemIndex }
                .collectLatest { itemIndex = it }
        }
        LazyColumn(
            modifier = modifier,
            state = state,
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
        ) {
            content()
        }
    }

    /**
     * 无法被序列化所以迫不得已写成伴生对象
     */
    companion object {
        val ToTopSwitchList: MutableMap<String, MutableState<Boolean>> = mutableMapOf()
    }
}
