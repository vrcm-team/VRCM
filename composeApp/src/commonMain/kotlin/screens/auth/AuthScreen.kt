package io.github.vrcmteam.vrcm.screens.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import io.github.vrcmteam.vrcm.screens.auth.AuthCardPage.Authed
import io.github.vrcmteam.vrcm.screens.auth.AuthCardPage.EmailCode
import io.github.vrcmteam.vrcm.screens.auth.AuthCardPage.Loading
import io.github.vrcmteam.vrcm.screens.auth.AuthCardPage.Login
import io.github.vrcmteam.vrcm.screens.auth.AuthCardPage.TFACode
import io.github.vrcmteam.vrcm.screens.auth.AuthCardPage.TTFACode
import io.github.vrcmteam.vrcm.screens.auth.card.LoginCardInput
import io.github.vrcmteam.vrcm.screens.auth.card.VerifyCardInput
import io.github.vrcmteam.vrcm.screens.util.AuthFold
import io.github.vrcmteam.vrcm.screens.util.SnackBarToast
import io.github.vrcmteam.vrcm.screens.util.fadeSlideHorizontally

class AuthScreen() : Screen {
    @Composable
    override fun Content() {
//        val authScreenModel: AuthScreenModel = getScreenModel()
//        Auth(authScreenModel) {}
        val current = LocalNavigator.current

        Box(modifier = Modifier.fillMaxSize().background(Color.Red).clickable { current!!.push( AuthScreen1()) })
    }

}

class AuthScreen1() : Screen {
    @Composable
    override fun Content() {
//        val authScreenModel: AuthScreenModel = getScreenModel()
//        Auth(authScreenModel) {}
        val current = LocalNavigator.current
        Box(modifier = Modifier.fillMaxSize().background(Color.Black).clickable { current!!.pop( ) })
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
        imageVector = Icons.Outlined.KeyboardArrowLeft,
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




