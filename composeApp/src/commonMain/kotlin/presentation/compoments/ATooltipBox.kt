package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTooltipBox

@Composable
fun ATooltipBox(
    modifier: Modifier = Modifier,
    tooltip: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    AppTooltipBox(
        tooltip = tooltip,
        modifier = modifier,
        content = content,
    )
}
