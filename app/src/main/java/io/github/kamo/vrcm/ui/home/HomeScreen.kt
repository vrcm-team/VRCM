package io.github.kamo.vrcm.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.github.kamo.vrcm.MainRouteEnum
import io.github.kamo.vrcm.data.api.UserStatus
import io.github.kamo.vrcm.data.api.auth.FriendInfo
import io.github.kamo.vrcm.ui.home.page.LocationsPage
import io.github.kamo.vrcm.ui.theme.GameColor
import io.github.kamo.vrcm.ui.util.AImage
import io.github.kamo.vrcm.ui.util.SnackBarToast
import io.github.kamo.vrcm.ui.util.UserStateIcon
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

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
    val onClickUserIcon = remember {
        { friendId: String ->
            onNavigate(
                MainRouteEnum.Profile,
                false,
                listOf(friendId)
            )
        }
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
        modifier = Modifier.width(60.dp).clickable { onClickUserIcon() },
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

@Composable
fun FriedScreen(friend: FriendInfo?) {
    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier
            .systemBarsPadding()
            .fillMaxSize()
    ) {
        val imageHeight = (LocalConfiguration.current.screenHeightDp / 3)
        val offsetDp = with(LocalDensity.current) { scrollState.value.toDp() }
        val ratio =
            (((imageHeight - offsetDp.value) / imageHeight.toFloat()).let { if (it >= 0) it else 0f }).let {
                FastOutSlowInEasing.transform(it)
            }
        val fl = scrollState.value / imageHeight.toFloat()
        val blurDp = with(LocalDensity.current) { (fl * 20).toDp() }
        val inverseRatio = 1 - ratio
        val topBarHeight = 50
        val iconSize = (topBarHeight * inverseRatio).dp
        val initUserIconPadding = imageHeight.toFloat()
        val lastIconPadding = initUserIconPadding - (topBarHeight * ratio)

        Surface(
            Modifier
                .verticalScroll(scrollState)
                .height(2000.dp)
                .fillMaxWidth()
        ) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                AImage(
                    modifier = Modifier
                        .height(imageHeight.dp)
                        .fillMaxWidth()
                        .padding(top = offsetDp)
                        .clip(
                            RoundedCornerShape(
                                bottomStart = (30 * ratio).dp,
                                bottomEnd = (30 * ratio).dp
                            )
                        )
                        .blur(blurDp),
                    imageUrl = friend?.currentAvatarThumbnailImageUrl,
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = initUserIconPadding.dp),
                shape = RoundedCornerShape(
                    topStart = (30 * ratio).dp,
                    topEnd = (30 * ratio).dp
                )
            ) {
                Spacer(modifier = Modifier.height((topBarHeight * inverseRatio).dp))
                Text(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = 6.dp),
                    text = friend?.displayName ?: "",
                    fontSize = 24.sp
                )
                Row(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .align(Alignment.CenterHorizontally),
                ) {

                    Canvas(
                        modifier = Modifier
                            .size(12.dp)
                            .align(Alignment.CenterVertically)
                    ) {
                        drawCircle(
                            when (friend?.status) {
                                UserStatus.Online -> GameColor.Status.Online
                                UserStatus.JoinMe -> GameColor.Status.JoinMe
                                UserStatus.AskMe -> GameColor.Status.AskMe
                                UserStatus.Busy -> GameColor.Status.Busy
                                UserStatus.Offline -> GameColor.Status.Offline
                                else -> GameColor.Status.Offline
                            }
                        )
                    }
                    Text(
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .padding(horizontal = 6.dp),
                        text = friend?.status?.typeName ?: "",
                    )
                    Icon(
                        modifier = Modifier.size(15.dp),
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = GameColor.Level.Trusted
                    )
                    Text(
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .padding(horizontal = 6.dp),
                        text = "Trusted",
                    )
                }

            }

            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .height(topBarHeight.dp)
                        .offset(y = offsetDp)
                        .alpha(ratio),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        modifier = Modifier
                            .padding(horizontal = 10.dp),
                        imageVector = Icons.Rounded.ArrowBackIosNew,
                        tint = Color.White,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        modifier = Modifier
                            .padding(horizontal = 10.dp),
                        imageVector = Icons.Rounded.Menu,
                        tint = Color.White,
                        contentDescription = null
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .height(topBarHeight.dp)
                        .offset(y = offsetDp)
                        .alpha(inverseRatio)
                        .background(
                            Color.White, RoundedCornerShape(
                                bottomStart = 12.dp,
                                bottomEnd = 12.dp
                            )
                        ),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        modifier = Modifier
                            .padding(horizontal = 10.dp),
                        imageVector = Icons.Rounded.ArrowBackIosNew,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        modifier = Modifier
                            .padding(horizontal = 10.dp),
                        imageVector = Icons.Rounded.Menu,
                        contentDescription = null
                    )
                }
            }


            Box(modifier = Modifier.height(iconSize)) {
                Row(
                    modifier = Modifier
                        .run { if (initUserIconPadding == lastIconPadding) offset(y = (offsetDp - imageHeight.dp)) else this }
                        .padding(top = lastIconPadding.dp)
                        .alpha(inverseRatio)
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    AsyncImage(
                        modifier = Modifier
                            .clip(CircleShape)
                            .size(iconSize),
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(friend?.currentAvatarImageUrl)
                            .size(100, 100).build(),
                        contentScale = ContentScale.Crop,
                        contentDescription = "UserIcon",
                        imageLoader = koinInject()
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

        }

    }
}

