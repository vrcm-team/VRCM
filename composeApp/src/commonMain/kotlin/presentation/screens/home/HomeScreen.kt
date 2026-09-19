package io.github.vrcmteam.vrcm.presentation.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.auth.data.CurrentUserData
import io.github.vrcmteam.vrcm.presentation.adaptive.AppWindowWidthClass
import io.github.vrcmteam.vrcm.presentation.adaptive.LocalAppWindowWidthClass
import io.github.vrcmteam.vrcm.presentation.animations.DefaultBoundsTransform
import io.github.vrcmteam.vrcm.presentation.animations.IconBoundsTransform
import io.github.vrcmteam.vrcm.presentation.compoments.*
import io.github.vrcmteam.vrcm.presentation.compoments.ContentTopInset
import io.github.vrcmteam.vrcm.presentation.compoments.LocalContentTopInset
import io.github.vrcmteam.vrcm.presentation.designsystem.*
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.extensions.simpleCombinedClickable
import io.github.vrcmteam.vrcm.presentation.extensions.simpleClickable
import io.github.vrcmteam.vrcm.presentation.navigation.*
import io.github.vrcmteam.vrcm.presentation.screens.activity.*
import io.github.vrcmteam.vrcm.presentation.screens.auth.AuthAnimeScreen
import io.github.vrcmteam.vrcm.presentation.screens.favorites.FavoritesGroupsModel
import io.github.vrcmteam.vrcm.presentation.screens.favorites.FavoritesHubContent
import io.github.vrcmteam.vrcm.presentation.screens.favorites.FavoritesHubSelectionActions
import io.github.vrcmteam.vrcm.presentation.screens.favorites.FavoritesHubTabRow
import io.github.vrcmteam.vrcm.presentation.screens.favorites.FavoritesTab
import io.github.vrcmteam.vrcm.presentation.screens.gallery.GalleryScreen
import io.github.vrcmteam.vrcm.presentation.screens.home.dialog.UserStatusDialog
import io.github.vrcmteam.vrcm.presentation.screens.home.dialog.LogoutConfirmationDialog
import io.github.vrcmteam.vrcm.presentation.screens.home.drawer.PersonalDrawerUser
import io.github.vrcmteam.vrcm.presentation.screens.home.drawer.PersonalNavigationDrawer
import io.github.vrcmteam.vrcm.presentation.screens.home.drawer.drawerStatusSharedUserId
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.FriendListPagerModel
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.FriendLocationPager
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.GroupInstancePager
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.HomeLocationSource
import io.github.vrcmteam.vrcm.presentation.screens.inventory.InventoryScreen
import io.github.vrcmteam.vrcm.presentation.screens.meetup.*
import io.github.vrcmteam.vrcm.presentation.screens.notification.NotificationCenterContent
import io.github.vrcmteam.vrcm.presentation.screens.notification.NotificationCenterModel
import io.github.vrcmteam.vrcm.presentation.screens.search.GlobalSearchScreen
import io.github.vrcmteam.vrcm.presentation.screens.settings.InviteMessageSlotsScreen
import io.github.vrcmteam.vrcm.presentation.screens.settings.PlayerModerationListScreen
import io.github.vrcmteam.vrcm.presentation.screens.settings.SettingsScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.FriendNetworkScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.world.RecentWorldsScreen
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlin.math.roundToInt
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
        val stateHolder = rememberSaveableStateHolder()
        val scope = rememberCoroutineScope()
        val drawerState = rememberAppDrawerState(AppDrawerValue.Closed)
        val drawerCoordinator = remember { HomeDrawerStateCoordinator() }
        val windowWidthClass = LocalAppWindowWidthClass.current
        val useRail = windowWidthClass != AppWindowWidthClass.Compact
        val selectedDestination = HomeDestination.entries[model.selectedDestinationIndex]
        val selectedFavoritesTab = FavoritesTab.entries[model.selectedFavoritesTabIndex]
        val friendListModel = if (selectedDestination == HomeDestination.Favorites) {
            koinViewModel<FriendListPagerModel>()
        } else {
            null
        }
        val groupsModel = if (selectedDestination == HomeDestination.Favorites) {
            koinViewModel<FavoritesGroupsModel>()
        } else {
            null
        }
        val topRoute = navigator.lastItem
        val showMainNavigation = topRoute == HomeScreen ||
            (windowWidthClass == AppWindowWidthClass.Expanded && topRoute is AppDetailRoute)
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

        var locationSourceIndex by rememberSaveable {
            mutableIntStateOf(HomeLocationSource.Friends.ordinal)
        }
        val locationSource = HomeLocationSource.entries.getOrElse(locationSourceIndex) {
            HomeLocationSource.Friends
        }
        // 顶栏 / 底栏随列表滚动收起；换页面、换标签或点标签回到顶部时放出来，否则没有栏可点
        val barsState = remember { HomeBarsScrollState() }
        val barsConnection = rememberHomeBarsNestedScrollConnection(barsState)
        LaunchedEffect(
            barsConnection,
            selectedDestination,
            model.selectedHomeTabIndex,
            model.selectedFavoritesTabIndex,
            locationSourceIndex,
        ) {
            barsConnection.show()
        }
        LaunchedEffect(barsConnection) {
            SharedFlowCentre.toPagerTop.collect { barsConnection.show() }
        }

        // 标签条在顶部容器里、页面在内容区里，两边共用的分页状态提到它们之上
        val homePagerState = rememberPagerState(
            initialPage = model.selectedHomeTabIndex,
            pageCount = { HomeTab.entries.size },
        )
        var activityFilterIndex by rememberSaveable {
            mutableIntStateOf(FriendActivityTimelineFilter.All.ordinal)
        }
        val activityFilter = FriendActivityTimelineFilter.entries.getOrElse(activityFilterIndex) {
            FriendActivityTimelineFilter.All
        }
        val favoritesPagerState = rememberPagerState(
            initialPage = model.selectedFavoritesTabIndex,
            pageCount = { FavoritesTab.entries.size },
        )

        // 收藏、动态是通栏列表（iOS 信息那种），用系统背景色；位置、通知是卡片，用分组灰底
        val showsPlainList = selectedDestination == HomeDestination.Favorites ||
            (selectedDestination == HomeDestination.Home && model.selectedHomeTabIndex == HomeTab.Activity.ordinal)
        val pageColor by animateColorAsState(
            targetValue = if (showsPlainList) AppTheme.colors.systemBackground else AppTheme.colors.groupedBackground,
            animationSpec = tween(AppTheme.motion.normalMs),
        )
        HomePersonalDrawer(
            model = model,
            drawerState = drawerState,
            gesturesEnabled = model.drawerVisible || drawerState.isOpen,
        ) {
            AppScaffold(
                containerColor = pageColor,
                topBar = {
                    if (showMainNavigation) {
                        // 顶部容器 = 身份栏 + 当前页面的标签条：一整块模糊玻璃，内容从它下面滚过；随滚动整体收起
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .collapseUpwardWith(barsState)
                                .glassBar(pageColor),
                        ) {
                            Column(Modifier.fadeOutWith(barsState)) {
                                // 平时身份栏右侧没有按钮：刷新靠下拉，搜索在底栏；只有收藏页批量选择期间才出现动作
                                HomeIdentityTopBar(model = model) {
                                    if (selectedDestination == HomeDestination.Favorites) {
                                        FavoritesHubSelectionActions(
                                            selectedTab = selectedFavoritesTab,
                                            favoritesModel = requireNotNull(friendListModel),
                                            groupsModel = requireNotNull(groupsModel),
                                        )
                                    }
                                }
                                when (selectedDestination) {
                                    HomeDestination.Home -> HomeTabRow(
                                        pagerState = homePagerState,
                                        locationSource = locationSource,
                                        onLocationSourceSelected = { locationSourceIndex = it.ordinal },
                                        activityFilter = activityFilter,
                                        onActivityFilterSelected = { activityFilterIndex = it.ordinal },
                                    )
                                    HomeDestination.Favorites -> FavoritesHubTabRow(favoritesPagerState)
                                    HomeDestination.Notifications -> Unit
                                }
                            }
                        }
                    }
                },
                bottomBar = {
                    if (!useRail && showMainNavigation) {
                        MainNavigationBar(
                            selected = selectedDestination,
                            hasUnread = notificationModel.hasUnread,
                            onSelect = onDestinationSelected,
                            onSearch = { navigator push GlobalSearchScreen },
                            modifier = Modifier.slideOutDownwardWith(barsState),
                        )
                    }
                },
            ) { contentPadding ->
                // 顶部容器能收起的只有状态栏以下的那一段；内容铺在它下面，列表自己把它盖住的高度让出来
                val expandedTop = contentPadding.calculateTopPadding()
                val statusBarTop = getInsetPadding(WindowInsets::getTop)
                val (expandedTopPx, collapseRangePx) = with(LocalDensity.current) {
                    expandedTop.roundToPx() to (expandedTop - statusBarTop).toPx()
                }
                SideEffect { barsState.updateCollapseRange(collapseRangePx) }
                val currentExpandedTopPx by rememberUpdatedState(expandedTopPx)
                val contentTopInset = remember(barsState) {
                    ContentTopInset(
                        current = { currentExpandedTopPx - barsState.collapsed.roundToInt() },
                        expanded = { currentExpandedTopPx },
                        onContentPulledChange = barsState::contentPulledChanged,
                    )
                }
                AppSurface(
                    modifier = Modifier.fillMaxSize(),
                    color = pageColor,
                ) {
                    Row {
                        if (useRail && showMainNavigation) {
                            MainNavigationRail(
                                selected = selectedDestination,
                                hasUnread = notificationModel.hasUnread,
                                onSelect = onDestinationSelected,
                                onSearch = { navigator push GlobalSearchScreen },
                            )
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .nestedScroll(barsConnection),
                        ) {
                            CompositionLocalProvider(LocalContentTopInset provides contentTopInset) {
                                stateHolder.SaveableStateProvider(selectedDestination.name) {
                                    when (selectedDestination) {
                                        HomeDestination.Home -> HomeDestinationContent(
                                            model = model,
                                            pagerState = homePagerState,
                                            locationSource = locationSource,
                                            activityFilter = activityFilter,
                                            hasBottomNavigation = !useRail && showMainNavigation,
                                        )
                                        HomeDestination.Favorites -> FavoritesHubContent(
                                            selectedTab = selectedFavoritesTab,
                                            onSelectedTab = model::selectFavoritesTab,
                                            favoritesModel = requireNotNull(friendListModel),
                                            groupsModel = requireNotNull(groupsModel),
                                            contentBottomPadding = getInsetPadding(12, WindowInsets::getBottom) +
                                                if (!useRail && showMainNavigation) 80.dp else 0.dp,
                                            pagerState = favoritesPagerState,
                                            showTabRow = false,
                                        )
                                        HomeDestination.Notifications -> NotificationCenterContent(
                                            bottomNavigationPadding = if (!useRail && showMainNavigation) 80.dp else 0.dp,
                                            showTopBar = false,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HomeDestinationContent(
    model: HomeScreenModel,
    pagerState: PagerState,
    locationSource: HomeLocationSource,
    activityFilter: FriendActivityTimelineFilter,
    hasBottomNavigation: Boolean,
) {
    val stateHolder = rememberSaveableStateHolder()
    var activityActivated by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(pagerState, model) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                model.selectHomeTab(HomeTab.entries[page])
                if (page == HomeTab.Activity.ordinal) activityActivated = true
            }
    }
    // 标签条在骨架的顶部容器里；这里只有页面
    Column(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            key = { HomeTab.entries[it].name },
        ) { page ->
            val tab = HomeTab.entries[page]
            // 位置页两种来源各存各的列表状态，换回来时还在原来的位置
            val pageKey = when (tab) {
                HomeTab.Location -> "${'$'}{tab.name}:${'$'}{locationSource.name}"
                HomeTab.Activity -> tab.name
            }
            stateHolder.SaveableStateProvider(pageKey) {
                when (tab) {
                    HomeTab.Location -> Box(Modifier.fillMaxSize()) {
                        CompositionLocalProvider(LocalSharedSuffixKey provides FriendLocationPager.title) {
                            val isActive = { pagerState.settledPage == HomeTab.Location.ordinal }
                            when (locationSource) {
                                HomeLocationSource.Friends -> FriendLocationPager.Content(isActive = isActive)
                                HomeLocationSource.Groups -> GroupInstancePager(isActive = isActive)
                            }
                        }
                    }
                    HomeTab.Activity -> if (activityActivated) {
                        FriendActivityTimelineDestination(
                            hasBottomNavigation = hasBottomNavigation,
                            isActive = { pagerState.settledPage == HomeTab.Activity.ordinal },
                            selectedFilter = activityFilter,
                        )
                    } else {
                        ActivityTimelinePreview()
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTabRow(
    pagerState: PagerState,
    locationSource: HomeLocationSource,
    onLocationSourceSelected: (HomeLocationSource) -> Unit,
    activityFilter: FriendActivityTimelineFilter,
    onActivityFilterSelected: (FriendActivityTimelineFilter) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var menuTab by remember { mutableStateOf<HomeTab?>(null) }
    AppTabRow(selectedTabIndex = pagerState.currentPage) {
        HomeTab.entries.forEachIndexed { index, tab ->
            AppTab(
                selected = index == pagerState.currentPage,
                onClick = {
                    // 已经选中的标签再点一次，拉出下拉框换这一页显示的内容
                    if (index == pagerState.settledPage && !pagerState.isScrollInProgress) {
                        menuTab = tab
                    } else {
                        scope.launch { pagerState.animateScrollToTab(index) }
                    }
                },
                text = {
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            AppText(
                                text = when (tab) {
                                    HomeTab.Location -> strings.homeTabLocation
                                    HomeTab.Activity -> strings.homeTabActivity
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            AppIcon(
                                imageVector = if (menuTab == tab) {
                                    AppIcons.ExpandLess
                                } else {
                                    AppIcons.ExpandMore
                                },
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        val dismiss = { menuTab = null }
                        when (tab) {
                            HomeTab.Location -> HomeTabOptionsMenu(
                                expanded = menuTab == tab,
                                onDismissRequest = dismiss,
                                options = HomeLocationSource.entries,
                                selected = locationSource,
                                label = { it.label() },
                                onSelected = onLocationSourceSelected,
                            )
                            HomeTab.Activity -> HomeTabOptionsMenu(
                                expanded = menuTab == tab,
                                onDismissRequest = dismiss,
                                options = FriendActivityTimelineFilter.entries,
                                selected = activityFilter,
                                label = { it.label() },
                                onSelected = onActivityFilterSelected,
                            )
                        }
                    }
                },
            )
        }
    }
}

/** 标签上的下拉框：这一页显示哪一种内容，选中项打对勾。 */
@Composable
private fun <T> HomeTabOptionsMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
) {
    AppMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.widthIn(min = 200.dp),
    ) {
        options.forEach { option ->
            AppMenuItem(
                text = { AppText(label(option)) },
                onClick = {
                    onDismissRequest()
                    onSelected(option)
                },
                leadingIcon = {
                    Box(
                        modifier = Modifier.size(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (option == selected) {
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

@Composable
private fun HomeLocationSource.label(): String = when (this) {
    HomeLocationSource.Friends -> strings.homeLocationSourceFriends
    HomeLocationSource.Groups -> strings.groupInstances
}

@Composable
private fun FriendActivityTimelineDestination(
    hasBottomNavigation: Boolean,
    isActive: () -> Boolean,
    selectedFilter: FriendActivityTimelineFilter,
) {
    val model: FriendActivityTimelineModel = koinViewModel()
    val state by model.state.collectAsState()
    val listState = rememberLazyListState()
    val navigator = currentNavigator
    val currentIsActive by rememberUpdatedState(isActive)

    LaunchedEffect(listState) {
        SharedFlowCentre.toPagerTop.collect {
            if (currentIsActive()) {
                launch { runCatching { listState.animateScrollToItem(0) } }
            }
        }
    }
    LaunchedEffect(model, selectedFilter) {
        model.selectFilter(selectedFilter)
        listState.scrollToItem(0)
    }

    FriendActivityTimelineContent(
        state = state,
        filter = selectedFilter,
        onFilterSelected = model::selectFilter,
        onLoadMore = model::loadMore,
        onRetry = model::retry,
        onRetryLoadMore = model::retryLoadMore,
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
        listState = listState,
        controlsInList = true,
        showFilterControls = false,
        bottomNavigationPadding = if (hasBottomNavigation) 80.dp else 0.dp,
    )
}

@Composable
private fun ActivityTimelinePreview() {
    Spacer(Modifier.fillMaxSize())
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HomeIdentityTopBar(
    model: HomeScreenModel,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth()
            .padding(top = getInsetPadding(WindowInsets::getTop))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            HomeIdentity(
                model = model,
                modifier = Modifier.widthIn(max = 286.dp),
            )
        }
        // 顶栏动作与其他页面的导航栏一致：玻璃圆钮
        CompositionLocalProvider(LocalAppControlContext provides AppControlContext.NavBar) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HomeIdentity(
    model: HomeScreenModel,
    modifier: Modifier = Modifier,
) {
    val userId = model.userId
    val currentUser = model.currentUser
    val navigator = currentNavigator
    val suffix = rememberContainerTransformToken("home-user:$userId") ?: LocalSharedSuffixKey.current
    var currentDialog by LocationDialogContent.current
    var statusVisible by remember(userId) { mutableStateOf(true) }
    val onLongClick = {
        val last = navigator.lastItem
        val alreadyOpen = (last as? MeetupCardDisplayRoute)?.ownerUserId == userId ||
            (last as? MeetupCardEditorRoute)?.ownerUserId == userId
        if (!alreadyOpen && currentUser != null) navigator push model.meetupCardStartRoute()
    }
    Row(
        modifier
            .sharedBoundsBy(meetupCardSharedKey(userId), useSuffixKey = false, resizeMode = MeetupCardResizeMode)
            .clip(AppShapes.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .testTag("home-user-avatar")
                .sharedBoundsBy(
                    key = "${userId}UserIcon",
                    suffixKey = AuthHomeSharedSuffixKey,
                    boundsTransform = IconBoundsTransform,
                )
                .size(54.dp)
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
        Column(
            modifier = Modifier
                .widthIn(max = 220.dp)
                .simpleClickable {
                    currentUser?.let { user ->
                        statusVisible = false
                        currentDialog = UserStatusDialog(user) {
                            currentDialog = null
                            statusVisible = true
                        }
                    }
                },
            horizontalAlignment = Alignment.Start,
        ) {
            UserInfoRow(
                iconSize = 16.dp,
                style = AppTheme.type.headline,
                user = currentUser,
                sharedUserId = userId,
                sharedSuffixKey = suffix,
                pronouns = currentUser?.pronouns,
            )
            AnimatedVisibility(statusVisible) {
                UserStatusRow(
                    iconSize = 8.dp,
                    style = AppTheme.type.caption1Emphasized,
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
private fun HomePersonalDrawer(
    model: HomeScreenModel,
    drawerState: AppDrawerState,
    gesturesEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    val navigator = currentNavigator
    val currentUser = model.currentUser
    var currentDialog by LocationDialogContent.current
    var statusVisible by remember(currentUser?.id) { mutableStateOf(true) }
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
        profileSharedSuffixKey = suffix,
        statusVisible = statusVisible,
        onProfileClick = {
            currentUser?.let {
                navigator push UserProfileScreen(UserProfileVo(it), suffix)
                model.hideDrawer()
            }
        },
        onStatusClick = {
            currentUser?.let { user ->
                statusVisible = false
                currentDialog = UserStatusDialog(
                    currentUser = user,
                    sharedUserId = drawerStatusSharedUserId(user.id),
                ) {
                    currentDialog = null
                    statusVisible = true
                }
            }
        },
        onFriendNetworkClick = { closeAndNavigate(FriendNetworkScreen) },
        onGalleryClick = { closeAndNavigate(GalleryScreen) },
        onInviteMessagesClick = { closeAndNavigate(InviteMessageSlotsScreen) },
        onPlayerManagementClick = { closeAndNavigate(PlayerModerationListScreen) },
        onRecentWorldsClick = { closeAndNavigate(RecentWorldsScreen) },
        onInventoryClick = { closeAndNavigate(InventoryScreen) },
        onNameplateClick = { closeAndNavigate(model.meetupCardStartRoute()) },
        onSettingsClick = { closeAndNavigate(SettingsScreen) },
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
    id = id,
    avatarUrl = iconUrl,
    customBannerUrl = bannerUrl?.takeIf {
        bannerType == "customImage" && it.isNotBlank()
    },
    displayName = displayName,
    pronouns = pronouns,
    isSupporter = isSupporter,
    status = status,
    statusDescription = statusDescription,
    location = location,
)

/** 底栏：标签栏 + 右侧独立的搜索圆钮（全局搜索三个页面都用得上），两块玻璃一起居中。 */
@Composable
private fun MainNavigationBar(
    selected: HomeDestination,
    hasUnread: Boolean,
    onSelect: (HomeDestination) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bottomPadding = getInsetPadding(12, WindowInsets::getBottom)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = bottomPadding),
        contentAlignment = Alignment.Center,
    ) {
        val spacing = 10.dp
        // 窄屏放不下时压缩标签，搜索钮保持圆形
        val itemWidth = appTabBarItemWidth(
            availableWidth = maxWidth - AppSize.tabBar - spacing,
            itemCount = HomeDestination.entries.size,
            preferred = 78.dp,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTabBar(
                itemCount = HomeDestination.entries.size,
                selectedIndex = selected.ordinal,
                onSelect = { onSelect(HomeDestination.entries[it]) },
                itemWidth = itemWidth,
            ) { index, isSelected ->
                val destination = HomeDestination.entries[index]
                val presentation = destination.presentation()
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    MainDestinationIcon(
                        presentation = presentation,
                        selected = isSelected,
                        unread = destination == HomeDestination.Notifications && hasUnread,
                        modifier = Modifier.size(24.dp),
                        tint = LocalContentColor.current,
                    )
                    AppText(presentation.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            AppTabBarAccessoryButton(onClick = onSearch) {
                AppIcon(AppIcons.Search, strings.fiendListPagerSearch, Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun MainNavigationRail(
    selected: HomeDestination,
    hasUnread: Boolean,
    onSelect: (HomeDestination) -> Unit,
    onSearch: () -> Unit,
) {
    AppNavigationRail(
        Modifier.fillMaxHeight(),
        containerColor = AppTheme.colors.secondaryGroupedBackground,
    ) {
        Spacer(Modifier.weight(1f))
        HomeDestination.entries.forEach { destination ->
            val presentation = destination.presentation()
            AppNavigationRailItem(
                selected = selected == destination,
                onClick = { onSelect(destination) },
                icon = {
                    MainDestinationIcon(
                        presentation = presentation,
                        selected = selected == destination,
                        unread = destination == HomeDestination.Notifications && hasUnread,
                    )
                },
                label = { AppText(presentation.label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            )
        }
        // 搜索排在页面之后（iOS 26 的搜索标签）：点了进搜索页，不改变选中的页面
        AppNavigationRailItem(
            selected = false,
            onClick = onSearch,
            icon = { AppIcon(AppIcons.Search, strings.fiendListPagerSearch, Modifier.size(24.dp)) },
            label = { AppText(strings.fiendListPagerSearch, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun MainDestinationIcon(presentation: MainDestinationPresentation, selected: Boolean, unread: Boolean) {
    MainDestinationIcon(
        presentation = presentation,
        selected = selected,
        unread = unread,
        modifier = Modifier.size(24.dp),
        tint = LocalContentColor.current,
    )
}

@Composable
private fun MainDestinationIcon(
    presentation: MainDestinationPresentation,
    selected: Boolean,
    unread: Boolean,
    modifier: Modifier,
    tint: Color,
) {
    AppBadgedBox(
        badge = {
            if (unread) {
                val badgeColor = AppTheme.colors.secondaryTint
                Canvas(Modifier.offset(4.dp, (-4).dp).size(8.dp)) {
                    drawCircle(color = badgeColor, radius = 4.dp.toPx())
                }
            }
        },
    ) {
        AppIcon(
            // 选中是实心符号、未选中是描边：状态不只靠颜色传达
            imageVector = if (selected) presentation.selectedIcon else presentation.icon,
            contentDescription = presentation.label,
            modifier = modifier,
            tint = tint,
        )
    }
}

private data class MainDestinationPresentation(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

internal class HomeDrawerStateCoordinator {
    private var hasSettledOpen = false

    fun shouldHide(value: AppDrawerValue): Boolean = when (value) {
        AppDrawerValue.Open -> {
            hasSettledOpen = true
            false
        }
        AppDrawerValue.Closed -> {
            val shouldHide = hasSettledOpen
            hasSettledOpen = false
            shouldHide
        }
    }
}

@Composable
private fun HomeDestination.presentation(): MainDestinationPresentation = when (this) {
    HomeDestination.Home -> MainDestinationPresentation(strings.mainNavigationHome, AppIcons.Home, AppIcons.HomeFill)
    HomeDestination.Favorites -> MainDestinationPresentation(strings.favoritesTitle, AppIcons.FavoriteBorder, AppIcons.Favorite)
    HomeDestination.Notifications -> MainDestinationPresentation(
        strings.mainNavigationNotifications,
        AppIcons.Notifications,
        AppIcons.NotificationsFill,
    )
}
