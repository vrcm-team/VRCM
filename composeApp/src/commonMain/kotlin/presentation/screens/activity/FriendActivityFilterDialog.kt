package io.github.vrcmteam.vrcm.presentation.screens.activity

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import io.github.vrcmteam.vrcm.presentation.designsystem.AppAlert
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppCheckbox
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSize
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings

@Composable
internal fun FriendActivityFilterDialog(
    selectedFilters: Set<FriendActivityTimelineFilter>,
    onDismiss: () -> Unit,
    onConfirm: (Set<FriendActivityTimelineFilter>) -> Unit,
) {
    var draft by remember(selectedFilters) { mutableStateOf(selectedFilters) }
    AppAlert(
        onDismissRequest = onDismiss,
        title = { AppText(strings.friendActivityFilterTitle) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                FriendActivityTimelineFilter.entries.forEach { filter ->
                    val checked = filter in draft
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .heightIn(min = AppSize.touchTarget)
                            .toggleable(
                                value = checked,
                                role = Role.Checkbox,
                                onValueChange = { draft = if (it) draft + filter else draft - filter },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppCheckbox(checked = checked, onCheckedChange = null)
                        AppText(filter.label(), modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            AppButton(onClick = { onConfirm(draft) }) { AppText(strings.confirm) }
        },
        dismissButton = {
            AppButton(onClick = onDismiss) { AppText(strings.cancel) }
        },
    )
}
