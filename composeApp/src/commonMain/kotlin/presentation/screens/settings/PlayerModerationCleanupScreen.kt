package io.github.vrcmteam.vrcm.presentation.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.network.api.playermoderation.data.PlayerModerationType
import io.github.vrcmteam.vrcm.presentation.navigation.AppDetailRoute
import io.github.vrcmteam.vrcm.presentation.navigation.BlockBackNavigation
import io.github.vrcmteam.vrcm.presentation.navigation.LocalNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.currentOrThrow
import io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStrings
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

@Serializable
object PlayerModerationCleanupScreen : AppDetailRoute {
    @Composable
    override fun Content() {
        PlayerModerationCleanupContent()
    }
}

private data class PendingPlayerModerationCleanup(
    val type: PlayerModerationType,
    val sessionToken: AccountSessionToken,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerModerationCleanupContent(
    model: PlayerModerationCleanupModel = koinViewModel(),
) {
    val navigator = LocalNavigator.currentOrThrow
    val state by model.state.collectAsState()
    var pendingConfirmation by remember { mutableStateOf<PendingPlayerModerationCleanup?>(null) }
    val pendingOption = pendingConfirmation?.let { pending ->
        state.availableTypes.firstOrNull { it.type == pending.type }
    }

    LaunchedEffect(Unit) { model.loadIfNeeded() }
    LaunchedEffect(
        state.sessionToken,
        state.isSessionAvailable,
        state.isLoading,
        state.isClearing,
        state.selectedType,
    ) {
        val pending = pendingConfirmation ?: return@LaunchedEffect
        if (!state.isSessionAvailable || state.isLoading || state.isClearing ||
            state.sessionToken != pending.sessionToken || state.selectedType != pending.type
        ) {
            pendingConfirmation = null
        }
    }
    BlockBackNavigation(blocked = state.isClearing)

    val pending = pendingConfirmation
    if (pending != null && pendingOption != null && !state.isLoading && !state.isClearing &&
        state.sessionToken == pending.sessionToken && state.selectedType == pending.type
    ) {
        AlertDialog(
            onDismissRequest = { pendingConfirmation = null },
            title = { Text(strings.playerModerationCleanupConfirmTitle) },
            text = {
                Text(
                    strings.playerModerationCleanupConfirmMessage
                        .replace("%type%", pendingOption.type.localizedName(strings))
                        .replace("%count%", pendingOption.targetCount.toString()),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingConfirmation = null
                        model.clearSelected(pending.type, pending.sessionToken)
                    },
                ) {
                    Text(
                        text = strings.playerModerationCleanupAction,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirmation = null }) {
                    Text(strings.cancel)
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.playerModerationCleanupTitle) },
                navigationIcon = {
                    IconButton(
                        enabled = !state.isClearing,
                        onClick = { navigator.pop() },
                    ) {
                        Icon(AppIcons.ArrowBackIosNew, strings.back)
                    }
                },
                actions = {
                    IconButton(
                        enabled = state.isSessionAvailable && !state.isLoading && !state.isClearing,
                        onClick = model::refresh,
                    ) {
                        Icon(AppIcons.Update, strings.refresh)
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !state.isSessionAvailable -> StateMessage(
                    text = strings.playerModerationCleanupSessionUnavailable,
                    action = null,
                    onAction = null,
                )

                state.isLoading && !state.hasLoaded ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.loadFailed && state.availableTypes.isEmpty() -> StateMessage(
                    text = strings.playerModerationCleanupLoadFailed,
                    action = strings.retry,
                    onAction = model::refresh,
                )

                else -> CleanupLoadedContent(
                    state = state,
                    onSelect = model::select,
                    onConfirm = {
                        val type = state.selectedType
                        val token = state.sessionToken
                        if (!state.isLoading && type != null && token != null) {
                            pendingConfirmation = PendingPlayerModerationCleanup(type, token)
                        }
                    },
                    onRetry = model::refresh,
                )
            }
        }
    }
}

@Composable
private fun CleanupLoadedContent(
    state: PlayerModerationCleanupState,
    onSelect: (PlayerModerationType) -> Unit,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = strings.playerModerationCleanupDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.result?.let { result ->
            item { CleanupResultMessage(result) }
        }
        if (state.loadFailed && state.availableTypes.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = strings.playerModerationCleanupLoadFailed,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRetry) { Text(strings.retry) }
                }
            }
        }
        if (state.availableTypes.isEmpty()) {
            item {
                Text(
                    text = strings.playerModerationCleanupEmpty,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item {
                Text(
                    text = strings.playerModerationCleanupSelectType,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            items(state.availableTypes, key = { it.type.apiValue }) { option ->
                val selected = state.selectedType == option.type
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .selectable(
                            selected = selected,
                            enabled = !state.isLoading && !state.isClearing,
                            role = Role.RadioButton,
                            onClick = { onSelect(option.type) },
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = null,
                        enabled = !state.isLoading && !state.isClearing,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = option.type.localizedName(strings),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = strings.playerModerationCleanupTargetCount
                            .replace("%count%", option.targetCount.toString()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
            item {
                if (state.isClearing) {
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
                } else {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoading && state.selectedType != null,
                        onClick = onConfirm,
                    ) {
                        Icon(AppIcons.Clear, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.playerModerationCleanupAction)
                    }
                }
            }
        }
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
private fun BoxScope.StateMessage(
    text: String,
    action: String?,
    onAction: (() -> Unit)?,
) {
    Column(
        modifier = Modifier.align(Alignment.Center).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

private fun PlayerModerationType.localizedName(locale: LocaleStrings): String = when (this) {
    PlayerModerationType.Block -> locale.playerModerationTypeBlock
    PlayerModerationType.InteractOff -> locale.playerModerationTypeInteractOff
    PlayerModerationType.InteractOn -> locale.playerModerationTypeInteractOn
    PlayerModerationType.Mute -> locale.playerModerationTypeMute
    PlayerModerationType.MuteChat -> locale.playerModerationTypeMuteChat
    PlayerModerationType.Unmute -> locale.playerModerationTypeUnmute
    PlayerModerationType.UnmuteChat -> locale.playerModerationTypeUnmuteChat
}
