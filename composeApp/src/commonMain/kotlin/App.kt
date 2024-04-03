package io.github.vrcmteam.vrcm

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import io.github.vrcmteam.vrcm.presentation.animations.authAnimeToHomeTransition
import io.github.vrcmteam.vrcm.presentation.animations.homeToAuthAnimeTransition
import io.github.vrcmteam.vrcm.presentation.animations.slideScreenTransition
import io.github.vrcmteam.vrcm.presentation.compoments.SnackBarToastBox
import io.github.vrcmteam.vrcm.presentation.extensions.isTransitioningFromTo
import io.github.vrcmteam.vrcm.presentation.extensions.isTransitioningOn
import io.github.vrcmteam.vrcm.presentation.screens.auth.AuthAnimeScreen
import io.github.vrcmteam.vrcm.presentation.screens.auth.StartupAnimeScreen
import io.github.vrcmteam.vrcm.presentation.screens.home.HomeScreen
import io.github.vrcmteam.vrcm.presentation.screens.profile.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldProfileScreen
import io.github.vrcmteam.vrcm.presentation.theme.VRCMTheme
import org.koin.compose.KoinContext
import presentation.compoments.SelectableTransitionScreen

@Composable
fun App() {
    KoinContext {
        VRCMTheme {
            Navigator(StartupAnimeScreen) {
                SnackBarToastBox(
                    Modifier
                        .systemBarsPadding()
                        .padding(vertical = 76.dp, horizontal = 12.dp)
                ) {
                    SelectableTransitionScreen(it) {
                        selectTransition(it)
                    }
                }
            }
        }
    }
}

fun AnimatedContentTransitionScope<Screen>.selectTransition(navigator: Navigator): ContentTransform =
    when {
        isTransitioningOn<HomeScreen, UserProfileScreen>() -> slideScreenTransition(navigator)
        isTransitioningOn<HomeScreen, WorldProfileScreen>() -> slideScreenTransition(navigator)
        isTransitioningFromTo<HomeScreen, AuthAnimeScreen>() -> homeToAuthAnimeTransition
        isTransitioningFromTo<AuthAnimeScreen, HomeScreen>() -> authAnimeToHomeTransition
        else -> ContentTransform(EnterTransition.None, ExitTransition.None)
    }
