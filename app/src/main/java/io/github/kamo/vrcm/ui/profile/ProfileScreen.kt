package io.github.kamo.vrcm.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import io.github.kamo.vrcm.data.api.auth.FriendInfo
import io.github.kamo.vrcm.ui.home.FriedScreen
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import org.koin.compose.koinInject

@Composable
fun Profile(
    httpClient: HttpClient = koinInject(),
    friendId: String
) {
    val friend: MutableState<FriendInfo?> = remember {
        mutableStateOf(null)
    }
    LaunchedEffect(Unit) {
        runCatching {
            friend.value = httpClient.get("users/$friendId").body()
        }
    }
    FriedScreen(friend.value)
}