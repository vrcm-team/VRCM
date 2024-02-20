package io.github.kamo.vrcm.ui.util

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import io.github.kamo.vrcm.R
import io.github.kamo.vrcm.ui.theme.GameColor
import org.koin.compose.koinInject

@Composable
fun AuthFold(
    iconYOffset: Dp = (-180).dp,
    cardYOffset: Dp = 0.dp,
    cardAlpha: Float = 1.00f,
    cardHeightDp: Dp = 380.dp,
    shapeDp: Dp = 30.dp,
    cardContext: @Composable () -> Unit = {}
) {
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
                .offset(y = iconYOffset)
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = cardYOffset)
                .alpha(cardAlpha)
                .height(cardHeightDp)
                .align(Alignment.BottomCenter),
            color = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(topStart = shapeDp, topEnd = shapeDp),
        ) {
            cardContext()
        }
    }
}

@Composable
fun LoadingButton(
    modifier: Modifier = Modifier,
    text: String,
    enabled: Boolean = true,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Button(
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        onClick = onClick,
        enabled = enabled
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
fun SnackBarToast(
    text: String,
    onEffect: () -> Unit,
    content: @Composable (SnackbarData) -> Unit = {
        Text(
            text = it.visuals.message,
            textAlign = TextAlign.Center
        )
    }
) {
    val snackBarHostState = remember { SnackbarHostState() }

    LaunchedEffect(text) {
        if (text.isNotBlank()) {
            snackBarHostState.showSnackbar(text)
            onEffect()
        }
    }
    SnackbarHost(snackBarHostState) {
        Snackbar(
            modifier = Modifier
                .systemBarsPadding()
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.onPrimary,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(12.dp),
            actionOnNewLine = true
        ) {
            content(it)
        }
    }
}

@Composable
fun UserStateIcon(
    modifier: Modifier = Modifier,
    iconUrl: String,
    userStatus: String
) {
    AImage(
        modifier = modifier
            .sateCircle(when(userStatus){
                UserStatus.Online.type -> GameColor.Status.Online
                UserStatus.JoinMe.type -> GameColor.Status.JoinMe
                UserStatus.AskMe.type -> GameColor.Status.AskMe
                UserStatus.Busy.type -> GameColor.Status.Busy
                UserStatus.Offline.type -> GameColor.Status.Offline
                else -> GameColor.Status.Offline
            })
            .aspectRatio(1f)
            .clip(CircleShape),
        iconUrl = iconUrl,
        contentDescription = "UserStateIcon"
    )
}

@Composable
fun AImage(
    modifier: Modifier = Modifier,
    iconUrl: String,
    color: Color = MaterialTheme.colorScheme.inverseOnSurface,
    contentDescription: String? = null,
    imageLoader: ImageLoader = koinInject(),
    contentScale: ContentScale = ContentScale.Crop,
) {
    val placeholder = remember(color) { ColorPainter(color) }
    AsyncImage(
        modifier = modifier,
        model = iconUrl,
        contentDescription = contentDescription,
        imageLoader = imageLoader,
        placeholder = placeholder,
        error = placeholder,
        contentScale = contentScale,
    )
}

enum class UserStatus(val type: String) {
    Online("active"),
    JoinMe("join me"),
    AskMe("ask me"),
    Busy("busy"),
    Offline("offline")
}

