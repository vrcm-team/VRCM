package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.presentation.adaptive.AppWindowWidthClass
import io.github.vrcmteam.vrcm.presentation.adaptive.LocalAppWindowWidthClass
import io.github.vrcmteam.vrcm.presentation.compoments.*
import io.github.vrcmteam.vrcm.presentation.extensions.animateScrollToFirst
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
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
        val bottomNavigationPadding = if (
            LocalAppWindowWidthClass.current == AppWindowWidthClass.Compact
        ) 80.dp else 0.dp
        val bottomPadding = getInsetPadding(12, WindowInsets::getBottom) + bottomNavigationPadding
        FriendsDirectoryContent(
            contentPadding = PaddingValues(top = 12.dp, bottom = bottomPadding),
        )
    }
}

/** Friend-only list using the established friend search and row presentation. */
@Composable
fun FriendsDirectoryContent(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 24.dp),
    model: FriendListPagerModel = koinViewModel(),
) {
    val navigator = currentNavigator
    val searchText by model.searchText.collectAsState()
    val friends by model.friendDirectoryFriends.collectAsState()
    val favoriteGroups by model.friendFavoriteGroupsFlow.collectAsState()
    val options by model.friendGroupOptions.collectAsState()
    val total by model.friendTotal.collectAsState()
    val refreshing by model.directoryRefreshing.collectAsState()
    val refreshFailed by model.directoryRefreshFailed.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(model) { model.refreshFriendDirectory() }
    LaunchedEffect(listState) {
        SharedFlowCentre.toPagerTop.collect {
            runCatching { listState.animateScrollToFirst() }
        }
    }

    RefreshBox(
        modifier = modifier,
        isRefreshing = refreshing,
        doRefresh = { model.refreshFriendDirectory() },
    ) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "friend-directory-controls") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        )
                    }
                }

                renderUserItems(friends) { friend, suffix ->
                    navigator push UserProfileScreen(UserProfileVo(friend), suffix)
                }
            }

            if (refreshing && friends.isEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else if (friends.isEmpty() && refreshFailed) {
                DirectoryMessage(
                    message = strings.friendDirectoryLoadFailed,
                    retry = true,
                    onRetry = model::refreshFriendDirectory,
                )
            } else if (friends.isEmpty()) {
                DirectoryMessage(
                    message = if (searchText.isBlank() && options.selectedGroup == null) {
                        strings.friendDirectoryEmpty
                    } else {
                        strings.friendDirectoryNoMatches
                    },
                    retry = false,
                    onRetry = model::refreshFriendDirectory,
                )
            } else if (refreshFailed) {
                DirectoryErrorBanner(
                    bottomPadding = contentPadding.calculateBottomPadding(),
                    onRetry = model::refreshFriendDirectory,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.DirectoryMessage(message: String, retry: Boolean, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.align(Alignment.Center).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (retry) TextButton(onClick = onRetry) { Text(strings.retry) }
    }
}

@Composable
private fun BoxScope.DirectoryErrorBanner(bottomPadding: Dp, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = bottomPadding),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(strings.friendDirectoryLoadFailed, Modifier.weight(1f))
            TextButton(onClick = onRetry) { Text(strings.retry) }
        }
    }
}
