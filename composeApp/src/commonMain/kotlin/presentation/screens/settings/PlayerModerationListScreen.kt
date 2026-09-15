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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.vrcmteam.vrcm.core.extensions.toLocalDateTime
import io.github.vrcmteam.vrcm.network.api.playermoderation.data.PlayerModerationData
import io.github.vrcmteam.vrcm.network.api.playermoderation.data.PlayerModerationType
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

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(strings.playerModerationTitle) },
                navigationIcon = {
                    IconButton(
                        enabled = !state.isClearing,
                        onClick = { navigator.pop() },
                    ) {
                        Icon(
                            imageVector = AppIcons.ArrowBackIosNew,
                            contentDescription = strings.notificationBack,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showCleanupDialog = true },
                        enabled = state.availableTypes.isNotEmpty() &&
                            !state.isLoading &&
                            !state.isClearing,
                    ) {
                        Icon(
                            imageVector = AppIcons.Clear,
                            contentDescription = strings.playerModerationCleanupTitle,
                        )
                    }
                    IconButton(
                        onClick = model::refresh,
                        enabled = state.isSessionAvailable &&
                            !state.isLoading &&
                            !state.isClearing,
                    ) {
                        Icon(
                            imageVector = AppIcons.Update,
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
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }

        state.loadFailed && state.records.isEmpty() -> MessageState(
            message = strings.playerModerationLoadFailed,
            contentPadding = contentPadding,
            action = {
                TextButton(onClick = onRetry) { Text(strings.retry) }
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
                        Text(
                            text = strings.playerModerationLoadFailed,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = onRetry) { Text(strings.retry) }
                    }
                }
                if (state.isLoading && state.hasLoaded) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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
                Text(
                    text = strings.playerModerationEmpty,
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    FilterChip(
                        selected = state.selectedFilter == null,
                        onClick = { onSelectFilter(null) },
                        enabled = !state.isClearing,
                        label = { Text(strings.playerModerationFilterAll) },
                    )
                }
                items(state.availableFilterTypes) { type ->
                    FilterChip(
                        selected = state.selectedFilter == type,
                        onClick = { onSelectFilter(type) },
                        enabled = !state.isClearing,
                        label = { Text(strings.playerModerationTypeLabel(type)) },
                    )
                }
            }
            HorizontalDivider()
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                itemsIndexed(
                    items = state.visibleRecords,
                    key = { _, item -> item.key },
                ) { index, item ->
                    if (index > 0) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.playerModerationCleanupConfirmTitle) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = strings.playerModerationCleanupDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = strings.playerModerationCleanupSelectType,
                    style = MaterialTheme.typography.titleSmall,
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
                        RadioButton(
                            selected = selected,
                            onClick = null,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = option.type.localizedName(strings),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = strings.playerModerationCleanupTargetCount
                                    .replace("%count%", option.targetCount.toString()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                selectedOption?.let { option ->
                    Text(
                        text = strings.playerModerationCleanupConfirmMessage
                            .replace("%type%", option.type.localizedName(strings))
                            .replace("%count%", option.targetCount.toString()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedOption != null,
                onClick = onConfirm,
            ) {
                Text(
                    text = strings.playerModerationCleanupAction,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        },
    )
}

@Composable
private fun PlayerModerationCleanupProgress(state: PlayerModerationState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(
            progress = {
                if (state.totalCount == 0) 0f
                else state.processedCount.toFloat() / state.totalCount
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = if (state.totalCount == 0) {
                strings.playerModerationCleanupPreparing
            } else {
                strings.playerModerationCleanupProgress
                    .replace("%processed%", state.processedCount.toString())
                    .replace("%total%", state.totalCount.toString())
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CleanupResultMessage(result: PlayerModerationCleanupResult) {
    val (message, containerColor, contentColor) = when (result.kind) {
        PlayerModerationCleanupResultKind.Success -> Triple(
            strings.playerModerationCleanupSuccess
                .replace("%removed%", result.removedCount.toString()),
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )

        PlayerModerationCleanupResultKind.NoRecords -> Triple(
            strings.playerModerationCleanupNoRecords,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PlayerModerationCleanupResultKind.PartialFailure -> Triple(
            strings.playerModerationCleanupPartialFailure
                .replace("%removed%", result.removedCount.toString())
                .replace("%failed%", result.failedCount.toString()),
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )

        PlayerModerationCleanupResultKind.Failure -> Triple(
            strings.playerModerationCleanupFailure,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PlayerModerationRecordCleanupDialog(
    record: PlayerModerationData,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = AppIcons.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(strings.playerModerationRemoveConfirmTitle) },
        text = {
            Text(
                strings.playerModerationRemoveConfirmMessage
                    .replace("%player%", record.targetLabel(strings))
                    .replace("%type%", strings.playerModerationTypeLabel(record.type)),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = strings.playerModerationRemoveSetting,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
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

    ListItem(
        headlineContent = {
            Text(
                text = target,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = strings.playerModerationTypeLabel(record.type),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = created,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
        trailingContent = if (canManage) {
            {
                Box {
                    IconButton(
                        enabled = enabled,
                        onClick = { menuExpanded = true },
                    ) {
                        Icon(
                            imageVector = AppIcons.MoreVert,
                            contentDescription = strings.playerModerationPlayerActions,
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(strings.playerModerationManagePlayer) },
                            leadingIcon = {
                                Icon(
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
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = strings.playerModerationRemoveSetting,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = AppIcons.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
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
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
