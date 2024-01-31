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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Home( onNavigate: () -> Unit) {

    var presses by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier =  Modifier.background(MaterialTheme.colorScheme.inverseOnSurface),
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
                if (presses>3){
                    onNavigate()
                }
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { innerPadding ->
        Box (
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)

        ){
            Column(
                modifier = Modifier
                    .padding(9.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                LocationCard()
                LocationCard()
                LocationCard()
                LocationCard()
            }
        }

    }

}

@Preview
@Composable
fun LocationCard() {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.onPrimary)
            .fillMaxWidth()
            .height(146.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(bottom = 3.dp)
                    .fillMaxWidth()
//                    .background(MaterialTheme.colorScheme.primary)
            ) {

                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data("https://files.vrchat.cloud/thumbnails/file_28750aba-4305-41f7-bbf9-4e300a68be39.b05f8573fa5ef316b0b76c51be3fafd7d774de0456a812fe494e02d84c8a709f.1.thumbnail-256.png?Expires=1706692841&Key-Pair-Id=K3JAQ0Y971TV2Z&Signature=I1BPTFNKXPN3FIbsBQjKa2K0ju66DKN212v7FkNogXUXYDdyzMzl7o05Q6aQTAi-bCcRLkVskbjoZgZUYd9m6ibilDARJ3VqsibWDdqAnFecd6HyMAvz8NuSdgi69zYMprOFwfMpruSuZjO1RsJh6B-ZtWcJYLJZvgeuybP78iMNIU2Y1Y5rZynZNZg5OuHPGKp64z4YoAuFZvQwaltFgB9h8t8Qe5S6JxZJCsptEdm1qMQhLp9dOiOWyCsGjqjF0seoZyu2TAn36JlLj5JgP940uh8SEGyD0QxdRJM8WAcm8hplyN2pAymIdJs9CXV4por3wpy~qTPgEMVa8P5QUQ__")
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(90.dp)
                        .height(60.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.inverseOnSurface)
                )
                Column(
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = "WordName",
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = "description",
                    )
                }
            }
            Row(
                modifier = Modifier
                    .padding(top = 3.dp)
//                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {

                LocationFriend()
                Spacer(modifier = Modifier.weight(1f))
                LocationFriend()
                Spacer(modifier = Modifier.weight(1f))
                LocationFriend()
                Spacer(modifier = Modifier.weight(1f))
                LocationFriend()
                Spacer(modifier = Modifier.weight(1f))
                LocationFriend()
                Spacer(modifier = Modifier.weight(1f))
                LocationFriend()
            }
        }
    }
}

@Preview
@Composable
fun LocationFriend() {
    Column(
        modifier = Modifier.size(50.dp, 68.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data("https://api.vrchat.cloud/api/1/file/file_f6598f8f-f95c-48a5-89a1-b20fd5665460/1/file")
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.inverseOnSurface)
        )
        Text(
            modifier = Modifier.fillMaxSize(),
            text = "ikutsuu",
            maxLines = 1,
            textAlign = TextAlign.Center,
            fontSize = 10.sp
        )
    }
}