package io.github.vrcmteam.vrcm.presentation.screens.favorites

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.compoments.ATooltipBox
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons

internal data class SelectionRemovalResult(
    val itemId: String,
    val errorMessage: String? = null,
) {
    val succeeded: Boolean get() = errorMessage == null
}

internal data class SelectionRemovalState(
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val confirmationVisible: Boolean = false,
    val isSubmitting: Boolean = false,
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val results: Map<String, SelectionRemovalResult> = emptyMap(),
) {
    val successCount: Int get() = results.values.count(SelectionRemovalResult::succeeded)
    val failureCount: Int get() = results.size - successCount
}

@Composable
internal fun SelectionRemovalStatusRow(
    state: SelectionRemovalState,
    visibleIds: Set<String>,
    selectedCountText: String,
    progressText: String,
    selectAllText: String,
    clearSelectionText: String,
    onToggleVisibleSelection: (Set<String>) -> Unit,
) {
    val allVisibleSelected = visibleIds.isNotEmpty() && visibleIds.all { it in state.selectedIds }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (state.isSubmitting) {
                progressText
                    .replaceFirst("%d", state.completedCount.toString())
                    .replaceFirst("%d", state.totalCount.toString())
            } else {
                selectedCountText.replaceFirst("%d", state.selectedIds.size.toString())
            },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
        )
        TextButton(
            enabled = !state.isSubmitting && visibleIds.isNotEmpty(),
            onClick = { onToggleVisibleSelection(visibleIds) },
        ) {
            Text(if (allVisibleSelected) clearSelectionText else selectAllText)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun RowScope.SelectionRemovalActions(
    state: SelectionRemovalState,
    canEnterSelection: Boolean,
    enterSelectionDescription: String,
    removeSelectedDescription: String,
    cancelDescription: String,
    onEnterSelection: () -> Unit,
    onExitSelection: () -> Unit,
    onRequestRemoval: () -> Unit,
) {
    if (state.selectionMode) {
        ATooltipBox(tooltip = { Text(cancelDescription) }) {
            IconButton(enabled = !state.isSubmitting, onClick = onExitSelection) {
                Icon(AppIcons.Close, cancelDescription)
            }
        }
        ATooltipBox(tooltip = { Text(removeSelectedDescription) }) {
            IconButton(
                enabled = state.selectedIds.isNotEmpty() && !state.isSubmitting,
                onClick = onRequestRemoval,
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.DeleteOutline, removeSelectedDescription)
                }
            }
        }
    } else {
        ATooltipBox(tooltip = { Text(enterSelectionDescription) }) {
            IconButton(enabled = canEnterSelection, onClick = onEnterSelection) {
                Icon(Icons.Outlined.DeleteOutline, enterSelectionDescription)
            }
        }
    }
}

@Composable
internal fun SelectionRemovalConfirmationDialog(
    state: SelectionRemovalState,
    title: String,
    message: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!state.confirmationVisible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(message.replaceFirst("%d", state.selectedIds.size.toString()))
        },
        confirmButton = {
            TextButton(
                enabled = state.selectedIds.isNotEmpty() && !state.isSubmitting,
                onClick = onConfirm,
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(cancelLabel) }
        },
    )
}
