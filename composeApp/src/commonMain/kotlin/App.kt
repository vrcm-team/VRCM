package io.github.vrcmteam.vrcm

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import io.github.vrcmteam.vrcm.screens.auth.AuthScreen
import org.koin.compose.KoinContext

@Composable
fun App(){
    KoinContext {
//        Box(modifier = Modifier.fillMaxSize().background(Color.Red))

        Navigator(AuthScreen())
    }
}