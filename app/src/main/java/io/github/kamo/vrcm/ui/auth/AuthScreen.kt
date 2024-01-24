package io.github.kamo.vrcm.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardReturn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.kamo.vrcm.R
import io.github.kamo.vrcm.ui.auth.AuthCardState.*
import io.github.kamo.vrcm.ui.auth.card.LoginCardInput
import io.github.kamo.vrcm.ui.auth.card.VerifyCardInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

@Composable
fun Auth(authViewModel: AuthViewModel, onNavigate: () -> Unit) {
    val uiState by authViewModel.uiState
    var isStartUp by remember { mutableStateOf(false) }
    val durationMillis = 1500
    val logoOffset by animateDpAsState(
        if (isStartUp) (-180).dp else 0.dp,
        tween(durationMillis)
    )
    LaunchedEffect(Unit) {
        isStartUp = true
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
        AuthCard(durationMillis, isStartUp, uiState, authViewModel, onNavigate)
    }
}

@Composable
private fun BoxScope.AuthCard(
    durationMillis: Int,
    isStartUp: Boolean,
    uiState: AuthUIState,
    authViewModel: AuthViewModel,
    onNavigate: () -> Unit
) {
    var isAuthed by remember { mutableStateOf(false) }
    val cardOffset by animateDpAsState(
        if (isStartUp) 0.dp else  380.dp,
        tween(durationMillis)
    )
    val cardAlpha by animateFloatAsState(
        if (isStartUp) 1.00f else 0.00f,
        tween(durationMillis + 1000)
    )
    val doNavigate = {
        isAuthed = true
//        runBlocking(Dispatchers.Main){
//            delay(2000)
//            onNavigate()
//            authViewModel.reset()
//        }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(cardAlpha)
            .animateContentSize(tween(durationMillis))
            .offset(y = cardOffset)
            .height(if (!isAuthed) 380.dp else 1000.dp)
            .align(Alignment.BottomCenter),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.onPrimary),
        shape = if (!isAuthed) RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp) else CardDefaults.shape,
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
                            onClick = { authViewModel.login(doNavigate) }
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
                            onClick = { authViewModel.verify(doNavigate) }
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

@Composable
private fun ReturnIcon(returnTo: (AuthCardState) -> Unit) {
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
private fun NavCard(
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


