package io.github.vrcmteam.vrcm.presentation.screens.user

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.invite.data.InviteMessageData
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.service.InviteMessageAction

@Composable
internal fun InviteMessageSelectorDialog(
    state: InviteMessageSelectionState?,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onSend: (Int) -> Unit,
) {
    state ?: return
    var selectedSlot by remember(state.action, state.targetUserId, state.messages) {
        mutableIntStateOf(state.messages.firstOrNull()?.slot ?: -1)
    }
    val sending = state.sendingSlot != null
    val title = when (state.action) {
        InviteMessageAction.Invite -> strings.inviteMessageSelectorInviteTitle
        InviteMessageAction.RequestInvite -> strings.inviteMessageSelectorRequestTitle
    }.replace("%name%", state.targetDisplayName)

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    state.isLoading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )

                    state.loadFailed -> {
                        Text(strings.inviteMessageSelectorLoadFailed)
                        TextButton(
                            modifier = Modifier.align(Alignment.End),
                            onClick = onRetry,
                        ) {
                            Text(strings.retry)
                        }
                    }

                    state.messages.isEmpty() -> Text(strings.inviteMessageSelectorEmpty)

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(state.messages, key = InviteMessageData::slot) { message ->
                            InviteMessageSlotRow(
                                message = message,
                                selected = message.slot == selectedSlot,
                                enabled = !sending,
                                onSelect = { selectedSlot = message.slot },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !state.isLoading && !state.loadFailed && selectedSlot >= 0 && !sending,
                onClick = { onSend(selectedSlot) },
            ) {
                if (sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.size(8.dp))
                }
                Text(strings.inviteMessageSelectorSend)
            }
        },
        dismissButton = {
            TextButton(enabled = !sending, onClick = onDismiss) {
                Text(strings.cancel)
            }
        },
    )
}

@Composable
private fun InviteMessageSlotRow(
    message: InviteMessageData,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, enabled = enabled, onClick = onSelect)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = strings.inviteMessageSelectorSlot.replace("%slot%", (message.slot + 1).toString()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = message.message,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
