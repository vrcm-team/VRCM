package io.github.vrcmteam.vrcm.presentation.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.core.extensions.toLocalDateTime
import io.github.vrcmteam.vrcm.network.api.playermoderation.PlayerModerationData
import io.github.vrcmteam.vrcm.presentation.extensions.ignoredFormat
import io.github.vrcmteam.vrcm.presentation.navigation.AppDetailRoute
import io.github.vrcmteam.vrcm.presentation.navigation.LocalNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.currentOrThrow
import io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStrings
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

@Serializable
object PlayerModerationListScreen : AppDetailRoute {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = koinViewModel<PlayerModerationListScreenModel>()
        val state by model.state.collectAsState()

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(strings.playerModerationTitle) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = AppIcons.ArrowBackIosNew,
                                contentDescription = strings.notificationBack,
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = model::refresh,
                            enabled = state is PlayerModerationListState.Ready ||
                                state is PlayerModerationListState.Failed,
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
            PlayerModerationListContent(
                state = state,
                contentPadding = contentPadding,
                onSelectType = model::selectType,
                onRetry = model::retry,
            )
        }
    }
}

@Composable
private fun PlayerModerationListContent(
    state: PlayerModerationListState,
    contentPadding: PaddingValues,
    onSelectType: (String?) -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        PlayerModerationListState.Unavailable -> MessageState(
            message = strings.playerModerationSignedOut,
            contentPadding = contentPadding,
        )

        PlayerModerationListState.Loading -> Box(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }

        is PlayerModerationListState.Failed -> MessageState(
            message = strings.playerModerationLoadFailed,
            contentPadding = contentPadding,
            action = {
                TextButton(onClick = onRetry) { Text(strings.retry) }
            },
        )

        is PlayerModerationListState.Ready -> {
            if (state.records.isEmpty()) {
                MessageState(
                    message = strings.playerModerationEmpty,
                    contentPadding = contentPadding,
                )
            } else {
                PlayerModerationRecords(
                    state = state,
                    contentPadding = contentPadding,
                    onSelectType = onSelectType,
                )
            }
        }
    }
}

@Composable
private fun PlayerModerationRecords(
    state: PlayerModerationListState.Ready,
    contentPadding: PaddingValues,
    onSelectType: (String?) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = state.selectedType == null,
                    onClick = { onSelectType(null) },
                    label = { Text(strings.playerModerationFilterAll) },
                )
            }
            items(state.availableTypes) { type ->
                FilterChip(
                    selected = state.selectedType == type,
                    onClick = { onSelectType(type) },
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
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PlayerModerationRecordItem(item.record)
            }
        }
    }
}

@Composable
private fun PlayerModerationRecordItem(record: PlayerModerationData) {
    val target = record.targetDisplayName.ifBlank {
        record.targetUserId.ifBlank { strings.playerModerationUnknownPlayer }
    }
    val created = record.created.toLocalDateTime()?.ignoredFormat
        ?: record.created.ifBlank { strings.unknown }

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
