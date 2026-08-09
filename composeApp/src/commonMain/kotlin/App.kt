package io.github.vrcmteam.vrcm

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.navigation3.scene.Scene
import io.github.vrcmteam.vrcm.presentation.animations.AuthAnimeToHomeTransition
import io.github.vrcmteam.vrcm.presentation.animations.HomeToAuthAnimeTransition
import io.github.vrcmteam.vrcm.presentation.animations.fadeScreenTransition
import io.github.vrcmteam.vrcm.presentation.animations.slideScreenTransition
import io.github.vrcmteam.vrcm.presentation.animations.SlideOrientation
import io.github.vrcmteam.vrcm.presentation.compoments.SharedTransitionDialog
import io.github.vrcmteam.vrcm.presentation.compoments.SharedTransitionScreen
import io.github.vrcmteam.vrcm.presentation.compoments.SnackBarToastBox
import io.github.vrcmteam.vrcm.presentation.compoments.OfficialLinkPrompt
import io.github.vrcmteam.vrcm.presentation.extensions.isTransitioning
import io.github.vrcmteam.vrcm.presentation.extensions.isTransitioningFromTo
import io.github.vrcmteam.vrcm.presentation.extensions.isTransitioningOn
import io.github.vrcmteam.vrcm.presentation.navigation.BackNavigationPolicy
import io.github.vrcmteam.vrcm.presentation.navigation.AppNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.AppRoute
import io.github.vrcmteam.vrcm.presentation.navigation.LocalBackNavigationPolicy
import io.github.vrcmteam.vrcm.presentation.navigation.LocalNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.rememberAppNavigator
import io.github.vrcmteam.vrcm.presentation.adaptive.LocalAppContentSize
import io.github.vrcmteam.vrcm.presentation.adaptive.LocalAppWindowWidthClass
import io.github.vrcmteam.vrcm.presentation.adaptive.appWindowWidthClass
import io.github.vrcmteam.vrcm.presentation.screens.auth.AuthAnimeScreen
import io.github.vrcmteam.vrcm.presentation.screens.auth.StartupAnimeScreen
import io.github.vrcmteam.vrcm.presentation.screens.auth.VersionDialog
import io.github.vrcmteam.vrcm.presentation.screens.gallery.GalleryScreen
import io.github.vrcmteam.vrcm.presentation.screens.group.GroupProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.home.HomeScreen
import io.github.vrcmteam.vrcm.presentation.screens.meetup.MeetupCardDisplayRoute
import io.github.vrcmteam.vrcm.presentation.screens.meetup.MeetupCardEditorRoute
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.FriendLocationPagerModel
import io.github.vrcmteam.vrcm.presentation.settings.SettingsProvider
import io.github.vrcmteam.vrcm.network.websocket.WebSocketApi
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.CardListDetailScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.MutualFriendsScreen
import io.github.vrcmteam.vrcm.service.FriendActivityService
import io.github.vrcmteam.vrcm.service.OfficialLinkInbox
import org.koin.compose.KoinContext
import org.koin.compose.koinInject

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App(
    isConfigurationChange: () -> Boolean = { false },
    windowChrome: @Composable () -> Unit = {},
    officialLinkInbox: OfficialLinkInbox? = null,
) {
    val backNavigationPolicy = remember { BackNavigationPolicy() }
    val navigator = rememberAppNavigator(StartupAnimeScreen)
    val lifecycleEventGate = remember { AppLifecycleEventGate() }
    val activeOfficialLinkInbox = remember(officialLinkInbox) {
        officialLinkInbox ?: OfficialLinkInbox()
    }
    KoinContext {
        val webSocketApi = koinInject<WebSocketApi>()
        val friendLocationPagerModel = koinInject<FriendLocationPagerModel>()
        koinInject<FriendActivityService>()
        LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
            if (lifecycleEventGate.onStop(isConfigurationChange())) {
                friendLocationPagerModel.onBackground()
                webSocketApi.onBackground()
            }
        }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            if (lifecycleEventGate.onResume()) {
                webSocketApi.onForeground()
                friendLocationPagerModel.onForeground()
            }
        }
        SettingsProvider {
            Column(Modifier.fillMaxSize()) {
                windowChrome()
                BoxWithConstraints(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val windowWidthClass = appWindowWidthClass(maxWidth)
                    CompositionLocalProvider(
                        LocalBackNavigationPolicy provides backNavigationPolicy,
                        LocalNavigator provides navigator,
                        LocalAppWindowWidthClass provides windowWidthClass,
                        LocalAppContentSize provides DpSize(maxWidth, maxHeight),
                    ) {
                        SnackBarToastBox(
                            Modifier
                                .systemBarsPadding()
                                .padding(vertical = 76.dp, horizontal = 12.dp)
                        ) {
                            VersionDialog()
                            OfficialLinkPrompt(navigator, activeOfficialLinkInbox)
                            SharedTransitionScreen(
                                navigator = navigator,
                                transitionSpec = { selectTransition(isPop = false) },
                                popTransitionSpec = { selectTransition(isPop = true) },
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
}

fun AnimatedContentTransitionScope<Scene<AppRoute>>.selectTransition(isPop: Boolean): ContentTransform =
    when {
        isTransitioningOn<HomeScreen, UserProfileScreen>() -> slideScreenTransition(isPop)
        isTransitioningOn<HomeScreen, WorldProfileScreen>() -> slideScreenTransition(isPop)
        isTransitioningOn<HomeScreen, GroupProfileScreen>() -> slideScreenTransition(isPop)
        isTransitioningOn<HomeScreen, AvatarProfileScreen>() -> slideScreenTransition(isPop)
        // 首页头像与整张铭牌是同一个共享元素，主体运动由它承担，屏幕只淡入淡出。
        isTransitioningOn<HomeScreen, MeetupCardDisplayRoute>() -> fadeScreenTransition()
        isTransitioningOn<HomeScreen, MeetupCardEditorRoute>() -> fadeScreenTransition()
        isTransitioningOn<MeetupCardDisplayRoute, MeetupCardEditorRoute>() -> fadeScreenTransition()
        isTransitioningOn<MutualFriendsScreen, UserProfileScreen>() -> slideScreenTransition(isPop)
        isTransitioningOn<CardListDetailScreen, WorldProfileScreen>() -> slideScreenTransition(isPop)
        isTransitioningOn<CardListDetailScreen, AvatarProfileScreen>() -> slideScreenTransition(isPop)
        isTransitioningOn<UserProfileScreen, GroupProfileScreen>() -> slideScreenTransition(isPop)
        isTransitioningOn<UserProfileScreen, GalleryScreen>() -> slideScreenTransition(isPop, SlideOrientation.Horizontal)
        isTransitioningOn<UserProfileScreen, GroupProfileScreen>() -> slideScreenTransition(isPop, SlideOrientation.Horizontal)
        isTransitioningOn<WorldProfileScreen, UserProfileScreen>() -> slideScreenTransition(isPop, SlideOrientation.Horizontal)
        isTransitioningOn<WorldProfileScreen, GroupProfileScreen>() -> slideScreenTransition(isPop, SlideOrientation.Horizontal)
        isTransitioningFromTo<HomeScreen, AuthAnimeScreen>() -> HomeToAuthAnimeTransition
        isTransitioningFromTo<AuthAnimeScreen, HomeScreen>() -> AuthAnimeToHomeTransition
        isTransitioning<StartupAnimeScreen>() -> ContentTransform(EnterTransition.None, ExitTransition.None)
        isTransitioning<AuthAnimeScreen>() -> ContentTransform(EnterTransition.None, ExitTransition.None)
        else -> slideScreenTransition(isPop, SlideOrientation.Horizontal)
    }
