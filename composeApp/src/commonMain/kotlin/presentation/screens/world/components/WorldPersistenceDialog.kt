package io.github.vrcmteam.vrcm.presentation.screens.world.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.designsystem.AppActivityIndicator
import io.github.vrcmteam.vrcm.presentation.designsystem.AppAlert
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonRole
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonStyle
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldPersistenceStatus
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldPersistenceUiState
import io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStrings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons

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
        AppAlert(
            onDismissRequest = onDismissDeletion,
            icon = {
                AppIcon(
                    imageVector = AppIcons.DeleteForever,
                    contentDescription = null,
                )
            },
            title = { AppText(localeStrings.worldPersistenceDeleteConfirmTitle) },
            text = { AppText(localeStrings.worldPersistenceDeleteConfirmMessage) },
            confirmButton = {
                AppButton(
                    onClick = onConfirmDeletion,
                    style = AppButtonStyle.Plain,
                    role = AppButtonRole.Destructive,
                ) {
                    AppText(localeStrings.worldPersistenceDelete)
                }
            },
            dismissButton = {
                AppButton(onClick = onDismissDeletion, style = AppButtonStyle.Plain) {
                    AppText(localeStrings.cancel)
                }
            },
        )
        return
    }

    AppAlert(
        onDismissRequest = onDismiss,
        icon = {
            AppIcon(
                imageVector = AppIcons.Storage,
                contentDescription = null,
            )
        },
        title = { AppText(localeStrings.worldPersistenceTitle) },
        text = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.status == WorldPersistenceStatus.Checking ||
                    state.status == WorldPersistenceStatus.Deleting
                ) {
                    AppActivityIndicator(modifier = Modifier.size(20.dp))
                }
                AppText(state.status.message(localeStrings))
            }
        },
        confirmButton = {
            when (state.status) {
                WorldPersistenceStatus.Initial -> {
                    AppButton(onClick = onCheck, style = AppButtonStyle.Plain) {
                        AppText(localeStrings.worldPersistenceCheck)
                    }
                }

                WorldPersistenceStatus.Exists -> {
                    AppButton(
                        onClick = onRequestDeletion,
                        style = AppButtonStyle.Plain,
                        role = AppButtonRole.Destructive,
                    ) {
                        AppText(localeStrings.worldPersistenceDelete)
                    }
                }

                is WorldPersistenceStatus.Missing -> {
                    AppButton(onClick = onCheck, style = AppButtonStyle.Plain) {
                        AppText(localeStrings.worldPersistenceCheckAgain)
                    }
                }

                WorldPersistenceStatus.CheckFailed -> {
                    AppButton(onClick = onCheck, style = AppButtonStyle.Plain) {
                        AppText(localeStrings.retry)
                    }
                }

                WorldPersistenceStatus.DeleteFailed -> {
                    AppButton(
                        onClick = onRequestDeletion,
                        style = AppButtonStyle.Plain,
                        role = AppButtonRole.Destructive,
                    ) {
                        AppText(localeStrings.retry)
                    }
                }

                WorldPersistenceStatus.Checking,
                WorldPersistenceStatus.Deleting -> Unit
            }
        },
        dismissButton = {
            AppButton(onClick = onDismiss, style = AppButtonStyle.Plain) {
                AppText(localeStrings.close)
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
