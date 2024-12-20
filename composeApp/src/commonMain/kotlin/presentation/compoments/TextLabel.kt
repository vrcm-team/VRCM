package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun TextLabel(
    modifier: Modifier = Modifier,
    text: String
) {
    Box(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.inverseOnSurface,
                MaterialTheme.shapes.medium
            )
            .clip(MaterialTheme.shapes.medium)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
