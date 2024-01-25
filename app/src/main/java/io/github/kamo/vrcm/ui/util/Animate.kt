package io.github.kamo.vrcm.ui.util

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import io.github.kamo.vrcm.ui.auth.AuthCardState

fun fadeSlideInHorizontally(
    cardInDurationMillis: Int = 600,
    cardOutDurationMillis: Int = cardInDurationMillis - 200
): AnimatedContentTransitionScope<AuthCardState>.() -> ContentTransform =
    {
        (fadeIn(tween(cardInDurationMillis, cardOutDurationMillis))
                + slideInHorizontally(tween(cardInDurationMillis, cardOutDurationMillis)) { it / 2 })
            .togetherWith(fadeOut(tween(cardOutDurationMillis)) + slideOutHorizontally(tween(cardOutDurationMillis)) { -it / 2 })
    }