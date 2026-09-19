package io.github.vrcmteam.vrcm.presentation.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import io.github.vrcmteam.vrcm.presentation.designsystem.AppActivityIndicator
import io.github.vrcmteam.vrcm.presentation.designsystem.AppAlert
import io.github.vrcmteam.vrcm.presentation.designsystem.AppBannerHost
import io.github.vrcmteam.vrcm.presentation.designsystem.AppBannerHostState
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonStyle
import io.github.vrcmteam.vrcm.presentation.designsystem.AppDivider
import io.github.vrcmteam.vrcm.presentation.designsystem.AppGroup
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIconButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppNavBar
import io.github.vrcmteam.vrcm.presentation.designsystem.AppScaffold
import io.github.vrcmteam.vrcm.presentation.designsystem.AppScrollableTabRow
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSize
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSpacing
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTab
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTextField
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.designsystem.LocalContentColor
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

@Composable
private fun InviteMessageSlotsContent(
    state: InviteMessageSlotsUiState,
    model: InviteMessageSlotsModel,
) {
    val navigator = LocalNavigator.currentOrThrow
    val locale = strings
    val bannerHostState = remember { AppBannerHostState() }
    var editingTarget by remember { mutableStateOf<InviteMessageDialogTarget?>(null) }
    var resettingTarget by remember { mutableStateOf<InviteMessageDialogTarget?>(null) }

    LaunchedEffect(state.session, state.selectedType) {
        editingTarget = null
        resettingTarget = null
    }
    LaunchedEffect(state.feedback?.id) {
        val feedback = state.feedback ?: return@LaunchedEffect
        if (feedback.kind == InviteMessageFeedbackKind.Updated) editingTarget = null
        bannerHostState.show(feedback.kind.localizedMessage(locale))
        model.clearFeedback(feedback.id)
    }

    AppScaffold(
        topBar = {
            AppNavBar(
                title = {
                    AppText(
                        text = locale.inviteMessageSlotsTitle,
                    )
                },
                navigationIcon = {
                    AppIconButton(onClick = { navigator.pop() }) {
                        AppIcon(
                            imageVector = AppIcons.ArrowBackIosNew,
                            contentDescription = locale.back,
                        )
                    }
                },
            )
        },
        bannerHost = { AppBannerHost(bannerHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            val messageTypes = InviteMessageType.entries
            AppScrollableTabRow(
                edgePadding = 8.dp,
            ) {
                messageTypes.forEach { messageType ->
                    AppTab(
                        selected = state.selectedType == messageType,
                        onClick = { model.selectType(messageType) },
                        text = {
                            AppText(
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

                    state.isLoading -> AppActivityIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )

                    state.loadFailed -> EmptyContent(
                        message = locale.inviteMessageLoadFailed,
                        actionContent = {
                            AppButton(onClick = model::retry, style = AppButtonStyle.Prominent) { AppText(locale.retry) }
                        },
                    )

                    state.messages.isEmpty() -> EmptyContent(
                        message = locale.inviteMessageEmpty,
                    )

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = AppSpacing.page, vertical = 8.dp),
                    ) {
                        // 槽位数量固定且很少：整组放进一张分组卡片，行间用发丝线
                        item {
                            AppGroup {
                                state.messages.forEachIndexed { index, message ->
                                    key(message.slot) {
                                        if (index > 0) AppDivider(Modifier.padding(start = AppSpacing.row))
                                        InviteMessageSlotRow(
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

/** 一个槽位：上面一行小字是槽位号与状态，下面是消息正文；尾部是编辑 / 重置。 */
@Composable
private fun InviteMessageSlotRow(
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
    val lockedText = when {
        message.remainingCooldownMinutes > 0 -> locale.inviteMessageCooldownRemaining.replace(
            "%d",
            message.remainingCooldownMinutes.toString(),
        )
        !message.canBeUpdated -> locale.inviteMessageUnavailable
        else -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppSize.rowMinHeight)
            .padding(start = AppSpacing.row, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(
                    text = locale.inviteMessageSlotLabel.replace("%d", message.slot.toString()),
                    style = AppTheme.type.footnote,
                    color = AppTheme.colors.secondaryLabel,
                    maxLines = 1,
                )
                if (lockedText != null) {
                    AppText(
                        text = lockedText,
                        style = AppTheme.type.footnote,
                        color = AppTheme.colors.onWarningSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            AppText(
                text = message.message,
                style = AppTheme.type.body,
                color = AppTheme.colors.label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isPending) {
            Box(modifier = Modifier.size(AppSize.iconButton), contentAlignment = Alignment.Center) {
                AppActivityIndicator(modifier = Modifier.size(20.dp))
            }
        } else {
            ATooltipBox(tooltip = { AppText(locale.inviteMessageEdit) }) {
                AppIconButton(onClick = onEdit, enabled = actionsEnabled, contentColor = AppTheme.colors.tint) {
                    AppIcon(
                        imageVector = AppIcons.Edit,
                        contentDescription = locale.inviteMessageEdit,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        ATooltipBox(tooltip = { AppText(locale.inviteMessageReset) }) {
            AppIconButton(onClick = onReset, enabled = actionsEnabled, contentColor = AppTheme.colors.tint) {
                AppIcon(
                    imageVector = AppIcons.Reset,
                    contentDescription = locale.inviteMessageReset,
                    modifier = Modifier.size(20.dp),
                )
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

    AppAlert(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            AppText(locale.inviteMessageEditTitle.replace("%d", message.slot.toString()))
        },
        text = {
            AppTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                isError = isError,
                label = { AppText(locale.inviteMessageFieldLabel) },
                minLines = 2,
                maxLines = 3,
                supportingText = {
                    AppText(
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
            AppButton(
                onClick = { onSave(value) },
                enabled = validation == InviteMessageEditValidation.Valid && !isSaving,
                style = AppButtonStyle.Prominent,
            ) {
                if (isSaving) {
                    AppActivityIndicator(
                        modifier = Modifier.size(18.dp),
                        color = LocalContentColor.current,
                    )
                } else {
                    AppText(locale.editProfileSave)
                }
            }
        },
        dismissButton = {
            AppButton(onClick = onDismiss, enabled = !isSaving, style = AppButtonStyle.Plain) { AppText(locale.cancel) }
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
    AppAlert(
        onDismissRequest = onDismiss,
        title = { AppText(locale.inviteMessageResetTitle) },
        text = {
            AppText(locale.inviteMessageResetMessage.replace("%d", slot.toString()))
        },
        confirmButton = {
            AppButton(onClick = onConfirm, style = AppButtonStyle.Prominent) { AppText(locale.inviteMessageResetConfirm) }
        },
        dismissButton = {
            AppButton(onClick = onDismiss, style = AppButtonStyle.Plain) { AppText(locale.cancel) }
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
