package io.github.vrcmteam.vrcm.presentation.screens.world.data

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize

/**
 * 世界详情界面的尺寸计算
 */
data class WorldDetailSizesState(
    val maxHeight: Dp,
    val imageHigh: Dp,
    val collapsedHeight: Dp,
    val halfExpandedHeight: Dp,
    val expandedHeight: Dp,
    val topBarHeight: Dp,
    val sysTopPadding: Dp,
    val itemSize: DpSize
)