package io.github.vrcmteam.vrcm.presentation.screens.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import io.github.vrcmteam.vrcm.presentation.compoments.ATooltipBox
import io.github.vrcmteam.vrcm.presentation.designsystem.AppActivityIndicator
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonSize
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonStyle
import io.github.vrcmteam.vrcm.presentation.designsystem.AppCard
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIconButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppMenu
import io.github.vrcmteam.vrcm.presentation.designsystem.AppMenuItem
import io.github.vrcmteam.vrcm.presentation.designsystem.AppNavBar
import io.github.vrcmteam.vrcm.presentation.designsystem.AppProgressBar
import io.github.vrcmteam.vrcm.presentation.designsystem.AppScaffold
import io.github.vrcmteam.vrcm.presentation.designsystem.AppShapes
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.extensions.ignoredFormat
import io.github.vrcmteam.vrcm.presentation.navigation.AppRoute
import io.github.vrcmteam.vrcm.presentation.screens.settings.RewardCodeDialog
import io.github.vrcmteam.vrcm.presentation.screens.settings.RewardCodeScreenModel
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import vrcm.composeapp.generated.resources.Res
import vrcm.composeapp.generated.resources.vrchat_credits

@Serializable
object InventoryScreen : AppRoute {
    @Composable
    override fun Content() {
        InventoryScreenContent()
    }
}

@Composable
private fun InventoryScreenContent(
    model: InventoryScreenModel = koinViewModel(),
    rewardCodeModel: RewardCodeScreenModel = koinViewModel(),
) {
    val navigator = currentNavigator
    val filters by model.filters.collectAsState()
    val state by model.state.collectAsState()
    val creditsBalanceState by model.creditsBalanceState.collectAsState()
    val rewardCodeState by rewardCodeModel.state.collectAsState()
    val content = state as? InventoryScreenState.Content
    var showRewardCodeDialog by remember { mutableStateOf(false) }

    AppScaffold(
        topBar = {
            AppNavBar(
                title = { AppText(strings.inventoryTitle) },
                navigationIcon = {
                    AppIconButton(onClick = { navigator.pop() }) {
                        AppIcon(AppIcons.ArrowBackIosNew, strings.back)
                    }
                },
                actions = {
                    CreditsBalanceAction(
                        state = creditsBalanceState,
                        onRetry = model::refreshCreditsBalance,
                    )
                    ATooltipBox(tooltip = { AppText(strings.rewardCodeEntry) }) {
                        AppIconButton(onClick = { showRewardCodeDialog = true }) {
                            AppIcon(AppIcons.Redeem, strings.rewardCodeEntry)
                        }
                    }
                    AppIconButton(
                        enabled = content != null &&
                            !content.isRefreshing &&
                            !content.isLoadingMore,
                        onClick = model::refresh,
                    ) {
                        AppIcon(AppIcons.Refresh, strings.refresh)
                    }
                },
            )
        },
    ) { padding ->
        // 顶部留白做外边距（筛选条固定在导航栏下方）；底部安全区交给列表，物品能滚到系统导航条下面
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            InventoryFilterBar(
                filters = filters,
                onTypeSelected = model::selectType,
                onArchivedSelected = model::selectArchived,
                onOrderSelected = model::selectOrder,
            )
            if (content?.isRefreshing == true) {
                AppProgressBar(Modifier.fillMaxWidth())
            }
            if (content != null) {
                AppText(
                    text = strings.inventoryCount.replace(
                        "%d",
                        (content.totalCount ?: content.items.size).toString(),
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = AppTheme.type.caption1Emphasized,
                    color = AppTheme.colors.secondaryLabel,
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
    if (showRewardCodeDialog) {
        RewardCodeDialog(
            state = rewardCodeState,
            onCodeChange = rewardCodeModel::updateCode,
            onSubmit = rewardCodeModel::submit,
            onDismiss = {
                showRewardCodeDialog = false
                rewardCodeModel.reset()
            },
        )
    }
}

@Composable
private fun CreditsBalanceAction(
    state: CreditsBalanceState,
    onRetry: () -> Unit,
) {
    val icon = painterResource(Res.drawable.vrchat_credits)
    val iconModifier = Modifier.size(20.dp)
    Box(
        modifier = Modifier.width(112.dp).height(48.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        when (state) {
            CreditsBalanceState.Loading -> Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(
                    painter = icon,
                    contentDescription = strings.inventoryCreditsTitle,
                    modifier = iconModifier,
                    tint = AppTheme.colors.tint,
                )
                AppActivityIndicator(
                    modifier = Modifier.size(16.dp),
                )
            }

            is CreditsBalanceState.Available -> Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(
                    painter = icon,
                    contentDescription = strings.inventoryCreditsTitle,
                    modifier = iconModifier,
                    tint = AppTheme.colors.tint,
                )
                AppText(
                    text = state.balance.toString(),
                    modifier = Modifier.widthIn(max = 72.dp),
                    style = AppTheme.type.subheadlineEmphasized,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            CreditsBalanceState.Unavailable -> AppIcon(
                painter = icon,
                contentDescription = strings.inventoryCreditsUnavailable,
                modifier = Modifier.padding(end = 8.dp).size(20.dp),
                tint = AppTheme.colors.secondaryLabel.copy(alpha = 0.38f),
            )

            CreditsBalanceState.Error -> AppIconButton(onClick = onRetry) {
                AppIcon(
                    painter = icon,
                    contentDescription = strings.inventoryCreditsLoadFailed,
                    modifier = iconModifier,
                    tint = AppTheme.colors.destructive,
                )
            }
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
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InventoryDropdown(
            label = strings.inventoryFilterType,
            selected = filters.type,
            options = listOf<InventoryItemType?>(null) + InventoryItemType.entries,
            optionLabel = { it?.localizedLabel() ?: strings.inventoryFilterAll },
            onSelected = onTypeSelected,
            modifier = Modifier.weight(1f),
        )
        InventoryDropdown(
            label = strings.inventoryFilterArchived,
            selected = filters.archived,
            options = InventoryArchivedFilter.entries,
            optionLabel = InventoryArchivedFilter::localizedLabel,
            onSelected = onArchivedSelected,
            modifier = Modifier.weight(1f),
        )
        InventoryDropdown(
            label = strings.inventorySortLabel,
            selected = filters.order,
            options = InventorySortOrder.entries,
            optionLabel = InventorySortOrder::localizedLabel,
            onSelected = onOrderSelected,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun <T> InventoryDropdown(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        AppButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp),
            // 筛选项是灰底小胶囊的弹出按钮；三个并排，字号用小一档才放得下"标签: 值"
            style = AppButtonStyle.Gray,
            size = AppButtonSize.Small,
        ) {
            AppText(
                text = "$label: ${optionLabel(selected)}",
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AppIcon(
                imageVector = if (expanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
        }
        AppMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 200.dp),
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                AppMenuItem(
                    text = { AppText(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                    leadingIcon = {
                        Box(
                            modifier = Modifier.size(20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                AppIcon(
                                    imageVector = AppIcons.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
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
            AppActivityIndicator()
        }
        InventoryScreenState.SessionMissing -> InventoryMessage(modifier) {
            AppText(strings.inventorySessionMissing)
        }
        InventoryScreenState.Error -> InventoryMessage(modifier) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppText(strings.inventoryLoadFailed)
                AppButton(onClick = onRetry, style = AppButtonStyle.Plain) { AppText(strings.retry) }
            }
        }
        is InventoryScreenState.Content -> if (state.items.isEmpty()) {
            InventoryMessage(modifier) { AppText(strings.inventoryEmpty) }
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
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 8.dp,
            end = 16.dp,
            bottom = getInsetPadding(WindowInsets::getBottom) + 8.dp,
        ),
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
                    AppActivityIndicator(Modifier.size(24.dp))
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
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.m,
        color = AppTheme.colors.secondaryGroupedBackground,
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
                modifier = Modifier.size(72.dp).clip(AppShapes.s),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                AppText(
                    text = item.name?.takeIf(String::isNotBlank) ?: strings.inventoryUnknownName,
                    style = AppTheme.type.subheadlineEmphasized,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                AppText(
                    text = item.localizedTypeLabel(),
                    style = AppTheme.type.caption1Emphasized,
                    color = AppTheme.colors.tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.description?.takeIf(String::isNotBlank)?.let { description ->
                    AppText(
                        text = description,
                        style = AppTheme.type.caption1,
                        color = AppTheme.colors.secondaryLabel,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                item.expiryDate?.takeIf(String::isNotBlank)?.let { expiry ->
                    val displayExpiry = expiry.toLocalDateTime()?.ignoredFormat ?: expiry
                    AppText(
                        text = strings.inventoryExpires.replace("%s", displayExpiry),
                        style = AppTheme.type.caption2Emphasized,
                        color = AppTheme.colors.secondaryLabel,
                    )
                }
                if (item.quantifiable == true) {
                    AppText(
                        text = strings.inventoryQuantifiable,
                        style = AppTheme.type.caption2Emphasized,
                        color = AppTheme.colors.secondaryLabel,
                    )
                }
                if (item.isArchived == true) {
                    AppText(
                        text = strings.inventoryArchivedBadge,
                        style = AppTheme.type.caption2Emphasized,
                        color = AppTheme.colors.secondaryTint,
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
        AppText(
            text = message,
            modifier = Modifier.weight(1f),
            style = AppTheme.type.caption1,
            color = AppTheme.colors.destructive,
        )
        AppButton(onClick = onRetry, style = AppButtonStyle.Plain) { AppText(strings.retry) }
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
