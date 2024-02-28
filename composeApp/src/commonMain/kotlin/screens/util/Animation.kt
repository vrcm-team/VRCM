package io.github.vrcmteam.vrcm.screens.util

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AuthAnime(
    isAuthed: Boolean,
    onNavigate: () -> Unit
) {

    BoxWithConstraints {
        var isAuthedState by remember { mutableStateOf(isAuthed) }
        val cardUpAnimationSpec = tween<Dp>(1200)
        val cardHeightDp by animateDpAsState(
            if (!isAuthedState) 380.dp else maxHeight + 100.dp,
            cardUpAnimationSpec,
            label = "AuthCardHeightDp",
        )
        val shapeDp by animateDpAsState(
            if (!isAuthedState) 30.dp else 0.dp,
            cardUpAnimationSpec,
            label = "AuthCardShapeDp",
        ) {
            onNavigate()
        }
        LaunchedEffect(Unit) {
            isAuthedState = !isAuthedState
        }
        AuthFold(
            cardHeightDp = cardHeightDp,
            shapeDp = shapeDp,
        ) {
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
    }
}

@Composable
fun StartupAnime(
    onNavigate: () -> Unit
) {
    val durationMillis = 1200
    var isStartUp by remember { mutableStateOf(false) }
    val iconYOffset by animateDpAsState(
        if (isStartUp) (-180).dp else 0.dp,
        tween(durationMillis),
        label = "LogoOffset"
    )
    val authSurfaceOffset by animateDpAsState(
        if (isStartUp) 0.dp else 380.dp,
        tween(durationMillis),
        label = "AuthSurfaceOffset"
    )
    val authSurfaceAlpha by animateFloatAsState(
        if (isStartUp) 1.00f else 0.00f,
        tween(durationMillis),
        label = "AuthSurfaceAlpha"
    ) {
        onNavigate()
    }
    LaunchedEffect(Unit) {
        isStartUp = true
    }
    AuthFold(
        iconYOffset = iconYOffset,
        cardYOffset = authSurfaceOffset,
        cardAlpha = authSurfaceAlpha,
    )
}

fun fadeSlideHorizontally(
    cardInDurationMillis: Int = 600,
    cardOutDurationMillis: Int = cardInDurationMillis - 200,
    direction: Int = 1
): ContentTransform =
    (fadeIn(tween(cardInDurationMillis, cardOutDurationMillis))
            + slideInHorizontally(tween(cardInDurationMillis, cardOutDurationMillis)) { it * direction / 2 })
        .togetherWith(fadeOut(tween(cardOutDurationMillis))
                + slideOutHorizontally(tween(cardOutDurationMillis)) { (-it) * direction / 2 })


@Composable
fun transformationColor(
    initialColor: Color = Color.White,
    targetColor: Color = Color.LightGray,
    durationMillis: Int = AnimationConstants.DefaultDurationMillis,
    animation: DurationBasedAnimationSpec<Color> = tween(500)
): Color {
    val color = remember {
        Animatable(initialColor)
    }
    LaunchedEffect(Unit) {
        color.animateTo(targetColor, infiniteRepeatable(tween(2000), RepeatMode.Reverse))
    }

    return color.value
}

@Composable
fun TransformationColor(
    initialColor: Color = Color.White,
    targetColor: Color = Color.LightGray,
    durationMillis: Int = AnimationConstants.DefaultDurationMillis,
    content: @Composable () -> Unit
) {

    CompositionLocalProvider(
        LocalTransformationColor provides transformationColor(
            initialColor = initialColor,
            targetColor = targetColor,
            durationMillis = durationMillis,
        )
    ) {
        content()
    }
}

val LocalTransformationColor: ProvidableCompositionLocal<Color> = compositionLocalOf {
    error("TransformationColor is not provided")
}

@Composable
fun Modifier.applyTransformationColor(enabled: Boolean = true, shape: Shape = RectangleShape) =
    this.composed {
        if (enabled) background(LocalTransformationColor.current, shape) else this
    }