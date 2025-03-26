package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun IChip(
    color: Color = MaterialTheme.colorScheme.tertiary,
    content: @Composable () -> Unit
) {
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(12.dp)
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
    color: Color = MaterialTheme.colorScheme.tertiary,
) {
    IChip(color = color){
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
fun TextChip(
    text: String,
    color: Color = MaterialTheme.colorScheme.tertiary,
) {
    IChip(color = color){
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}