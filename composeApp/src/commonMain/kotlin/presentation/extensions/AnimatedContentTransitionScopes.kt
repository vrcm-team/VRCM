package io.github.vrcmteam.vrcm.presentation.extensions

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.navigation3.scene.Scene
import io.github.vrcmteam.vrcm.presentation.navigation.AppRoute
import io.github.vrcmteam.vrcm.presentation.compoments.route

// Any->A || A->Any
internal inline fun <reified T : AppRoute> AnimatedContentTransitionScope<Scene<AppRoute>>.isTransitioning() =
    isTransitioningTo<T>() || isTransitioningFrom<T>()

// A->B || B->A
internal inline fun <reified T : AppRoute, reified K : AppRoute> AnimatedContentTransitionScope<Scene<AppRoute>>.isTransitioningOn() =
    isTransitioningFromTo<T, K>() || isTransitioningFromTo<K, T>()

// A->B
internal inline fun <reified T : AppRoute, reified K : AppRoute> AnimatedContentTransitionScope<Scene<AppRoute>>.isTransitioningFromTo() =
    isTransitioningTo<T>() && isTransitioningFrom<K>()

// A->A || B->A
internal inline fun <reified T : AppRoute> AnimatedContentTransitionScope<Scene<AppRoute>>.isTransitioningTo() =
    targetState.route is T

// A->A || A->B
internal inline fun <reified T : AppRoute> AnimatedContentTransitionScope<Scene<AppRoute>>.isTransitioningFrom() =
    initialState.route is T
