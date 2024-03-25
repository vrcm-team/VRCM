package io.github.vrcmteam.vrcm.presentation.screens.auth

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.vrcmteam.vrcm.presentation.compoments.AuthFold

object StartupAnimeScreen : Screen {
    @Composable
    override fun Content() {
        val durationMillis = 1000
        val current = LocalNavigator.currentOrThrow
        var isStartUp by remember { mutableStateOf(false) }
        val iconYOffset by animateDpAsState(
            if (isStartUp) (-180).dp else 0.dp,
            tween(durationMillis),
            label = "LogoOffset"
        )
        val authSurfaceOffset by animateDpAsState(
            if (isStartUp) 0.dp else 380.dp,
            tween(durationMillis),
            label = "AuthSurfaceOffset"
        )
        val authSurfaceAlpha by animateFloatAsState(
            if (isStartUp) 1.00f else 0.00f,
            tween(durationMillis),
            label = "AuthSurfaceAlpha"
        ) {
            current replace AuthScreen
        }
        LaunchedEffect(Unit) {
            isStartUp = true
        }
        AuthFold(
            iconYOffset = iconYOffset,
            cardYOffset = authSurfaceOffset,
            cardAlpha = authSurfaceAlpha,
        )
    }
}


