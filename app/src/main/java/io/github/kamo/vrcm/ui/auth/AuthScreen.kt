package io.github.kamo.vrcm.ui.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
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
        authViewModel.onCardStateChange(if (authViewModel.awaitAuth()) AuthCardPage.Authed else AuthCardPage.Login)
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
                    Box(modifier = Modifier.fillMaxSize()) {
                        onNavigate()
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
    val cardChangeAnimationSpec = tween<Float>(
        cardChangeDurationMillis,
        cardChangeDurationMillis
    )
    AnimatedContent(
        label = "AuthSurfaceChange",
        targetState = cardState,
        transitionSpec = {
            println(this.initialState)
            when (this.targetState) {
                AuthCardPage.Loading -> fadeIn(cardChangeAnimationSpec)
                    .togetherWith(fadeOut(tween(cardChangeDurationMillis)))

                AuthCardPage.Login -> fadeSlideHorizontally(cardChangeDurationMillis, direction = -1)

                AuthCardPage.EmailCode, AuthCardPage.TFACode -> fadeSlideHorizontally(cardChangeDurationMillis)

                AuthCardPage.Authed -> EnterTransition.None
                    .togetherWith(fadeOut(tween(cardChangeDurationMillis)))
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

private fun Modifier.cardInAnimate(inDurationMillis: Int, startUpTransition: Transition<Boolean>) =
    composed {
        val authSurfaceOffset by startUpTransition.animateDp(
            { tween(inDurationMillis) },
            label = "AuthSurfaceOffset"
        ) {
            if (it) 0.dp else 380.dp
        }
        val authSurfaceAlpha by startUpTransition.animateFloat(
            { tween(inDurationMillis + 1000) },
            label = "AuthSurfaceAlpha"
        ) {
            if (it) 1.00f else 0.00f
        }

        alpha(authSurfaceAlpha)
            .offset(y = authSurfaceOffset)
    }

private fun Modifier.iconInAnimate(durationMillis: Int, startUpTransition: Transition<Boolean>) =
    this.then(composed {
        val logoOffset by startUpTransition.animateDp(
            { tween(durationMillis) },
            label = "LogoOffset"
        ) {
            if (it) (-180).dp else 0.dp
        }
        offset(y = logoOffset)
    })



