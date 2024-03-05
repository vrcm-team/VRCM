package io.github.vrcmteam.vrcm.presentation.extensions

import androidx.compose.animation.AnimatedContentTransitionScope
import cafe.adriel.voyager.core.screen.Screen

// A->A || A->B || B->A
inline fun <reified T> AnimatedContentTransitionScope<Screen>.isTransitioning() =
    isTransitioningTo<T>() || isTransitioningFrom<T>()
// A->B && B->A
inline fun <reified T,reified K> AnimatedContentTransitionScope<Screen>.isTransitioningOn() =
    (isTransitioningTo<T>() || isTransitioningFrom<T>()) && (isTransitioningTo<K>() || isTransitioningFrom<K>())
// A->B
inline fun <reified T,reified K> AnimatedContentTransitionScope<Screen>.isTransitioningBy() =
    isTransitioningTo<T>() && isTransitioningFrom<K>()
// A->A || B->A
inline fun <reified T> AnimatedContentTransitionScope<Screen>.isTransitioningTo() =
    targetState::class == T::class
// A->A || A->B
inline fun <reified T> AnimatedContentTransitionScope<Screen>.isTransitioningFrom() =
    initialState::class == T::class