package io.github.vrcmteam.vrcm.presentation.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
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
        var statusVisible by remember { mutableStateOf(true) }
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
            onStatusVisibilityChanged = { statusVisible = it },
        ) {
            Scaffold(
                contentColor = MaterialTheme.colorScheme.primary,
                topBar = {
                    if (showMainNavigation) {
                        HomeIdentityTopBar(
                            model = model,
                            hazeState = hazeState,
                            statusVisible = statusVisible,
                        ) {
                            if (selectedDestination == HomeDestination.Notifications) {
                                NotificationRefreshAction(notificationModel)
                            }
                        }
                    }
                },
                bottomBar = {
                    if (!useRail && showMainNavigation) {
                        MainNavigationBar(
                            selected = selectedDestination,
                            hasUnread = notificationModel.hasUnread,
                            hazeState = hazeState,
                            onSelect = onDestinationSelected,
                        )
                    }
                },
            ) { contentPadding ->
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
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(top = contentPadding.calculateTopPadding()),
                        ) {
                            stateHolder.SaveableStateProvider(selectedDestination.name) {
                                when (selectedDestination) {
                                    HomeDestination.Home -> HomeDestinationContent(
                                        model,
                                        timelineModel,
                                        timelineState,
                                        timelineFilter,
                                        hasBottomNavigation = !useRail && showMainNavigation,
                                    )
                                    HomeDestination.Search -> SearchListPager.Content()
                                    HomeDestination.Notifications -> NotificationCenterContent(
                                        bottomNavigationPadding = if (!useRail && showMainNavigation) 80.dp else 0.dp,
                                        showTopBar = false,
                                    )
                                    HomeDestination.Friends -> FriendListPager.Content()
                                }
                            }
                        }
                    }
                }
            }
        }

        SettingsBottomSheet(model.settingsVisible, model::hideSettings)
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
    val timelineListState = rememberLazyListState()
    val navigator = currentNavigator
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = model.selectedHomeTabIndex,
        pageCount = { HomeTab.entries.size },
    )

    LaunchedEffect(pagerState, model) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page -> model.selectHomeTab(HomeTab.entries[page]) }
    }
    LaunchedEffect(timelineListState) {
        SharedFlowCentre.toPagerTop.collect {
            if (pagerState.currentPage == HomeTab.Activity.ordinal) {
                runCatching { timelineListState.animateScrollToItem(0) }
            }
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        key = { HomeTab.entries[it].name },
    ) { page ->
        val tabRow: @Composable () -> Unit = {
            HomeTabRow(
                pagerState = pagerState,
                onReselect = { scope.launch { SharedFlowCentre.toPagerTop.emit(Unit) } },
            )
        }
        when (HomeTab.entries[page]) {
            HomeTab.Location -> Box(Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalSharedSuffixKey provides FriendLocationPager.title) {
                    FriendLocationPager.Content(
                        headerContent = tabRow,
                        isActive = { pagerState.currentPage == HomeTab.Location.ordinal },
                    )
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
                headerContent = tabRow,
                modifier = Modifier
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
                    )
                    .padding(bottom = if (hasBottomNavigation) 80.dp else 0.dp),
            )
        }
    }
}

@Composable
private fun HomeTabRow(
    pagerState: PagerState,
    onReselect: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
        HomeTab.entries.forEachIndexed { index, tab ->
            Tab(
                selected = index == pagerState.currentPage,
                onClick = {
                    if (index == pagerState.currentPage && !pagerState.isScrollInProgress) {
                        onReselect()
                    } else {
                        scope.launch { pagerState.animateScrollToTab(index) }
                    }
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
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HomeIdentityTopBar(
    model: HomeScreenModel,
    hazeState: HazeState?,
    statusVisible: Boolean,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val modifier = if (hazeState != null) {
        Modifier.hazeEffect(
            state = hazeState,
            style = style(backgroundColor = backgroundColor),
        )
    } else {
        Modifier.shadow(2.dp)
    }
    Surface(
        modifier = modifier,
        color = if (hazeState != null) Color.Transparent else backgroundColor,
    ) {
        Row(
            Modifier.fillMaxWidth()
                .padding(top = getInsetPadding(WindowInsets::getTop))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                HomeIdentity(
                    model = model,
                    statusVisible = statusVisible,
                    modifier = Modifier.widthIn(max = 286.dp),
                )
            }
            actions()
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HomeIdentity(
    model: HomeScreenModel,
    statusVisible: Boolean,
    modifier: Modifier = Modifier,
) {
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
    Row(
        modifier
            .testTag("home-user-avatar")
            .sharedBoundsBy(meetupCardSharedKey(userId), useSuffixKey = false, resizeMode = MeetupCardResizeMode)
            .clip(MaterialTheme.shapes.medium)
            .simpleCombinedClickable(onClick = model::showDrawer, onLongClick = onLongClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .sharedBoundsBy(
                    key = "${userId}UserIcon",
                    suffixKey = AuthHomeSharedSuffixKey,
                    boundsTransform = IconBoundsTransform,
                )
                .size(54.dp),
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
        Column(
            modifier = Modifier.widthIn(max = 220.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            UserInfoRow(
                iconSize = 16.dp,
                style = MaterialTheme.typography.titleMedium,
                user = currentUser,
                sharedUserId = userId,
                sharedSuffixKey = suffix,
                pronouns = currentUser?.pronouns,
            )
            AnimatedVisibility(statusVisible) {
                UserStatusRow(
                    iconSize = 8.dp,
                    style = MaterialTheme.typography.labelMedium,
                    user = currentUser,
                    animatedVisibilityScope = this,
                    sharedUserId = userId,
                    sharedSuffixKey = suffix,
                )
            }
        }
    }
}

@Composable
private fun NotificationRefreshAction(model: NotificationCenterModel) {
    IconButton(enabled = !model.isRefreshing, onClick = model::refreshAllNotification) {
        if (model.isRefreshing) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(AppIcons.Update, strings.notificationRefresh)
        }
    }
}

@Composable
private fun HomePersonalDrawer(
    model: HomeScreenModel,
    drawerState: DrawerState,
    gesturesEnabled: Boolean,
    onStatusVisibilityChanged: (Boolean) -> Unit,
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
                    onStatusVisibilityChanged(false)
                    currentDialog = UserStatusDialog(user) {
                        currentDialog = null
                        onStatusVisibilityChanged(true)
                    }
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
    selected: HomeDestination,
    hasUnread: Boolean,
    hazeState: HazeState?,
    onSelect: (HomeDestination) -> Unit,
) {
    val bottomPadding = getInsetPadding(12, WindowInsets::getBottom)
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerLowest
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 28.dp, bottom = bottomPadding),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
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
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
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
private fun MainNavigationItem(
    presentation: MainDestinationPresentation,
    selected: Boolean,
    unread: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.simpleClickable(onClick),
    ) {
        MainDestinationIcon(
            presentation = presentation,
            unread = unread,
            modifier = Modifier.size(40.dp),
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
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
    HomeDestination.Home -> MainDestinationPresentation(strings.mainNavigationHome, AppIcons.Explore)
    HomeDestination.Search -> MainDestinationPresentation(strings.mainNavigationSearch, AppIcons.Search)
    HomeDestination.Notifications -> MainDestinationPresentation(strings.mainNavigationNotifications, AppIcons.Notifications)
    HomeDestination.Friends -> MainDestinationPresentation(strings.mainNavigationFriends, AppIcons.Person)
}
