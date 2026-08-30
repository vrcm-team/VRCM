package io.github.vrcmteam.vrcm.presentation.screens.home

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeDefaults.style
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.getAppPlatform
import io.github.vrcmteam.vrcm.network.api.auth.data.CurrentUserData
import io.github.vrcmteam.vrcm.presentation.adaptive.AppWindowWidthClass
import io.github.vrcmteam.vrcm.presentation.adaptive.LocalAppWindowWidthClass
import io.github.vrcmteam.vrcm.presentation.animations.DefaultBoundsTransform
import io.github.vrcmteam.vrcm.presentation.animations.IconBoundsTransform
import io.github.vrcmteam.vrcm.presentation.compoments.*
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.enableIf
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.extensions.isSupportBlur
import io.github.vrcmteam.vrcm.presentation.extensions.simpleCombinedClickable
import io.github.vrcmteam.vrcm.presentation.extensions.simpleClickable
import io.github.vrcmteam.vrcm.presentation.navigation.*
import io.github.vrcmteam.vrcm.presentation.screens.activity.*
import io.github.vrcmteam.vrcm.presentation.screens.auth.AuthAnimeScreen
import io.github.vrcmteam.vrcm.presentation.screens.favorites.FavoritesScreen
import io.github.vrcmteam.vrcm.presentation.screens.favorites.MyGroupsScreen
import io.github.vrcmteam.vrcm.presentation.screens.gallery.GalleryScreen
import io.github.vrcmteam.vrcm.presentation.screens.home.dialog.UserStatusDialog
import io.github.vrcmteam.vrcm.presentation.screens.home.dialog.LogoutConfirmationDialog
import io.github.vrcmteam.vrcm.presentation.screens.home.drawer.PersonalDrawerUser
import io.github.vrcmteam.vrcm.presentation.screens.home.drawer.PersonalNavigationDrawer
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.FriendListPager
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.FriendLocationPager
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.SearchListPager
import io.github.vrcmteam.vrcm.presentation.screens.home.sheet.SettingsBottomSheet
import io.github.vrcmteam.vrcm.presentation.screens.meetup.*
import io.github.vrcmteam.vrcm.presentation.screens.notification.NotificationCenterContent
import io.github.vrcmteam.vrcm.presentation.screens.notification.NotificationCenterModel
import io.github.vrcmteam.vrcm.presentation.screens.user.FriendNetworkScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.world.RecentWorldsScreen
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Serializable
object HomeScreen : AppListRoute {
    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    override fun Content() {
        val navigator = currentNavigator
        val model: HomeScreenModel = koinViewModel()
        val notificationModel = koinInject<NotificationCenterModel>()
        val timelineModel: FriendActivityTimelineModel = koinViewModel()
        val timelineState by timelineModel.state.collectAsState()
        val timelineFilter by timelineModel.filter.collectAsState()
        val stateHolder = rememberSaveableStateHolder()
        val scope = rememberCoroutineScope()
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val drawerCoordinator = remember { HomeDrawerStateCoordinator() }
        val useRail = LocalAppWindowWidthClass.current != AppWindowWidthClass.Compact
        val supportBlur = getAppPlatform().isSupportBlur
        val hazeState = if (supportBlur) remember { HazeState() } else null
        val selectedDestination = HomeDestination.entries[model.selectedDestinationIndex]
        val showMainNavigation = navigator.lastItem == HomeScreen
        val onDestinationSelected: (HomeDestination) -> Unit = { destination ->
            if (model.selectDestination(destination)) {
                if (destination == HomeDestination.Notifications) {
                    notificationModel.refreshAllNotification()
                } else {
                    scope.launch { SharedFlowCentre.toPagerTop.emit(Unit) }
                }
            }
        }

        LaunchedEffect(Unit) {
            SharedFlowCentre.logout.collect {
                model.clearOverlays()
                model.currentUser = null
                navigator replaceAll AuthAnimeScreen(false)
            }
        }
        LaunchedEffect(Unit) {
            SharedFlowCentre.currentSession.collect { model.clearOverlays() }
        }
        LaunchedEffect(model.drawerVisible) {
            if (model.drawerVisible) drawerState.open() else drawerState.close()
        }
        LaunchedEffect(drawerState) {
            snapshotFlow { drawerState.currentValue }
                .distinctUntilChanged()
                .collect { value ->
                    if (drawerCoordinator.shouldHide(value) && model.drawerVisible) {
                        model.hideDrawer()
                    }
                }
        }
        val closeDrawer: () -> Unit = {
            scope.launch {
                drawerState.close()
                model.hideDrawer()
            }
        }
        HandleBackNavigation(model.drawerVisible || drawerState.isOpen, closeDrawer)

        HomePersonalDrawer(
            model = model,
            drawerState = drawerState,
            gesturesEnabled = model.drawerVisible || drawerState.isOpen,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .enableIf(supportBlur) { hazeSource(hazeState!!) },
                tonalElevation = 2.dp,
            ) {
                Row {
                    if (useRail && showMainNavigation) {
                        MainNavigationRail(
                            selectedDestination,
                            notificationModel.hasUnread,
                            onDestinationSelected,
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        stateHolder.SaveableStateProvider(selectedDestination.name) {
                            when (selectedDestination) {
                                HomeDestination.Home -> HomeDestinationContent(
                                    model,
                                    timelineModel,
                                    timelineState,
                                    timelineFilter,
                                    hasBottomNavigation = !useRail && showMainNavigation,
                                )
                                HomeDestination.Search -> RootPagerContent(strings.mainNavigationSearch) {
                                    SearchListPager.Content()
                                }
                                HomeDestination.Notifications -> NotificationCenterContent(
                                    modifier = if (useRail || !showMainNavigation) {
                                        Modifier
                                    } else {
                                        Modifier.padding(bottom = 80.dp)
                                    },
                                    showBackButton = false,
                                    navigationIcon = { RootAvatarButton(model) },
                                )
                                HomeDestination.Friends -> RootPagerContent(strings.mainNavigationFriends) {
                                    FriendListPager.Content()
                                }
                            }
                        }
                        if (!useRail && showMainNavigation) {
                            MainNavigationBar(
                                modifier = Modifier.align(Alignment.BottomCenter),
                                selected = selectedDestination,
                                hasUnread = notificationModel.hasUnread,
                                hazeState = hazeState,
                                onSelect = onDestinationSelected,
                            )
                        }
                    }
                }
            }
        }

        SettingsBottomSheet(model.settingsVisible, model::hideSettings)
    }
}

@Composable
private fun RootPagerContent(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        RootTopBar(title)
        Box(Modifier.weight(1f)) { content() }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HomeDestinationContent(
    model: HomeScreenModel,
    timelineModel: FriendActivityTimelineModel,
    timelineState: FriendActivityTimelineState,
    timelineFilter: FriendActivityTimelineFilter,
    hasBottomNavigation: Boolean,
) {
    val selectedTab = HomeTab.entries[model.selectedHomeTabIndex]
    val stateHolder = rememberSaveableStateHolder()
    val timelineListState = rememberLazyListState()
    val navigator = currentNavigator
    val scope = rememberCoroutineScope()

    if (selectedTab == HomeTab.Activity) {
        LaunchedEffect(timelineListState) {
            SharedFlowCentre.toPagerTop.collect {
                runCatching { timelineListState.animateScrollToItem(0) }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        RootTopBar(strings.mainNavigationHome)
        PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
            HomeTab.entries.forEach { tab ->
                Tab(
                    selected = tab == selectedTab,
                    onClick = {
                        if (tab == selectedTab) scope.launch { SharedFlowCentre.toPagerTop.emit(Unit) }
                        else model.selectHomeTab(tab)
                    },
                    text = {
                        Text(
                            if (tab == HomeTab.Location) strings.homeTabLocation else strings.homeTabActivity,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
        Box(Modifier.weight(1f)) {
            stateHolder.SaveableStateProvider(selectedTab.name) {
                when (selectedTab) {
                    HomeTab.Location -> Box(Modifier.fillMaxSize()) {
                        CompositionLocalProvider(LocalSharedSuffixKey provides FriendLocationPager.title) {
                            FriendLocationPager.Content()
                        }
                    }
                    HomeTab.Activity -> FriendActivityTimelineContent(
                        state = timelineState,
                        filter = timelineFilter,
                        onFilterSelected = timelineModel::selectFilter,
                        onLoadMore = timelineModel::loadMore,
                        onRetry = timelineModel::retry,
                        onRetryLoadMore = timelineModel::retryLoadMore,
                        onUserClick = { event ->
                            navigator push UserProfileScreen(
                                UserProfileVo(
                                    id = event.friendUserId,
                                    displayName = event.displayName,
                                    profileImageUrl = event.profileImageUrl,
                                )
                            )
                        },
                        onWorldClick = { event ->
                            val worldId = event.navigableWorldId() ?: return@FriendActivityTimelineContent
                            navigator push WorldProfileScreen(
                                WorldProfileVo(worldId = worldId, worldName = event.worldName.orEmpty())
                            )
                        },
                        listState = timelineListState,
                        modifier = Modifier
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
                            )
                            .padding(bottom = if (hasBottomNavigation) 80.dp else 0.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RootTopBar(title: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shadowElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val model: HomeScreenModel = koinViewModel()
            RootAvatarButton(model)
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun RootAvatarButton(model: HomeScreenModel) {
    val userId = model.userId
    val currentUser = model.currentUser
    val navigator = currentNavigator
    val suffix = rememberContainerTransformToken("home-user:$userId") ?: LocalSharedSuffixKey.current
    val onLongClick = {
        val last = navigator.lastItem
        val alreadyOpen = (last as? MeetupCardDisplayRoute)?.ownerUserId == userId ||
            (last as? MeetupCardEditorRoute)?.ownerUserId == userId
        if (!alreadyOpen && currentUser != null) navigator push model.meetupCardStartRoute()
    }
    Box(
        Modifier
            .testTag("home-user-avatar")
            .sharedBoundsBy(meetupCardSharedKey(userId), useSuffixKey = false, resizeMode = MeetupCardResizeMode)
            .sharedBoundsBy(
                key = "${userId}UserIcon",
                suffixKey = AuthHomeSharedSuffixKey,
                boundsTransform = IconBoundsTransform,
            )
            .size(48.dp)
            .clip(MaterialTheme.shapes.medium)
            .simpleCombinedClickable(onClick = model::showDrawer, onLongClick = onLongClick),
    ) {
        UserStateIcon(
            modifier = Modifier.fillMaxSize().sharedBoundsBy(
                key = "${userId}UserIcon",
                suffixKey = suffix,
                boundsTransform = if (currentUser != null) DefaultBoundsTransform else IconBoundsTransform,
            ),
            iconUrl = currentUser?.iconUrl ?: model.iconUrl,
            cachedPlaceholderKey = model.iconUrl,
        )
    }
}

@Composable
private fun HomePersonalDrawer(
    model: HomeScreenModel,
    drawerState: DrawerState,
    gesturesEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    val navigator = currentNavigator
    val currentUser = model.currentUser
    var currentDialog by LocationDialogContent.current
    var showLogoutConfirmation by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val suffix = rememberContainerTransformToken("home-user:${model.userId}") ?: LocalSharedSuffixKey.current
    fun closeAndNavigate(route: AppRoute) {
        scope.launch {
            drawerState.close()
            model.hideDrawer()
            navigator push route
        }
    }
    PersonalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = gesturesEnabled,
        user = currentUser?.toPersonalDrawerUser(),
        onProfileClick = {
            currentUser?.let {
                scope.launch {
                    drawerState.close()
                    model.hideDrawer()
                    navigator push UserProfileScreen(UserProfileVo(it), suffix)
                }
            }
        },
        onStatusClick = {
            currentUser?.let { user ->
                scope.launch {
                    drawerState.close()
                    model.hideDrawer()
                    currentDialog = UserStatusDialog(user) { currentDialog = null }
                }
            }
        },
        onFriendNetworkClick = { closeAndNavigate(FriendNetworkScreen) },
        onGalleryClick = { closeAndNavigate(GalleryScreen) },
        onFavoritesClick = { closeAndNavigate(FavoritesScreen) },
        onMyGroupsClick = { closeAndNavigate(MyGroupsScreen) },
        onRecentWorldsClick = { closeAndNavigate(RecentWorldsScreen) },
        onNameplateClick = { closeAndNavigate(model.meetupCardStartRoute()) },
        onSettingsClick = {
            scope.launch {
                drawerState.close()
                model.hideDrawer()
                model.showSettings()
            }
        },
        onLogoutClick = { showLogoutConfirmation = true },
        content = content,
    )
    if (showLogoutConfirmation) {
        LogoutConfirmationDialog(
            onDismissRequest = { showLogoutConfirmation = false },
            onConfirm = {
                showLogoutConfirmation = false
                scope.launch {
                    drawerState.close()
                    model.hideDrawer()
                    model.logout()
                }
            },
        )
    }
}

private fun CurrentUserData.toPersonalDrawerUser() = PersonalDrawerUser(
    avatarUrl = iconUrl,
    displayName = displayName,
    pronouns = pronouns,
    status = status,
    statusDescription = statusDescription,
)

@Composable
private fun MainNavigationBar(
    modifier: Modifier,
    selected: HomeDestination,
    hasUnread: Boolean,
    hazeState: HazeState?,
    onSelect: (HomeDestination) -> Unit,
) {
    val bottomPadding = getInsetPadding(12, WindowInsets::getBottom)
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerLowest
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = bottomPadding),
        contentAlignment = Alignment.Center,
    ) {
        val horizontalPadding = if (maxWidth < 320.dp) 12.dp else 28.dp
        Surface(
            modifier = Modifier
                .padding(horizontal = horizontalPadding)
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .height(64.dp)
                .run {
                    if (hazeState != null) {
                        clip(CircleShape).hazeEffect(
                            state = hazeState,
                            style = style(backgroundColor = backgroundColor),
                        )
                    } else {
                        shadow(elevation = 2.dp, shape = CircleShape)
                    }
                },
            color = if (hazeState != null) Color.Transparent else backgroundColor,
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeDestination.entries.forEach { destination ->
                    val presentation = destination.presentation()
                    MainNavigationItem(
                        presentation = presentation,
                        selected = selected == destination,
                        unread = destination == HomeDestination.Notifications && hasUnread,
                        onClick = { onSelect(destination) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.MainNavigationItem(
    presentation: MainDestinationPresentation,
    selected: Boolean,
    unread: Boolean,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "Main navigation icon tint",
    )
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(CircleShape)
            .simpleClickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        MainDestinationIcon(
            presentation = presentation,
            unread = unread,
            modifier = Modifier.size(40.dp),
            tint = tint,
        )
    }
}

@Composable
private fun MainNavigationRail(
    selected: HomeDestination,
    hasUnread: Boolean,
    onSelect: (HomeDestination) -> Unit,
) {
    NavigationRail(
        Modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Spacer(Modifier.weight(1f))
        HomeDestination.entries.forEach { destination ->
            val presentation = destination.presentation()
            NavigationRailItem(
                selected = selected == destination,
                onClick = { onSelect(destination) },
                icon = { MainDestinationIcon(presentation, destination == HomeDestination.Notifications && hasUnread) },
                label = { Text(presentation.label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun MainDestinationIcon(presentation: MainDestinationPresentation, unread: Boolean) {
    MainDestinationIcon(
        presentation = presentation,
        unread = unread,
        modifier = Modifier.size(24.dp),
        tint = LocalContentColor.current,
    )
}

@Composable
private fun MainDestinationIcon(
    presentation: MainDestinationPresentation,
    unread: Boolean,
    modifier: Modifier,
    tint: Color,
) {
    BadgedBox(
        badge = {
            if (unread) {
                val badgeColor = MaterialTheme.colorScheme.tertiary
                Canvas(Modifier.offset(4.dp, (-4).dp).size(8.dp)) {
                    drawCircle(color = badgeColor, radius = 4.dp.toPx())
                }
            }
        },
    ) {
        Icon(
            imageVector = presentation.icon,
            contentDescription = presentation.label,
            modifier = modifier,
            tint = tint,
        )
    }
}

private data class MainDestinationPresentation(val label: String, val icon: ImageVector)

internal class HomeDrawerStateCoordinator {
    private var hasSettledOpen = false

    fun shouldHide(value: DrawerValue): Boolean = when (value) {
        DrawerValue.Open -> {
            hasSettledOpen = true
            false
        }
        DrawerValue.Closed -> {
            val shouldHide = hasSettledOpen
            hasSettledOpen = false
            shouldHide
        }
    }
}

@Composable
private fun HomeDestination.presentation(): MainDestinationPresentation = when (this) {
    HomeDestination.Home -> MainDestinationPresentation(strings.mainNavigationHome, AppIcons.Dashboard)
    HomeDestination.Search -> MainDestinationPresentation(strings.mainNavigationSearch, AppIcons.Search)
    HomeDestination.Notifications -> MainDestinationPresentation(strings.mainNavigationNotifications, AppIcons.Notifications)
    HomeDestination.Friends -> MainDestinationPresentation(strings.mainNavigationFriends, AppIcons.Person)
}
