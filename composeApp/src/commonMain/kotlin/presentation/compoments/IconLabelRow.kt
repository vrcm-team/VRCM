package io.github.vrcmteam.vrcm.presentation.compoments
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp

@Composable
fun IconLabelRow(
    iconScope: @Composable () -> Unit,
    text: String,
    spacing: Dp,
    textStyle: TextStyle = MaterialTheme.typography.bodySmall,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        iconScope()
        Text(
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
    textStyle: TextStyle = MaterialTheme.typography.bodySmall,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    iconAlpha: Float = 1f
) = IconLabelRow(
    iconScope = {
        Icon(
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
