package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.designsystem.*

@Composable
fun IChip(
    color: Color = AppTheme.colors.secondaryTint,
    content: @Composable () -> Unit
) {
    AppSurface(
        color = color.copy(alpha = 0.2f),
        shape = AppShapes.m
    ) {
        CompositionLocalProvider(LocalContentColor provides color){
            Box(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            ){
                content()
            }
        }
    }
}

@Composable
inline fun IconTextChip(
    text: String,
    icon: ImageVector,
    color: Color = AppTheme.colors.secondaryTint,
) {
    IChip(color = color){
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AppIcon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp)
            )
            AppText(
                text = text,
                style = AppTheme.type.caption2Emphasized,
            )
        }
    }
}

@Composable
fun TextChip(
    text: String,
    color: Color = AppTheme.colors.secondaryTint,
) {
    IChip(color = color){
        AppText(
            text = text,
            style = AppTheme.type.caption2Emphasized,
        )
    }
}