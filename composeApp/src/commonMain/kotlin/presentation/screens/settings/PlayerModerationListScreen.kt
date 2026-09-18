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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.vrcmteam.vrcm.core.extensions.toLocalDateTime
import io.github.vrcmteam.vrcm.network.api.playermoderation.data.PlayerModerationData
import io.github.vrcmteam.vrcm.network.api.playermoderation.data.PlayerModerationType
import io.github.vrcmteam.vrcm.presentation.designsystem.AppActivityIndicator
import io.github.vrcmteam.vrcm.presentation.designsystem.AppAlert
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonStyle
import io.github.vrcmteam.vrcm.presentation.designsystem.AppDivider
import io.github.vrcmteam.vrcm.presentation.designsystem.AppFilterChip
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIconButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppListItem
import io.github.vrcmteam.vrcm.presentation.designsystem.AppMenu
import io.github.vrcmteam.vrcm.presentation.designsystem.AppMenuItem
import io.github.vrcmteam.vrcm.presentation.designsystem.AppNavBar
import io.github.vrcmteam.vrcm.presentation.designsystem.AppProgressBar
import io.github.vrcmteam.vrcm.presentation.designsystem.AppRadioButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppScaffold
import io.github.vrcmteam.vrcm.presentation.designsystem.AppShapes
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSurface
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.extensions.ignoredFormat
import io.github.vrcmteam.vrcm.presentation.navigation.AppDetailRoute
import io.github.vrcmteam.vrcm.presentation.navigation.BlockBackNavigation
import io.github.vrcmteam.vrcm.presentation.navigation.LocalNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.currentOrThrow
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStrings
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

@Serializable
object PlayerModerationListScreen : AppDetailRoute {
    @Composable
    override fun Content() {
        PlayerModerationScreenContent()
    }
}

@Composable
internal fun PlayerModerationScreenContent(
    model: PlayerModerationListScreenModel = koinViewModel(),
) {
    val navigator = LocalNavigator.currentOrThrow
    val state by model.state.collectAsState()
    var showCleanupDialog by remember { mutableStateOf(false) }
    var pendingRecordCleanup by remember { mutableStateOf<PlayerModerationData?>(null) }
    var refreshAfterManagingPlayer by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { model.loadIfNeeded() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (refreshAfterManagingPlayer) {
            refreshAfterManagingPlayer = false
            model.refresh()
        }
    }
    LaunchedEffect(
        state.sessionToken,
        state.isSessionAvailable,
        state.isLoading,
        state.isClearing,
        state.availableTypes,
    ) {
        if (!state.isSessionAvailable || state.isLoading || state.isClearing ||
            state.availableTypes.isEmpty()
        ) {
            showCleanupDialog = false
        }
    }
    LaunchedEffect(
        state.sessionToken,
        state.isSessionAvailable,
        state.isLoading,
        state.isClearing,
        state.records,
    ) {
        pendingRecordCleanup = pendingRecordCleanup?.takeIf { pending ->
            state.isSessionAvailable && !state.isLoading && !state.isClearing &&
                state.records.any { record ->
                    record.targetUserId == pending.targetUserId && record.type == pending.type
                }
        }
    }
    BlockBackNavigation(blocked = state.isClearing)

    if (showCleanupDialog) {
        PlayerModerationCleanupDialog(
            state = state,
            onSelect = model::selectCleanupType,
            onDismiss = { showCleanupDialog = false },
            onConfirm = {
                val type = state.selectedCleanupType
                val token = state.sessionToken
                showCleanupDialog = false
                if (type != null && token != null) model.clearSelected(type, token)
            },
        )
    }

    pendingRecordCleanup?.let { record ->
        PlayerModerationRecordCleanupDialog(
            record = record,
            onDismiss = { pendingRecordCleanup = null },
            onConfirm = {
                val token = state.sessionToken
                pendingRecordCleanup = null
                if (token != null) model.clearRecord(record, token)
            },
        )
    }

    AppScaffold(
        topBar = {
            AppNavBar(
                title = { AppText(strings.playerModerationTitle) },
                navigationIcon = {
                    AppIconButton(
                        enabled = !state.isClearing,
                        onClick = { navigator.pop() },
                    ) {
                        AppIcon(
                            imageVector = AppIcons.ArrowBackIosNew,
                            contentDescription = strings.notificationBack,
                        )
                    }
                },
                actions = {
                    AppIconButton(
                        onClick = { showCleanupDialog = true },
                        enabled = state.availableTypes.isNotEmpty() &&
                            !state.isLoading &&
                            !state.isClearing,
                    ) {
                        AppIcon(
                            imageVector = AppIcons.Delete,
                            contentDescription = strings.playerModerationCleanupTitle,
                        )
                    }
                    AppIconButton(
                        onClick = model::refresh,
                        enabled = state.isSessionAvailable &&
                            !state.isLoading &&
                            !state.isClearing,
                    ) {
                        AppIcon(
                            imageVector = AppIcons.Refresh,
                            contentDescription = strings.playerModerationRefresh,
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        PlayerModerationContent(
            state = state,
            contentPadding = contentPadding,
            onSelectFilter = model::selectFilter,
            onManagePlayer = { record ->
                refreshAfterManagingPlayer = true
                navigator.push(
                    UserProfileScreen(
                        UserProfileVo(
                            id = record.targetUserId,
                            displayName = record.targetDisplayName,
                        ),
                        openActionMenuOnEntry = true,
                    ),
                )
            },
            onClearRecord = { pendingRecordCleanup = it },
            onRetry = model::refresh,
        )
    }
}

@Composable
private fun PlayerModerationContent(
    state: PlayerModerationState,
    contentPadding: PaddingValues,
    onSelectFilter: (String?) -> Unit,
    onManagePlayer: (PlayerModerationData) -> Unit,
    onClearRecord: (PlayerModerationData) -> Unit,
    onRetry: () -> Unit,
) {
    when {
        !state.isSessionAvailable -> MessageState(
            message = strings.playerModerationSignedOut,
            contentPadding = contentPadding,
        )

        state.isLoading && !state.hasLoaded -> Box(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            AppActivityIndicator(modifier = Modifier.size(32.dp))
        }

        state.loadFailed && state.records.isEmpty() -> MessageState(
            message = strings.playerModerationLoadFailed,
            contentPadding = contentPadding,
            action = {
                AppButton(onClick = onRetry, style = AppButtonStyle.Plain) { AppText(strings.retry) }
            },
        )

        else -> PlayerModerationLoadedContent(
            state = state,
            contentPadding = contentPadding,
            onSelectFilter = onSelectFilter,
            onManagePlayer = onManagePlayer,
            onClearRecord = onClearRecord,
            onRetry = onRetry,
        )
    }
}

@Composable
private fun PlayerModerationLoadedContent(
    state: PlayerModerationState,
    contentPadding: PaddingValues,
    onSelectFilter: (String?) -> Unit,
    onManagePlayer: (PlayerModerationData) -> Unit,
    onClearRecord: (PlayerModerationData) -> Unit,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        if (state.result != null || state.loadFailed || state.isLoading || state.isClearing) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.result?.let { CleanupResultMessage(it) }
                if (state.loadFailed && state.records.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppText(
                            text = strings.playerModerationLoadFailed,
                            modifier = Modifier.weight(1f),
                            style = AppTheme.type.caption1,
                            color = AppTheme.colors.destructive,
                        )
                        AppButton(onClick = onRetry, style = AppButtonStyle.Plain) { AppText(strings.retry) }
                    }
                }
                if (state.isLoading && state.hasLoaded) {
                    AppProgressBar(modifier = Modifier.fillMaxWidth())
                }
                if (state.isClearing) {
                    PlayerModerationCleanupProgress(state)
                }
            }
        }

        if (state.records.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    text = strings.playerModerationEmpty,
                    modifier = Modifier.padding(24.dp),
                    color = AppTheme.colors.secondaryLabel,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    AppFilterChip(
                        selected = state.selectedFilter == null,
                        onClick = { onSelectFilter(null) },
                        enabled = !state.isClearing,
                        label = { AppText(strings.playerModerationFilterAll) },
                    )
                }
                items(state.availableFilterTypes) { type ->
                    AppFilterChip(
                        selected = state.selectedFilter == type,
                        onClick = { onSelectFilter(type) },
                        enabled = !state.isClearing,
                        label = { AppText(strings.playerModerationTypeLabel(type)) },
                    )
                }
            }
            AppDivider()
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                itemsIndexed(
                    items = state.visibleRecords,
                    key = { _, item -> item.key },
                ) { index, item ->
                    if (index > 0) {
                        AppDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    PlayerModerationRecordItem(
                        record = item.record,
                        enabled = !state.isLoading && !state.isClearing,
                        onManage = { onManagePlayer(item.record) },
                        onClear = { onClearRecord(item.record) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerModerationCleanupDialog(
    state: PlayerModerationState,
    onSelect: (PlayerModerationType) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val selectedOption = state.availableTypes.firstOrNull {
        it.type == state.selectedCleanupType
    }
    AppAlert(
        onDismissRequest = onDismiss,
        title = { AppText(strings.playerModerationCleanupConfirmTitle) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppText(
                    text = strings.playerModerationCleanupDescription,
                    style = AppTheme.type.subheadline,
                    color = AppTheme.colors.secondaryLabel,
                )
                AppText(
                    text = strings.playerModerationCleanupSelectType,
                    style = AppTheme.type.subheadlineEmphasized,
                )
                state.availableTypes.forEach { option ->
                    val selected = option.type == state.selectedCleanupType
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(option.type) },
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppRadioButton(
                            selected = selected,
                            onClick = null,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            AppText(
                                text = option.type.localizedName(strings),
                                style = AppTheme.type.body,
                            )
                            AppText(
                                text = strings.playerModerationCleanupTargetCount
                                    .replace("%count%", option.targetCount.toString()),
                                style = AppTheme.type.caption1,
                                color = AppTheme.colors.secondaryLabel,
                            )
                        }
                    }
                }
                selectedOption?.let { option ->
                    AppText(
                        text = strings.playerModerationCleanupConfirmMessage
                            .replace("%type%", option.type.localizedName(strings))
                            .replace("%count%", option.targetCount.toString()),
                        style = AppTheme.type.subheadline,
                        color = AppTheme.colors.destructive,
                    )
                }
            }
        },
        confirmButton = {
            AppButton(
                enabled = selectedOption != null,
                onClick = onConfirm,
                style = AppButtonStyle.Plain,
            ) {
                AppText(
                    text = strings.playerModerationCleanupAction,
                    color = AppTheme.colors.destructive,
                )
            }
        },
        dismissButton = {
            AppButton(onClick = onDismiss, style = AppButtonStyle.Plain) {
                AppText(strings.cancel)
            }
        },
    )
}

@Composable
private fun PlayerModerationCleanupProgress(state: PlayerModerationState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppProgressBar(
            progress = {
                if (state.totalCount == 0) 0f
                else state.processedCount.toFloat() / state.totalCount
            },
            modifier = Modifier.fillMaxWidth(),
        )
        AppText(
            text = if (state.totalCount == 0) {
                strings.playerModerationCleanupPreparing
            } else {
                strings.playerModerationCleanupProgress
                    .replace("%processed%", state.processedCount.toString())
                    .replace("%total%", state.totalCount.toString())
            },
            style = AppTheme.type.caption1,
            color = AppTheme.colors.secondaryLabel,
        )
    }
}

@Composable
private fun CleanupResultMessage(result: PlayerModerationCleanupResult) {
    val (message, containerColor, contentColor) = when (result.kind) {
        PlayerModerationCleanupResultKind.Success -> Triple(
            strings.playerModerationCleanupSuccess
                .replace("%removed%", result.removedCount.toString()),
            AppTheme.colors.tintSoft,
            AppTheme.colors.onTintSoft,
        )

        PlayerModerationCleanupResultKind.NoRecords -> Triple(
            strings.playerModerationCleanupNoRecords,
            AppTheme.colors.fill,
            AppTheme.colors.secondaryLabel,
        )

        PlayerModerationCleanupResultKind.PartialFailure -> Triple(
            strings.playerModerationCleanupPartialFailure
                .replace("%removed%", result.removedCount.toString())
                .replace("%failed%", result.failedCount.toString()),
            AppTheme.colors.destructiveSoft,
            AppTheme.colors.onDestructiveSoft,
        )

        PlayerModerationCleanupResultKind.Failure -> Triple(
            strings.playerModerationCleanupFailure,
            AppTheme.colors.destructiveSoft,
            AppTheme.colors.onDestructiveSoft,
        )
    }
    AppSurface(
        modifier = Modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = contentColor,
        shape = AppShapes.s,
    ) {
        AppText(
            text = message,
            modifier = Modifier.padding(12.dp),
            style = AppTheme.type.subheadline,
        )
    }
}

@Composable
private fun PlayerModerationRecordCleanupDialog(
    record: PlayerModerationData,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AppAlert(
        onDismissRequest = onDismiss,
        icon = {
            AppIcon(
                imageVector = AppIcons.Delete,
                contentDescription = null,
                tint = AppTheme.colors.destructive,
            )
        },
        title = { AppText(strings.playerModerationRemoveConfirmTitle) },
        text = {
            AppText(
                strings.playerModerationRemoveConfirmMessage
                    .replace("%player%", record.targetLabel(strings))
                    .replace("%type%", strings.playerModerationTypeLabel(record.type)),
            )
        },
        confirmButton = {
            AppButton(onClick = onConfirm, style = AppButtonStyle.Plain) {
                AppText(
                    text = strings.playerModerationRemoveSetting,
                    color = AppTheme.colors.destructive,
                )
            }
        },
        dismissButton = {
            AppButton(onClick = onDismiss, style = AppButtonStyle.Plain) {
                AppText(strings.cancel)
            }
        },
    )
}

@Composable
private fun PlayerModerationRecordItem(
    record: PlayerModerationData,
    enabled: Boolean,
    onManage: () -> Unit,
    onClear: () -> Unit,
) {
    val target = record.targetLabel(strings)
    val created = record.created.toLocalDateTime()?.ignoredFormat
        ?: record.created.ifBlank { strings.unknown }
    val canManage = record.targetUserId.isNotBlank()
    val canClear = PlayerModerationType.fromApiValue(record.type) != null &&
        canManage
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(enabled) {
        if (!enabled) menuExpanded = false
    }

    AppListItem(
        headlineContent = {
            AppText(
                text = target,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                AppText(
                    text = strings.playerModerationTypeLabel(record.type),
                    color = AppTheme.colors.secondaryLabel,
                )
                AppText(
                    text = created,
                    color = AppTheme.colors.secondaryLabel,
                    style = AppTheme.type.caption2Emphasized,
                )
            }
        },
        trailingContent = if (canManage) {
            {
                Box {
                    AppIconButton(
                        enabled = enabled,
                        onClick = { menuExpanded = true },
                    ) {
                        AppIcon(
                            imageVector = AppIcons.More,
                            contentDescription = strings.playerModerationPlayerActions,
                        )
                    }
                    AppMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        AppMenuItem(
                            text = { AppText(strings.playerModerationManagePlayer) },
                            leadingIcon = {
                                AppIcon(
                                    imageVector = AppIcons.Person,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onManage()
                            },
                        )
                        if (canClear) {
                            AppMenuItem(
                                text = {
                                    AppText(
                                        text = strings.playerModerationRemoveSetting,
                                        color = AppTheme.colors.destructive,
                                    )
                                },
                                leadingIcon = {
                                    AppIcon(
                                        imageVector = AppIcons.Delete,
                                        contentDescription = null,
                                        tint = AppTheme.colors.destructive,
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onClear()
                                },
                            )
                        }
                    }
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun MessageState(
    message: String,
    contentPadding: PaddingValues,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        AppText(
            text = message,
            color = AppTheme.colors.secondaryLabel,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}

private fun PlayerModerationType.localizedName(locale: LocaleStrings): String =
    locale.playerModerationTypeLabel(apiValue)

private fun PlayerModerationData.targetLabel(locale: LocaleStrings): String =
    targetDisplayName.ifBlank {
        targetUserId.ifBlank { locale.playerModerationUnknownPlayer }
    }

private fun LocaleStrings.playerModerationTypeLabel(type: String): String = when (type) {
    "mute" -> playerModerationTypeMute
    "unmute" -> playerModerationTypeUnmute
    "block" -> playerModerationTypeBlock
    "unblock" -> playerModerationTypeUnblock
    "interactOff" -> playerModerationTypeInteractOff
    "interactOn" -> playerModerationTypeInteractOn
    "muteChat" -> playerModerationTypeMuteChat
    "unmuteChat" -> playerModerationTypeUnmuteChat
    else -> playerModerationTypeUnknown.replace("%s", type.ifBlank { unknown })
}
