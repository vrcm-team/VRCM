package io.github.kamo.vrcm.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.kamo.vrcm.R
import io.github.kamo.vrcm.ui.auth.AuthCardState.EmailCode
import io.github.kamo.vrcm.ui.auth.AuthCardState.Login
import io.github.kamo.vrcm.ui.auth.AuthCardState.NONE
import io.github.kamo.vrcm.ui.auth.AuthCardState.TFACode
import io.github.kamo.vrcm.ui.auth.card.LoginCardInput
import io.github.kamo.vrcm.ui.auth.card.VerifyCardInput
import io.github.kamo.vrcm.ui.util.SnackBarToast
import io.github.kamo.vrcm.ui.util.fadeSlideHorizontally

@Composable
fun Auth(authViewModel: AuthViewModel, onNavigate: () -> Unit) {
    val durationMillis = 1500
    var isStartUp by remember { mutableStateOf(false) }
    val startUpTransition = updateTransition(targetState = isStartUp, label = "StartUp")
    LaunchedEffect(Unit) {
        isStartUp = true
    }
    val doNavigate = {
        authViewModel.onCardStateChange(NONE)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = MaterialTheme.colorScheme.primaryContainer)
    ) {
        SnackBarToast(
            text = authViewModel.uiState.errorMsg,
            onEffect = { authViewModel.onErrorMessageChange("") }
        )
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "logo",
            modifier = Modifier
                .width(128.dp)
                .align(Alignment.Center)
                .iconInAnimate(durationMillis, startUpTransition)
        )
        AuthCard(
            inDurationMillis = durationMillis,
            startUpTransition = startUpTransition,
            uiState = authViewModel.uiState,
            onNavigate = onNavigate
        ) { state ->
            when (state) {
                Login -> {
                    NavCard("Login") {
                        LoginCardInput(uiState = authViewModel.uiState,
                            onUsernameChange = authViewModel::onUsernameChange,
                            onPasswordChange = authViewModel::onPasswordChange,
                            onClick = { authViewModel.login(doNavigate) })
                    }
                }

                TFACode, EmailCode -> {
                    NavCard("Verify", barContent = {
                        ReturnIcon {
                            authViewModel.cancelJob()
                            authViewModel.onCardStateChange(Login)
                        }
                    }) {
                        VerifyCardInput(uiState = authViewModel.uiState,
                            onVerifyCodeChange = authViewModel::onVerifyCodeChange,
                            onClick = { authViewModel.verify(doNavigate) })
                    }
                }

                NONE -> {
                    Box(modifier = Modifier.fillMaxSize())
                }

            }
        }
    }
}

@Composable
private fun BoxScope.AuthCard(
    inDurationMillis: Int,
    startUpTransition: Transition<Boolean>,
    uiState: AuthUIState,
    onNavigate: () -> Unit,
    content: @Composable (AuthCardState) -> Unit
) {
    val isAuthed  = uiState.cardState ==  NONE
    val cardChangeDurationMillis = 600
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(tween(1000,cardChangeDurationMillis - 200)) { _, _ -> onNavigate() }
            .cardInAnimate(inDurationMillis, startUpTransition)
            .run { if (!isAuthed) height(380.dp) else fillMaxHeight() }
            .align(Alignment.BottomCenter),
        color = MaterialTheme.colorScheme.onPrimary,
        shape = if (!isAuthed) RoundedCornerShape(
            topStart = 30.dp,
            topEnd = 30.dp
        ) else CardDefaults.shape,
    ) {
        AnimatedContent(
            label = "AuthSurfaceChange",
            targetState = uiState.cardState,
            transitionSpec = {
                when (this.targetState) {
                    Login -> fadeSlideHorizontally(cardChangeDurationMillis, direction = -1)
                    EmailCode, TFACode -> fadeSlideHorizontally(cardChangeDurationMillis)
                    NONE -> EnterTransition.None
                        .togetherWith(fadeOut(tween(cardChangeDurationMillis)))
                }
            },

        ) {
            content(it)
        }
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
        contentDescription = "",
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



