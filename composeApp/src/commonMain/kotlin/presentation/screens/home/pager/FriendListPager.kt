package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.presentation.adaptive.AppWindowWidthClass
import io.github.vrcmteam.vrcm.presentation.adaptive.LocalAppWindowWidthClass
import io.github.vrcmteam.vrcm.presentation.compoments.*
import io.github.vrcmteam.vrcm.presentation.designsystem.*
import io.github.vrcmteam.vrcm.presentation.extensions.animateScrollToFirst
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.navigation.HandleBackNavigation
import io.github.vrcmteam.vrcm.presentation.screens.favorites.FavoriteGroupEditDialog
import io.github.vrcmteam.vrcm.presentation.screens.home.compoments.FavoriteGroupClearDialog
import io.github.vrcmteam.vrcm.presentation.screens.home.compoments.GroupOptionsUI
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.presentation.supports.Pager
import org.koin.compose.viewmodel.koinViewModel

object FriendListPager : Pager {
    override val index: Int = 1

    override val title: String
        @Composable get() = strings.friendDirectoryTitle

    override val icon: Painter
        @Composable get() = rememberVectorPainter(AppIcons.Groups)

    @Composable
    override fun Content() {
        Content(koinViewModel())
    }

    @Composable
    fun Content(model: FriendListPagerModel) {
        val bottomNavigationPadding = if (
            LocalAppWindowWidthClass.current == AppWindowWidthClass.Compact
        ) 80.dp else 0.dp
        val bottomPadding = getInsetPadding(12, WindowInsets::getBottom) + bottomNavigationPadding
        FriendsDirectoryContent(
            contentPadding = PaddingValues(top = 12.dp, bottom = bottomPadding),
            model = model,
        )
    }
}

/** Friend-only list using the established friend search and row presentation. */
@Composable
fun FriendsDirectoryContent(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 24.dp),
    showFavoriteGroupDialogs: Boolean = true,
    model: FriendListPagerModel = koinViewModel(),
) {
    val navigator = currentNavigator
    val favoriteLocale = strings
    val searchText by model.searchText.collectAsState()
    val friends by model.friendDirectoryFriends.collectAsState()
    val favoriteGroups by model.friendFavoriteGroupsFlow.collectAsState()
    val options by model.friendGroupOptions.collectAsState()
    val total by model.friendTotal.collectAsState()
    val refreshing by model.directoryRefreshing.collectAsState()
    val refreshFailed by model.directoryRefreshFailed.collectAsState()
    val clearState by model.favoriteGroupClearState.collectAsState()
    val editState by model.favoriteGroupEditState.collectAsState()
    val removalState by model.friendRemovalState.collectAsState()
    val listState = rememberLazyListState()
    val localeStrings = strings
    val visibleUserIds = remember(friends) { friends.mapTo(mutableSetOf()) { it.id } }
    val allVisibleSelected = visibleUserIds.isNotEmpty() &&
        visibleUserIds.all { it in removalState.selectedUserIds }

    SideEffect { model.updateFavoriteLocale(favoriteLocale) }
    LaunchedEffect(model, localeStrings) {
        model.updateFriendDirectoryLocale(localeStrings)
        model.activateFriendDirectory()
    }
    LaunchedEffect(listState) {
        SharedFlowCentre.toPagerTop.collect {
            runCatching { listState.animateScrollToFirst() }
        }
    }
    HandleBackNavigation(
        enabled = removalState.selectionMode && !removalState.isSubmitting,
        onBack = model::exitFriendSelectionMode,
    )

    RefreshBox(
        modifier = modifier.fillMaxSize(),
        refreshContainerOffsetY = 12.dp,
        isRefreshing = refreshing,
        // 批量选择期间列表不能在手底下变
        enabled = !removalState.selectionMode,
        doRefresh = { model.refreshFriendDirectory() },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = contentPadding,
        ) {
            item(key = "friend-directory-controls") {
                Column(
                    modifier = Modifier.padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SearchTextField(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        value = searchText,
                        onValueChange = model::setFriendDirectorySearchText,
                    )
                    GroupOptionsUI(
                        currentOptions = options,
                        favoriteType = FavoriteType.Friend,
                        favoriteGroups = favoriteGroups,
                        total = total,
                        defaultText = strings.friendListPagerAllFriends,
                        onOptionsChanged = model::updateFriendDirectoryGroupOptions,
                        getSelectedGroup = FriendGroupOptions::selectedGroup,
                        updateOptions = { current, selected -> current.copy(selectedGroup = selected) },
                        onClearGroup = model::openFavoriteGroupClearConfirmation,
                        clearGroupEnabled = options.selectedGroup
                            ?.let(model::canClearFavoriteGroup) == true,
                        clearGroupInProgress = clearState.isClearing &&
                            clearState.group?.type == FavoriteType.Friend.value,
                        clearGroupContentDescription = strings.favoriteGroupClearAction,
                        onEditGroup = model::openFavoriteGroupEditor,
                        editGroupContentDescription = strings.favoriteGroupEditAction,
                    )
                    if (removalState.selectionMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppText(
                                text = if (removalState.isSubmitting) {
                                    localeStrings.friendDirectoryRemovingProgress
                                        .replaceFirst("%d", removalState.completedCount.toString())
                                        .replaceFirst("%d", removalState.totalCount.toString())
                                } else {
                                    localeStrings.friendDirectorySelectedCount.replaceFirst(
                                        "%d",
                                        removalState.selectedUserIds.size.toString(),
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                style = AppTheme.type.subheadlineEmphasized,
                            )
                            AppButton(
                                enabled = !removalState.isSubmitting && visibleUserIds.isNotEmpty(),
                                onClick = {
                                    model.toggleVisibleFriendSelection(visibleUserIds)
                                },
                                style = AppButtonStyle.Plain,
                            ) {
                                AppText(
                                    if (allVisibleSelected) {
                                        localeStrings.friendDirectoryClearSelection
                                    } else {
                                        localeStrings.friendDirectorySelectAll
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (removalState.selectionMode) {
                renderSelectableUserItems(
                    users = friends,
                    selectedUserIds = removalState.selectedUserIds,
                    enabled = !removalState.isSubmitting,
                    onSelectionToggle = model::toggleFriendSelection,
                )
            } else {
                renderUserItems(
                    users = friends,
                    onUserLongClick = { friend -> model.beginFriendSelection(friend.id) },
                ) { friend, suffix -> navigator push UserProfileScreen(UserProfileVo(friend), suffix) }
            }
        }

        ListStateOverlay(
            isEmpty = friends.isEmpty(),
            isLoading = refreshing,
            emptyMessage = if (searchText.isBlank() && options.selectedGroup == null) {
                strings.friendDirectoryEmpty
            } else {
                strings.friendDirectoryNoMatches
            },
            errorMessage = strings.friendDirectoryLoadFailed.takeIf { refreshFailed },
            onRetry = model::refreshFriendDirectory,
            bottomPadding = contentPadding.calculateBottomPadding(),
        )
    }

    if (showFavoriteGroupDialogs) {
        clearState.group?.let { group ->
            FavoriteGroupClearDialog(
                groupDisplayName = group.displayName,
                itemCount = clearState.itemCount,
                isClearing = clearState.isClearing,
                hasFailure = clearState.failure != null,
                onConfirm = model::confirmFavoriteGroupClear,
                onDismiss = model::dismissFavoriteGroupClearConfirmation,
            )
        }

        FavoriteGroupEditDialog(
            state = editState,
            onDismiss = model::dismissFavoriteGroupEditor,
            onClearFailure = model::clearFavoriteGroupEditFailure,
            onSave = model::saveFavoriteGroup,
        )
    }

    if (removalState.confirmationVisible) {
        AppAlert(
            onDismissRequest = model::dismissFriendRemovalConfirmation,
            title = { AppText(localeStrings.friendDirectoryRemoveConfirmTitle) },
            text = {
                AppText(
                    localeStrings.friendDirectoryRemoveConfirmMessage.replaceFirst(
                        "%d",
                        removalState.selectedUserIds.size.toString(),
                    )
                )
            },
            confirmButton = {
                AppButton(
                    enabled = removalState.selectedUserIds.isNotEmpty() && !removalState.isSubmitting,
                    onClick = model::confirmFriendRemoval,
                    style = AppButtonStyle.Plain,
                ) {
                    AppText(localeStrings.friendDirectoryRemoveSelected)
                }
            },
            dismissButton = {
                AppButton(onClick = model::dismissFriendRemovalConfirmation, style = AppButtonStyle.Plain) {
                    AppText(localeStrings.cancel)
                }
            },
        )
    }
}
