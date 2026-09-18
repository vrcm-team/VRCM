package io.github.vrcmteam.vrcm.presentation.screens.user

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.designsystem.AppActivityIndicator
import io.github.vrcmteam.vrcm.presentation.designsystem.AppAlert
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonRole
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonStyle
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
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
    AppAlert(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        icon = {
            AppIcon(
                imageVector = AppIcons.Shield,
                contentDescription = null,
                tint = AppTheme.colors.destructive,
            )
        },
        title = { AppText(localeStrings.profileReportTitle) },
        text = {
            Column {
                AppText(localeStrings.profileReportMessage.replace("%name%", targetName))
                if (state == UserReportState.Failed) {
                    Spacer(Modifier.height(12.dp))
                    AppText(
                        text = localeStrings.profileReportFailed,
                        color = AppTheme.colors.destructive,
                        style = AppTheme.type.caption1,
                    )
                }
            }
        },
        confirmButton = {
            AppButton(
                enabled = !isSubmitting,
                onClick = onSubmit,
                style = AppButtonStyle.Plain,
                role = AppButtonRole.Destructive,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AppText(
                        text = localeStrings.profileReportSubmit,
                        modifier = Modifier.alpha(if (isSubmitting) 0f else 1f),
                    )
                    if (isSubmitting) {
                        AppActivityIndicator(
                            modifier = Modifier.size(18.dp),
                            color = AppTheme.colors.destructive,
                        )
                    }
                }
            }
        },
        dismissButton = {
            AppButton(
                enabled = !isSubmitting,
                onClick = onDismiss,
                style = AppButtonStyle.Plain,
            ) {
                AppText(localeStrings.cancel)
            }
        },
    )
}
