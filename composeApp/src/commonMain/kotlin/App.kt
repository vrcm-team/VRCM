package io.github.vrcmteam.vrcm

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import io.github.vrcmteam.vrcm.screens.auth.AuthScreen

@Composable
fun App(){
//    KoinContext {
        Navigator(AuthScreen())
//    }
}