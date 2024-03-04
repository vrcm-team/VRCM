package io.github.vrcmteam.vrcm

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import io.github.vrcmteam.vrcm.presentation.screens.auth.StartupAnimeScreen
import org.koin.compose.KoinContext

@Composable
fun App(){
    KoinContext {
        Navigator(StartupAnimeScreen)
    }
}