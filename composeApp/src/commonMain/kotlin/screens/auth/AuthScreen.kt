package io.github.vrcmteam.vrcm.screens.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.vrcmteam.vrcm.screens.auth.AuthCardPage.*
import io.github.vrcmteam.vrcm.screens.auth.card.LoginCardInput
import io.github.vrcmteam.vrcm.screens.auth.card.VerifyCardInput
import io.github.vrcmteam.vrcm.screens.home.HomeScreen
import io.github.vrcmteam.vrcm.screens.util.AuthFold
import io.github.vrcmteam.vrcm.screens.util.SnackBarToast
import io.github.vrcmteam.vrcm.screens.util.fadeSlideHorizontally

class AuthScreen() : Screen {
    @Composable
    override fun Content() {
        val authScreenModel: AuthScreenModel = getScreenModel()
        val current = LocalNavigator.currentOrThrow

        Auth(authScreenModel) {
            current.pop()
            current.push(AuthAnimeScreen(false){
                current.push(HomeScreen())
            })
        }

    }

}
class AuthAnimeScreen(
    val isAuthed: Boolean,
    val onNavigate: () -> Unit
) : Screen {
    @Composable
    override fun Content() {
        BoxWithConstraints {
            var isAuthedState by remember { mutableStateOf(isAuthed) }
            val cardUpAnimationSpec = tween<Dp>(1200)
            val cardHeightDp by animateDpAsState(
                if (!isAuthedState) 380.dp else maxHeight + 100.dp,
                cardUpAnimationSpec,
                label = "AuthCardHeightDp",
            )
            val shapeDp by animateDpAsState(
                if (!isAuthedState) 30.dp else 0.dp,
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


@Composable
fun Auth(
    authScreenModel: AuthScreenModel,
    onNavigate: () -> Unit
) {
    LaunchedEffect(Unit) {
        authScreenModel.tryAuth()
    }
    AuthFold(
        context = {
            SnackBarToast(
                modifier = Modifier
                    .systemBarsPadding()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                text = authScreenModel.uiState.errorMsg,
                onEffect = { authScreenModel.onErrorMessageChange("") }
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = it.visuals.message,
                    textAlign = TextAlign.Center
                )
            }
        }
    ) {
        AuthCard(
            cardState = authScreenModel.uiState.cardState,
        ) { state ->
            when (state) {
                Loading -> {
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

                Login -> {
                    NavCard("Login") {
                        LoginCardInput(
                            uiState = authScreenModel.uiState,
                            onUsernameChange = authScreenModel::onUsernameChange,
                            onPasswordChange = authScreenModel::onPasswordChange,
                            onClick = authScreenModel::login
                        )
                    }
                }

                EmailCode, TFACode, TTFACode -> {
                    NavCard("Verify", barContent = {
                        ReturnIcon {
                            authScreenModel.cancelJob()
                            authScreenModel.onCardStateChange(Login)
                        }
                    }) {
                        VerifyCardInput(
                            uiState = authScreenModel.uiState,
                            onVerifyCodeChange = authScreenModel::onVerifyCodeChange,
                            onClick = authScreenModel::verify
                        )
                    }
                }

                Authed -> {
                    LaunchedEffect(Unit) {
                        onNavigate()
                    }
                    Box(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun AuthCard(
    cardState: AuthCardPage,
    content: @Composable (AuthCardPage) -> Unit
) {
    val cardChangeDurationMillis = 600
    val cardChangeAnimationSpec =
        remember { tween<Float>(cardChangeDurationMillis, cardChangeDurationMillis) }
    val animationSpec = remember { tween<Float>(cardChangeDurationMillis) }
    AnimatedContent(
        label = "AuthSurfaceChange",
        targetState = cardState,
        transitionSpec = {
            when (this.targetState) {
                Loading -> fadeIn(cardChangeAnimationSpec)
                    .togetherWith(fadeOut(animationSpec))

                Login -> fadeSlideHorizontally(
                    cardChangeDurationMillis,
                    direction = -1
                )

                EmailCode, TFACode, TTFACode -> fadeSlideHorizontally(
                    cardChangeDurationMillis
                )

                Authed -> EnterTransition.None
                    .togetherWith(fadeOut(animationSpec))
            }
        },
    ) {
        content(it)
    }

}


@Composable
private fun ReturnIcon(onClick: () -> Unit) {
    Icon(
        modifier = Modifier
            .padding(start = 24.dp, top = 24.dp)
            .size(18.dp)
            .clickable(onClick = onClick),
        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
        contentDescription = "ReturnIcon",
    )
}


@Composable
private fun NavCard(
    tileText: String,
    barContent: @Composable () -> Unit = {
        Spacer(modifier = Modifier.height(42.dp))
    },
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        barContent()
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = tileText,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        content()
    }
}




