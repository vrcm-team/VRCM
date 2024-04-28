package io.github.vrcmteam.vrcm.presentation.screens.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.rememberVectorPainter
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
import io.github.vrcmteam.vrcm.presentation.extensions.animateScrollToFirst
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.enableIf
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.extensions.isSupportBlur
import io.github.vrcmteam.vrcm.presentation.extensions.simpleClickable
import io.github.vrcmteam.vrcm.presentation.screens.auth.AuthAnimeScreen
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.FriendListPagerProvider
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.FriendLocationPagerProvider
import io.github.vrcmteam.vrcm.presentation.screens.home.sheet.NotificationBottomSheet
import io.github.vrcmteam.vrcm.presentation.screens.home.sheet.SettingsBottomSheet
import io.github.vrcmteam.vrcm.presentation.screens.profile.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.profile.data.UserProfileVO
import io.github.vrcmteam.vrcm.presentation.supports.PagerProvider
import io.github.vrcmteam.vrcm.presentation.theme.GameColor
import kotlinx.coroutines.launch


object HomeScreen : Screen {

    private val pagerList = listOf(
        FriendLocationPagerProvider,
        FriendListPagerProvider,
    )

    @Composable
    @OptIn(ExperimentalFoundationApi::class)
    override fun Content() {
        val currentNavigator = currentNavigator
        val homeScreenModel: HomeScreenModel = getScreenModel()

        LifecycleEffect(onStarted = (homeScreenModel::ini))
        LaunchedEffect(Unit) {
            // 报错时跳到验证页面
            SharedFlowCentre.error.collect {
                currentNavigator replaceAll AuthAnimeScreen(false)
            }
        }
        // 为了控制点击BottomBar的图标自动回到列表顶部功能,把LazyListState在这里提前声明
        val pagerProvidersLazyListState = pagerList.map { it to rememberLazyListState() }
        // 适配不支持模糊效果的设备，比如低于Android 12的安卓设备
        val supportBlur = getAppPlatform().isSupportBlur
        val hazeState = if (supportBlur) remember { HazeState() } else null
        val pagerState = rememberPagerState { pagerProvidersLazyListState.size }

        Scaffold(
            contentColor = MaterialTheme.colorScheme.primary,
            topBar = { HomeTopAppBar(homeScreenModel, hazeState) },
            bottomBar = { HomeBottomBar(pagerProvidersLazyListState, pagerState, hazeState) },
            floatingActionButton = {
                SettingsActionButton()
            }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .enableIf(supportBlur) { haze(state = hazeState!!) },
                tonalElevation = 2.dp
            ) {
                HorizontalPager(pagerState) {
                    val (pager, lazyListState) = pagerProvidersLazyListState[it]
                    pager.Content(lazyListState)
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
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerLowest
    // notification
    var bottomSheetIsVisible by remember { mutableStateOf(false) }
    val onClickNotification: () -> Unit = {
        bottomSheetIsVisible = true
    }
    // 初始化刷新一次
    LaunchedEffect(Unit) {
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
                        .size(54.dp),
                    iconUrl = currentUser?.currentAvatarThumbnailImageUrl
                        ?: "",
                    userStatus = currentUser?.status
                )
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = currentUser?.displayName ?: "",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                        Icon(
                            modifier = Modifier
                                .size(16.dp)
                                .align(Alignment.CenterVertically),
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = "CurrentUserTrustRankIcon",
                            tint = rankColor
                        )
                    }
                    Text(
                        text = currentUser?.statusDescription ?: currentUser?.status?.value
                        ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1
                    )
                }
            }


            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = onClickNotification
            ) {
                BadgedBox(
                    badge = {
                        val primaryColor = MaterialTheme.colorScheme.primary
                        if (notifications.isNotEmpty()) {
                            Canvas(modifier = Modifier.size(8.dp)) {
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
    }

    NotificationBottomSheet(
        bottomSheetIsVisible = bottomSheetIsVisible,
        notificationList = notifications,
        onDismissRequest = { bottomSheetIsVisible = false },
        onResponseNotification = { id, response ->
            homeScreenModel.responseNotification(id, response)
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private inline fun HomeBottomBar(
    pagerProvidersState: List<Pair<PagerProvider, LazyListState>>,
    pagerState: PagerState,
    hazeState: HazeState?
) {
    // 如果没有底部系统手势条，则加12dp
    val bottomPadding = if (getInsetPadding(WindowInsets::getBottom) != 0.dp) 0.dp else 12.dp
    val scope = rememberCoroutineScope()
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerLowest

    val pagerNavigationItems: @Composable RowScope.() -> Unit = {
        pagerProvidersState.forEach {
            val (pager, lazyListState) = it
            val index = pager.index
            val selected = pagerState.currentPage == index
            PagerNavigationItem(
                provider = pager,
                selected = selected,
                onClick = {
                    scope.launch {
                        if (selected) {
                            lazyListState.animateScrollToFirst()
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
    provider: PagerProvider,
    selected: Boolean,
    onClick: () -> Unit
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
private fun SettingsActionButton(
) {
    var bottomSheetIsVisible by remember { mutableStateOf(false) }
    IconButton(
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
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

