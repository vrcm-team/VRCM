package io.github.vrcmteam.vrcm.presentation.extensions

import androidx.compose.animation.AnimatedContentTransitionScope
import cafe.adriel.voyager.core.screen.Screen

// Any->A || A->Any
inline fun <reified T : Screen> AnimatedContentTransitionScope<Screen>.isTransitioning() =
    isTransitioningTo<T>() || isTransitioningFrom<T>()

// A->B || B->A
inline fun <reified T : Screen, reified K : Screen> AnimatedContentTransitionScope<Screen>.isTransitioningOn() =
    isTransitioningFromTo<T, K>() || isTransitioningFromTo<K, T>()

// A->B
inline fun <reified T : Screen, reified K : Screen> AnimatedContentTransitionScope<Screen>.isTransitioningFromTo() =
    isTransitioningTo<T>() && isTransitioningFrom<K>()

// A->A || B->A
inline fun <reified T : Screen> AnimatedContentTransitionScope<Screen>.isTransitioningTo() =
    targetState::class == T::class

// A->A || A->B
inline fun <reified T : Screen> AnimatedContentTransitionScope<Screen>.isTransitioningFrom() =
    initialState::class == T::class