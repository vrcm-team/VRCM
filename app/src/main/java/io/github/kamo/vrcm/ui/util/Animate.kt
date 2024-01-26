package io.github.kamo.vrcm.ui.util

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import io.github.kamo.vrcm.ui.auth.AuthCardState

fun fadeSlideHorizontally(
    cardInDurationMillis: Int = 600,
    cardOutDurationMillis: Int = cardInDurationMillis - 200,
    direction: Int = 1
): AnimatedContentTransitionScope<AuthCardState>.() -> ContentTransform =
    {
        (fadeIn(tween(cardInDurationMillis, cardOutDurationMillis))
                + slideInHorizontally(tween(cardInDurationMillis, cardOutDurationMillis)) { it * direction / 2 })
            .togetherWith(fadeOut(tween(cardOutDurationMillis)) + slideOutHorizontally(tween(cardOutDurationMillis)) { (-it) * direction / 2 })
    }
