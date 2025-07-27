package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TextLabel(
    modifier: Modifier = Modifier,
    text: String,
    color: Color = MaterialTheme.colorScheme.outline,
    backgroundColor: Color = MaterialTheme.colorScheme.inverseOnSurface
) {
    Box(
        modifier = modifier
            .background(
                backgroundColor,
                MaterialTheme.shapes.medium
            )
            .clip(MaterialTheme.shapes.medium)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = text,
            style = MaterialTheme.typography.labelSmall.merge(color = color),
            autoSize = TextAutoSize.StepBased(8.sp,MaterialTheme.typography.labelSmall.fontSize),
            maxLines = 1
        )
    }
}
