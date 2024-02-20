package io.github.kamo.vrcm.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
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
import io.github.kamo.vrcm.ui.auth.card.LoginCardInput
import io.github.kamo.vrcm.ui.auth.card.VerifyCardInput
import io.github.kamo.vrcm.ui.util.AuthFold
import io.github.kamo.vrcm.ui.util.fadeSlideHorizontally
import org.koin.androidx.compose.koinViewModel

@Composable
fun Auth(
    authViewModel: AuthViewModel = koinViewModel(),
    onNavigate: () -> Unit
) {
    LaunchedEffect(Unit) {
        println("cardState: " + authViewModel.hashCode())
        println("cardState: " + authViewModel.uiState.cardState)
        authViewModel.onCardStateChange(AuthCardPage.Loading)
        val cardState = if (authViewModel.awaitAuth()) AuthCardPage.Authed else AuthCardPage.Login
        println("cardState2: " + cardState)
        authViewModel.onCardStateChange(cardState)
        println("cardState1: " + authViewModel.hashCode())
        println("cardState1: " + authViewModel.uiState.cardState)
    }

    AuthFold {
        AuthCard(
            cardState = authViewModel.uiState.cardState,
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
                            uiState = authViewModel.uiState,
                            onUsernameChange = authViewModel::onUsernameChange,
                            onPasswordChange = authViewModel::onPasswordChange,
                            onClick = authViewModel::login
                        )
                    }
                }

                AuthCardPage.TFACode, AuthCardPage.EmailCode -> {
                    NavCard("Verify", barContent = {
                        ReturnIcon {
                            authViewModel.cancelJob()
                            authViewModel.onCardStateChange(AuthCardPage.Login)
                        }
                    }) {
                        VerifyCardInput(
                            uiState = authViewModel.uiState,
                            onVerifyCodeChange = authViewModel::onVerifyCodeChange,
                            onClick = authViewModel::verify
                        )
                    }
                }

                AuthCardPage.Authed -> {
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
                AuthCardPage.Loading -> fadeIn(cardChangeAnimationSpec)
                    .togetherWith(fadeOut(animationSpec))

                AuthCardPage.Login -> fadeSlideHorizontally(
                    cardChangeDurationMillis,
                    direction = -1
                )

                AuthCardPage.EmailCode, AuthCardPage.TFACode -> fadeSlideHorizontally(cardChangeDurationMillis)

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
        imageVector = Icons.Rounded.ArrowBackIosNew,
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




