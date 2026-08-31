package io.github.vrcmteam.vrcm.presentation.screens.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.core.extensions.toLocalDateTime
import io.github.vrcmteam.vrcm.network.api.inventory.InventoryItemType
import io.github.vrcmteam.vrcm.network.api.inventory.InventorySortOrder
import io.github.vrcmteam.vrcm.network.api.inventory.data.InventoryItemData
import io.github.vrcmteam.vrcm.presentation.compoments.AImage
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.ignoredFormat
import io.github.vrcmteam.vrcm.presentation.navigation.AppRoute
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

@Serializable
object InventoryScreen : AppRoute {
    @Composable
    override fun Content() {
        InventoryScreenContent()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InventoryScreenContent(
    model: InventoryScreenModel = koinViewModel(),
) {
    val navigator = currentNavigator
    val filters by model.filters.collectAsState()
    val state by model.state.collectAsState()
    val content = state as? InventoryScreenState.Content

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.inventoryTitle) },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(AppIcons.ArrowBackIosNew, strings.back)
                    }
                },
                actions = {
                    IconButton(
                        enabled = content != null &&
                            !content.isRefreshing &&
                            !content.isLoadingMore,
                        onClick = model::refresh,
                    ) {
                        Icon(AppIcons.Update, strings.refresh)
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            InventoryFilterBar(
                filters = filters,
                onTypeSelected = model::selectType,
                onArchivedSelected = model::selectArchived,
                onOrderSelected = model::selectOrder,
            )
            if (content?.isRefreshing == true) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (content != null) {
                Text(
                    text = strings.inventoryCount.replace(
                        "%d",
                        (content.totalCount ?: content.items.size).toString(),
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (content?.refreshError == true) {
                InventoryErrorBanner(
                    message = strings.inventoryRefreshFailed,
                    onRetry = model::retry,
                )
            }
            InventoryBody(
                state = state,
                onRetry = model::retry,
                onLoadMore = model::loadMore,
                onRetryLoadMore = model::retryLoadMore,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun InventoryFilterBar(
    filters: InventoryFilters,
    onTypeSelected: (InventoryItemType?) -> Unit,
    onArchivedSelected: (InventoryArchivedFilter) -> Unit,
    onOrderSelected: (InventorySortOrder) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "inventory-type-filter") {
            InventoryDropdown(
                label = strings.inventoryFilterType,
                selected = filters.type,
                options = listOf<InventoryItemType?>(null) + InventoryItemType.entries,
                optionLabel = { it?.localizedLabel() ?: strings.inventoryFilterAll },
                onSelected = onTypeSelected,
            )
        }
        item(key = "inventory-archive-filter") {
            InventoryDropdown(
                label = strings.inventoryFilterArchived,
                selected = filters.archived,
                options = InventoryArchivedFilter.entries,
                optionLabel = InventoryArchivedFilter::localizedLabel,
                onSelected = onArchivedSelected,
            )
        }
        item(key = "inventory-order-filter") {
            InventoryDropdown(
                label = strings.inventorySortLabel,
                selected = filters.order,
                options = InventorySortOrder.entries,
                optionLabel = InventorySortOrder::localizedLabel,
                onSelected = onOrderSelected,
            )
        }
    }
}

@Composable
private fun <T> InventoryDropdown(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.widthIn(min = 152.dp, max = 232.dp),
        ) {
            Text(
                text = "$label: ${optionLabel(selected)}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(AppIcons.ExpandMore, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun InventoryBody(
    state: InventoryScreenState,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        InventoryScreenState.Loading -> InventoryMessage(modifier) {
            CircularProgressIndicator()
        }
        InventoryScreenState.SessionMissing -> InventoryMessage(modifier) {
            Text(strings.inventorySessionMissing)
        }
        InventoryScreenState.Error -> InventoryMessage(modifier) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(strings.inventoryLoadFailed)
                TextButton(onClick = onRetry) { Text(strings.retry) }
            }
        }
        is InventoryScreenState.Content -> if (state.items.isEmpty()) {
            InventoryMessage(modifier) { Text(strings.inventoryEmpty) }
        } else {
            InventoryList(
                state = state,
                onLoadMore = onLoadMore,
                onRetryLoadMore = onRetryLoadMore,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun InventoryList(
    state: InventoryScreenState.Content,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(
        listState,
        state.items.size,
        state.hasMore,
        state.isLoadingMore,
        state.loadMoreError,
    ) {
        if (!state.hasMore || state.isLoadingMore || state.loadMoreError) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            layout.totalItemsCount > 0 &&
                lastVisible >= layout.totalItemsCount - LOAD_MORE_THRESHOLD
        }.filter { it }.first()
        onLoadMore()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().navigationBarsPadding(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(
            items = state.items,
            key = { index, item -> item.id.takeIf(String::isNotBlank) ?: "inventory-item:$index" },
        ) { _, item ->
            InventoryItemCard(item)
        }
        if (state.isLoadingMore) {
            item(key = "inventory-load-more") {
                Box(
                    Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        } else if (state.loadMoreError) {
            item(key = "inventory-load-more-error") {
                InventoryErrorBanner(
                    message = strings.inventoryLoadMoreFailed,
                    onRetry = onRetryLoadMore,
                )
            }
        }
    }
}

@Composable
private fun InventoryItemCard(item: InventoryItemData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            AImage(
                imageData = item.displayImageUrl,
                contentDescription = item.name?.takeIf(String::isNotBlank)
                    ?: strings.inventoryUnknownName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(6.dp)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = item.name?.takeIf(String::isNotBlank) ?: strings.inventoryUnknownName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.localizedTypeLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.description?.takeIf(String::isNotBlank)?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                item.expiryDate?.takeIf(String::isNotBlank)?.let { expiry ->
                    val displayExpiry = expiry.toLocalDateTime()?.ignoredFormat ?: expiry
                    Text(
                        text = strings.inventoryExpires.replace("%s", displayExpiry),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.quantifiable == true) {
                    Text(
                        text = strings.inventoryQuantifiable,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.isArchived == true) {
                    Text(
                        text = strings.inventoryArchivedBadge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun InventoryErrorBanner(message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onRetry) { Text(strings.retry) }
    }
}

@Composable
private fun InventoryMessage(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        content()
    }
}

@Composable
private fun InventoryItemData.localizedTypeLabel(): String =
    InventoryItemType.entries.firstOrNull { it.value == itemType }?.localizedLabel()
        ?: itemTypeLabel?.takeIf(String::isNotBlank)
        ?: itemType?.takeIf(String::isNotBlank)
        ?: strings.unknown

@Composable
private fun InventoryItemType.localizedLabel(): String = when (this) {
    InventoryItemType.Bundle -> strings.inventoryTypeBundle
    InventoryItemType.DroneSkin -> strings.inventoryTypeDroneSkin
    InventoryItemType.Emoji -> strings.inventoryTypeEmoji
    InventoryItemType.PortalSkin -> strings.inventoryTypePortalSkin
    InventoryItemType.Prop -> strings.inventoryTypeProp
    InventoryItemType.Sticker -> strings.inventoryTypeSticker
    InventoryItemType.WarpEffect -> strings.inventoryTypeWarpEffect
}

@Composable
private fun InventoryArchivedFilter.localizedLabel(): String = when (this) {
    InventoryArchivedFilter.All -> strings.inventoryFilterAll
    InventoryArchivedFilter.Active -> strings.inventoryFilterActive
    InventoryArchivedFilter.Archived -> strings.inventoryFilterArchivedOnly
}

@Composable
private fun InventorySortOrder.localizedLabel(): String = when (this) {
    InventorySortOrder.NewestUpdated -> strings.inventorySortNewestUpdated
    InventorySortOrder.NewestCreated -> strings.inventorySortNewestCreated
    InventorySortOrder.OldestUpdated -> strings.inventorySortOldestUpdated
    InventorySortOrder.OldestCreated -> strings.inventorySortOldestCreated
}

private const val LOAD_MORE_THRESHOLD = 5
