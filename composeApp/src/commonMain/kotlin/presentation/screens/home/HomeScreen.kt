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
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
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
import io.github.vrcmteam.vrcm.presentation.extensions.*
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
                // 为了切换头像的共享元素动画
                homeScreenModel.currentUser = null
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
                    .enableIf(supportBlur) { hazeSource(state = hazeState!!) },
                tonalElevation = 2.dp
            ) {
                HorizontalPager(pagerState) {
                    val pager = pagerList[it]
                    CompositionLocalProvider(LocalSharedSuffixKey provides pager.title) {
                        pager.Content()
                    }
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
    val hasNotifications by remember { derivedStateOf { (homeScreenModel.friendRequestNotifications + homeScreenModel.notifications).isNotEmpty() } }
    // to ProfileScreen
    val currentNavigator = currentNavigator
    val sharedSuffixKey = LocalSharedSuffixKey.current
    var currentDialog by LocationDialogContent.current
    val onClickUserIcon = { user: IUser ->
        // 防止多次点击在栈中存在相同key的屏幕报错
        if (currentNavigator.size <= 1) {
            currentNavigator push UserProfileScreen(UserProfileVo(user), sharedSuffixKey)
        }
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
    // 初始化刷新一次
    LaunchedEffect(Unit) {
        homeScreenModel.refreshAllNotification()
    }

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
            Row(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UserStateIcon(
                    modifier = Modifier.simpleClickable { currentUser?.let { onClickUserIcon(it) } }
                        .sharedBoundsBy(
                            key = "${currentUser?.id ?: homeScreenModel.userId}UserIcon",
                            boundsTransform = if (currentUser != null) DefaultBoundsTransform else IconBoundsTransform
                        )
                        .size(54.dp),
                    iconUrl = currentUser?.iconUrl ?: homeScreenModel.iconUrl
                )
                Column(
                    modifier = Modifier.widthIn(max = 220.dp)
                        .simpleClickable { currentUser?.let { onClickShowStatusDialog(currentUser) } },
                    horizontalAlignment = Alignment.Start,
                ) {
                    UserInfoRow(
                        iconSize = 16.dp,
                        style = MaterialTheme.typography.titleMedium,
                        user = currentUser,
                    )
                    AnimatedVisibility(statusVisibility){
                        UserStatusRow(
                            iconSize = 8.dp,
                            style = MaterialTheme.typography.labelMedium,
                            user = currentUser,
                            animatedVisibilityScope = this
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            NotificationActionButton(
                hasNotifications,
                homeScreenModel::refreshAllNotification
            )
            SettingsActionButton()
        }
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
inline fun NotificationActionButton(
    hasNotifications: Boolean,
    crossinline refreshNotification: () -> Unit,
) {
    val (_, onClickNotification) = LocationDialogContent.current
    LaunchedEffect(Unit) {
        refreshNotification()
    }
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

