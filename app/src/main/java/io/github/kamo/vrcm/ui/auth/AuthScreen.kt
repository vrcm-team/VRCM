package io.github.kamo.vrcm.ui.auth

import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.kamo.vrcm.R
import io.github.kamo.vrcm.ui.auth.AuthCardState.*
import io.github.kamo.vrcm.ui.auth.card.LoginCardInput
import io.github.kamo.vrcm.ui.auth.card.VerifyCardInput
import io.github.kamo.vrcm.ui.util.fadeSlideHorizontally

@Composable
fun Auth(authViewModel: AuthViewModel, onNavigate: () -> Unit) {
    val errorMsg = authViewModel.uiState.value.errorMsg
    val durationMillis = 1500
    var isStartUp by remember { mutableStateOf(false)  }
    val startUpTransition = updateTransition(targetState = isStartUp, label = "startUp")
    val logoOffset by startUpTransition.animateDp({ tween(durationMillis) }) {
        if (it) (-180).dp else 0.dp
    }
    LaunchedEffect(Unit) {
        isStartUp = true
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = MaterialTheme.colorScheme.primaryContainer)
    ) {
        if (errorMsg.isNotBlank()){
            val toast = Toast.makeText(LocalContext.current, errorMsg, Toast.LENGTH_SHORT)
            toast.addCallback(@RequiresApi(Build.VERSION_CODES.R)
            object : Toast.Callback(){
                override fun onToastHidden() = authViewModel.onErrorMessageChange("")
            })
            toast.show()
        }

        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "logo",
            modifier = Modifier
                .width(128.dp)
                .align(Alignment.Center)
                .offset(y = logoOffset)
        )
        AuthCard(durationMillis, startUpTransition, authViewModel.uiState.value, authViewModel, onNavigate)
    }
}

@Composable
private fun BoxScope.AuthCard(
    durationMillis: Int,
    startUpTransition: Transition<Boolean>,
    uiState: AuthUIState,
    authViewModel: AuthViewModel,
    onNavigate: () -> Unit
) {
    var isAuthed by remember { mutableStateOf(false) }
    val cardOffset by startUpTransition.animateDp({tween(durationMillis)}) {
        if (it) 0.dp else 380.dp
    }
    val cardAlpha by startUpTransition.animateFloat({tween(durationMillis + 1000)}) {
        if (it) 1.00f else 0.00f
    }
    val doNavigate = {
        authViewModel.onCardStateChange(NONE)
        isAuthed = true
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(tween(durationMillis)) { _, _ ->
                onNavigate()
            }
            .alpha(cardAlpha)
            .offset(y = cardOffset)
            .height(if (!isAuthed) 380.dp else 1000.dp)
            .align(Alignment.BottomCenter),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.onPrimary),
        shape = if (!isAuthed) RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp) else CardDefaults.shape,
    ) {
        AnimatedContent(
            targetState = uiState.cardState,
            transitionSpec = if (uiState.cardState == Login)fadeSlideHorizontally(600) else fadeSlideHorizontally(600, direction = -1),
        ) { state ->
            when (state) {
                Login -> {
                    NavCard("Login") {
                        LoginCardInput(uiState = uiState,
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
                        VerifyCardInput(uiState = uiState,
                            onVerifyCodeChange = authViewModel::onVerifyCodeChange,
                            onClick = { authViewModel.verify(doNavigate) })
                    }
                }

                NONE -> {}
            }
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


