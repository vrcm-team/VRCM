package io.github.vrcmteam.vrcm.presentation.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.navigation.AppListRoute
import io.github.vrcmteam.vrcm.presentation.navigation.rememberContainerTransformToken
import io.github.vrcmteam.vrcm.presentation.adaptive.AppWindowWidthClass
import io.github.vrcmteam.vrcm.presentation.adaptive.LocalAppWindowWidthClass
import org.koin.compose.viewmodel.koinViewModel
import dev.chrisbanes.haze.HazeDefaults.style
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.getAppPlatform
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.auth.data.CurrentUserData
import io.github.vrcmteam.vrcm.presentation.animations.DefaultBoundsTransform
import io.github.vrcmteam.vrcm.presentation.animations.IconBoundsTransform
import io.github.vrcmteam.vrcm.presentation.compoments.*
import androidx.compose.ui.platform.testTag
import io.github.vrcmteam.vrcm.presentation.extensions.*
import io.github.vrcmteam.vrcm.presentation.screens.meetup.MeetupCardDisplayRoute
import io.github.vrcmteam.vrcm.presentation.screens.meetup.MeetupCardResizeMode
import io.github.vrcmteam.vrcm.presentation.screens.meetup.MeetupCardEditorRoute
import io.github.vrcmteam.vrcm.presentation.screens.meetup.meetupCardSharedKey
import io.github.vrcmteam.vrcm.presentation.screens.auth.AuthAnimeScreen
import io.github.vrcmteam.vrcm.presentation.screens.home.dialog.NotificationDialog
import io.github.vrcmteam.vrcm.presentation.screens.home.dialog.UserStatusDialog
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.FriendListPager
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.FriendLocationPager
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.SearchListPager
import io.github.vrcmteam.vrcm.presentation.screens.home.sheet.SettingsBottomSheet
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.presentation.supports.Pager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable


@Serializable
object HomeScreen : AppListRoute {

    private val pagerList = listOf(
        FriendLocationPager,
        FriendListPager,
        SearchListPager,
    )

    @ExperimentalSharedTransitionApi
    @Composable
    override fun Content() {
        val currentNavigator = currentNavigator
        val homeScreenModel: HomeScreenModel = koinViewModel()

        LaunchedEffect(Unit) {
            // 登出时跳到验证页面
            SharedFlowCentre.logout.collect {
                // 为了切换头像的共享元素动画
                homeScreenModel.currentUser = null
                currentNavigator replaceAll AuthAnimeScreen(false)
            }
        }
        // 适配不支持模糊效果的设备，比如低于Android 12的安卓设备
        val supportBlur = getAppPlatform().isSupportBlur
        val hazeState = if (supportBlur) remember { HazeState() } else null
        val windowWidthClass = LocalAppWindowWidthClass.current
        val pagerState = rememberPagerState(
            initialPage = homeScreenModel.selectedPagerIndex,
        ) { pagerList.size }
        val useNavigationRail = windowWidthClass != AppWindowWidthClass.Compact

        LaunchedEffect(pagerState, windowWidthClass) {
            pagerState.scrollToPage(homeScreenModel.selectedPagerIndex)
            snapshotFlow { pagerState.settledPage }.collect { page ->
                homeScreenModel.onPagerSettled(page)
            }
        }

        Scaffold(
            contentColor = MaterialTheme.colorScheme.primary,
            topBar = { HomeTopAppBar(hazeState) },
            bottomBar = {
                if (!useNavigationRail) {
                    HomeBottomBar(pagerList, pagerState, hazeState)
                }
            },
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .enableIf(supportBlur) { hazeSource(state = hazeState!!) },
                tonalElevation = 2.dp
            ) {
                Row {
                    if (useNavigationRail) {
                        HomeNavigationRail(pagerList, pagerState)
                    }
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f),
                    ) {
                        val pager = pagerList[it]
                        CompositionLocalProvider(LocalSharedSuffixKey provides pager.title) {
                            pager.Content()
                        }
                    }
                }
            }
        }

    }
}


@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private inline fun AppListRoute.HomeTopAppBar(
    hazeState: HazeState?,
) {
    val homeScreenModel: HomeScreenModel = koinViewModel()
    val currentUser = homeScreenModel.currentUser
    val hasNotifications by remember { derivedStateOf { (homeScreenModel.friendRequestNotifications + homeScreenModel.notifications).isNotEmpty() } }
    // to ProfileScreen
    val currentNavigator = currentNavigator
    val homeUserId = homeScreenModel.userId
    val sharedSuffixKey = rememberContainerTransformToken(
        "home-user:$homeUserId",
    ) ?: LocalSharedSuffixKey.current
    var currentDialog by LocationDialogContent.current
    val onClickUserIcon = { user: IUser ->
        currentNavigator push UserProfileScreen(UserProfileVo(user), sharedSuffixKey)
    }
    // 长按进入身份牌；当前已在同一 owner 的身份牌路由时忽略，防重复入栈。
    val onLongClickUserIcon = onLongClickUserIcon@{
        val last = currentNavigator.lastItem
        val alreadyOpen = (last as? MeetupCardDisplayRoute)?.ownerUserId == homeUserId ||
            (last as? MeetupCardEditorRoute)?.ownerUserId == homeUserId
        if (alreadyOpen) return@onLongClickUserIcon
        currentNavigator push homeScreenModel.meetupCardStartRoute()
    }
    var statusVisibility by remember { mutableStateOf(true) }
    val onClickShowStatusDialog: (CurrentUserData) -> Unit = {
        statusVisibility = false
        currentDialog = UserStatusDialog(it) {
            currentDialog = null
            statusVisibility = true
        }
    }
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val modifier = if (hazeState != null) {
        Modifier.hazeEffect(
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
                .padding(top = getInsetPadding(WindowInsets::getTop))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 头像+名字这一组就是"我的身份"，铭牌是它的全屏版：以整组作为
            // 共享主体，形变比从 54dp 头像炸开更平缓，也不必额外包一层节点。
            Row(
                modifier = Modifier
                    .sharedBoundsBy(
                        key = meetupCardSharedKey(homeUserId),
                        useSuffixKey = false,
                        resizeMode = MeetupCardResizeMode,
                    )
                    .clip(MaterialTheme.shapes.medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .simpleCombinedClickable(
                            onLongClick = { currentUser?.let { onLongClickUserIcon() } },
                            onClick = { currentUser?.let { onClickUserIcon(it) } },
                        )
                        .testTag("home-user-avatar")
                        .sharedBoundsBy(
                            key = "${homeUserId}UserIcon",
                            suffixKey = AuthHomeSharedSuffixKey,
                            boundsTransform = IconBoundsTransform,
                        )
                        .size(54.dp),
                ) {
                    UserStateIcon(
                        modifier = Modifier
                            .sharedBoundsBy(
                                key = "${homeUserId}UserIcon",
                                suffixKey = sharedSuffixKey,
                                boundsTransform = if (currentUser != null) {
                                    DefaultBoundsTransform
                                } else {
                                    IconBoundsTransform
                                },
                            )
                            .fillMaxSize(),
                        iconUrl = currentUser?.iconUrl ?: homeScreenModel.iconUrl,
                        cachedPlaceholderKey = homeScreenModel.iconUrl,
                    )
                }
                Column(
                    modifier = Modifier.widthIn(max = 220.dp)
                        .simpleClickable { currentUser?.let { onClickShowStatusDialog(currentUser) } },
                    horizontalAlignment = Alignment.Start,
                ) {
                    UserInfoRow(
                        iconSize = 16.dp,
                        style = MaterialTheme.typography.titleMedium,
                        user = currentUser,
                        sharedUserId = homeUserId,
                        sharedSuffixKey = sharedSuffixKey,
                        pronouns = currentUser?.pronouns
                    )
                    AnimatedVisibility(statusVisibility){
                        UserStatusRow(
                            iconSize = 8.dp,
                            style = MaterialTheme.typography.labelMedium,
                            user = currentUser,
                            animatedVisibilityScope = this,
                            sharedUserId = homeUserId,
                            sharedSuffixKey = sharedSuffixKey,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            NotificationActionButton(hasNotifications)
            SettingsActionButton()
        }
    }

}

@Composable
private fun HomeNavigationRail(
    pagerList: List<Pager>,
    pagerState: PagerState,
) {
    val scope = rememberCoroutineScope()
    NavigationRail(
        modifier = Modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Spacer(Modifier.weight(1f))
        pagerList.forEach { pager ->
            val selected = pagerState.currentPage == pager.index
            NavigationRailItem(
                selected = selected,
                onClick = { scope.selectPager(pager, pagerState, selected) },
                icon = {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        painter = pager.icon!!,
                        contentDescription = pager.title,
                    )
                },
            )
        }
        Spacer(Modifier.weight(1f))
    }
}


@Composable
private inline fun HomeBottomBar(
    pagerList: List<Pager>,
    pagerState: PagerState,
    hazeState: HazeState?,
) {
    // 如果没有底部系统手势条，则加12dp
    val bottomPadding = getInsetPadding(12, WindowInsets::getBottom)
    val scope = rememberCoroutineScope()
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerLowest

    val pagerNavigationItems: @Composable RowScope.() -> Unit = {
        pagerList.forEach { pager ->
            val selected = pagerState.currentPage == pager.index
            PagerNavigationItem(
                provider = pager,
                selected = selected,
                onClick = { scope.selectPager(pager, pagerState, selected) }
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 28.dp, bottom = bottomPadding),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .height(64.dp)
                .run {
                    if (hazeState != null) {
                        clip(CircleShape)
                            .hazeEffect(
                                state = hazeState,
                                style = style(
                                    backgroundColor = backgroundColor
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

private fun CoroutineScope.selectPager(
    pager: Pager,
    pagerState: PagerState,
    selected: Boolean,
) {
    launch {
        if (selected) {
            SharedFlowCentre.toPagerTop.emit(Unit)
        } else {
            pagerState.animateScrollToPage(
                page = pager.index,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            )
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
            painter = rememberVectorPainter(image = AppIcons.Settings),
            contentDescription = "Settings",
        )

    }
    SettingsBottomSheet(
        isVisible = bottomSheetIsVisible,
        onDismissRequest = { bottomSheetIsVisible = false }
    )
}

@Composable
fun NotificationActionButton(
    hasNotifications: Boolean,
) {
    val (_, onClickNotification) = LocationDialogContent.current
    IconButton(
        onClick = { onClickNotification(NotificationDialog) },
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.tertiary
        ),
    ) {
        BadgedBox(
            badge = {
                val primaryColor = MaterialTheme.colorScheme.tertiary
                if (hasNotifications) {
                    Canvas(modifier = Modifier.offset(4.dp, (-4).dp).size(8.dp)) {
                        drawCircle(color = primaryColor, radius = 4.dp.toPx())
                    }
                }
            }
        ) {
            Icon(
                imageVector = AppIcons.Notifications,
                contentDescription = "NotificationIcon"
            )
        }
    }
}
