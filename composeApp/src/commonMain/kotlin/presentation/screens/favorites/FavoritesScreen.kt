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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.presentation.compoments.*
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.avatar.data.AvatarProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.home.compoments.GroupOptionsUI
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.*
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
) {
    val navigator = currentNavigator
    val favoriteLocale = strings
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val searchText by favoritesModel.searchText.collectAsState()
    val refreshingTabs by favoritesModel.refreshingTabs.collectAsState()
    val refreshErrors by favoritesModel.refreshErrors.collectAsState()
    val listStates = List(2) { rememberLazyListState() }

    val worlds by favoritesModel.worldList.collectAsState()
    val avatars by favoritesModel.avatarList.collectAsState()
    val worldGroups by favoritesModel.worldFavoriteGroupsFlow.collectAsState()
    val avatarGroups by favoritesModel.avatarFavoriteGroupsFlow.collectAsState()
    val worldOptions by favoritesModel.worldGroupOptions.collectAsState()
    val avatarOptions by favoritesModel.avatarGroupOptions.collectAsState()
    val worldTotal by favoritesModel.worldTotal.collectAsState()
    val avatarTotal by favoritesModel.avatarTotal.collectAsState()
    val modelTabIndex = selectedTab + 1

    SideEffect {
        favoritesModel.updateFavoriteLocale(favoriteLocale)
    }
    LaunchedEffect(Unit) {
        favoritesModel.activateFavoritesPage()
    }
    LaunchedEffect(selectedTab) {
        favoritesModel.syncSelectedTabIndex(modelTabIndex)
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
                        enabled = modelTabIndex !in refreshingTabs,
                        onClick = { favoritesModel.refreshCurrentTabCacheData(tabIndex = modelTabIndex) },
                    ) { Icon(AppIcons.Update, strings.refresh) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val tabs = listOf(strings.worlds, strings.avatars)
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            favoritesModel.setSelectedTabIndex(index + 1)
                        },
                        text = {
                            Text(
                                title,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        },
                    )
                }
            }
            SearchTextField(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                value = searchText,
                onValueChange = favoritesModel::setSearchText,
            )
            when (selectedTab) {
                0 -> GroupOptionsUI(worldOptions, FavoriteType.World, worldGroups, worldTotal, strings.friendListPagerAllWorlds, favoritesModel::updateWorldGroupOptions, { it.selectedGroup }, { o, g -> o.copy(selectedGroup = g) })
                1 -> GroupOptionsUI(avatarOptions, FavoriteType.Avatar, avatarGroups, avatarTotal, strings.friendListPagerAllAvatars, favoritesModel::updateAvatarGroupOptions, { it.selectedGroup }, { o, g -> o.copy(selectedGroup = g) })
            }
            val loading = modelTabIndex in refreshingTabs
            val error = refreshErrors[modelTabIndex]
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listStates[selectedTab],
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    when (selectedTab) {
                        0 -> renderWorldItems(worlds) { world, suffix -> if (!world.isHiddenWorld()) navigator push WorldProfileScreen(WorldProfileVo(world), suffix, world.safeImageUrl().orEmpty()) }
                        1 -> renderAvatarItems(avatars) { avatar, suffix -> if (avatar.releaseStatus != "hidden") navigator push AvatarProfileScreen(AvatarProfileVo(avatar), suffix) }
                    }
                }
                val empty = when (selectedTab) {
                    0 -> worlds.isEmpty()
                    else -> avatars.isEmpty()
                }
                if (loading && empty) CircularProgressIndicator(Modifier.align(Alignment.Center))
                else if (error != null && empty) StateMessage(strings.favoritesLoadFailed, strings.retry) {
                    favoritesModel.refreshCurrentTabCacheData(tabIndex = modelTabIndex)
                }
                else if (empty) StateMessage(strings.favoritesEmpty, null, null)
                else if (error != null) ErrorBanner(strings.favoritesLoadFailed) {
                    favoritesModel.refreshCurrentTabCacheData(tabIndex = modelTabIndex)
                }
            }
        }
    }
}

@Composable
internal fun BoxScope.ErrorBanner(message: String, onRetry: () -> Unit) {
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
internal fun BoxScope.StateMessage(message: String, action: String?, onAction: (() -> Unit)?) {
    Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}
