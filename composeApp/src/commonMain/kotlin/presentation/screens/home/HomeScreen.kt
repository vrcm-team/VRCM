package io.github.vrcmteam.vrcm.presentation.screens.home

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.lifecycle.LifecycleEffect
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabNavigator
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.presentation.compoments.UserStateIcon
import io.github.vrcmteam.vrcm.presentation.extensions.createFailureCallbackDoNavigation
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.getCallbackScreenModel
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.screens.auth.AuthAnimeScreen
import io.github.vrcmteam.vrcm.presentation.screens.home.tab.FriendListTab
import io.github.vrcmteam.vrcm.presentation.screens.home.tab.FriendLocationTab
import io.github.vrcmteam.vrcm.presentation.screens.profile.ProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.profile.data.ProfileUserVO
import io.github.vrcmteam.vrcm.presentation.supports.RefreshLazyColumnTab
import io.github.vrcmteam.vrcm.presentation.theme.GameColor


object HomeScreen : Screen {
    @Composable
    @OptIn(ExperimentalMaterial3Api::class)
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
                currentNavigator push ProfileScreen(ProfileUserVO(user))
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
            ) {
                TabNavigationItem(FriendLocationTab)
                TabNavigationItem(FriendListTab)
            }
        }
        TabNavigator(FriendLocationTab){tabNavigator ->
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
                    AnimatedContent(
                        tabNavigator.current
                    ) {
                        tabNavigator.saveableState(it.key) {
                            when (it) {
                                FriendLocationTab -> FriendLocationTab.Content()
                                FriendListTab -> FriendListTab.Content()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.TabNavigationItem(tab: RefreshLazyColumnTab) {
    val tabNavigator = LocalTabNavigator.current
    NavigationBarItem(
        modifier = Modifier.align(Alignment.CenterVertically),
        icon = { Icon(painter = tab.options.icon!!, contentDescription = tab.options.title) },
        label = { Text(tab.options.title) },
        selected = tabNavigator.current == tab,
        onClick = {
            if (tabNavigator.current == tab) {
                tab.toTop()
            }
            tabNavigator.current = tab
        },
        alwaysShowLabel = tabNavigator.current == tab,
        colors = NavigationBarItemDefaults.colors(
            indicatorColor = MaterialTheme.colorScheme.primary,
            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
            unselectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedTextColor = MaterialTheme.colorScheme.onPrimary,
        )
    )
}

