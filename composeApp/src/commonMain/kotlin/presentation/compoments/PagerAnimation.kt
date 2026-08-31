package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.pager.PagerState

private val TabPagerAnimationSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessLow,
)

internal suspend fun PagerState.animateScrollToTab(page: Int) {
    if (page == currentPage && !isScrollInProgress) return
    animateScrollToPage(page, animationSpec = TabPagerAnimationSpec)
}
