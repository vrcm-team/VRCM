package io.github.vrcmteam.vrcm.presentation.screens.world.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Storage
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
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldPersistenceStatus
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldPersistenceUiState
import io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStrings

@Composable
internal fun WorldPersistenceDialog(
    state: WorldPersistenceUiState,
    localeStrings: LocaleStrings,
    onDismiss: () -> Unit,
    onCheck: () -> Unit,
    onRequestDeletion: () -> Unit,
    onDismissDeletion: () -> Unit,
    onConfirmDeletion: () -> Unit,
) {
    if (state.confirmingDeletion) {
        AlertDialog(
            onDismissRequest = onDismissDeletion,
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                )
            },
            title = { Text(localeStrings.worldPersistenceDeleteConfirmTitle) },
            text = { Text(localeStrings.worldPersistenceDeleteConfirmMessage) },
            confirmButton = {
                TextButton(
                    onClick = onConfirmDeletion,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(localeStrings.worldPersistenceDelete)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDeletion) {
                    Text(localeStrings.cancel)
                }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
            )
        },
        title = { Text(localeStrings.worldPersistenceTitle) },
        text = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.status == WorldPersistenceStatus.Checking ||
                    state.status == WorldPersistenceStatus.Deleting
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                Text(state.status.message(localeStrings))
            }
        },
        confirmButton = {
            when (state.status) {
                WorldPersistenceStatus.Initial -> {
                    TextButton(onClick = onCheck) {
                        Text(localeStrings.worldPersistenceCheck)
                    }
                }

                WorldPersistenceStatus.Exists -> {
                    TextButton(
                        onClick = onRequestDeletion,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text(localeStrings.worldPersistenceDelete)
                    }
                }

                is WorldPersistenceStatus.Missing -> {
                    TextButton(onClick = onCheck) {
                        Text(localeStrings.worldPersistenceCheckAgain)
                    }
                }

                WorldPersistenceStatus.CheckFailed -> {
                    TextButton(onClick = onCheck) {
                        Text(localeStrings.retry)
                    }
                }

                WorldPersistenceStatus.DeleteFailed -> {
                    TextButton(
                        onClick = onRequestDeletion,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text(localeStrings.retry)
                    }
                }

                WorldPersistenceStatus.Checking,
                WorldPersistenceStatus.Deleting -> Unit
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(localeStrings.close)
            }
        },
    )
}

private fun WorldPersistenceStatus.message(strings: LocaleStrings): String = when (this) {
    WorldPersistenceStatus.Initial -> strings.worldPersistenceInitial
    WorldPersistenceStatus.Checking -> strings.worldPersistenceChecking
    WorldPersistenceStatus.Exists -> strings.worldPersistenceExists
    is WorldPersistenceStatus.Missing -> if (deleted) {
        strings.worldPersistenceDeleted
    } else {
        strings.worldPersistenceMissing
    }
    WorldPersistenceStatus.CheckFailed -> strings.worldPersistenceCheckFailed
    WorldPersistenceStatus.Deleting -> strings.worldPersistenceDeleting
    WorldPersistenceStatus.DeleteFailed -> strings.worldPersistenceDeleteFailed
}
