package io.github.vrcmteam.vrcm.presentation.animations

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape


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