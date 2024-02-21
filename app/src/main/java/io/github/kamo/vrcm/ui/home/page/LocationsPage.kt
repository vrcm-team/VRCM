package io.github.kamo.vrcm.ui.home.page

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.PullToRefreshState
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
import io.github.kamo.vrcm.data.api.LocationType
import io.github.kamo.vrcm.data.api.auth.FriendInfo
import io.github.kamo.vrcm.ui.home.FriendLocation
import io.github.kamo.vrcm.ui.home.LocationFriend
import io.github.kamo.vrcm.ui.util.AImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationsPage(
    friendLocationMap: Map<LocationType, MutableList<FriendLocation>>,
    pullToRefreshState: PullToRefreshState,
    onRefreshLocations: suspend () -> Unit
) {
    Box(
        Modifier.nestedScroll(pullToRefreshState.nestedScrollConnection)
    ) {
        if (pullToRefreshState.isRefreshing) {
            LaunchedEffect(Unit) {
                onRefreshLocations()
            }
        }
        val offlineFriendLocation = friendLocationMap[LocationType.Offline]?.get(0)
        val privateFriendLocation = friendLocationMap[LocationType.Private]?.get(0)
        val travelingFriendLocation = friendLocationMap[LocationType.Traveling]?.get(0)
        val instanceFriendLocations = friendLocationMap[LocationType.Instance]
        LazyColumn(
            modifier = Modifier
                .padding(6.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
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
            if (travelingFriendLocation != null) {
                item(key = LocationType.Traveling) {
                    Text(text = "Friends is Traveling")
                    Spacer(modifier = Modifier.height(6.dp))
                    UserIconsRow(travelingFriendLocation.friends)
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
        val scaleFraction = if (pullToRefreshState.isRefreshing) 1f else
            LinearOutSlowInEasing.transform(pullToRefreshState.progress).coerceIn(0f, 1f)
        PullToRefreshContainer(
            modifier = Modifier
                .graphicsLayer(scaleX = scaleFraction, scaleY = scaleFraction)
                .align(Alignment.TopCenter),
            state = pullToRefreshState,
        )

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
    val shape = RoundedCornerShape(12.dp)
    Surface(
        color = MaterialTheme.colorScheme.onPrimary,
        shape = shape,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
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
                        .clip(shape),
                    imageUrl = instants.worldImageUrl,
                    contentDescription = "WorldImage"
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp)
                ) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = instants.worldName,
                        fontSize = 15.sp,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.weight(1f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                    ) {
                        AImage(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .border(1.dp, Color.LightGray, CircleShape),
                            imageUrl = instants.regionIconUrl
                        )
                        Text(
                            modifier = Modifier.padding(horizontal = 6.dp),
                            text = instants.accessType.displayName,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            modifier = Modifier.padding(horizontal = 6.dp),
                            text = instants.userCount,
                        )
                        Icon(
                            modifier = Modifier.offset(y = 1.dp),
                            imageVector = Icons.Rounded.Person,
                            contentDescription = "PersonCount"
                        )
                    }
                }
            }
            content()
        }
    }
}