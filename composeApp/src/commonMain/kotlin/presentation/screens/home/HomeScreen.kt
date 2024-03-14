package io.github.vrcmteam.vrcm.presentation.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.lifecycle.LifecycleEffect
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.stack.StackEvent
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.presentation.compoments.SnackBarToast
import io.github.vrcmteam.vrcm.presentation.compoments.UserStateIcon
import io.github.vrcmteam.vrcm.presentation.screens.auth.AuthAnimeScreen
import io.github.vrcmteam.vrcm.presentation.screens.home.page.LocationsPage
import io.github.vrcmteam.vrcm.presentation.screens.profile.ProfileScreen
import io.github.vrcmteam.vrcm.presentation.theme.MediumRoundedShape


object HomeScreen : Screen {
    @Composable
    @OptIn(ExperimentalMaterial3Api::class)
    override fun Content() {
        val homeScreenModel: HomeScreenModel = getScreenModel()
        val pullToRefreshState = rememberPullToRefreshState()
        val currentNavigator = LocalNavigator.currentOrThrow
        val onErrorToAuthPage =  {
            currentNavigator.replace(AuthAnimeScreen(false))
        }
        // to ProfileScreen
        val onClickUserIcon = { user: IUser ->
            currentNavigator.push(ProfileScreen(user.id))
        }
        if (currentNavigator.lastEvent == StackEvent.Replace){
            LifecycleEffect(onStarted = {
                homeScreenModel.ini(onErrorToAuthPage)
                pullToRefreshState.startRefresh()
            })
        }
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
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.onPrimary,
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onErrorToAuthPage() },
                        textAlign = TextAlign.Center,
                        text = "Return to Auth Page",
                    )
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    modifier = Modifier
                        .blur(10.dp)
                        .alpha(0.5f),
                    onClick = {
                        homeScreenModel.onErrorMessageChange("Hello World")
                    }
                ) {
                    Icon(
                        Icons.Default.Add, contentDescription = "Add"
                    )
                }
            }
        ) { innerPadding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                LocationsPage(
                    friendLocationMap = homeScreenModel.friendLocationMap,
                    pullToRefreshState = pullToRefreshState,
                    onClickUserIcon = onClickUserIcon,
                    onRefreshLocations = {
                        homeScreenModel.refresh(onErrorToAuthPage)
                        pullToRefreshState.endRefresh()
                    }
                )
                SnackBarToast(
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.TopCenter),
                    text = homeScreenModel.errorMessage,
                    onEffect = { homeScreenModel.onErrorMessageChange("") }
                )
            }
        }
    }

}


@Composable
fun LocationFriend(
    iconUrl: String,
    name: String,
    userStatus: UserStatus,
    onClickUserIcon: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(60.dp)
            .clip(MediumRoundedShape)
            .clickable { onClickUserIcon() },
        verticalArrangement = Arrangement.Center
    ) {
        UserStateIcon(
            modifier = Modifier.fillMaxSize(),
            iconUrl = iconUrl,
            userStatus = userStatus
        )
        Text(
            modifier = Modifier.fillMaxSize(),
            text = name,
            maxLines = 1,
            textAlign = TextAlign.Center,
            fontSize = 12.sp
        )
    }
}

