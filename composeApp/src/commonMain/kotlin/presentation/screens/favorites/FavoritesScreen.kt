package io.github.vrcmteam.vrcm.presentation.screens.favorites

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.groups.data.LimitedGroup
import io.github.vrcmteam.vrcm.presentation.compoments.*
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.avatar.data.AvatarProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.group.GroupProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.group.data.GroupProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.home.compoments.GroupOptionsUI
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.*
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.presentation.navigation.AppRoute
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

@Serializable
object FavoritesScreen : AppRoute {
    @Composable
    override fun Content() {
        FavoritesScreenContent()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoritesScreenContent(
    favoritesModel: FriendListPagerModel = koinViewModel(),
    groupsModel: FavoritesGroupsModel = koinViewModel(),
) {
    val navigator = currentNavigator
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val searchText by favoritesModel.searchText.collectAsState()
    val refreshingTabs by favoritesModel.refreshingTabs.collectAsState()
    val refreshErrors by favoritesModel.refreshErrors.collectAsState()
    val groupsState by groupsModel.state.collectAsState()
    val listStates = List(4) { rememberLazyListState() }

    val friends by favoritesModel.friendList.collectAsState()
    val worlds by favoritesModel.worldList.collectAsState()
    val avatars by favoritesModel.avatarList.collectAsState()
    val friendGroups by favoritesModel.friendFavoriteGroupsFlow.collectAsState()
    val worldGroups by favoritesModel.worldFavoriteGroupsFlow.collectAsState()
    val avatarGroups by favoritesModel.avatarFavoriteGroupsFlow.collectAsState()
    val friendOptions by favoritesModel.friendGroupOptions.collectAsState()
    val worldOptions by favoritesModel.worldGroupOptions.collectAsState()
    val avatarOptions by favoritesModel.avatarGroupOptions.collectAsState()
    val friendTotal by favoritesModel.friendTotal.collectAsState()
    val worldTotal by favoritesModel.worldTotal.collectAsState()
    val avatarTotal by favoritesModel.avatarTotal.collectAsState()

    LaunchedEffect(Unit) {
        (0..2).forEach { favoritesModel.refreshCurrentTabCacheData(tabIndex = it) }
    }
    LaunchedEffect(selectedTab) {
        if (selectedTab == 3) groupsModel.loadIfNeeded()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.favoritesTitle) },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(AppIcons.ArrowBackIosNew, strings.back)
                    }
                },
                actions = {
                    IconButton(
                        enabled = if (selectedTab == 3) !groupsState.isLoading else selectedTab !in refreshingTabs,
                        onClick = {
                            if (selectedTab == 3) groupsModel.refresh()
                            else favoritesModel.refreshCurrentTabCacheData(tabIndex = selectedTab)
                        },
                    ) { Icon(AppIcons.Update, strings.refresh) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val tabs = listOf(strings.favoritesFriends, strings.worlds, strings.avatars, strings.myGroups)
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            if (index < 3) favoritesModel.setSelectedTabIndex(index)
                        },
                        text = { Text(title, maxLines = 2, textAlign = TextAlign.Center) },
                    )
                }
            }
            val activeSearch = if (selectedTab == 3) groupsState.searchText else searchText
            SearchTextField(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                value = activeSearch,
                onValueChange = {
                    if (selectedTab == 3) {
                        groupsModel.setSearchText(it)
                    } else favoritesModel.setSearchText(it)
                },
            )
            when (selectedTab) {
                0 -> GroupOptionsUI(friendOptions, FavoriteType.Friend, friendGroups, friendTotal, strings.friendListPagerAllFriends, favoritesModel::updateFriendGroupOptions, { it.selectedGroup }, { o, g -> o.copy(selectedGroup = g) })
                1 -> GroupOptionsUI(worldOptions, FavoriteType.World, worldGroups, worldTotal, strings.friendListPagerAllWorlds, favoritesModel::updateWorldGroupOptions, { it.selectedGroup }, { o, g -> o.copy(selectedGroup = g) })
                2 -> GroupOptionsUI(avatarOptions, FavoriteType.Avatar, avatarGroups, avatarTotal, strings.friendListPagerAllAvatars, favoritesModel::updateAvatarGroupOptions, { it.selectedGroup }, { o, g -> o.copy(selectedGroup = g) })
            }
            val loading = if (selectedTab == 3) groupsState.isLoading else selectedTab in refreshingTabs
            val error = if (selectedTab == 3) groupsState.error else refreshErrors[selectedTab]
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listStates[selectedTab],
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    when (selectedTab) {
                        0 -> renderUserItems(friends) { user, suffix -> navigator push UserProfileScreen(UserProfileVo(user), suffix) }
                        1 -> renderWorldItems(worlds) { world, suffix -> if (!world.isHiddenWorld()) navigator push WorldProfileScreen(WorldProfileVo(world), suffix, world.safeImageUrl().orEmpty()) }
                        2 -> renderAvatarItems(avatars) { avatar, suffix -> if (avatar.releaseStatus != "hidden") navigator push AvatarProfileScreen(AvatarProfileVo(avatar), suffix) }
                        3 -> renderGroupItems(groupsState.visibleGroups.map { group ->
                            LimitedGroup(id = group.groupId, name = group.name, shortCode = group.shortCode, description = group.description, iconUrl = group.iconUrl, bannerUrl = group.bannerUrl, memberCount = group.memberCount, membershipStatus = "member")
                        }) { group, suffix -> navigator push GroupProfileScreen(GroupProfileVo(group), suffix) }
                    }
                }
                val empty = when (selectedTab) {
                    0 -> friends.isEmpty()
                    1 -> worlds.isEmpty()
                    2 -> avatars.isEmpty()
                    else -> groupsState.visibleGroups.isEmpty()
                }
                if (loading && empty) CircularProgressIndicator(Modifier.align(Alignment.Center))
                else if (error != null && empty) StateMessage(strings.favoritesLoadFailed, strings.retry) {
                    if (selectedTab == 3) groupsModel.refresh() else favoritesModel.refreshCurrentTabCacheData(tabIndex = selectedTab)
                }
                else if (empty) StateMessage(strings.favoritesEmpty, null, null)
                else if (error != null) ErrorBanner(strings.favoritesLoadFailed) {
                    if (selectedTab == 3) groupsModel.refresh() else favoritesModel.refreshCurrentTabCacheData(tabIndex = selectedTab)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.ErrorBanner(message: String, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRetry) { Text(strings.retry) }
        }
    }
}

@Composable
private fun BoxScope.StateMessage(message: String, action: String?, onAction: (() -> Unit)?) {
    Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}
