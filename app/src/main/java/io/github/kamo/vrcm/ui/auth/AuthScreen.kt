package io.github.kamo.vrcm.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardReturn
import androidx.compose.material3.*
import androidx.compose.material3.ProgressIndicatorDefaults.CircularDeterminateStrokeCap
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.kamo.vrcm.R
import io.github.kamo.vrcm.ui.auth.AuthCardState.*
import io.github.kamo.vrcm.ui.auth.card.LoginCardInput
import io.github.kamo.vrcm.ui.auth.card.VerifyCardInput

@Composable
fun Auth(authViewModel: AuthViewModel, onNavigate: () -> Unit) {
    val uiState by authViewModel.uiState
    val context = LocalContext.current
    var isStartUp by remember { mutableStateOf(false) }
    val durationMillis = 1500
    val logoOffset by animateDpAsState(
        if (isStartUp) (-180).dp else 0.dp,
        tween(durationMillis)
    )
    val cardOffset by animateDpAsState(
        if (isStartUp) 0.dp else 380.dp,
        tween(durationMillis)
    )
    val cardAlpha by animateFloatAsState(
        if (isStartUp) 1.00f else 0.00f,
        tween(durationMillis + 1000)
    )
    LaunchedEffect(Unit) {
        isStartUp = true
    }
    LaunchedEffect(uiState, context) {
        authViewModel.channel.collect {
            if (it) {
                onNavigate()
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "logo",
            modifier = Modifier
                .width(128.dp)
                .align(Alignment.Center)
                .offset(y = logoOffset)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(cardAlpha)
                .offset(y = cardOffset)
                .height(380.dp)
                .align(Alignment.BottomCenter),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.onPrimary),
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        ) {
            if (!isStartUp) return@Card
            AnimatedContent(
                targetState = uiState.cardState
            ) {
                when (it) {
                    Login -> {
                        NavCard("Login") {
                            LoginCardInput(
                                uiState = uiState,
                                onUsernameChange = authViewModel::onUsernameChange,
                                onPasswordChange = authViewModel::onPasswordChange,
                                onClick = authViewModel::login
                            )
                        }
                    }

                    TFACode, EmailCode -> {
                        NavCard("Verify",
                            barContent = {
                                ReturnIcon(authViewModel::onCardStateChange)
                            }
                        ) {
                            VerifyCardInput(
                                uiState = uiState,
                                onVerifyCodeChange = authViewModel::onVerifyCodeChange,
                                onClick = authViewModel::verify
                            )
                        }
                    }

                    Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(100.dp)
                                    .align(Alignment.Center),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                strokeWidth = 8.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReturnIcon(returnTo: (AuthCardState) -> Unit) {
    Icon(
        modifier = Modifier
            .padding(start = 20.dp, top = 20.dp, bottom = 4.dp)
            .size(18.dp)
            .clickable { returnTo(Login) },
        imageVector = Icons.Rounded.KeyboardReturn,
        contentDescription = "",
    )
}


@Composable
fun NavCard(
    tileText: String,
    barContent: @Composable ColumnScope.() -> Unit = {
        Spacer(modifier = Modifier.height(42.dp))
    },
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        barContent()
        Text(
            modifier = Modifier
                .fillMaxWidth(),
            text = tileText,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        content()
    }
}


