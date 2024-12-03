package io.github.vrcmteam.vrcm.presentation.screens.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import io.github.vrcmteam.vrcm.presentation.animations.fadeSlideHorizontally
import io.github.vrcmteam.vrcm.presentation.compoments.AuthFold
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.screens.auth.card.LoginCardInput
import io.github.vrcmteam.vrcm.presentation.screens.auth.card.VerifyCardInput
import io.github.vrcmteam.vrcm.presentation.screens.auth.data.AuthCardPage
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings

object AuthScreen : Screen {
    @Composable
    override fun Content() {
        val currentNavigator = currentNavigator
        val authScreenModel: AuthScreenModel = koinScreenModel()

        LaunchedEffect(Unit){
            authScreenModel.tryAuth()
        }

        BoxWithConstraints(
            modifier = Modifier.imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            AuthFold(
                iconYOffset = maxHeight.times(-0.2f),
                cardHeightDp = maxHeight.times(0.42f),
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
}

@Composable
private fun AuthCard(
    modifier: Modifier = Modifier,
    cardState: AuthCardPage,
    content: @Composable (AuthCardPage) -> Unit,
) {
    val cardChangeDurationMillis = 600
    val cardChangeAnimationSpec =
        remember { tween<Float>(cardChangeDurationMillis, cardChangeDurationMillis) }
    val animationSpec = remember { tween<Float>(cardChangeDurationMillis) }
    AnimatedContent(
        modifier = modifier,
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
    ) {
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
//        Spacer(modifier = Modifier.height(42.dp))
    },
    content: @Composable ColumnScope.() -> Unit,
) {
    Box {
        barContent()
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
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

}