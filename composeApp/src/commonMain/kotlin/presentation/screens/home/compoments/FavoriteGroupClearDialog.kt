package io.github.vrcmteam.vrcm.presentation.screens.home.compoments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings

@Composable
fun FavoriteGroupClearDialog(
    groupDisplayName: String,
    itemCount: Int,
    isClearing: Boolean,
    hasFailure: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isClearing) onDismiss() },
        title = {
            Text(strings.favoriteGroupClearTitle.replaceFirst("%s", groupDisplayName))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(formatFavoriteGroupClearMessage(strings.favoriteGroupClearMessage, itemCount))
                if (hasFailure) {
                    Text(
                        text = strings.favoriteGroupClearFailed,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !isClearing, onClick = onConfirm) {
                if (isClearing) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(strings.favoriteGroupClearing)
                    }
                } else {
                    Text(strings.favoriteGroupClearAction)
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !isClearing, onClick = onDismiss) {
                Text(strings.cancel)
            }
        },
    )
}

internal fun formatFavoriteGroupClearMessage(template: String, itemCount: Int): String =
    template.replaceFirst("%d", itemCount.toString())
