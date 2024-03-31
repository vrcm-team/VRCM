package io.github.vrcmteam.vrcm.presentation.extensions

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState

suspend fun LazyListState.animateScrollToFirst(animationSpec: AnimationSpec<Float> =  tween(durationMillis = 2000)){
    if (this.firstVisibleItemIndex == 0 && this.firstVisibleItemScrollOffset == 0) return
    // 计算每一个item的高度加上间距
    var targetScrollOffset = this.layoutInfo.visibleItemsInfo.last().size + this.layoutInfo.mainAxisItemSpacing.toFloat()
    // 乘以已经滚动完的Item数量
    targetScrollOffset *= this.firstVisibleItemIndex
    // 加上已经最后一个没有滚完的偏移量
    targetScrollOffset += this.firstVisibleItemScrollOffset
    // TODO() durationMillis根据划过的item数量动态调整,如果划过很多item，durationMillis应该变快
    this.animateScrollBy(-targetScrollOffset, animationSpec)
}