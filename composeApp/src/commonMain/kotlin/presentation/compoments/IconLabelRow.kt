package io.github.vrcmteam.vrcm.presentation.compoments
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme

@Composable
fun IconLabelRow(
    iconScope: @Composable () -> Unit,
    text: String,
    spacing: Dp,
    textStyle: TextStyle = AppTheme.type.caption1,
    textColor: Color = AppTheme.colors.label,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        iconScope()
        AppText(
            text = text,
            style = textStyle,
            color = textColor,
            maxLines = 1
        )
    }
}

@Composable
fun IconLabelRow(
    icon: ImageVector,
    text: String,
    iconSize: Dp,
    spacing: Dp,
    textStyle: TextStyle = AppTheme.type.caption1,
    textColor: Color = AppTheme.colors.label,
    iconAlpha: Float = 1f
) = IconLabelRow(
    iconScope = {
        AppIcon(
            imageVector = icon,
            contentDescription = "IconLabelRow",
            modifier = Modifier.size(iconSize),
            tint = textColor.copy(alpha = iconAlpha)
        )
    },
    text = text,
    spacing = spacing,
    textStyle = textStyle,
    textColor = textColor,
)
