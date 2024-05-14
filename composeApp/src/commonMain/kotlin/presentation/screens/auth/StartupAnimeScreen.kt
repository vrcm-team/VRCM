package io.github.vrcmteam.vrcm.presentation.screens.auth

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.vrcmteam.vrcm.presentation.compoments.AuthFold
import presentation.compoments.UpdateDialog
import presentation.screens.auth.data.VersionVo

object StartupAnimeScreen : Screen {
    @Composable
    override fun Content() {
        val durationMillis = 1000
        val current = LocalNavigator.currentOrThrow
        var isStartUp by remember { mutableStateOf(false) }
        val startUpAnime = { isStartUp = true }

        VersionDialog(startUpAnime)

        BoxWithConstraints {

            val iconYOffset by animateDpAsState(
                if (isStartUp) maxHeight.times(-0.2f) else 0.dp,
                tween(durationMillis),
                label = "LogoOffset"
            )

            val authSurfaceOffset by animateDpAsState(
                if (isStartUp) 0.dp else maxHeight.times(0.42f),
                tween(durationMillis),
                label = "AuthSurfaceOffset"
            )
            val authSurfaceAlpha by animateFloatAsState(
                if (isStartUp) 1.00f else 0.00f,
                tween(durationMillis),
                label = "AuthSurfaceAlpha",
                finishedListener = {  current replace AuthScreen }
            )
            AuthFold(
                iconYOffset = iconYOffset,
                cardYOffset = authSurfaceOffset,
                cardAlpha = authSurfaceAlpha,
                cardHeightDp = maxHeight.times(0.42f),
            )
        }


    }

}

@Composable
private fun Screen.VersionDialog(startUpAnime: () -> Unit) {
    val authScreenModel: AuthScreenModel  = getScreenModel()
    var version by remember { mutableStateOf(VersionVo()) }

    LaunchedEffect(Unit) {
        authScreenModel.tryCheckVersion().let {
            if (it.hasNewVersion) {
                version = it
            }else{
                startUpAnime()
            }
        }
    }
    UpdateDialog(
        version = version,
        onDismissRequest = {
            version = VersionVo()
            startUpAnime()
        },
        onRememberVersion = authScreenModel::rememberVersion
    )
}

