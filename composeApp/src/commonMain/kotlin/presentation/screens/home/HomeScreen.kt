package io.github.vrcmteam.vrcm.presentation.screens.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.lifecycle.LifecycleEffect
import cafe.adriel.voyager.core.screen.Screen
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.presentation.compoments.UserStateIcon
import io.github.vrcmteam.vrcm.presentation.extensions.animateScrollToFirst
import io.github.vrcmteam.vrcm.presentation.extensions.createFailureCallbackDoNavigation
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.getCallbackScreenModel
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.screens.auth.AuthAnimeScreen
import io.github.vrcmteam.vrcm.presentation.screens.home.data.createPagerProvidersState
import io.github.vrcmteam.vrcm.presentation.screens.home.tab.FriendListPagerProvider
import io.github.vrcmteam.vrcm.presentation.screens.home.tab.FriendLocationPagerProvider
import io.github.vrcmteam.vrcm.presentation.screens.profile.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.profile.data.UserProfileVO
import io.github.vrcmteam.vrcm.presentation.supports.PagerProvider
import io.github.vrcmteam.vrcm.presentation.theme.GameColor
import kotlinx.coroutines.launch


object HomeScreen : Screen {
    @Composable
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
    override fun Content() {
//        var snackBarToastText by snackBarToastText
        val currentNavigator = currentNavigator
        val homeScreenModel: HomeScreenModel = getCallbackScreenModel(
            createFailureCallbackDoNavigation { AuthAnimeScreen(false) }
        )
        val currentUser = homeScreenModel.currentUser
        // to ProfileScreen
        val onClickUserIcon = { user: IUser ->
            // 防止多次点击在栈中存在相同key的屏幕报错
            if (currentNavigator.size <= 1) {
                currentNavigator push UserProfileScreen(UserProfileVO(user))
            }
        }
        LifecycleEffect(onStarted = (homeScreenModel::ini))

        val trustRank = remember(currentUser) { currentUser?.trustRank }
        val rankColor = GameColor.Rank.fromValue(trustRank)
        val topBar = @Composable {
            TopAppBar(
                modifier = Modifier.shadow(2.dp),
                colors = topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.onPrimary,
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
                            modifier = Modifier.alpha(0.6f),
                            text = currentUser?.statusDescription ?:currentUser?.status?.value?: "",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }
                },
                actions = {
                    IconButton(
                        modifier = Modifier
                            .padding(6.dp),
                        onClick = {}
                    ){
                        Icon(
                            imageVector = Icons.Rounded.Notifications,
                            contentDescription = "NotificationIcon"
                        )
                    }
                }
            )
        }

        val pagerProvidersState = createPagerProvidersState(
            FriendLocationPagerProvider,
            FriendListPagerProvider
        )
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
        // 如果没有底部系统手势条，则加12dp
        val bottomPadding = if (getInsetPadding(WindowInsets::getBottom) != 0.dp) 0.dp else 12.dp
        val bottomBar = @Composable {
            NavigationBar(
                modifier = Modifier
                    .padding(start = 12.dp, end = 12.dp, bottom = bottomPadding)
                    .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                    .shadow(
                        elevation = 2.dp,
                        shape = MaterialTheme.shapes.extraLarge,
                    ),
                containerColor = MaterialTheme.colorScheme.onPrimary,
                contentColor = MaterialTheme.colorScheme.primary,
                content = pagerNavigationItems
            )
        }

        Scaffold(
            contentColor = MaterialTheme.colorScheme.primary,
            topBar = topBar,
            bottomBar = bottomBar,
            floatingActionButton = {
//                    Button(onClick = { snackBarToastText = "132132131" }) {}
            }
        ) { innerPadding ->
            Surface(
                modifier = Modifier
                    .padding(top = innerPadding.calculateTopPadding())
                    .fillMaxSize(),
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

