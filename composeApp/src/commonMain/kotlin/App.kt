package io.github.vrcmteam.vrcm

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import io.github.vrcmteam.vrcm.presentation.animations.authAnimeToHomeTransition
import io.github.vrcmteam.vrcm.presentation.animations.homeToAuthAnimeTransition
import io.github.vrcmteam.vrcm.presentation.animations.slideScreenTransition
import io.github.vrcmteam.vrcm.presentation.extensions.isTransitioningBy
import io.github.vrcmteam.vrcm.presentation.extensions.isTransitioningOn
import io.github.vrcmteam.vrcm.presentation.screens.auth.AuthAnimeScreen
import io.github.vrcmteam.vrcm.presentation.screens.auth.StartupAnimeScreen
import io.github.vrcmteam.vrcm.presentation.screens.home.HomeScreen
import io.github.vrcmteam.vrcm.presentation.screens.profile.ProfileScreen
import org.koin.compose.KoinContext
import presentation.compoments.SelectableScreenTransition

@Composable
fun App(){
    KoinContext {
        Navigator(StartupAnimeScreen){
            SelectableScreenTransition(it){
                selectTransition(it)
            }
        }
    }
}

fun AnimatedContentTransitionScope<Screen>.selectTransition(navigator: Navigator): ContentTransform =
     when {
        isTransitioningOn<HomeScreen,ProfileScreen>() -> slideScreenTransition(navigator)
        isTransitioningBy<HomeScreen,AuthAnimeScreen>() -> homeToAuthAnimeTransition
        isTransitioningBy<AuthAnimeScreen,HomeScreen>() -> authAnimeToHomeTransition
        else -> ContentTransform(EnterTransition.None, ExitTransition.None)
    }
