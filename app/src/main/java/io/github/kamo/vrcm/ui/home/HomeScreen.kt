package io.github.kamo.vrcm.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.kamo.vrcm.ui.util.UserStateIcon
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Home(
    homeViewModel: HomeViewModel = koinViewModel(),
    onNavigate: () -> Unit
) {
    var presses by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        homeViewModel.flush()
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)

        ) {
            val friendLocationMap = homeViewModel.friendLocationMap
            val instanceLocation =
                friendLocationMap.filter { (k, _) -> k != "offline" && k != "private" }.values.toList()
            println("aaa")
            LazyColumn(
                modifier = Modifier
                    .padding(9.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                item {
                    Text(text = "Friends Active on the Website")
                }
                item(key = "offline") {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(friendLocationMap["offline"] ?: emptyList(), key = { it.value.id }) {
                            LocationFriend(it.value.imageUrl, it.value.displayName,when (it.value.status) {
                                "active" -> Color.Green
                                "join me" -> Color.Blue
                                "ask me" -> Color.Yellow
                                "busy" -> Color.Red
                                else -> Color.Gray
                            })
                        }
                    }
                }
                item {
                    Text(text = "Friends in Private Worlds")
                }
                item(key = "private") {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(friendLocationMap["private"] ?: emptyList(), key = { it.value.id }) {
                            LocationFriend(it.value.imageUrl, it.value.displayName,when (it.value.status) {
                                "active" -> Color.Green
                                "join me" -> Color.Blue
                                "ask me" -> Color.Yellow
                                "busy" -> Color.Red
                                else -> Color.Gray
                            })
                        }
                    }
                }
                item {
                    Text(text = "by Location")
                }
                items(instanceLocation, key = { it.first().value.location }) { friendInfos ->
                    LocationCard {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(friendInfos, key = { it.value.id }) {
                                LocationFriend(
                                    it.value.imageUrl, it.value.displayName, when (it.value.status) {
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
    }
}


@Composable
fun LocationCard(content: @Composable () -> Unit) {
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
                    .data("https://files.vrchat.cloud/World-Time-to-sleep-Image-201943.file_28750aba-4305-41f7-bbf9-4e300a68be39.1.png?Expires=1706771965&Key-Pair-Id=K3JAQ0Y971TV2Z&Signature=LdW~Ub4oJQWMSsFCnrgWjHFKXDjgm5Rk8KRt5Nl2JQs3EyzxxOU~cvmXvQP2DjlCsX8C5K32Ca5dvjMZyxZ9aMw57UIFhlFjYsgufR1qIl1fCLhU0VLFzBjPNyjsCth~5Vy0NUEHAfLwMDdoyFMTqcmuQREBsW38HvCd43OxE0cf1WmzeMTnhMgyhQjoz9cvhx5mRAYHPjnKfDUPhiesI2vOykv6VLO0QRhdFJiSyGYO41Kn0zOSarko8S5hjEOpw0JuGVJfDC9V~REeIRaJK6bYcQQVD1Wjq~~Yu8BOX56EeB09Uzd0X~enXe~NLlXyikYI91jkEJ1gb1YMHJXqFg__")
                    .crossfade(true)
                    .build(),
                contentDescription = null,
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
                    text = "WordName",
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "description",
                )
                Spacer(modifier = Modifier.weight(1f))
                Row {
                    Text(
                        text = "Public",
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "10/64",
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
