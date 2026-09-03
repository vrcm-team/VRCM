package io.github.vrcmteam.vrcm.presentation.screens.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.FavoriteGroupEditFailure
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.FavoriteGroupEditState
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings

@OptIn(ExperimentalMaterial3Api::class)
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

    AlertDialog(
        onDismissRequest = { if (!state.isSaving) onDismiss() },
        title = { Text(strings.favoriteGroupEditTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = {
                        displayName = it
                        onClearFailure()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(strings.favoriteGroupEditName) },
                    supportingText = if (invalidName) {
                        { Text(strings.favoriteGroupEditNameRequired) }
                    } else {
                        null
                    },
                    isError = invalidName || state.failure == FavoriteGroupEditFailure.InvalidName,
                    singleLine = true,
                    enabled = !state.isSaving,
                )

                Text(
                    text = strings.favoriteGroupEditVisibility,
                    style = MaterialTheme.typography.labelLarge,
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    FavoriteGroupVisibility.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = visibility == option,
                            onClick = {
                                visibility = option
                                onClearFailure()
                            },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = FavoriteGroupVisibility.entries.size,
                            ),
                            enabled = !state.isSaving,
                            icon = {},
                            label = {
                                Text(
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
                    Text(
                        text = strings.favoriteGroupEditFailed,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(normalizedDisplayName, visibility) },
                enabled = hasChanges && !invalidName && !state.isSaving,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = LocalContentColor.current,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(
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
            TextButton(
                onClick = onDismiss,
                enabled = !state.isSaving,
            ) {
                Text(strings.cancel)
            }
        },
    )
}
