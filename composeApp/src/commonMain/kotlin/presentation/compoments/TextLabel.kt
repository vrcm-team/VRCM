package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vrcmteam.vrcm.presentation.designsystem.AppShapes
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme

@Composable
fun TextLabel(
    modifier: Modifier = Modifier,
    text: String,
    color: Color = AppTheme.colors.tertiaryLabel,
    backgroundColor: Color = AppTheme.colors.fill
) {
    Box(
        modifier = modifier
            .background(
                backgroundColor,
                AppShapes.m
            )
            .clip(AppShapes.m)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = text,
            style = AppTheme.type.caption2Emphasized.merge(color = color),
            autoSize = TextAutoSize.StepBased(8.sp,AppTheme.type.caption2Emphasized.fontSize),
            maxLines = 1
        )
    }
}
