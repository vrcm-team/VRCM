package io.github.kamo.vrcm.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kamo.vrcm.MainRouteEnum
import io.github.kamo.vrcm.data.api.UserStatus
import io.github.kamo.vrcm.ui.home.page.LocationsPage
import io.github.kamo.vrcm.ui.theme.MediumRoundedShape
import io.github.kamo.vrcm.ui.util.SnackBarToast
import io.github.kamo.vrcm.ui.util.UserStateIcon
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Home(
    homeViewModel: HomeViewModel = koinViewModel(),
    onNavigate: (MainRouteEnum, Boolean, List<Any>) -> Unit
) {
    val pullToRefreshState = rememberPullToRefreshState()
    val onErrorToAuthPage = remember { { onNavigate(MainRouteEnum.AuthAnime, true, listOf(true)) } }

    LaunchedEffect(Unit) {
        homeViewModel.ini()
    }
    val onClickUserIcon = remember {
        { friendId: String ->
            onNavigate(
                MainRouteEnum.Profile,
                false,
                listOf(friendId)
            )
        }
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
                            .clickable { homeViewModel.currentUser?.let { onClickUserIcon(it.id) } },
                        iconUrl = homeViewModel.currentUser?.currentAvatarThumbnailImageUrl
                            ?: "",
                        userStatus = homeViewModel.currentUser?.status
                            ?: UserStatus.Offline
                    )
                },
                title = {
                    Column {
                        Text(
                            text = homeViewModel.currentUser?.displayName ?: "",
                            fontSize = 20.sp
                        )
                        Text(
                            text = homeViewModel.currentUser?.statusDescription ?: "",
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
                    homeViewModel.onErrorMessageChange("Hello World")
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
                friendLocationMap = homeViewModel.friendLocationMap,
                pullToRefreshState = pullToRefreshState,
                onClickUserIcon = onClickUserIcon,
                onRefreshLocations = {
                    homeViewModel.refresh(onErrorToAuthPage)
                    pullToRefreshState.endRefresh()
                }
            )
            SnackBarToast(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                text = homeViewModel.errorMessage,
                onEffect = { homeViewModel.onErrorMessageChange("") }
            )
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



