package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.PopupPositionProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ATooltipBox(
    modifier: Modifier = Modifier,
    tooltip: @Composable TooltipScope.() -> Unit,
    positionProvider: PopupPositionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
    state: TooltipState = rememberTooltipState(),
    content: @Composable () -> Unit,
) {
    TooltipBox(
        modifier = modifier,
        positionProvider = positionProvider,
        tooltip = {
            PlainTooltip {
                tooltip()
            }
        },
        state =state ,
        content = content
    )
}