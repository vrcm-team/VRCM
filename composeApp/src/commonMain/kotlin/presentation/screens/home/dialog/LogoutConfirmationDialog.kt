package io.github.vrcmteam.vrcm.presentation.screens.home.dialog

import androidx.compose.runtime.Composable
import io.github.vrcmteam.vrcm.presentation.designsystem.AppAlert
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonStyle
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings

@Composable
fun LogoutConfirmationDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AppAlert(
        onDismissRequest = onDismissRequest,
        title = { AppText(strings.logoutConfirmTitle) },
        text = { AppText(strings.logoutConfirmMessage) },
        confirmButton = {
            AppButton(onClick = onConfirm, style = AppButtonStyle.Plain) { AppText(strings.confirm) }
        },
        dismissButton = {
            AppButton(onClick = onDismissRequest, style = AppButtonStyle.Plain) { AppText(strings.cancel) }
        },
    )
}
