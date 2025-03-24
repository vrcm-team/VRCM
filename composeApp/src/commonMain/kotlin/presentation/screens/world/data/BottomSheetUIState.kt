package io.github.vrcmteam.vrcm.presentation.screens.world.data

import androidx.compose.ui.unit.Dp

/**
 * BottomSheet的状态信息
 */
data class BottomSheetUIState(
    val targetHeight: Dp,
    val currentHeight: Dp,
    val animatedHeight: Dp,
    val blurProgress: Float,
    val blurRadius: Float,
    val blurAlpha: Float,
    val overlayAlpha: Float,
    val collapsedProgress: Float,
    val collapsedAlpha: Float,
)