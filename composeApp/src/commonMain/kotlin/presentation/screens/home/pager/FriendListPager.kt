package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.core.extensions.toLocalDateTime
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.attributes.lastSeenAt
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.presentation.compoments.*
import io.github.vrcmteam.vrcm.presentation.extensions.animateScrollToFirst
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.extensions.ignoredFormat
import io.github.vrcmteam.vrcm.presentation.navigation.rememberContainerTransformToken
import io.github.vrcmteam.vrcm.presentation.screens.home.compoments.GroupOptionsUI
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStrings
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
        val topPadding = getInsetPadding(WindowInsets::getTop) + 80.dp
        val bottomPadding = getInsetPadding(12, WindowInsets::getBottom) + 80.dp
        FriendsDirectoryContent(
            contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
        )
    }
}

/** Friend-only directory content that can be embedded in a top-level destination. */
@Composable
fun FriendsDirectoryContent(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 24.dp),
    model: FriendListPagerModel = koinViewModel(),
) {
    val navigator = currentNavigator
    val searchText by model.searchText.collectAsState()
    val groups by model.friendDirectoryGroups.collectAsState()
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
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item(key = "friend-directory-controls") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                    }
                }

                groups.forEach { group ->
                    item(key = "friend-directory-section-${group.section}") {
                        Text(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            text = "${group.section.title(strings)} (${group.friends.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(group.friends, key = FriendData::id) { friend ->
                        FriendDirectoryRow(friend) { suffix ->
                            navigator push UserProfileScreen(UserProfileVo(friend), suffix)
                        }
                    }
                }
            }

            val isEmpty = groups.isEmpty()
            if (refreshing && isEmpty) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else if (isEmpty) {
                DirectoryMessage(
                    message = if (searchText.isBlank() && options.selectedGroup == null) {
                        strings.friendDirectoryEmpty
                    } else {
                        strings.friendDirectoryNoMatches
                    },
                    retry = refreshFailed,
                    onRetry = model::refreshFriendDirectory,
                )
            } else if (refreshFailed) {
                DirectoryErrorBanner(model::refreshFriendDirectory)
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun LazyItemScope.FriendDirectoryRow(friend: FriendData, onClick: (String) -> Unit) {
    val suffix = rememberContainerTransformToken("user:${friend.id}") ?: LocalSharedSuffixKey.current
    ListItem(
        modifier = Modifier
            .animateItem()
            .fillMaxWidth()
            .clickable { onClick(suffix) }
            .padding(horizontal = 8.dp),
        leadingContent = {
            UserStateIcon(
                modifier = Modifier
                    .sharedBoundsBy(key = "${friend.id}UserIcon", suffixKey = suffix)
                    .size(48.dp),
                iconUrl = friend.iconUrl,
                userStatus = friend.status,
                location = friend.location,
            )
        },
        headlineContent = {
            UserInfoRow(
                user = friend,
                iconSize = 16.dp,
                style = MaterialTheme.typography.titleMedium,
                sharedSuffixKey = suffix,
                pronouns = friend.pronouns,
            )
        },
        supportingContent = {
            Column {
                UserStatusRow(
                    user = friend,
                    iconSize = 8.dp,
                    style = MaterialTheme.typography.bodyMedium,
                    sharedSuffixKey = suffix,
                )
                Text(
                    text = friend.directoryLocationText(strings),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    )
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
private fun BoxScope.DirectoryErrorBanner(onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(strings.friendDirectoryLoadFailed, Modifier.weight(1f))
            TextButton(onClick = onRetry) {
                Icon(AppIcons.Update, contentDescription = null)
                Text(strings.retry)
            }
        }
    }
}

private fun FriendDirectorySection.title(locale: LocaleStrings): String = when (this) {
    FriendDirectorySection.InGame -> locale.friendDirectoryInGame
    FriendDirectorySection.Web -> locale.friendDirectoryWeb
    FriendDirectorySection.Private -> locale.friendDirectoryPrivate
    FriendDirectorySection.Offline -> locale.friendDirectoryOffline
}

private fun FriendData.directoryLocationText(locale: LocaleStrings): String {
    if (status == UserStatus.Offline) {
        val lastActive = lastSeenAt()?.toLocalDateTime()?.ignoredFormat
        return lastActive?.let { "${locale.friendDirectoryLastActive}: $it" }
            ?: locale.friendDirectoryOffline
    }
    if (location == LocationType.Traveling.value) return locale.friendDirectoryTraveling
    if (location == LocationType.Web.value || location == LocationType.Offline.value) {
        return locale.friendDirectoryWeb
    }
    if (!location.startsWith(LocationType.Instance.value)) return locale.friendDirectoryPrivate
    return when {
        "~private(" in location -> locale.friendDirectoryInviteWorld
        "~friends(" in location || "~hidden(" in location -> locale.friendDirectoryFriendsWorld
        else -> locale.friendDirectoryPublicWorld
    }
}
