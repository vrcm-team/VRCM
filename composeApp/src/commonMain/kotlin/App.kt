package io.github.vrcmteam.vrcm

import androidx.compose.animation.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import io.github.vrcmteam.vrcm.presentation.animations.AuthAnimeToHomeTransition
import io.github.vrcmteam.vrcm.presentation.animations.HomeToAuthAnimeTransition
import io.github.vrcmteam.vrcm.presentation.animations.slideScreenTransition
import io.github.vrcmteam.vrcm.presentation.compoments.AAlertDialog
import io.github.vrcmteam.vrcm.presentation.compoments.SharedTransitionScreen
import io.github.vrcmteam.vrcm.presentation.compoments.SnackBarToastBox
import io.github.vrcmteam.vrcm.presentation.extensions.isTransitioningFromTo
import io.github.vrcmteam.vrcm.presentation.extensions.isTransitioningOn
import io.github.vrcmteam.vrcm.presentation.extensions.slideBack
import io.github.vrcmteam.vrcm.presentation.screens.auth.AuthAnimeScreen
import io.github.vrcmteam.vrcm.presentation.screens.auth.StartupAnimeScreen
import io.github.vrcmteam.vrcm.presentation.screens.home.HomeScreen
import io.github.vrcmteam.vrcm.presentation.screens.profile.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldProfileScreen
import io.github.vrcmteam.vrcm.presentation.settings.SettingsProvider
import org.koin.compose.KoinContext

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun App() {
    KoinContext {
        SettingsProvider {
            Navigator(StartupAnimeScreen) {
                SnackBarToastBox(
                    Modifier
                        .systemBarsPadding()
                        .padding(vertical = 76.dp, horizontal = 12.dp)
                ) {
                    AAlertDialog {
                        SharedTransitionScreen(
                            navigator = it,
                            modifier = Modifier.slideBack(),
                            transitionSpec = { selectTransition(it) }
                        )
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
        isTransitioningFromTo<HomeScreen, AuthAnimeScreen>() -> HomeToAuthAnimeTransition
        isTransitioningFromTo<AuthAnimeScreen, HomeScreen>() -> AuthAnimeToHomeTransition
        else -> ContentTransform(EnterTransition.None, ExitTransition.None)
    }
