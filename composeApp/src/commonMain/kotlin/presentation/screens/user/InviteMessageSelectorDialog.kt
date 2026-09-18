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
import io.github.vrcmteam.vrcm.presentation.designsystem.AppActivityIndicator
import io.github.vrcmteam.vrcm.presentation.designsystem.AppAlert
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonStyle
import io.github.vrcmteam.vrcm.presentation.designsystem.AppRadioButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
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

    AppAlert(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { AppText(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    state.isLoading -> AppActivityIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )

                    state.loadFailed -> {
                        AppText(strings.inviteMessageSelectorLoadFailed)
                        AppButton(
                            modifier = Modifier.align(Alignment.End),
                            onClick = onRetry,
                            style = AppButtonStyle.Plain,
                        ) {
                            AppText(strings.retry)
                        }
                    }

                    state.messages.isEmpty() -> AppText(strings.inviteMessageSelectorEmpty)

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
            AppButton(
                enabled = !state.isLoading && !state.loadFailed && selectedSlot >= 0 && !sending,
                onClick = { onSend(selectedSlot) },
                style = AppButtonStyle.Prominent,
            ) {
                if (sending) {
                    AppActivityIndicator(
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                }
                AppText(strings.inviteMessageSelectorSend)
            }
        },
        dismissButton = {
            AppButton(enabled = !sending, onClick = onDismiss, style = AppButtonStyle.Plain) {
                AppText(strings.cancel)
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
        AppRadioButton(selected = selected, enabled = enabled, onClick = onSelect)
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = strings.inviteMessageSelectorSlot.replace("%slot%", (message.slot + 1).toString()),
                style = AppTheme.type.caption1Emphasized,
                color = AppTheme.colors.secondaryLabel,
            )
            AppText(
                text = message.message,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
