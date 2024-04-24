package io.github.vrcmteam.vrcm.presentation.screens.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import io.github.vrcmteam.vrcm.presentation.animations.fadeSlideHorizontally
import io.github.vrcmteam.vrcm.presentation.compoments.AuthFold
import io.github.vrcmteam.vrcm.presentation.configs.locale.strings
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.screens.auth.card.LoginCardInput
import io.github.vrcmteam.vrcm.presentation.screens.auth.card.VerifyCardInput
import io.github.vrcmteam.vrcm.presentation.screens.auth.data.AuthCardPage

object AuthScreen : Screen {
    @Composable
    override fun Content() {
        val currentNavigator = currentNavigator
        val authScreenModel: AuthScreenModel = getScreenModel()

        LifecycleEffect(onStarted = { authScreenModel.tryAuth() })
        AuthFold {
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
                        NavCard(strings.authLoginTitle) {
                            LoginCardInput(
                                uiState = authScreenModel.uiState,
                                onUsernameChange = authScreenModel::onUsernameChange,
                                onPasswordChange = authScreenModel::onPasswordChange,
                                onClick = authScreenModel::login
                            )
                        }
                    }

                    AuthCardPage.EmailCode, AuthCardPage.TFACode, AuthCardPage.TTFACode -> {
                        NavCard(strings.authVerifyTitle, barContent = {
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
                        LaunchedEffect(Unit) {
                            currentNavigator replace AuthAnimeScreen(true)
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
    IconButton(
        modifier = Modifier
            .padding(start = 6.dp, top = 6.dp),
        onClick = onClick
    ){
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
            contentDescription = "ReturnIcon",
            tint = MaterialTheme.colorScheme.primary
        )
    }
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
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        content()
    }
}