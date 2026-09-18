package io.github.vrcmteam.vrcm.presentation.screens.favorites

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.compoments.ATooltipBox
import io.github.vrcmteam.vrcm.presentation.designsystem.AppActivityIndicator
import io.github.vrcmteam.vrcm.presentation.designsystem.AppAlert
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonStyle
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIconButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
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
        AppText(
            text = if (state.isSubmitting) {
                progressText
                    .replaceFirst("%d", state.completedCount.toString())
                    .replaceFirst("%d", state.totalCount.toString())
            } else {
                selectedCountText.replaceFirst("%d", state.selectedIds.size.toString())
            },
            modifier = Modifier.weight(1f),
            style = AppTheme.type.subheadlineEmphasized,
        )
        AppButton(
            enabled = !state.isSubmitting && visibleIds.isNotEmpty(),
            onClick = { onToggleVisibleSelection(visibleIds) },
            style = AppButtonStyle.Plain,
        ) {
            AppText(if (allVisibleSelected) clearSelectionText else selectAllText)
        }
    }
}

@Composable
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
        ATooltipBox(tooltip = { AppText(cancelDescription) }) {
            AppIconButton(enabled = !state.isSubmitting, onClick = onExitSelection) {
                AppIcon(AppIcons.Close, cancelDescription)
            }
        }
        ATooltipBox(tooltip = { AppText(removeSelectedDescription) }) {
            AppIconButton(
                enabled = state.selectedIds.isNotEmpty() && !state.isSubmitting,
                onClick = onRequestRemoval,
            ) {
                if (state.isSubmitting) {
                    AppActivityIndicator(Modifier.size(20.dp))
                } else {
                    AppIcon(AppIcons.Delete, removeSelectedDescription)
                }
            }
        }
    } else {
        ATooltipBox(tooltip = { AppText(enterSelectionDescription) }) {
            AppIconButton(enabled = canEnterSelection, onClick = onEnterSelection) {
                AppIcon(AppIcons.Delete, enterSelectionDescription)
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
    AppAlert(
        onDismissRequest = onDismiss,
        title = { AppText(title) },
        text = {
            AppText(message.replaceFirst("%d", state.selectedIds.size.toString()))
        },
        confirmButton = {
            AppButton(
                enabled = state.selectedIds.isNotEmpty() && !state.isSubmitting,
                onClick = onConfirm,
                style = AppButtonStyle.Plain,
            ) {
                AppText(confirmLabel)
            }
        },
        dismissButton = {
            AppButton(onClick = onDismiss, style = AppButtonStyle.Plain) { AppText(cancelLabel) }
        },
    )
}
