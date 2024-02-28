package io.github.vrcmteam.vrcm.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import io.github.vrcmteam.vrcm.data.api.UserStatus
import io.github.vrcmteam.vrcm.screens.theme.MediumRoundedShape
import io.github.vrcmteam.vrcm.screens.util.UserStateIcon

class HomeScreen : Screen {
    @Composable
    override fun Content() {
        val profileScreenModel: HomeScreenModel = getScreenModel()
        Home(profileScreenModel)
    }

}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Home(
    homeScreenModel: HomeScreenModel,
//    onNavigate: (MainRouteEnum, Boolean, List<Any>) -> Unit
) {
//    val pullToRefreshState = rememberPullToRefreshState()
//    val onErrorToAuthPage = remember { { onNavigate(MainRouteEnum.AuthAnime, true, listOf(true)) } }
//
//    LaunchedEffect(Unit) {
//        homeScreenModel.ini()
//    }
//    val onClickUserIcon = remember {
//        // TODO :
//        { friendId: String ->
//            onNavigate(
//                MainRouteEnum.Profile,
//                false,
//                listOf(friendId)
//            )
//        }
//    }
//
//    Scaffold(
//        modifier = Modifier.background(Color.LightGray),
//        topBar = {
//            TopAppBar(
//                colors = topAppBarColors(
//                    containerColor = MaterialTheme.colorScheme.onPrimary,
//                    titleContentColor = MaterialTheme.colorScheme.primary,
//                ),
//                navigationIcon = {
//                    UserStateIcon(
//                        modifier = Modifier
//                            .height(56.dp)
//                            .clip(CircleShape)
//                            .clickable { homeScreenModel.currentUser?.let { onClickUserIcon(it.id) } },
//                        iconUrl = homeScreenModel.currentUser?.currentAvatarThumbnailImageUrl
//                            ?: "",
//                        userStatus = homeScreenModel.currentUser?.status
//                            ?: UserStatus.Offline
//                    )
//                },
//                title = {
//                    Column {
//                        Text(
//                            text = homeScreenModel.currentUser?.displayName ?: "",
//                            fontSize = 20.sp
//                        )
//                        Text(
//                            text = homeScreenModel.currentUser?.statusDescription ?: "",
//                            fontSize = 12.sp
//                        )
//                    }
//                },
//
//                actions = {
//                    Icon(
//                        modifier = Modifier
//                            .padding(6.dp),
//                        imageVector = Icons.Rounded.Notifications,
//                        contentDescription = "notificationIcon"
//                    )
//                }
//            )
//        },
//        bottomBar = {
//            BottomAppBar(
//                containerColor = MaterialTheme.colorScheme.onPrimary,
//                contentColor = MaterialTheme.colorScheme.primary,
//            ) {
//                Text(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .clickable { onErrorToAuthPage() },
//                    textAlign = TextAlign.Center,
//                    text = "Return to Auth Page",
//                )
//            }
//        },
//        floatingActionButton = {
//            FloatingActionButton(
//                modifier = Modifier
//                    .blur(10.dp)
//                    .alpha(0.5f),
//                onClick = {
//                    homeScreenModel.onErrorMessageChange("Hello World")
//                }
//            ) {
//                Icon(
//                    Icons.Default.Add, contentDescription = "Add"
//                )
//            }
//        }
//    ) { innerPadding ->
//        Box(
//            Modifier
//                .fillMaxSize()
//                .padding(innerPadding)
//        ) {
//            LocationsPage(
//                friendLocationMap = homeScreenModel.friendLocationMap,
//                pullToRefreshState = pullToRefreshState,
//                onClickUserIcon = onClickUserIcon,
//                onRefreshLocations = {
//                    homeScreenModel.refresh(onErrorToAuthPage)
//                    pullToRefreshState.endRefresh()
//                }
//            )
//            SnackBarToast(
//                modifier = Modifier
//                    .padding(16.dp)
//                    .align(Alignment.TopCenter),
//                text = homeScreenModel.errorMessage,
//                onEffect = { homeScreenModel.onErrorMessageChange("") }
//            )
//        }
//    }
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



