package io.github.vrcmteam.vrcm.presentation.screens.home.data

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import io.github.vrcmteam.vrcm.presentation.supports.ListPagerProvider

data class PagerProvidersState @OptIn(ExperimentalFoundationApi::class) constructor(
    val pagerProviders: List<ListPagerProvider>,
    val pagerState: PagerState,
    val pagers: List<@Composable () -> Unit>,
    val lazyListStates: List<LazyListState>
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
inline fun createPagerProvidersState(vararg listPagerProviders: ListPagerProvider): PagerProvidersState {
    val lazyListStates = listPagerProviders.map { rememberLazyListState() }
    val pagers = listPagerProviders.map { it.createPager(lazyListStates[it.index]) }
    val pagerState = rememberPagerState { listPagerProviders.size }
    return PagerProvidersState(
        listPagerProviders.toList(),
        pagerState,
        pagers,
        lazyListStates
    )
}
