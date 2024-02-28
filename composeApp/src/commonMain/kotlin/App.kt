package io.github.vrcmteam.vrcm

import androidx.compose.runtime.Composable
import org.koin.mp.KoinPlatform

@Composable
fun App(){
    KoinPlatform.getKoin().get()
}