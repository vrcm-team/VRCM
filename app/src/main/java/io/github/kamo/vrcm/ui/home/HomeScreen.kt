package io.github.kamo.vrcm.ui.home

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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.github.kamo.vrcm.data.api.instance.InstanceInfo
import io.github.kamo.vrcm.ui.util.UserStateIcon
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun Home(
    homeViewModel: HomeViewModel = koinViewModel(),
    onNavigate: () -> Unit
) {
    var presses by remember { mutableIntStateOf(0) }
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
                    modifier = Modifier
                        .fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    text = "Bottom app bar",
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                presses++
                if (presses > 3) {
                    onNavigate()
                }
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
                    item {
                        Text(text = "Friends Active on the Website")
                    }
                    item(key = "offline") {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(offlineFriendLocation.friends, key = { it.value.id }) {
                                LocationFriend(
                                    it.value.imageUrl,
                                    it.value.displayName,
                                    when (it.value.status) {
                                        "active" -> Color.Green
                                        "join me" -> Color.Blue
                                        "ask me" -> Color.Yellow
                                        "busy" -> Color.Red
                                        else -> Color.Gray
                                    }
                                )
                            }
                        }
                    }
                }
                if (privateFriendLocation != null) {
                    item {
                        Text(text = "Friends in Private Worlds")
                    }
                    item(key = "private") {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(privateFriendLocation.friends, key = { it.value.id }) {
                                LocationFriend(
                                    it.value.imageUrl,
                                    it.value.displayName,
                                    when (it.value.status) {
                                        "active" -> Color.Green
                                        "join me" -> Color.Blue
                                        "ask me" -> Color.Yellow
                                        "busy" -> Color.Red
                                        else -> Color.Gray
                                    }
                                )
                            }
                        }
                    }
                }
                if (instanceFriendLocations != null) {
                    item {
                        Text(text = "by Location")
                    }
                    items(instanceFriendLocations, key = { it.location }) { locations ->
                        val instance by locations.instance!!
                        LocationCard(instance) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(locations.friends, key = { it.value.id }) {
                                    LocationFriend(
                                        it.value.imageUrl,
                                        it.value.displayName,
                                        when (it.value.status) {
                                            "active" -> Color.Green
                                            "join me" -> Color.Blue
                                            "ask me" -> Color.Yellow
                                            "busy" -> Color.Red
                                            else -> Color.Gray
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            PullRefreshIndicator(refreshing, pullRefreshState, Modifier.align(Alignment.TopCenter))

        }
    }
}


@Composable
fun LocationCard(instance: InstanceInfo, content: @Composable () -> Unit) {
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
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(instance.world.imageUrl)
                    .data("https://files.vrchat.cloud/World-Time-to-sleep-Image-201943.file_28750aba-4305-41f7-bbf9-4e300a68be39.1.png?Expires=1706771965&Key-Pair-Id=K3JAQ0Y971TV2Z&Signature=LdW~Ub4oJQWMSsFCnrgWjHFKXDjgm5Rk8KRt5Nl2JQs3EyzxxOU~cvmXvQP2DjlCsX8C5K32Ca5dvjMZyxZ9aMw57UIFhlFjYsgufR1qIl1fCLhU0VLFzBjPNyjsCth~5Vy0NUEHAfLwMDdoyFMTqcmuQREBsW38HvCd43OxE0cf1WmzeMTnhMgyhQjoz9cvhx5mRAYHPjnKfDUPhiesI2vOykv6VLO0QRhdFJiSyGYO41Kn0zOSarko8S5hjEOpw0JuGVJfDC9V~REeIRaJK6bYcQQVD1Wjq~~Yu8BOX56EeB09Uzd0X~enXe~NLlXyikYI91jkEJ1gb1YMHJXqFg__")
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                imageLoader = koinInject<ImageLoader>(),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.inverseOnSurface)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp)
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = instance.world.name,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = instance.world.description,
                )
                Spacer(modifier = Modifier.weight(1f))
                Row {
                    Text(
                        text = instance.type,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${instance.userCount}/${instance.world.capacity}",
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
            size = 60,
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
