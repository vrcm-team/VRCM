package io.github.vrcmteam.vrcm.presentation.screens.auth

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.vrcmteam.vrcm.getAppPlatform
import io.github.vrcmteam.vrcm.presentation.compoments.AuthFold
import io.github.vrcmteam.vrcm.presentation.extensions.openUrl
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings

object StartupAnimeScreen : Screen {
    @Composable
    override fun Content() {
        val durationMillis = 1000
        val current = LocalNavigator.currentOrThrow
        var isStartUp by remember { mutableStateOf(false) }
        val startUpAnime = { isStartUp = true }

        UpdateDialog(startUpAnime)

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
private fun Screen.UpdateDialog(startUpAnime: () -> Unit) {
    val authScreenModel: AuthScreenModel  = getScreenModel()
    var newVersionUrl by remember { mutableStateOf("") }
    var newVersionTagName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        authScreenModel.tryCheckVersion().let {
            if (it != null) {
                newVersionTagName = it.first
                newVersionUrl = it.second
            }else{
                startUpAnime()
            }
        }
    }
    if (newVersionUrl.isNotEmpty()) {
        val appPlatform = getAppPlatform()
        AlertDialog(
            icon = {
                Icon(Icons.Filled.Update, contentDescription = "AlertDialogIcon")
            },
            title = {
                    Text(
                        text = strings.startupDialogTitle,
                    )
            },
            text = {
                Row {
                    Checkbox(
                        checked = false,
                        onCheckedChange = {
                            val version = if (it) {
                                newVersionTagName
                            }else{
                                null
                            }
                            authScreenModel.rememberVersion(version)
                        }
                    )
                    Text(
                        text = strings.startupDialogRememberVersion,
                    )
                }

            },
            onDismissRequest = startUpAnime,
            confirmButton = {
                FilledTonalButton(
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    onClick = {
                        appPlatform.openUrl(newVersionUrl)
                    }
                ) {
                    Text(strings.startupDialogUpdate)
                }
                FilledTonalButton(
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ),
                    onClick = {
                        newVersionUrl = ""
                        startUpAnime()
                    }
                ) {
                    Text(strings.startupDialogIgnore)
                }
            }
        )
    }
}
