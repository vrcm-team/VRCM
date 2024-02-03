package io.github.kamo.vrcm.ui.startup

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.kamo.vrcm.MainRouteEnum
import io.github.kamo.vrcm.R
import org.koin.androidx.compose.koinViewModel

@Composable
fun StartUp(
    startUpViewModel: StartUpViewModel = koinViewModel(),
    onNavigate: (MainRouteEnum) -> Unit
) {

    LaunchedEffect(Unit) {
        val mainRouteEnum = if (startUpViewModel.awaitAuth() == true) {
            MainRouteEnum.Home
        } else {
            MainRouteEnum.Auth
        }
        onNavigate(mainRouteEnum)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Image(
            painter = painterResource(R.drawable.logo),
            contentDescription = "logo",
            modifier = Modifier
                .width(128.dp)
                .align(Alignment.Center)
        )
    }
}










