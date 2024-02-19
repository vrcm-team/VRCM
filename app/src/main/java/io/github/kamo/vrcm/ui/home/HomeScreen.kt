package io.github.kamo.vrcm.ui.home

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kamo.vrcm.data.api.auth.FriendInfo
import io.github.kamo.vrcm.ui.util.AImage
import io.github.kamo.vrcm.ui.util.UserStateIcon
import io.github.kamo.vrcm.ui.util.UserStatus
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Home(
    homeViewModel: HomeViewModel = koinViewModel(),
    onNavigate: () -> Unit
) {
    val pullToRefreshState = rememberPullToRefreshState()
    LaunchedEffect(UInt) {
        pullToRefreshState.startRefresh()
    }
    println("Home")
    Scaffold(
        modifier = Modifier.background(Color.LightGray),
        topBar = {
            CenterAlignedTopAppBar(
                colors = topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.onPrimary,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                title = {

                    Text(
                        modifier = Modifier.padding(6.dp), text = "Top app bar"
                    )
                },
                actions = {
                    UserStateIcon(
                        modifier = Modifier.size(40.dp),
                        iconUrl = "https://api.vrchat.cloud/api/1/image/file_11ca3656-30fb-49e2-8f75-d4f2e5bc8120/6/256",
                        userStatus = UserStatus.Online.type
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
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    text = "Bottom app bar",
                )
            }
        },
        floatingActionButton = {
            Card(onClick = { /*TODO*/ }) {

            }
//            FloatingActionButton(onClick = {
//                onNavigate()
//            }) {
//                Icon(Icons.Default.Add, contentDescription = "Add")
//            }
        }
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxWidth()
                .padding(innerPadding)
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
            val scaleFraction = if (pullToRefreshState.isRefreshing) 1f else
                LinearOutSlowInEasing.transform(pullToRefreshState.progress).coerceIn(0f, 1f)
            if (pullToRefreshState.isRefreshing) {
                LaunchedEffect(Unit) {
                    homeViewModel.refresh()
                    pullToRefreshState.endRefresh()
                }
            }
            val friendLocationMap = homeViewModel.friendLocationMap
            val offlineFriendLocation = friendLocationMap[LocationType.Offline]?.get(0)
            val privateFriendLocation = friendLocationMap[LocationType.Private]?.get(0)
            val instanceFriendLocations = friendLocationMap[LocationType.Instance]
            LazyColumn(
                modifier = Modifier
                    .padding(9.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {

                if (offlineFriendLocation != null) {
                    item(key = LocationType.Offline) {
                        Text(text = "Friends Active on the Website")
                        Spacer(modifier = Modifier.height(6.dp))
                        UserIconsRow(offlineFriendLocation.friends)
                    }
                }
                if (privateFriendLocation != null) {
                    item(key = LocationType.Private) {
                        Text(text = "Friends in Private Worlds")
                        Spacer(modifier = Modifier.height(6.dp))
                        UserIconsRow(privateFriendLocation.friends)
                    }
                }
                if (instanceFriendLocations != null) {
                    item(key = LocationType.Instance) {
                        Text(text = "by Location")
                    }
                    items(instanceFriendLocations, key = { it.location }) { locations ->
                        LocationCard(locations) {
                            UserIconsRow(locations.friends)
                        }
                    }
                }
            }

            PullToRefreshContainer(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer(scaleX = scaleFraction, scaleY = scaleFraction),
                state = pullToRefreshState,
            )
        }
    }
}

@Composable
private fun UserIconsRow(friends: List<State<FriendInfo>>) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(friends, key = { it.value.id }) {
            LocationFriend(
                it.value.imageUrl,
                it.value.displayName,
                it.value.status
            )
        }
    }
}


@Composable
fun LocationCard(location: FriendLocation, content: @Composable () -> Unit) {
    val instants by location.instants
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.onPrimary, shape = RoundedCornerShape(12.dp))
            .fillMaxWidth()
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier
                .height(80.dp)
                .fillMaxWidth()
        ) {
            AImage(
                modifier = Modifier
                    .width(120.dp)
                    .clip(RoundedCornerShape(12.dp)),
                iconUrl = instants.worldImageUrl,
                contentDescription = "WorldImage"
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp)
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = instants.worldName ?: "",
                    fontSize = 15.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.weight(1f))
                Row {
                    Text(
                        text = instants.instantsType,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = instants.userCount,
                    )
                }

            }

        }
        content()
    }

}


@Composable
fun LocationFriend(
    iconUrl: String,
    name: String,
    userStatus: String
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
