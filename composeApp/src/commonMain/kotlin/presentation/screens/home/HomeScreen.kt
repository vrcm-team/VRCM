package io.github.vrcmteam.vrcm.presentation.screens.home

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import dev.chrisbanes.haze.HazeDefaults.style
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.getAppPlatform
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.presentation.compoments.UserStateIcon
import io.github.vrcmteam.vrcm.presentation.compoments.sharedBoundsBy
import io.github.vrcmteam.vrcm.presentation.compoments.sharedElementBy
import io.github.vrcmteam.vrcm.presentation.extensions.*
import io.github.vrcmteam.vrcm.presentation.screens.auth.AuthAnimeScreen
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationItemData
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.FriendListPager
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.FriendLocationPager
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.SearchListPager
import io.github.vrcmteam.vrcm.presentation.screens.home.sheet.NotificationBottomSheet
import io.github.vrcmteam.vrcm.presentation.screens.home.sheet.SettingsBottomSheet
import io.github.vrcmteam.vrcm.presentation.screens.profile.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.profile.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.supports.Pager
import io.github.vrcmteam.vrcm.presentation.theme.GameColor
import kotlinx.coroutines.launch


object HomeScreen : Screen {

    private val pagerList = listOf(
        FriendLocationPager,
        FriendListPager,
        SearchListPager,
    )

    @ExperimentalSharedTransitionApi
    @Composable
    override fun Content() {
        val currentNavigator = currentNavigator
        val homeScreenModel: HomeScreenModel = koinScreenModel()

        LaunchedEffect(Unit) {
            homeScreenModel.init()
            // 登出时跳到验证页面
            SharedFlowCentre.logout.collect {
                currentNavigator replaceAll AuthAnimeScreen(false)
            }
        }
        // 适配不支持模糊效果的设备，比如低于Android 12的安卓设备
        val supportBlur = getAppPlatform().isSupportBlur
        val hazeState = if (supportBlur) remember { HazeState() } else null
        val pagerState = rememberPagerState { pagerList.size }

        Scaffold(
            contentColor = MaterialTheme.colorScheme.primary,
            topBar = { HomeTopAppBar(hazeState) },
            bottomBar = { HomeBottomBar(pagerList, pagerState, hazeState) },
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .enableIf(supportBlur) { haze(state = hazeState!!) },
                tonalElevation = 2.dp
            ) {
                HorizontalPager(pagerState) {
                    pagerList[it].Content()
                }
            }
        }

    }
}


@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private inline fun Screen.HomeTopAppBar(
    hazeState: HazeState?,
) {
    val homeScreenModel: HomeScreenModel = koinScreenModel()
    val currentUser = homeScreenModel.currentUser
    val refreshNotification = {
        homeScreenModel.refreshAllNotification()
    }
    val notifications = homeScreenModel.friendRequestNotifications + homeScreenModel.notifications
    // to ProfileScreen
    val currentNavigator = currentNavigator
    val onClickUserIcon = { user: IUser ->
        // 防止多次点击在栈中存在相同key的屏幕报错
        if (currentNavigator.size <= 1) {
            currentNavigator push UserProfileScreen(UserProfileVo(user))
        }
    }
    val trustRank = remember(currentUser) { currentUser?.trustRank }
    val rankColor = GameColor.Rank.fromValue(trustRank)
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerLowest
    // 初始化刷新一次
    LaunchedEffect(Unit) {
        refreshNotification()
    }

    val modifier = if (hazeState != null) {
        Modifier.hazeChild(
            state = hazeState,
            style = style(
                backgroundColor = backgroundColor,
            )
        )
    } else {
        Modifier.shadow(2.dp)
    }
    Surface(
        modifier = modifier,
        color = if (hazeState != null) Color.Transparent else backgroundColor,
    ) {
        Row(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .simpleClickable { currentUser?.let { onClickUserIcon(it) } },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UserStateIcon(
                    modifier = Modifier
                        .size(54.dp)
                        .enableIf(currentUser != null) {
                            sharedElementBy("${currentUser!!.id}UserIcon")
                        },
                    iconUrl = currentUser?.currentAvatarThumbnailImageUrl.orEmpty(),
                    userStatus = currentUser?.status
                )
                Column(
                    modifier = Modifier.widthIn(max = 220.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            modifier = Modifier
                                .enableIf(currentUser != null) {
                                    sharedBoundsBy("${currentUser!!.id}UserName")
                                },
                            text = currentUser?.displayName.orEmpty(),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                        Icon(
                            modifier =
                                Modifier
                                    .size(16.dp)
                                    .align(Alignment.CenterVertically)
                                    .enableIf(currentUser != null) {
                                        sharedElementBy("${currentUser!!.id}UserTrustRankIcon")
                                    },
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = "CurrentUserTrustRankIcon",
                            tint = rankColor
                        )
                    }
                    Text(
                        modifier = Modifier
                            .enableIf(currentUser != null) {
                                sharedBoundsBy("${currentUser!!.id}UserStatusDescription")
                            },

                        text = currentUser?.statusDescription?.ifBlank { currentUser.status.value }.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            NotificationActionButton(
                notifications,
                homeScreenModel::refreshAllNotification
            ) { id, type, response ->
                homeScreenModel.responseAllNotification(id, type, response)
            }
            SettingsActionButton()
        }
    }

}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private inline fun HomeBottomBar(
    pagerList: List<Pager>,
    pagerState: PagerState,
    hazeState: HazeState?,
) {
    // 如果没有底部系统手势条，则加12dp
    val bottomPadding = if (getInsetPadding(WindowInsets::getBottom) != 0.dp) 0.dp else 12.dp
    val scope = rememberCoroutineScope()
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerLowest

    val pagerNavigationItems: @Composable RowScope.() -> Unit = {
        pagerList.forEach { pager ->
            val index = pager.index
            val selected = pagerState.currentPage == index
            PagerNavigationItem(
                provider = pager,
                selected = selected,
                onClick = {
                    scope.launch {
                        if (selected) {
                            SharedFlowCentre.toPagerTop.emit(Unit)
                        } else {
                            pagerState.animateScrollToPage(
                                page = index,
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                            )
                        }
                    }
                }
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 28.dp, bottom = bottomPadding)
            .windowInsetsPadding(NavigationBarDefaults.windowInsets.only(WindowInsetsSides.Bottom)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .height(64.dp)
                .run {
                    if (hazeState != null) {
                        hazeChild(
                            state = hazeState,
                            shape = CircleShape,
                            style = style(
                                backgroundColor = backgroundColor,
                            )
                        )
                    } else {
                        shadow(
                            elevation = 2.dp,
                            shape = CircleShape,
                        )
                    }
                },
            color = if (hazeState != null) Color.Transparent else backgroundColor,
        ) {
            Row(
                modifier = Modifier
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                pagerNavigationItems()
            }
        }
    }

}

@Composable
private fun RowScope.PagerNavigationItem(
    provider: Pager,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .simpleClickable(onClick)
    ) {
        Icon(
            modifier = Modifier
                .size(40.dp),
            painter = provider.icon!!,
            contentDescription = provider.title,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SettingsActionButton(
    modifier: Modifier = Modifier,
) {
    var bottomSheetIsVisible by remember { mutableStateOf(false) }
    IconButton(
        modifier = modifier,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.tertiary
        ),
        onClick = { bottomSheetIsVisible = !bottomSheetIsVisible }
    ) {
        Icon(
            painter = rememberVectorPainter(image = Icons.Rounded.Settings),
            contentDescription = "Settings",
        )

    }
    SettingsBottomSheet(
        isVisible = bottomSheetIsVisible,
        onDismissRequest = { bottomSheetIsVisible = false }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationActionButton(
    notifications: List<NotificationItemData>,
    refreshNotification: () -> Unit,
    onResponseNotification: (String, String, NotificationItemData.ActionData) -> Unit,
) {
    // notification
    var bottomSheetIsVisible by remember { mutableStateOf(false) }
    val onClickNotification: () -> Unit = {
        bottomSheetIsVisible = true
    }
    LaunchedEffect(Unit) {
        refreshNotification()
    }
    // 每打开一次刷新一次
    LaunchedEffect(bottomSheetIsVisible) {
        if (bottomSheetIsVisible) {
            refreshNotification()
        }
    }
    IconButton(
        onClick = onClickNotification,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.tertiary
        ),
    ) {
        BadgedBox(
            badge = {
                val primaryColor = MaterialTheme.colorScheme.tertiary
                if (notifications.isNotEmpty()) {
                    Canvas(modifier = Modifier.offset(4.dp, (-4).dp).size(8.dp)) {
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
    NotificationBottomSheet(
        bottomSheetIsVisible = bottomSheetIsVisible,
        notificationList = notifications,
        onDismissRequest = { bottomSheetIsVisible = false },
        onResponseNotification = onResponseNotification
    )
}

