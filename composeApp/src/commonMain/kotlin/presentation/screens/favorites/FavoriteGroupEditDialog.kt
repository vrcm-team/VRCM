package io.github.vrcmteam.vrcm.presentation.screens.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteGroupVisibility
import io.github.vrcmteam.vrcm.presentation.designsystem.AppActivityIndicator
import io.github.vrcmteam.vrcm.presentation.designsystem.AppAlert
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonStyle
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSegment
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSegmentedRow
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTextField
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.designsystem.LocalContentColor
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.FavoriteGroupEditFailure
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.FavoriteGroupEditState
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings

@Composable
internal fun FavoriteGroupEditDialog(
    state: FavoriteGroupEditState,
    onDismiss: () -> Unit,
    onClearFailure: () -> Unit,
    onSave: (String, FavoriteGroupVisibility) -> Unit,
) {
    val group = state.group ?: return
    var displayName by remember(group.id) { mutableStateOf(group.displayName) }
    var visibility by remember(group.id) {
        mutableStateOf(
            FavoriteGroupVisibility.fromValue(group.visibility) ?: FavoriteGroupVisibility.Private
        )
    }
    val normalizedDisplayName = displayName.trim()
    val invalidName = normalizedDisplayName.isEmpty()
    val hasChanges = normalizedDisplayName != group.displayName || visibility.value != group.visibility

    AppAlert(
        onDismissRequest = { if (!state.isSaving) onDismiss() },
        title = { AppText(strings.favoriteGroupEditTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(
                    value = displayName,
                    onValueChange = {
                        displayName = it
                        onClearFailure()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { AppText(strings.favoriteGroupEditName) },
                    supportingText = if (invalidName) {
                        { AppText(strings.favoriteGroupEditNameRequired) }
                    } else {
                        null
                    },
                    isError = invalidName || state.failure == FavoriteGroupEditFailure.InvalidName,
                    singleLine = true,
                    enabled = !state.isSaving,
                )

                AppText(
                    text = strings.favoriteGroupEditVisibility,
                    style = AppTheme.type.subheadlineEmphasized,
                )
                AppSegmentedRow(Modifier.fillMaxWidth()) {
                    FavoriteGroupVisibility.entries.forEachIndexed { index, option ->
                        AppSegment(
                            selected = visibility == option,
                            onClick = {
                                visibility = option
                                onClearFailure()
                            },
                            enabled = !state.isSaving,
                            label = {
                                AppText(
                                    text = when (option) {
                                        FavoriteGroupVisibility.Private -> strings.favoriteGroupVisibilityPrivate
                                        FavoriteGroupVisibility.Friends -> strings.favoriteGroupVisibilityFriends
                                        FavoriteGroupVisibility.Public -> strings.favoriteGroupVisibilityPublic
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }

                if (state.failure == FavoriteGroupEditFailure.SaveFailed) {
                    AppText(
                        text = strings.favoriteGroupEditFailed,
                        style = AppTheme.type.caption1,
                        color = AppTheme.colors.destructive,
                    )
                }
            }
        },
        confirmButton = {
            AppButton(
                onClick = { onSave(normalizedDisplayName, visibility) },
                enabled = hasChanges && !invalidName && !state.isSaving,
                style = AppButtonStyle.Plain,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (state.isSaving) {
                        AppActivityIndicator(
                            modifier = Modifier.size(18.dp),
                            color = LocalContentColor.current,
                        )
                        Spacer(Modifier.size(8.dp))
                    }
                    AppText(
                        if (state.isSaving) {
                            strings.favoriteGroupEditSaving
                        } else {
                            strings.favoriteGroupEditSave
                        }
                    )
                }
            }
        },
        dismissButton = {
            AppButton(
                onClick = onDismiss,
                enabled = !state.isSaving,
                style = AppButtonStyle.Plain,
            ) {
                AppText(strings.cancel)
            }
        },
    )
}
