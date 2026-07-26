package io.github.vrcmteam.vrcm

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideOrientation
import io.github.vrcmteam.vrcm.presentation.animations.AuthAnimeToHomeTransition
import io.github.vrcmteam.vrcm.presentation.animations.HomeToAuthAnimeTransition
import io.github.vrcmteam.vrcm.presentation.animations.slideScreenTransition
import io.github.vrcmteam.vrcm.presentation.compoments.SharedTransitionDialog
import io.github.vrcmteam.vrcm.presentation.compoments.SharedTransitionScreen
import io.github.vrcmteam.vrcm.presentation.compoments.SnackBarToastBox
import io.github.vrcmteam.vrcm.presentation.extensions.isTransitioning
import io.github.vrcmteam.vrcm.presentation.extensions.isTransitioningFromTo
import io.github.vrcmteam.vrcm.presentation.extensions.isTransitioningOn
import io.github.vrcmteam.vrcm.presentation.extensions.slideBack
import io.github.vrcmteam.vrcm.presentation.navigation.BackNavigationPolicy
import io.github.vrcmteam.vrcm.presentation.navigation.LocalBackNavigationPolicy
import io.github.vrcmteam.vrcm.presentation.screens.auth.AuthAnimeScreen
import io.github.vrcmteam.vrcm.presentation.screens.auth.StartupAnimeScreen
import io.github.vrcmteam.vrcm.presentation.screens.auth.VersionDialog
import io.github.vrcmteam.vrcm.presentation.screens.gallery.GalleryScreen
import io.github.vrcmteam.vrcm.presentation.screens.group.GroupProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.home.HomeScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldProfileScreen
import io.github.vrcmteam.vrcm.presentation.settings.SettingsProvider
import org.koin.compose.KoinContext

@Composable
fun App() {
    val backNavigationPolicy = remember { BackNavigationPolicy() }
    KoinContext {
        SettingsProvider {
            Navigator(
                screen = StartupAnimeScreen,
            ) {
                CompositionLocalProvider(LocalBackNavigationPolicy provides backNavigationPolicy) {
                    SnackBarToastBox(
                        Modifier
                            .systemBarsPadding()
                            .padding(vertical = 76.dp, horizontal = 12.dp)
                    ) {
                        VersionDialog()
                        SharedTransitionScreen(
                            navigator = it,
                            modifier = Modifier.slideBack(
                                enabled = backNavigationPolicy.isSlideBackEnabled,
                            ),
                            transitionSpec = { selectTransition(it) }
                        ) { screen ->
                            SharedTransitionDialog(key = screen.key) {
                                screen.Content()
                            }
                        }
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
        isTransitioningOn<HomeScreen, GroupProfileScreen>() -> slideScreenTransition(navigator)
        isTransitioningOn<UserProfileScreen, GalleryScreen>() -> slideScreenTransition(navigator, SlideOrientation.Horizontal)
        isTransitioningOn<UserProfileScreen, GroupProfileScreen>() -> slideScreenTransition(navigator, SlideOrientation.Horizontal)
        isTransitioningOn<WorldProfileScreen, UserProfileScreen>() -> slideScreenTransition(navigator, SlideOrientation.Horizontal)
        isTransitioningOn<WorldProfileScreen, GroupProfileScreen>() -> slideScreenTransition(navigator, SlideOrientation.Horizontal)
        isTransitioningFromTo<HomeScreen, AuthAnimeScreen>() -> HomeToAuthAnimeTransition
        isTransitioningFromTo<AuthAnimeScreen, HomeScreen>() -> AuthAnimeToHomeTransition
        isTransitioning<StartupAnimeScreen>() -> ContentTransform(EnterTransition.None, ExitTransition.None)
        isTransitioning<AuthAnimeScreen>() -> ContentTransform(EnterTransition.None, ExitTransition.None)
        else -> slideScreenTransition(navigator, SlideOrientation.Horizontal)
    }
