package io.github.vrcmteam.vrcm.presentation.screens.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.groups.data.LimitedGroup
import io.github.vrcmteam.vrcm.network.api.users.data.LimitedUserGroup
import io.github.vrcmteam.vrcm.presentation.compoments.ListStateOverlay
import io.github.vrcmteam.vrcm.presentation.compoments.RefreshBox
import io.github.vrcmteam.vrcm.presentation.compoments.SearchTextField
import io.github.vrcmteam.vrcm.presentation.compoments.renderGroupItems
import io.github.vrcmteam.vrcm.presentation.compoments.renderSelectableGroupItems
import io.github.vrcmteam.vrcm.presentation.compoments.withContentTopInset
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIconButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppNavBar
import io.github.vrcmteam.vrcm.presentation.designsystem.AppScaffold
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.AppRoute
import io.github.vrcmteam.vrcm.presentation.navigation.HandleBackNavigation
import io.github.vrcmteam.vrcm.presentation.screens.group.GroupProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.group.data.GroupProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

@Serializable
object MyGroupsScreen : AppRoute {
    @Composable
    override fun Content() {
        MyGroupsScreenContent()
    }
}

@Composable
private fun MyGroupsScreenContent(
    model: FavoritesGroupsModel = koinViewModel(),
) {
    val navigator = currentNavigator

    AppScaffold(
        topBar = {
            AppNavBar(
                title = { AppText(strings.myGroups) },
                navigationIcon = {
                    AppIconButton(onClick = { navigator.pop() }) {
                        AppIcon(AppIcons.ArrowBackIosNew, strings.back)
                    }
                },
                actions = {
                    MyGroupsSelectionActions(model)
                },
            )
        },
    ) { padding ->
        // 顶部留白做外边距；底部安全区并入列表内边距，列表能滚到系统导航条下面
        MyGroupsContent(
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            contentBottomPadding = padding.calculateBottomPadding() + 24.dp,
            model = model,
        )
    }
}

@Composable
internal fun MyGroupsContent(
    modifier: Modifier = Modifier,
    contentBottomPadding: Dp = 24.dp,
    listState: LazyListState = rememberLazyListState(),
    model: FavoritesGroupsModel = koinViewModel(),
) {
    val navigator = currentNavigator
    val state by model.state.collectAsState()
    val removalState by model.removalState.collectAsState()
    val locale = strings
    val visibleGroups = remember(state.visibleGroups) {
        state.visibleGroups.map { it.toLimitedGroup() }
    }
    val visibleGroupIds = remember(visibleGroups) {
        visibleGroups.mapTo(mutableSetOf()) { it.id }
    }

    LaunchedEffect(model) { model.loadIfNeeded() }
    SideEffect { model.updateLocale(locale) }
    HandleBackNavigation(
        enabled = removalState.selectionMode && !removalState.isSubmitting,
        onBack = model::exitGroupSelectionMode,
    )

    RefreshBox(
        modifier = modifier.fillMaxSize(),
        refreshContainerOffsetY = 12.dp,
        isRefreshing = state.isLoading,
        // 批量选择期间列表不能在手底下变
        enabled = !removalState.selectionMode,
        doRefresh = { model.refresh() },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = 12.dp, bottom = contentBottomPadding).withContentTopInset(),
        ) {
            item(key = "my-groups-controls") {
                Column(
                    modifier = Modifier.padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SearchTextField(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        value = state.searchText,
                        onValueChange = model::setSearchText,
                    )
                    if (removalState.selectionMode) {
                        SelectionRemovalStatusRow(
                            state = removalState,
                            visibleIds = visibleGroupIds,
                            selectedCountText = locale.favoriteSelectionSelectedCount,
                            progressText = locale.groupSelectionLeavingProgress,
                            selectAllText = locale.favoriteSelectionSelectAll,
                            clearSelectionText = locale.favoriteSelectionClearSelection,
                            onToggleVisibleSelection = model::toggleVisibleGroupSelection,
                        )
                    }
                }
            }
            if (removalState.selectionMode) {
                renderSelectableGroupItems(
                    groups = visibleGroups,
                    selectedGroupIds = removalState.selectedIds,
                    enabled = !removalState.isSubmitting,
                    onSelectionToggle = model::toggleGroupSelection,
                )
            } else {
                renderGroupItems(
                    groups = visibleGroups,
                    onGroupLongClick = { model.beginGroupSelection(it.id) },
                ) { group, suffix ->
                    navigator push GroupProfileScreen(GroupProfileVo(group), suffix)
                }
            }
        }
        ListStateOverlay(
            isEmpty = state.visibleGroups.isEmpty(),
            isLoading = state.isLoading,
            emptyMessage = strings.myGroupsEmpty,
            errorMessage = strings.myGroupsLoadFailed.takeIf { state.error != null },
            onRetry = model::refresh,
            bottomPadding = contentBottomPadding,
        )
    }

    SelectionRemovalConfirmationDialog(
        state = removalState,
        title = locale.groupSelectionLeaveConfirmTitle,
        message = locale.groupSelectionLeaveConfirmMessage,
        confirmLabel = locale.groupSelectionLeaveSelected,
        cancelLabel = locale.cancel,
        onConfirm = model::confirmGroupLeave,
        onDismiss = model::dismissGroupLeaveConfirmation,
    )
}

/** 批量退出群组期间的顶栏动作；平时没有按钮（刷新靠下拉、选择靠长按）。 */
@Composable
internal fun RowScope.MyGroupsSelectionActions(model: FavoritesGroupsModel) {
    val removalState by model.removalState.collectAsState()
    SelectionRemovalActions(
        state = removalState,
        removeSelectedDescription = strings.groupSelectionLeaveSelected,
        cancelDescription = strings.cancel,
        onExitSelection = model::exitGroupSelectionMode,
        onRequestRemoval = model::requestGroupLeaveConfirmation,
    )
}

private fun LimitedUserGroup.toLimitedGroup() = LimitedGroup(
    id = groupId,
    name = name,
    shortCode = shortCode,
    description = description,
    iconUrl = iconUrl,
    bannerUrl = bannerUrl,
    memberCount = memberCount,
    membershipStatus = "member",
)
