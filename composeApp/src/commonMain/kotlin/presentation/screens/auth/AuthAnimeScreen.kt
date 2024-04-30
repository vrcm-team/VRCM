package io.github.vrcmteam.vrcm.presentation.screens.auth

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.vrcmteam.vrcm.presentation.compoments.AuthFold
import io.github.vrcmteam.vrcm.presentation.screens.home.HomeScreen

data class AuthAnimeScreen(
    private val isAuthed: Boolean,
) : Screen {
    @Composable
    override fun Content() {
        val currentNavigator = LocalNavigator.currentOrThrow
        val onNavigate: () -> Unit = {
            val screen = if (isAuthed) {
                HomeScreen
            } else {
                AuthScreen
            }
            currentNavigator replace screen
        }
        BoxWithConstraints {
            var isAuthedState by remember { mutableStateOf(isAuthed) }
            val cardUpAnimationSpec = tween<Dp>(1200)
            val cardHeightDp by animateDpAsState(
                if (isAuthedState) maxHeight.times(0.42f) else maxHeight + 100.dp,
                cardUpAnimationSpec,
                label = "AuthCardHeightDp",
            )
            val shapeDp by animateDpAsState(
                if (isAuthedState) 30.dp else 0.dp,
                cardUpAnimationSpec,
                label = "AuthCardShapeDp",
            ) {
                onNavigate()
            }
            LaunchedEffect(Unit) {
                isAuthedState = !isAuthedState
            }
            AuthFold(
                cardHeightDp = cardHeightDp,
                shapeDp = shapeDp,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(60.dp)
                            .align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 5.dp
                    )
                }
            }
        }
    }
}