package io.github.vrcmteam.vrcm.presentation.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.lifecycle.LifecycleEffect
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.presentation.compoments.UserStateIcon
import io.github.vrcmteam.vrcm.presentation.compoments.snackBarToastText
import io.github.vrcmteam.vrcm.presentation.extensions.createFailureCallbackDoNavigation
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.getCallbackScreenModel
import io.github.vrcmteam.vrcm.presentation.screens.auth.AuthAnimeScreen
import io.github.vrcmteam.vrcm.presentation.screens.home.tab.FriendListTab
import io.github.vrcmteam.vrcm.presentation.screens.home.tab.FriendLocationTab
import io.github.vrcmteam.vrcm.presentation.screens.profile.ProfileScreen


object HomeScreen : Screen {
    @Composable
    @OptIn(ExperimentalMaterial3Api::class)
    override fun Content() {
        var snackBarToastText by snackBarToastText
        val currentNavigator = currentNavigator
        val homeScreenModel: HomeScreenModel = getCallbackScreenModel(
            createFailureCallbackDoNavigation { AuthAnimeScreen(false) }
        )
        // to ProfileScreen
        val onClickUserIcon = { user: IUser ->
            // 防止多次点击在栈中存在相同key的屏幕报错
            if (currentNavigator.size <= 1) {
                currentNavigator.push(ProfileScreen(user))
            }
        }
        LifecycleEffect(onStarted = (homeScreenModel::ini))

        TabNavigator(FriendLocationTab){
            Scaffold(
                modifier = Modifier.background(Color.LightGray),
                topBar = {
                    TopAppBar(
                        colors = topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.onPrimary,
                            titleContentColor = MaterialTheme.colorScheme.primary,
                        ),
                        navigationIcon = {
                            UserStateIcon(
                                modifier = Modifier
                                    .height(56.dp)
                                    .clip(CircleShape)
                                    .clickable { homeScreenModel.currentUser?.let { onClickUserIcon(it) } },
                                iconUrl = homeScreenModel.currentUser?.currentAvatarThumbnailImageUrl
                                    ?: "",
                                userStatus = homeScreenModel.currentUser?.status
                                    ?: UserStatus.Offline
                            )
                        },
                        title = {
                            Column {
                                Text(
                                    text = homeScreenModel.currentUser?.displayName ?: "",
                                    fontSize = 20.sp
                                )
                                Text(
                                    text = homeScreenModel.currentUser?.statusDescription ?: "",
                                    fontSize = 12.sp
                                )
                            }
                        },
                        actions = {
                            Icon(
                                modifier = Modifier
                                    .padding(6.dp),
                                imageVector = Icons.Rounded.Notifications,
                                contentDescription = "notificationIcon"
                            )
                        }
                    )
                },
                bottomBar = {
                    NavigationBar(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                            .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                            .shadow(
                                elevation = 8.dp,
                                shape = MaterialTheme.shapes.medium,
                            )
                    ) {
                        TabNavigationItem(FriendLocationTab)
                        TabNavigationItem(FriendListTab)
                    }
                },
                floatingActionButton = {
                    Button(onClick = {snackBarToastText = "132132131"}){}
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .padding(top = innerPadding.calculateTopPadding())
                        .fillMaxSize()
                ) {
                    CurrentTab()
                }
            }
        }
    }
}

@Composable
private fun RowScope.TabNavigationItem(tab: Tab){
    val tabNavigator = LocalTabNavigator.current
    NavigationBarItem(
        modifier = Modifier.align(Alignment.CenterVertically),
        icon = { Icon(painter = tab.options.icon!!, contentDescription = tab.options.title) },
        label = { Text(tab.options.title) },
        selected = tabNavigator.current == tab,
        onClick = { tabNavigator.current = tab },
    )
}

