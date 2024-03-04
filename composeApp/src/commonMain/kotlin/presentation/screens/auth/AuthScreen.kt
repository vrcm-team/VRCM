package io.github.vrcmteam.vrcm.presentation.screens.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.lifecycle.LifecycleEffect
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.vrcmteam.vrcm.presentation.animations.fadeSlideHorizontally
import io.github.vrcmteam.vrcm.presentation.compoments.AuthFold
import io.github.vrcmteam.vrcm.presentation.compoments.SnackBarToast
import io.github.vrcmteam.vrcm.presentation.screens.auth.card.LoginCardInput
import io.github.vrcmteam.vrcm.presentation.screens.auth.card.VerifyCardInput
import io.github.vrcmteam.vrcm.presentation.screens.auth.data.AuthCardPage


object AuthScreen : Screen {
    @Composable
    override fun Content() {
        val authScreenModel: AuthScreenModel = getScreenModel()
        LifecycleEffect(onStarted = { authScreenModel.tryAuth() })
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
                    AuthCardPage.Loading -> {
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

                    AuthCardPage.Login -> {
                        NavCard("Login") {
                            LoginCardInput(
                                uiState = authScreenModel.uiState,
                                onUsernameChange = authScreenModel::onUsernameChange,
                                onPasswordChange = authScreenModel::onPasswordChange,
                                onClick = authScreenModel::login
                            )
                        }
                    }

                    AuthCardPage.EmailCode, AuthCardPage.TFACode, AuthCardPage.TTFACode -> {
                        NavCard("Verify", barContent = {
                            ReturnIcon {
                                authScreenModel.cancelJob()
                                authScreenModel.onCardStateChange(AuthCardPage.Login)
                            }
                        }) {
                            VerifyCardInput(
                                uiState = authScreenModel.uiState,
                                onVerifyCodeChange = authScreenModel::onVerifyCodeChange,
                                onClick = authScreenModel::verify
                            )
                        }
                    }

                    AuthCardPage.Authed -> {
                        val currentNavigator = LocalNavigator.currentOrThrow
                        LaunchedEffect(Unit) {
                            currentNavigator.pop()
                            currentNavigator.push(AuthAnimeScreen(true))
                        }
                        Box(modifier = Modifier.fillMaxSize())
                    }
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
                AuthCardPage.Loading -> fadeIn(cardChangeAnimationSpec)
                    .togetherWith(fadeOut(animationSpec))

                AuthCardPage.Login -> fadeSlideHorizontally(
                    cardChangeDurationMillis,
                    direction = -1
                )

                AuthCardPage.EmailCode, AuthCardPage.TFACode, AuthCardPage.TTFACode -> fadeSlideHorizontally(
                    cardChangeDurationMillis
                )

                AuthCardPage.Authed -> EnterTransition.None
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




