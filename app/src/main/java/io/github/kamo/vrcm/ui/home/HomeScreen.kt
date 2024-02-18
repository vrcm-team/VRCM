package io.github.kamo.vrcm.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kamo.vrcm.data.api.auth.FriendInfo
import io.github.kamo.vrcm.ui.theme.GameColor
import io.github.kamo.vrcm.ui.util.AImage
import io.github.kamo.vrcm.ui.util.UserStateIcon
import io.github.kamo.vrcm.ui.util.UserStatus
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun Home(
    homeViewModel: HomeViewModel = koinViewModel(),
    onNavigate: () -> Unit
) {
    LaunchedEffect(Unit) {
        homeViewModel.refresh()
    }
    Scaffold(
        modifier = Modifier.background(Color.LightGray),
        topBar = {
            TopAppBar(
                colors = topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.onPrimary,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                title = {
                    Text("Top app bar")
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
            FloatingActionButton(onClick = {
                onNavigate()
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { innerPadding ->
        val refreshing by homeViewModel.refreshing
        val pullRefreshState = rememberPullRefreshState(refreshing, { homeViewModel.refresh() })
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pullRefresh(pullRefreshState)
                .padding(innerPadding)

        ) {
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
            PullRefreshIndicator(refreshing, pullRefreshState, Modifier.align(Alignment.TopCenter))
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
                when(it.value.status){
                    UserStatus.Online.type -> GameColor.Status.Online
                    UserStatus.JoinMe.type -> GameColor.Status.JoinMe
                    UserStatus.AskMe.type -> GameColor.Status.AskMe
                    UserStatus.Busy.type -> GameColor.Status.Busy
                    UserStatus.Offline.type -> GameColor.Status.Offline
                    else -> GameColor.Status.Offline
                }
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
    sateColor: Color
) {

    Column(
        modifier = Modifier.width(60.dp),
        verticalArrangement = Arrangement.Center
    ) {
        UserStateIcon(
            iconUrl = iconUrl,
            sateColor = sateColor
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
