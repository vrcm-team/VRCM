package io.github.vrcmteam.vrcm.presentation.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.invite.InviteApi
import io.github.vrcmteam.vrcm.network.api.invite.inviteMessageCodePointCount
import io.github.vrcmteam.vrcm.network.api.invite.data.InviteMessageData
import io.github.vrcmteam.vrcm.network.api.invite.data.InviteMessageType
import io.github.vrcmteam.vrcm.presentation.compoments.ATooltipBox
import io.github.vrcmteam.vrcm.presentation.compoments.EmptyContent
import io.github.vrcmteam.vrcm.presentation.navigation.AppDetailRoute
import io.github.vrcmteam.vrcm.presentation.navigation.LocalNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.currentOrThrow
import io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStrings
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

@Serializable
object InviteMessageSlotsScreen : AppDetailRoute {
    @Composable
    override fun Content() {
        val model: InviteMessageSlotsModel = koinViewModel()
        val state by model.state.collectAsState()
        InviteMessageSlotsContent(state = state, model = model)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InviteMessageSlotsContent(
    state: InviteMessageSlotsUiState,
    model: InviteMessageSlotsModel,
) {
    val navigator = LocalNavigator.currentOrThrow
    val locale = strings
    val snackbarHostState = remember { SnackbarHostState() }
    var editingTarget by remember { mutableStateOf<InviteMessageDialogTarget?>(null) }
    var resettingTarget by remember { mutableStateOf<InviteMessageDialogTarget?>(null) }

    LaunchedEffect(state.session, state.selectedType) {
        editingTarget = null
        resettingTarget = null
    }
    LaunchedEffect(state.feedback?.id) {
        val feedback = state.feedback ?: return@LaunchedEffect
        if (feedback.kind == InviteMessageFeedbackKind.Updated) editingTarget = null
        snackbarHostState.showSnackbar(feedback.kind.localizedMessage(locale))
        model.clearFeedback(feedback.id)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = locale.inviteMessageSlotsTitle,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(
                            imageVector = AppIcons.ArrowBackIosNew,
                            contentDescription = locale.back,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            val messageTypes = InviteMessageType.entries
            ScrollableTabRow(
                selectedTabIndex = messageTypes.indexOf(state.selectedType),
                edgePadding = 8.dp,
                divider = { HorizontalDivider() },
            ) {
                messageTypes.forEach { messageType ->
                    Tab(
                        selected = state.selectedType == messageType,
                        onClick = { model.selectType(messageType) },
                        text = {
                            Text(
                                text = messageType.localizedName(locale),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when {
                    state.session == null -> EmptyContent(
                        message = locale.inviteMessageSessionUnavailable,
                    )

                    state.isLoading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )

                    state.loadFailed -> EmptyContent(
                        message = locale.inviteMessageLoadFailed,
                        actionContent = {
                            Button(onClick = model::retry) { Text(locale.retry) }
                        },
                    )

                    state.messages.isEmpty() -> EmptyContent(
                        message = locale.inviteMessageEmpty,
                    )

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(
                            items = state.messages,
                            key = { message -> message.id },
                        ) { message ->
                            InviteMessageSlotCard(
                                message = message,
                                pendingMutation = state.pendingMutation,
                                locale = locale,
                                onEdit = {
                                    state.session?.let { session ->
                                        editingTarget = InviteMessageDialogTarget(
                                            session = session,
                                            messageType = state.selectedType,
                                            slot = message.slot,
                                        )
                                    }
                                },
                                onReset = {
                                    state.session?.let { session ->
                                        resettingTarget = InviteMessageDialogTarget(
                                            session = session,
                                            messageType = state.selectedType,
                                            slot = message.slot,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    editingTarget
        ?.takeIf { it.session == state.session && it.messageType == state.selectedType }
        ?.let { target ->
            state.messages.firstOrNull {
                it.slot == target.slot && it.messageType == target.messageType
            }?.let { message ->
                InviteMessageEditDialog(
                    message = message,
                    isSaving = state.pendingMutation == PendingInviteMessageMutation(
                        messageType = target.messageType,
                        slot = target.slot,
                        kind = InviteMessageMutationKind.Update,
                    ),
                    locale = locale,
                    onDismiss = { editingTarget = null },
                    onSave = { value -> model.updateMessage(target.slot, value) },
                )
            }
        }

    resettingTarget
        ?.takeIf { it.session == state.session && it.messageType == state.selectedType }
        ?.let { target ->
            InviteMessageResetDialog(
                slot = target.slot,
                locale = locale,
                onDismiss = { resettingTarget = null },
                onConfirm = {
                    if (model.resetMessage(target.slot)) resettingTarget = null
                },
            )
        }
}

private data class InviteMessageDialogTarget(
    val session: InviteMessageSession,
    val messageType: InviteMessageType,
    val slot: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InviteMessageSlotCard(
    message: InviteMessageData,
    pendingMutation: PendingInviteMessageMutation?,
    locale: LocaleStrings,
    onEdit: () -> Unit,
    onReset: () -> Unit,
) {
    val isLocked = !message.canBeUpdated || message.remainingCooldownMinutes > 0
    val isPending = pendingMutation?.slot == message.slot &&
        pendingMutation.messageType == message.messageType
    val actionsEnabled = !isLocked && pendingMutation == null

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = locale.inviteMessageSlotLabel.replace("%d", message.slot.toString()),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.weight(1f))
                when {
                    message.remainingCooldownMinutes > 0 -> Text(
                        text = locale.inviteMessageCooldownRemaining.replace(
                            "%d",
                            message.remainingCooldownMinutes.toString(),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )

                    !message.canBeUpdated -> Text(
                        text = locale.inviteMessageUnavailable,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Text(
                text = message.message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isPending) {
                    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                } else {
                    ATooltipBox(tooltip = { Text(locale.inviteMessageEdit) }) {
                        IconButton(onClick = onEdit, enabled = actionsEnabled) {
                            Icon(
                                imageVector = AppIcons.Edit,
                                contentDescription = locale.inviteMessageEdit,
                            )
                        }
                    }
                }
                ATooltipBox(tooltip = { Text(locale.inviteMessageReset) }) {
                    IconButton(onClick = onReset, enabled = actionsEnabled) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = locale.inviteMessageReset,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InviteMessageEditDialog(
    message: InviteMessageData,
    isSaving: Boolean,
    locale: LocaleStrings,
    onDismiss: () -> Unit,
    onSave: (String) -> Boolean,
) {
    var value by remember(message.id) { mutableStateOf(message.message) }
    val validation = validateInviteMessage(value, message.message)
    val isError = validation == InviteMessageEditValidation.Blank ||
        validation == InviteMessageEditValidation.TooLong

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Text(locale.inviteMessageEditTitle.replace("%d", message.slot.toString()))
        },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                isError = isError,
                label = { Text(locale.inviteMessageFieldLabel) },
                minLines = 2,
                maxLines = 3,
                supportingText = {
                    Text(
                        when (validation) {
                            InviteMessageEditValidation.Blank -> locale.inviteMessageRequired
                            InviteMessageEditValidation.TooLong -> locale.inviteMessageTooLong
                            InviteMessageEditValidation.Unchanged -> locale.inviteMessageUnchanged
                            InviteMessageEditValidation.Valid ->
                                "${value.trim().inviteMessageCodePointCount()}/" +
                                    InviteApi.MAX_INVITE_MESSAGE_CODE_POINTS
                        }
                    )
                },
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(value) },
                enabled = validation == InviteMessageEditValidation.Valid && !isSaving,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = LocalContentColor.current,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(locale.editProfileSave)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text(locale.cancel) }
        },
    )
}

@Composable
private fun InviteMessageResetDialog(
    slot: Int,
    locale: LocaleStrings,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(locale.inviteMessageResetTitle) },
        text = {
            Text(locale.inviteMessageResetMessage.replace("%d", slot.toString()))
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text(locale.inviteMessageResetConfirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(locale.cancel) }
        },
    )
}

private fun InviteMessageType.localizedName(locale: LocaleStrings): String = when (this) {
    InviteMessageType.Message -> locale.inviteMessageTypeMessage
    InviteMessageType.Response -> locale.inviteMessageTypeResponse
    InviteMessageType.Request -> locale.inviteMessageTypeRequest
    InviteMessageType.RequestResponse -> locale.inviteMessageTypeRequestResponse
}

private fun InviteMessageFeedbackKind.localizedMessage(locale: LocaleStrings): String = when (this) {
    InviteMessageFeedbackKind.Updated -> locale.inviteMessageUpdated
    InviteMessageFeedbackKind.Reset -> locale.inviteMessageResetDone
    InviteMessageFeedbackKind.Cooldown -> locale.inviteMessageCooldownError
    InviteMessageFeedbackKind.UpdateFailed -> locale.inviteMessageUpdateFailed
    InviteMessageFeedbackKind.ResetFailed -> locale.inviteMessageResetFailed
}
