package io.github.vrcmteam.vrcm.presentation.screens.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.lifecycle.LifecycleEffect
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import dev.chrisbanes.haze.HazeDefaults.style
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.getAppPlatform
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.presentation.compoments.UserStateIcon
import io.github.vrcmteam.vrcm.presentation.extensions.*
import io.github.vrcmteam.vrcm.presentation.screens.auth.AuthAnimeScreen
import io.github.vrcmteam.vrcm.presentation.screens.home.data.PagerProvidersState
import io.github.vrcmteam.vrcm.presentation.screens.home.data.createPagerProvidersState
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.FriendListPagerProvider
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.FriendLocationPagerProvider
import io.github.vrcmteam.vrcm.presentation.screens.home.sheet.NotificationBottomSheet
import io.github.vrcmteam.vrcm.presentation.screens.profile.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.profile.data.UserProfileVO
import io.github.vrcmteam.vrcm.presentation.supports.PagerProvider
import io.github.vrcmteam.vrcm.presentation.theme.GameColor
import kotlinx.coroutines.launch


object HomeScreen : Screen {
    @Composable
    @OptIn(ExperimentalFoundationApi::class)
    override fun Content() {
        val currentNavigator = currentNavigator
        val homeScreenModel: HomeScreenModel = getScreenModel()

        LifecycleEffect(onStarted = (homeScreenModel::ini))
        LaunchedEffect(Unit){
            // 报错时跳到验证页面
            SharedFlowCentre.error.collect{
                currentNavigator replaceAll AuthAnimeScreen(false)
            }
        }

        val pagerProvidersState = createPagerProvidersState(
            FriendLocationPagerProvider,
            FriendListPagerProvider
        )
        // 适配不支持模糊效果的设备，比如低于Android 12的安卓设备
        val supportBlur = getAppPlatform().isSupportBlur
        val hazeState = if (supportBlur)remember { HazeState() } else null
        Scaffold(
            contentColor = MaterialTheme.colorScheme.primary,
            topBar = {
                HomeTopAppBar(homeScreenModel,hazeState)
            },
            bottomBar = { HomeBottomBar(pagerProvidersState,hazeState) },
            floatingActionButton = {
//                    Button(onClick = { snackBarToastText = "132132131" }) {}
            }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .enableIf(supportBlur) { haze(state = hazeState!!) },
                contentColor = MaterialTheme.colorScheme.primary,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 16.dp
            ) {
                HorizontalPager(pagerProvidersState.pagerState) {
                    pagerProvidersState.pagers[it]()
                }
            }
        }

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private inline fun HomeTopAppBar(
    homeScreenModel: HomeScreenModel,
    hazeState: HazeState?
) {
    val currentUser = homeScreenModel.currentUser
    val refreshNotification = {
        homeScreenModel.refreshNotifications()
    }
    val notifications = homeScreenModel.notifications
    // to ProfileScreen
    val currentNavigator = currentNavigator
    val onClickUserIcon = { user: IUser ->
        // 防止多次点击在栈中存在相同key的屏幕报错
        if (currentNavigator.size <= 1) {
            currentNavigator push UserProfileScreen(UserProfileVO(user))
        }
    }
    val trustRank = remember(currentUser) { currentUser?.trustRank }
    val rankColor = GameColor.Rank.fromValue(trustRank)
    // notification
    var bottomSheetIsVisible by remember { mutableStateOf(false) }
    val onClickNotification : () -> Unit ={
        bottomSheetIsVisible = true
    }
    // 初始化刷新一次
    LaunchedEffect(Unit){
        refreshNotification()
    }
    // 每打开一次刷新一次
    LaunchedEffect(bottomSheetIsVisible) {
        if (bottomSheetIsVisible) {
            refreshNotification()
        }
    }
    val modifier = if (hazeState != null) {
        Modifier.hazeChild(
                state = hazeState,
                style = style(
                    backgroundColor = MaterialTheme.colorScheme.onPrimary,
                )
            )
    }else{
        Modifier.shadow(2.dp)
    }
    TopAppBar(
        modifier = modifier,
        colors = topAppBarColors(
            containerColor = if (hazeState != null) Color.Transparent else MaterialTheme.colorScheme.onPrimary,
            titleContentColor = MaterialTheme.colorScheme.primary,
            actionIconContentColor = MaterialTheme.colorScheme.primary,
        ),
        navigationIcon = {
            UserStateIcon(
                modifier = Modifier
                    .height(56.dp)
                    .clip(CircleShape)
                    .clickable { currentUser?.let { onClickUserIcon(it) } },
                iconUrl = currentUser?.currentAvatarThumbnailImageUrl
                    ?: "",
                userStatus = currentUser?.status
            )
        },
        title = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        modifier = Modifier
                            .size(16.dp)
                            .align(Alignment.CenterVertically),
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = "CurrentUserTrustRankIcon",
                        tint = rankColor
                    )
                    Text(
                        text = currentUser?.displayName ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                }
                Text(
                    text = currentUser?.statusDescription ?:currentUser?.status?.value?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.inversePrimary,
                    maxLines = 1
                )
            }
        },
        actions = {
            IconButton(
                onClick = onClickNotification
            ){
                BadgedBox(
                    badge = {
                        val primaryColor = MaterialTheme.colorScheme.primary
                        if (notifications.isNotEmpty()){
                            Canvas(modifier = Modifier.size(8.dp)){
                                drawCircle(color = primaryColor, radius = 4.dp.toPx())
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Notifications,
                        contentDescription = "NotificationIcon"
                    )
                }
            }
        }
    )
    NotificationBottomSheet(
        bottomSheetIsVisible = bottomSheetIsVisible,
        notificationList = notifications,
        onDismissRequest = { bottomSheetIsVisible = false },
        onResponseNotification = {id, response ->
            homeScreenModel.responseNotification(id, response)
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private inline fun HomeBottomBar(
    pagerProvidersState: PagerProvidersState,
    hazeState: HazeState?
) {
    // 如果没有底部系统手势条，则加12dp
    val bottomPadding = if (getInsetPadding(WindowInsets::getBottom) != 0.dp) 0.dp else 12.dp
    val scope = rememberCoroutineScope()
    val pagerNavigationItems:@Composable RowScope.() -> Unit = {
        pagerProvidersState.pagerProviders.forEach {
            val index = it.index
            val selected = pagerProvidersState.pagerState.currentPage == index
            PagerNavigationItem(
                provider = it,
                selected = selected
            ) {
                scope.launch{
                    if (selected) {
                        pagerProvidersState.lazyListStates[it.index].animateScrollToFirst()
                    }else{
                        pagerProvidersState.pagerState.animateScrollToPage(page = index,animationSpec =  spring(stiffness = Spring.StiffnessMediumLow))
                    }
                }
            }
        }
    }
    NavigationBar(
        modifier = Modifier
            .padding(start = 12.dp, end = 12.dp, bottom = bottomPadding)
            .windowInsetsPadding(NavigationBarDefaults.windowInsets)
            .run {
                if (hazeState != null) {
                    hazeChild(
                        state = hazeState,
                        shape = MaterialTheme.shapes.extraLarge,
                        style = style(
                            backgroundColor = MaterialTheme.colorScheme.onPrimary,
                        )
                    )
                } else {
                    shadow(
                        elevation = 2.dp,
                        shape = MaterialTheme.shapes.extraLarge,
                    )
                }
            },
        containerColor = if (hazeState != null) Color.Transparent else MaterialTheme.colorScheme.onPrimary,
        contentColor = MaterialTheme.colorScheme.primary,
        content = pagerNavigationItems
    )
}

@Composable
private fun RowScope.PagerNavigationItem(provider: PagerProvider, selected: Boolean, onClick: () -> Unit) {
    NavigationBarItem(
        modifier = Modifier.align(Alignment.CenterVertically),
        icon = {
            Icon(
                modifier = Modifier.size(32.dp),
                painter = provider.icon!!,
                contentDescription = provider.title
            )
        },
            label = { Text(provider.title) },
        selected = selected,
        onClick = onClick,
        alwaysShowLabel = selected,
        colors = NavigationBarItemDefaults.colors(
            indicatorColor = MaterialTheme.colorScheme.primary,
            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
            unselectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedTextColor = MaterialTheme.colorScheme.onPrimary,
        )
    )
}

