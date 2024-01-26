package io.github.kamo.vrcm.ui.util

import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LoadingButton(
    modifier: Modifier = Modifier,
    text: String,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    FilledTonalButton(
        modifier = modifier,
        onClick = onClick,
        border = ButtonDefaults.outlinedButtonBorder,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(24.dp),
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
