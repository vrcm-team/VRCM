package io.github.vrcmteam.vrcm.presentation.screens.home.compoments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
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
    AppAlert(
        onDismissRequest = { if (!isClearing) onDismiss() },
        title = {
            AppText(strings.favoriteGroupClearTitle.replaceFirst("%s", groupDisplayName))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppText(formatFavoriteGroupClearMessage(strings.favoriteGroupClearMessage, itemCount))
                if (hasFailure) {
                    AppText(
                        text = strings.favoriteGroupClearFailed,
                        color = AppTheme.colors.destructive,
                        style = AppTheme.type.subheadline,
                    )
                }
            }
        },
        confirmButton = {
            AppButton(
                enabled = !isClearing,
                onClick = onConfirm,
                style = AppButtonStyle.Plain,
                role = AppButtonRole.Destructive,
            ) {
                if (isClearing) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppActivityIndicator(Modifier.size(18.dp))
                        AppText(strings.favoriteGroupClearing)
                    }
                } else {
                    AppText(strings.favoriteGroupClearAction)
                }
            }
        },
        dismissButton = {
            AppButton(enabled = !isClearing, onClick = onDismiss, style = AppButtonStyle.Plain) {
                AppText(strings.cancel)
            }
        },
    )
}

internal fun formatFavoriteGroupClearMessage(template: String, itemCount: Int): String =
    template.replaceFirst("%d", itemCount.toString())
