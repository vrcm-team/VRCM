package io.github.kamo.vrcm.ui.home

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
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
                    UserStateIcon(
                        modifier = Modifier.height(56.dp),
                        iconUrl = homeViewModel.currentUser?.currentAvatarThumbnailImageUrl
                            ?: "",
                        userStatus = homeViewModel.currentUser?.status
                            ?: UserStatus.Offline
                    )
                },
                title = {
                    Column(
                    ) {
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
    val scrollState = rememberScrollState()
    val nestedScrollInteropConnection: NestedScrollConnection =
        rememberNestedScrollInteropConnection()


    Surface(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollInteropConnection)
    ) {
        val height = (LocalConfiguration.current.screenHeightDp / 4)
        val offset = (scrollState.value / 2)
        val offsetDp = with(LocalDensity.current) { offset.toDp() }
        val blurDp =
            with(LocalDensity.current) { ((scrollState.value / height.toFloat()) * 20).toDp() }
        val offsetDp2 =
            with(LocalDensity.current) { ((scrollState.value / height.toFloat()) * (height - 50)).toDp() }
        println(scrollState.value / height.toFloat())
        Box(
            Modifier
                .verticalScroll(scrollState)
                .height(2000.dp)
                .fillMaxSize()
                .padding(10.dp)
        ) {
            AImage(
                modifier = Modifier
                    .heightIn(0.dp, max = height.dp)
                    .fillMaxWidth()
                    // TODO: Update to use offset to avoid recomposition
                    .padding(top = offsetDp)
                    .clip(RoundedCornerShape(12.dp))
                    .blur(blurDp),
                imageUrl = "https://api.vrchat.cloud/api/1/image/file_927f6134-ab99-4003-8039-8150f7a4fc17/3/256",
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .padding(start = 30.dp, end = 30.dp)
            ) {
                UserStateIcon(
                    modifier = Modifier
                        .border(3.dp, Color.White, CircleShape),
                    iconUrl = "https://api.vrchat.cloud/api/1/image/file_927f6134-ab99-4003-8039-8150f7a4fc17/3/256",
                    userStatus = UserStatus.Online
                )
                Column {
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        tint = GameColor.Level.Known,
                        contentDescription = ""
                    )
                    Text(text = "IKUTUS")
                    Text(text = "IKUTUS")
                }
            }
        }


    }

}

