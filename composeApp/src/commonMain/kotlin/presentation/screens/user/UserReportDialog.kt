package io.github.vrcmteam.vrcm.presentation.screens.user

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons

@Composable
internal fun UserReportDialog(
    visible: Boolean,
    targetName: String,
    state: UserReportState,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    if (!visible) return

    val localeStrings = strings
    val isSubmitting = state == UserReportState.Submitting
    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        icon = {
            Icon(
                imageVector = AppIcons.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(localeStrings.profileReportTitle) },
        text = {
            Column {
                Text(localeStrings.profileReportMessage.replace("%name%", targetName))
                if (state == UserReportState.Failed) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = localeStrings.profileReportFailed,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSubmitting,
                onClick = onSubmit,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = localeStrings.profileReportSubmit,
                        modifier = Modifier.alpha(if (isSubmitting) 0f else 1f),
                    )
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.error,
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isSubmitting,
                onClick = onDismiss,
            ) {
                Text(localeStrings.cancel)
            }
        },
    )
}
