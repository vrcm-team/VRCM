package io.github.vrcmteam.vrcm.presentation.screens.favorites

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.presentation.compoments.ATooltipBox
import io.github.vrcmteam.vrcm.presentation.compoments.SearchTextField
import io.github.vrcmteam.vrcm.presentation.compoments.animateScrollToTab
import io.github.vrcmteam.vrcm.presentation.compoments.contentTopInsetPadding
import io.github.vrcmteam.vrcm.presentation.compoments.isHiddenWorld
import io.github.vrcmteam.vrcm.presentation.compoments.renderAvatarItems
import io.github.vrcmteam.vrcm.presentation.compoments.renderSelectableAvatarItems
import io.github.vrcmteam.vrcm.presentation.compoments.renderSelectableWorldItems
import io.github.vrcmteam.vrcm.presentation.compoments.renderWorldItems
import io.github.vrcmteam.vrcm.presentation.compoments.safeImageUrl
import io.github.vrcmteam.vrcm.presentation.compoments.withContentTopInset
import io.github.vrcmteam.vrcm.presentation.designsystem.AppActivityIndicator
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonStyle
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIconButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppNavBar
import io.github.vrcmteam.vrcm.presentation.designsystem.AppProgressBar
import io.github.vrcmteam.vrcm.presentation.designsystem.AppScaffold
import io.github.vrcmteam.vrcm.presentation.designsystem.AppShapes
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSurface
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTab
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTabRow
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.extensions.animateScrollToFirst
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.AppRoute
import io.github.vrcmteam.vrcm.presentation.navigation.HandleBackNavigation
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.avatar.currentSessionDeletedAvatarIds
import io.github.vrcmteam.vrcm.presentation.screens.avatar.data.AvatarProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.home.compoments.FavoriteGroupClearDialog
import io.github.vrcmteam.vrcm.presentation.screens.home.compoments.GroupOptionsUI
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.AvatarGroupOptions
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.FriendListPagerModel
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.FriendsDirectoryContent
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.WorldGroupOptions
import io.github.vrcmteam.vrcm.presentation.screens.search.GlobalSearchScreen
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

@Serializable
object FavoritesScreen : AppRoute {
        @Composable
    override fun Content() {
        val navigator = currentNavigator
        val favoritesModel: FriendListPagerModel = koinViewModel()
        val groupsModel: FavoritesGroupsModel = koinViewModel()
        var selectedTabIndex by rememberSaveable { mutableIntStateOf(FavoritesTab.Player.ordinal) }
        val selectedTab = FavoritesTab.entries[selectedTabIndex]

        AppScaffold(
            topBar = {
                AppNavBar(
                    title = { AppText(strings.favoritesTitle) },
                    navigationIcon = {
                        AppIconButton(onClick = navigator::pop) {
                            AppIcon(AppIcons.ArrowBackIosNew, strings.back)
                        }
                    },
                    actions = {
                        FavoritesHubTopBarActions(
                            selectedTab = selectedTab,
                            favoritesModel = favoritesModel,
                            groupsModel = groupsModel,
                            onSearch = { navigator push GlobalSearchScreen },
                        )
                    },
                )
            },
        ) { padding ->
            FavoritesHubContent(
                selectedTab = selectedTab,
                onSelectedTab = { selectedTabIndex = it.ordinal },
                favoritesModel = favoritesModel,
                groupsModel = groupsModel,
                contentBottomPadding = 24.dp,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@Composable
internal fun FavoritesHubContent(
    selectedTab: FavoritesTab,
    onSelectedTab: (FavoritesTab) -> Unit,
    favoritesModel: FriendListPagerModel,
    groupsModel: FavoritesGroupsModel,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
    pagerState: PagerState = rememberPagerState(
        initialPage = selectedTab.ordinal,
        pageCount = { FavoritesTab.entries.size },
    ),
    // 主页把标签条放进自己的顶部容器里（见 [FavoritesHubTabRow]），这里就不再画
    showTabRow: Boolean = true,
) {
    val favoriteLocale = strings
    val worldListState = rememberLazyListState()
    val avatarListState = rememberLazyListState()
    val groupListState = rememberLazyListState()
    val clearState by favoritesModel.favoriteGroupClearState.collectAsState()
    val editState by favoritesModel.favoriteGroupEditState.collectAsState()

    SideEffect { favoritesModel.updateFavoriteLocale(favoriteLocale) }
    LaunchedEffect(favoritesModel) { favoritesModel.activateFavoritesPage() }
    LaunchedEffect(groupsModel) { groupsModel.loadIfNeeded() }
    LaunchedEffect(selectedTab, favoritesModel, pagerState) {
        selectedTab.favoriteModelTabIndex?.let(favoritesModel::syncSelectedTabIndex)
        if (pagerState.currentPage != selectedTab.ordinal) {
            pagerState.scrollToPage(selectedTab.ordinal)
        }
    }
    LaunchedEffect(pagerState, favoritesModel) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val tab = FavoritesTab.entries[page]
                tab.favoriteModelTabIndex?.let(favoritesModel::syncSelectedTabIndex)
                onSelectedTab(tab)
            }
    }
    LaunchedEffect(pagerState, favoritesModel) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .drop(1)
            .collect { page ->
                FavoritesTab.entries[page].favoriteModelTabIndex
                    ?.takeIf { it != FavoritesTab.Player.favoriteModelTabIndex }
                    ?.let { favoritesModel.refreshCurrentTabCacheData(showRefreshing = false, tabIndex = it) }
            }
    }
    LaunchedEffect(pagerState, worldListState, avatarListState, groupListState) {
        SharedFlowCentre.toPagerTop.collect {
            when (FavoritesTab.entries[pagerState.settledPage]) {
                FavoritesTab.Player -> Unit
                FavoritesTab.World -> launch { worldListState.animateScrollToFirst() }
                FavoritesTab.Avatar -> launch { avatarListState.animateScrollToFirst() }
                FavoritesTab.Group -> launch { groupListState.animateScrollToFirst() }
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        if (showTabRow) FavoritesHubTabRow(pagerState)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            key = { FavoritesTab.entries[it].name },
        ) { page ->
            when (FavoritesTab.entries[page]) {
                FavoritesTab.Player -> FriendsDirectoryContent(
                    contentPadding = PaddingValues(top = 12.dp, bottom = contentBottomPadding).withContentTopInset(),
                    showFavoriteGroupDialogs = false,
                    model = favoritesModel,
                )
                FavoritesTab.World -> FavoriteWorldsContent(
                    model = favoritesModel,
                    listState = worldListState,
                    contentBottomPadding = contentBottomPadding,
                )
                FavoritesTab.Avatar -> FavoriteAvatarsContent(
                    model = favoritesModel,
                    listState = avatarListState,
                    contentBottomPadding = contentBottomPadding,
                )
                FavoritesTab.Group -> MyGroupsContent(
                    contentBottomPadding = contentBottomPadding,
                    listState = groupListState,
                    model = groupsModel,
                )
            }
        }
    }

    clearState.group?.let { group ->
        FavoriteGroupClearDialog(
            groupDisplayName = group.displayName,
            itemCount = clearState.itemCount,
            isClearing = clearState.isClearing,
            hasFailure = clearState.failure != null,
            onConfirm = favoritesModel::confirmFavoriteGroupClear,
            onDismiss = favoritesModel::dismissFavoriteGroupClearConfirmation,
        )
    }
    FavoriteGroupEditDialog(
        state = editState,
        onDismiss = favoritesModel::dismissFavoriteGroupEditor,
        onClearFailure = favoritesModel::clearFavoriteGroupEditFailure,
        onSave = favoritesModel::saveFavoriteGroup,
    )
}

/** 收藏页的标签条：玩家 / 世界 / 模型 / 群组。 */
@Composable
internal fun FavoritesHubTabRow(pagerState: PagerState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    AppTabRow(selectedTabIndex = pagerState.currentPage, modifier = modifier) {
        FavoritesTab.entries.forEachIndexed { index, tab ->
            AppTab(
                selected = index == pagerState.currentPage,
                onClick = { scope.launch { pagerState.animateScrollToTab(index) } },
                text = {
                    AppText(
                        text = tab.title(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                },
            )
        }
    }
}

@Composable
private fun FavoritesTab.title(): String = when (this) {
    FavoritesTab.Player -> strings.favoritesPlayers
    FavoritesTab.World -> strings.worlds
    FavoritesTab.Avatar -> strings.avatars
    FavoritesTab.Group -> strings.groups
}

@Composable
private fun FavoriteWorldsContent(
    model: FriendListPagerModel,
    listState: LazyListState,
    contentBottomPadding: Dp,
) {
    val navigator = currentNavigator
    val searchText by model.searchText.collectAsState()
    val worlds by model.worldList.collectAsState()
    val createdWorlds by model.createdWorldList.collectAsState()
    val groups by model.worldFavoriteGroupsFlow.collectAsState()
    val options by model.worldGroupOptions.collectAsState()
    val total by model.worldContentTotal.collectAsState()
    val refreshingTabs by model.refreshingTabs.collectAsState()
    val refreshErrors by model.refreshErrors.collectAsState()
    val clearState by model.favoriteGroupClearState.collectAsState()
    val removalStates by model.favoriteRemovalStates.collectAsState()
    val removalState = removalStates.getValue(FavoriteType.World)
    val createdIds = remember(createdWorlds) { createdWorlds.mapTo(mutableSetOf()) { it.id } }
    val favoriteOnlyWorlds = remember(worlds, createdIds) { worlds.filterNot { it.id in createdIds } }
    val favoriteRecordIdByWorldId = remember(groups) {
        groups.values.flatten().associate { it.favoriteId to it.id }
    }
    val displayedFavoriteWorlds = if (removalState.selectionMode) worlds else favoriteOnlyWorlds
    val selectableWorldIds = remember(groups) {
        groups.values.flatten().mapTo(mutableSetOf()) { it.id }
    }
    val visibleSelectionIds = remember(displayedFavoriteWorlds, selectableWorldIds) {
        displayedFavoriteWorlds.mapNotNullTo(mutableSetOf()) { world ->
            (world.favoriteId ?: world.id).takeIf { it in selectableWorldIds }
        }
    }
    val tabIndex = FavoritesTab.World.favoriteModelTabIndex!!
    val loading = tabIndex in refreshingTabs
    val error = refreshErrors[tabIndex]
    val empty = favoriteOnlyWorlds.isEmpty() && createdWorlds.isEmpty()

    HandleBackNavigation(
        enabled = removalState.selectionMode && !removalState.isSubmitting,
        onBack = { model.exitFavoriteSelectionMode(FavoriteType.World) },
    )

    Column(Modifier.fillMaxSize().contentTopInsetPadding()) {
        SearchTextField(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            value = searchText,
            onValueChange = model::setSearchText,
        )
        GroupOptionsUI(
            currentOptions = options,
            favoriteType = FavoriteType.World,
            favoriteGroups = groups,
            total = total,
            defaultText = strings.friendListPagerAllWorlds,
            onOptionsChanged = model::updateWorldGroupOptions,
            getSelectedGroup = WorldGroupOptions::selectedGroup,
            updateOptions = { current, group -> current.copy(selectedGroup = group) },
            onClearGroup = model::openFavoriteGroupClearConfirmation,
            clearGroupEnabled = options.selectedGroup?.let(model::canClearFavoriteGroup) == true,
            clearGroupInProgress = clearState.isClearing && clearState.group?.type == FavoriteType.World.value,
            clearGroupContentDescription = strings.favoriteGroupClearAction,
            onEditGroup = model::openFavoriteGroupEditor,
            editGroupContentDescription = strings.favoriteGroupEditAction,
        )
        if (removalState.selectionMode) {
            SelectionRemovalStatusRow(
                state = removalState,
                visibleIds = visibleSelectionIds,
                selectedCountText = strings.favoriteSelectionSelectedCount,
                progressText = strings.favoriteSelectionRemovingProgress,
                selectAllText = strings.favoriteSelectionSelectAll,
                clearSelectionText = strings.favoriteSelectionClearSelection,
                onToggleVisibleSelection = {
                    model.toggleVisibleFavoriteSelection(FavoriteType.World, it)
                },
            )
        }
        Box(Modifier.fillMaxWidth().height(4.dp)) {
            if (loading) AppProgressBar(Modifier.fillMaxWidth())
        }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = contentBottomPadding),
            ) {
                if (displayedFavoriteWorlds.isNotEmpty()) {
                    item(key = "favorite-worlds-heading") { LibrarySectionHeader(strings.userFavoritedWorlds) }
                    if (removalState.selectionMode) {
                        renderSelectableWorldItems(
                            worlds = displayedFavoriteWorlds,
                            selectedWorldIds = removalState.selectedIds,
                            selectableWorldIds = selectableWorldIds,
                            enabled = !removalState.isSubmitting,
                            onSelectionToggle = {
                                model.toggleFavoriteSelection(FavoriteType.World, it)
                            },
                        )
                    } else {
                        renderWorldItems(
                            worlds = displayedFavoriteWorlds,
                            onWorldLongClick = { world ->
                                model.beginFavoriteSelection(
                                    FavoriteType.World,
                                    world.favoriteId ?: world.id,
                                )
                            },
                        ) { world, suffix ->
                            if (!world.isHiddenWorld()) {
                                navigator push WorldProfileScreen(
                                    worldProfileVO = WorldProfileVo(world),
                                    sharedSuffixKey = suffix,
                                    sharedImageCacheKey = world.safeImageUrl(),
                                )
                            }
                        }
                    }
                }
                if (createdWorlds.isNotEmpty() && !removalState.selectionMode) {
                    item(key = "created-worlds-heading") { LibrarySectionHeader(strings.userCreatedWorlds) }
                    renderWorldItems(
                        worlds = createdWorlds,
                        onWorldLongClick = { world ->
                            favoriteRecordIdByWorldId[world.id]?.let { favoriteRecordId ->
                                model.beginFavoriteSelection(FavoriteType.World, favoriteRecordId)
                            }
                        },
                    ) { world, suffix ->
                        if (!world.isHiddenWorld()) {
                            navigator push WorldProfileScreen(
                                worldProfileVO = WorldProfileVo(world),
                                sharedSuffixKey = suffix,
                                sharedKeyPrefix = "Created_",
                                sharedImageCacheKey = world.safeImageUrl(),
                            )
                        }
                    }
                }
            }
            when {
                loading && empty -> AppActivityIndicator(Modifier.align(Alignment.Center))
                error != null && empty -> StateMessage(strings.favoritesLoadFailed, strings.retry) {
                    model.refreshCurrentTabCacheData(tabIndex = tabIndex)
                }
                empty -> StateMessage(strings.favoritesEmpty, null, null)
                error != null -> ErrorBanner(
                    message = strings.favoritesLoadFailed,
                    bottomPadding = contentBottomPadding,
                    onRetry = { model.refreshCurrentTabCacheData(tabIndex = tabIndex) },
                )
            }
        }
    }

    SelectionRemovalConfirmationDialog(
        state = removalState,
        title = strings.favoriteSelectionRemoveConfirmTitle,
        message = strings.favoriteSelectionRemoveConfirmMessage,
        confirmLabel = strings.favoriteSelectionRemoveSelected,
        cancelLabel = strings.cancel,
        onConfirm = { model.confirmFavoriteRemoval(FavoriteType.World) },
        onDismiss = { model.dismissFavoriteRemovalConfirmation(FavoriteType.World) },
    )
}

@Composable
private fun FavoriteAvatarsContent(
    model: FriendListPagerModel,
    listState: LazyListState,
    contentBottomPadding: Dp,
) {
    val navigator = currentNavigator
    val searchText by model.searchText.collectAsState()
    val avatars by model.avatarList.collectAsState()
    val createdAvatars by model.createdAvatarList.collectAsState()
    val groups by model.avatarFavoriteGroupsFlow.collectAsState()
    val options by model.avatarGroupOptions.collectAsState()
    val total by model.avatarContentTotal.collectAsState()
    val refreshingTabs by model.refreshingTabs.collectAsState()
    val refreshErrors by model.refreshErrors.collectAsState()
    val clearState by model.favoriteGroupClearState.collectAsState()
    val removalStates by model.favoriteRemovalStates.collectAsState()
    val removalState = removalStates.getValue(FavoriteType.Avatar)
    val deletedAvatarIds = currentSessionDeletedAvatarIds()
    val visibleCreatedAvatars = remember(createdAvatars, deletedAvatarIds) {
        createdAvatars.filterNot { it.id in deletedAvatarIds }
    }
    val createdIds = remember(visibleCreatedAvatars) {
        visibleCreatedAvatars.mapTo(mutableSetOf()) { it.id }
    }
    val favoriteOnlyAvatars = remember(avatars, deletedAvatarIds, createdIds) {
        avatars.filterNot { it.id in deletedAvatarIds || it.id in createdIds }
    }
    val selectableAvatarIds = remember(groups) {
        groups.values.flatten().mapTo(mutableSetOf()) { it.favoriteId }
    }
    val displayedFavoriteAvatars = if (removalState.selectionMode) avatars else favoriteOnlyAvatars
    val visibleSelectionIds = remember(displayedFavoriteAvatars, selectableAvatarIds) {
        displayedFavoriteAvatars.mapNotNullTo(mutableSetOf()) { avatar ->
            avatar.id.takeIf { it in selectableAvatarIds }
        }
    }
    val tabIndex = FavoritesTab.Avatar.favoriteModelTabIndex!!
    val loading = tabIndex in refreshingTabs
    val error = refreshErrors[tabIndex]
    val empty = favoriteOnlyAvatars.isEmpty() && visibleCreatedAvatars.isEmpty()

    HandleBackNavigation(
        enabled = removalState.selectionMode && !removalState.isSubmitting,
        onBack = { model.exitFavoriteSelectionMode(FavoriteType.Avatar) },
    )

    Column(Modifier.fillMaxSize().contentTopInsetPadding()) {
        SearchTextField(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            value = searchText,
            onValueChange = model::setSearchText,
        )
        GroupOptionsUI(
            currentOptions = options,
            favoriteType = FavoriteType.Avatar,
            favoriteGroups = groups,
            total = total,
            defaultText = strings.friendListPagerAllAvatars,
            onOptionsChanged = model::updateAvatarGroupOptions,
            getSelectedGroup = AvatarGroupOptions::selectedGroup,
            updateOptions = { current, group -> current.copy(selectedGroup = group) },
            onClearGroup = model::openFavoriteGroupClearConfirmation,
            clearGroupEnabled = options.selectedGroup?.let(model::canClearFavoriteGroup) == true,
            clearGroupInProgress = clearState.isClearing && clearState.group?.type == FavoriteType.Avatar.value,
            clearGroupContentDescription = strings.favoriteGroupClearAction,
            onEditGroup = model::openFavoriteGroupEditor,
            editGroupContentDescription = strings.favoriteGroupEditAction,
        )
        if (removalState.selectionMode) {
            SelectionRemovalStatusRow(
                state = removalState,
                visibleIds = visibleSelectionIds,
                selectedCountText = strings.favoriteSelectionSelectedCount,
                progressText = strings.favoriteSelectionRemovingProgress,
                selectAllText = strings.favoriteSelectionSelectAll,
                clearSelectionText = strings.favoriteSelectionClearSelection,
                onToggleVisibleSelection = {
                    model.toggleVisibleFavoriteSelection(FavoriteType.Avatar, it)
                },
            )
        }
        Box(Modifier.fillMaxWidth().height(4.dp)) {
            if (loading) AppProgressBar(Modifier.fillMaxWidth())
        }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = contentBottomPadding),
            ) {
                if (displayedFavoriteAvatars.isNotEmpty()) {
                    item(key = "favorite-avatars-heading") { LibrarySectionHeader(strings.userFavoritedAvatars) }
                    if (removalState.selectionMode) {
                        renderSelectableAvatarItems(
                            avatars = displayedFavoriteAvatars,
                            selectedAvatarIds = removalState.selectedIds,
                            selectableAvatarIds = selectableAvatarIds,
                            enabled = !removalState.isSubmitting,
                            onSelectionToggle = {
                                model.toggleFavoriteSelection(FavoriteType.Avatar, it)
                            },
                        )
                    } else {
                        renderAvatarItems(
                            avatars = displayedFavoriteAvatars,
                            onAvatarLongClick = { avatar ->
                                model.beginFavoriteSelection(FavoriteType.Avatar, avatar.id)
                            },
                        ) { avatar, suffix ->
                            if (avatar.releaseStatus != "hidden") {
                                navigator push AvatarProfileScreen(AvatarProfileVo(avatar), suffix)
                            }
                        }
                    }
                }
                if (visibleCreatedAvatars.isNotEmpty() && !removalState.selectionMode) {
                    item(key = "created-avatars-heading") { LibrarySectionHeader(strings.userCreatedAvatars) }
                    renderAvatarItems(
                        avatars = visibleCreatedAvatars,
                        onAvatarLongClick = { avatar ->
                            if (avatar.id in selectableAvatarIds) {
                                model.beginFavoriteSelection(FavoriteType.Avatar, avatar.id)
                            }
                        },
                    ) { avatar, suffix ->
                        navigator push AvatarProfileScreen(AvatarProfileVo(avatar), suffix)
                    }
                }
            }
            when {
                loading && empty -> AppActivityIndicator(Modifier.align(Alignment.Center))
                error != null && empty -> StateMessage(strings.favoritesLoadFailed, strings.retry) {
                    model.refreshCurrentTabCacheData(tabIndex = tabIndex)
                }
                empty -> StateMessage(strings.favoritesEmpty, null, null)
                error != null -> ErrorBanner(
                    message = strings.favoritesLoadFailed,
                    bottomPadding = contentBottomPadding,
                    onRetry = { model.refreshCurrentTabCacheData(tabIndex = tabIndex) },
                )
            }
        }
    }

    SelectionRemovalConfirmationDialog(
        state = removalState,
        title = strings.favoriteSelectionRemoveConfirmTitle,
        message = strings.favoriteSelectionRemoveConfirmMessage,
        confirmLabel = strings.favoriteSelectionRemoveSelected,
        cancelLabel = strings.cancel,
        onConfirm = { model.confirmFavoriteRemoval(FavoriteType.Avatar) },
        onDismiss = { model.dismissFavoriteRemovalConfirmation(FavoriteType.Avatar) },
    )
}

@Composable
private fun LibrarySectionHeader(title: String) {
    AppText(
        text = title,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        style = AppTheme.type.subheadlineEmphasized,
        color = AppTheme.colors.secondaryLabel,
    )
}

@Composable
internal fun RowScope.FavoritesHubTopBarActions(
    selectedTab: FavoritesTab,
    favoritesModel: FriendListPagerModel,
    groupsModel: FavoritesGroupsModel,
    onSearch: () -> Unit,
) {
    when (selectedTab) {
        FavoritesTab.Player -> FriendDirectoryActions(favoritesModel)
        FavoritesTab.World, FavoritesTab.Avatar -> {
            val refreshingTabs by favoritesModel.refreshingTabs.collectAsState()
            val removalStates by favoritesModel.favoriteRemovalStates.collectAsState()
            val tabIndex = selectedTab.favoriteModelTabIndex!!
            val favoriteType = when (selectedTab) {
                FavoritesTab.World -> FavoriteType.World
                FavoritesTab.Avatar -> FavoriteType.Avatar
                else -> error("Unsupported favorite removal tab")
            }
            val removalState = removalStates.getValue(favoriteType)
            val total by if (favoriteType == FavoriteType.World) {
                favoritesModel.worldTotal.collectAsState()
            } else {
                favoritesModel.avatarTotal.collectAsState()
            }
            SelectionRemovalActions(
                state = removalState,
                canEnterSelection = total > 0 && tabIndex !in refreshingTabs,
                enterSelectionDescription = strings.favoriteSelectionAction,
                removeSelectedDescription = strings.favoriteSelectionRemoveSelected,
                cancelDescription = strings.cancel,
                onEnterSelection = { favoritesModel.enterFavoriteSelectionMode(favoriteType) },
                onExitSelection = { favoritesModel.exitFavoriteSelectionMode(favoriteType) },
                onRequestRemoval = {
                    favoritesModel.requestFavoriteRemovalConfirmation(favoriteType)
                },
            )
            if (!removalState.selectionMode) {
                AppIconButton(
                    enabled = tabIndex !in refreshingTabs,
                    onClick = { favoritesModel.refreshCurrentTabCacheData(tabIndex = tabIndex) },
                ) {
                    if (tabIndex in refreshingTabs) {
                        AppActivityIndicator(Modifier.size(20.dp))
                    } else {
                        AppIcon(AppIcons.Refresh, strings.refresh)
                    }
                }
            }
        }
        FavoritesTab.Group -> MyGroupsActions(groupsModel)
    }
    ATooltipBox(tooltip = { AppText(strings.fiendListPagerSearch) }) {
        AppIconButton(onClick = onSearch) {
            AppIcon(AppIcons.Search, strings.fiendListPagerSearch)
        }
    }
}

@Composable
private fun FriendDirectoryActions(model: FriendListPagerModel) {
    val isRefreshing by model.directoryRefreshing.collectAsState()
    val total by model.friendTotal.collectAsState()
    val removalState by model.friendRemovalState.collectAsState()

    if (removalState.selectionMode) {
        ATooltipBox(tooltip = { AppText(strings.cancel) }) {
            AppIconButton(enabled = !removalState.isSubmitting, onClick = model::exitFriendSelectionMode) {
                AppIcon(AppIcons.Close, strings.cancel)
            }
        }
        ATooltipBox(tooltip = { AppText(strings.friendDirectoryRemoveSelected) }) {
            AppIconButton(
                enabled = removalState.selectedUserIds.isNotEmpty() && !removalState.isSubmitting,
                onClick = model::requestFriendRemovalConfirmation,
            ) {
                if (removalState.isSubmitting) {
                    AppActivityIndicator(Modifier.size(20.dp))
                } else {
                    AppIcon(AppIcons.Delete, strings.friendDirectoryRemoveSelected)
                }
            }
        }
    } else {
        ATooltipBox(tooltip = { AppText(strings.friendDirectorySelect) }) {
            AppIconButton(enabled = total > 0 && !isRefreshing, onClick = model::enterFriendSelectionMode) {
                AppIcon(AppIcons.PersonRemove, strings.friendDirectorySelect)
            }
        }
        ATooltipBox(tooltip = { AppText(strings.refresh) }) {
            AppIconButton(enabled = !isRefreshing, onClick = model::refreshFriendDirectory) {
                if (isRefreshing) {
                    AppActivityIndicator(Modifier.size(20.dp))
                } else {
                    AppIcon(AppIcons.Refresh, strings.refresh)
                }
            }
        }
    }
}

@Composable
internal fun BoxScope.ErrorBanner(
    message: String,
    bottomPadding: Dp = 12.dp,
    onRetry: () -> Unit,
) {
    AppSurface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = bottomPadding),
        color = AppTheme.colors.destructiveSoft,
        contentColor = AppTheme.colors.onDestructiveSoft,
        shape = AppShapes.m,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(message, Modifier.weight(1f), style = AppTheme.type.subheadline)
            AppButton(onClick = onRetry, style = AppButtonStyle.Plain) { AppText(strings.retry) }
        }
    }
}

@Composable
internal fun BoxScope.StateMessage(message: String, action: String?, onAction: (() -> Unit)?) {
    Column(
        Modifier.align(Alignment.Center).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppText(message, color = AppTheme.colors.secondaryLabel, textAlign = TextAlign.Center)
        if (action != null && onAction != null) AppButton(onClick = onAction, style = AppButtonStyle.Plain) { AppText(action) }
    }
}
