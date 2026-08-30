package io.github.vrcmteam.vrcm.presentation.screens.home.dialog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings

@Composable
fun LogoutConfirmationDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(strings.logoutConfirmTitle) },
        text = { Text(strings.logoutConfirmMessage) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(strings.confirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(strings.cancel) }
        },
    )
}
