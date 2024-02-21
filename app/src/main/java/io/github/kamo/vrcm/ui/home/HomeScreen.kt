package io.github.kamo.vrcm.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kamo.vrcm.MainRouteEnum
import io.github.kamo.vrcm.data.api.UserStatus
import io.github.kamo.vrcm.ui.home.page.LocationsPage
import io.github.kamo.vrcm.ui.theme.GameColor
import io.github.kamo.vrcm.ui.util.AImage
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
    LaunchedEffect(Unit) {
        homeViewModel.ini()
        pullToRefreshState.startRefresh()
    }

    val onErrorToAuthPage = remember { { onNavigate(MainRouteEnum.AuthAnime, true, listOf(true)) } }
    Scaffold(
        modifier = Modifier.background(Color.LightGray),
        topBar = {
            TopAppBar(
                colors = topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.onPrimary,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                navigationIcon = {
                    Row(
                        modifier = Modifier
                            .padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        UserStateIcon(
                            modifier = Modifier.size(40.dp),
                            iconUrl = homeViewModel.currentUser?.currentAvatarThumbnailImageUrl
                                ?: "",
                            userStatus = homeViewModel.currentUser?.status
                                ?: UserStatus.Offline
                        )

                    }

                },
                title = {
                    Column(
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = homeViewModel.currentUser?.displayName ?: "",
                            fontSize = 16.sp
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
                            .size(32.dp)
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
                    text = "Bottom app bar",
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { homeViewModel.onErrorMessageChange("21321321312") }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxWidth()
                .padding(innerPadding)
        ) {
            LocationsPage(
                friendLocationMap = homeViewModel.friendLocationMap,
                pullToRefreshState = pullToRefreshState,
                onRefreshLocations = {
                    homeViewModel.refresh(onErrorToAuthPage)
                    pullToRefreshState.endRefresh()
                }
            )
            SnackBarToast(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.BottomCenter),
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
    userStatus: UserStatus
) {
    Column(
        modifier = Modifier.width(60.dp),
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

@Preview
@Composable
fun FriedScreen() {
    Box(Modifier.fillMaxSize()) {
        val height = (LocalConfiguration.current.screenHeightDp / 4)
        LocalConfiguration.current.smallestScreenWidthDp

        AImage(
            modifier = Modifier.height(height.dp),
            imageUrl = null,
            color = Color.Green
        )
        Column {
            Spacer(modifier = Modifier.height((height - 40).dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .padding(horizontal = 20.dp)
                    .align(Alignment.CenterHorizontally)

            ) {
                UserStateIcon(
                    modifier = Modifier
                        .border(3.dp, GameColor.Level.Known, CircleShape),
                    iconUrl = "https://api.vrchat.cloud/api/1/image/file_927f6134-ab99-4003-8039-8150f7a4fc17/3/256",
                    userStatus = UserStatus.Online
                )
                Column {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(text = "IKUTUS")
                    Text(text = "IKUTUS")
                }
            }
        }
    }
}
