package io.github.kamo.vrcm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.kamo.vrcm.ui.auth.Auth
import io.github.kamo.vrcm.ui.home.Home
import io.github.kamo.vrcm.ui.profile.Profile
import io.github.kamo.vrcm.ui.theme.VRCMTheme
import io.github.kamo.vrcm.ui.util.AuthAnime
import io.github.kamo.vrcm.ui.util.StartupAnime
import io.github.vrchatapi.model.CurrentUser
import io.github.vrchatapi.model.CurrentUserPresence
import org.koin.compose.KoinContext


enum class MainRouteEnum(val route: String) {
    StartupAnime("startupAnime"),
    Auth("auth"),
    AuthAnime("authAnime"),
    Home("home"),
    Profile("profile");
}

class MainActivity : ComponentActivity() {
    init {
        CurrentUser.openapiFields += "contentFilters"
        CurrentUserPresence.openapiFields += "userIcon"
        CurrentUserPresence.openapiFields += "currentAvatarTags"
        CurrentUserPresence.openapiFields += "debugflag"
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KoinContext {
                MainContent()
            }
        }
    }
}

@Composable
fun MainContent() {
    val navController = rememberNavController()
    VRCMTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            val onNavigate: NavBackStackEntry.(MainRouteEnum, Boolean, List<Any>) -> Unit =
                remember {
                    { mainRouter, isPop, arguments ->
                        val routePath =
                            "${mainRouter.route}/${arguments.joinToString("/") { it.toString() }}"
                        if (isPop) {
                            navController.navigate(routePath, navPopBuilder(this))
                        } else {
                            navController.navigate(routePath)
                        }
                    }
                }
            val popBackStack = remember { { navController.popBackStack() } }
            NavHost(
                navController = navController,
                startDestination = MainRouteEnum.StartupAnime.route
            ) {
                composable(
                    MainRouteEnum.StartupAnime.route,
                    exitTransition = { ExitTransition.None }
                ) {
                    StartupAnime {
                        navController.navigate(MainRouteEnum.Auth.route, navPopBuilder(it))
                    }
                }

                composable(
                    MainRouteEnum.Auth.route,
                    enterTransition = { EnterTransition.None },
                ) {
                    Auth {
                        navController.navigate(
                            MainRouteEnum.AuthAnime.route + "/false",
                            navPopBuilder(it)
                        )
                    }
                }
                composable(
                    "${MainRouteEnum.AuthAnime.route}/{isAuthed}",
                    arguments = listOf(navArgument("isAuthed") { defaultValue = false }),
                    enterTransition = { fadeIn(tween(500)) },
                    exitTransition = { fadeOut(tween(500)) },
                ) {
                    val isAuthed = it.arguments?.getBoolean("isAuthed") ?: false
                    AuthAnime(isAuthed) {
                        val mainRoute = if (isAuthed) MainRouteEnum.Auth else MainRouteEnum.Home
                        navController.navigate(mainRoute.route, navPopBuilder(it))
                    }
                }
                composable(
                    MainRouteEnum.Home.route,
                    enterTransition = { fadeIn(tween(600,300)) },
                    exitTransition = {
                        if (!targetState.destination.route!!.startsWith(MainRouteEnum.AuthAnime.route)) {
                            slideFadeOutTweenContainer(AnimatedContentTransitionScope.SlideDirection.Left)()
                        } else
                            fadeOut(tween(600)) +
                                    slideOut(tween(600)) { IntOffset(0, (it.height * 0.2f).toInt()) }
                    },
                ) {
                    Home { mainRouter, isPop, arguments ->
                        it.onNavigate(mainRouter, isPop, arguments)
                    }
                }
                composable(
                    "${MainRouteEnum.Profile.route}/{friendId}",
                    enterTransition = { fadeIn(tween(600,300)) },
                    popExitTransition = slideFadeOutTweenContainer(AnimatedContentTransitionScope.SlideDirection.Right),
                ) { stackEntry ->
                    Profile(
                        userId = stackEntry.arguments?.getString("friendId")!!,
                        popBackStack = { popBackStack() }
                    ) { mainRouter, isPop, arguments ->
                        stackEntry.onNavigate(mainRouter, isPop, arguments)
                    }
                }
            }
        }
    }
}

private fun navPopBuilder(it: NavBackStackEntry): NavOptionsBuilder.() -> Unit = {
    launchSingleTop = true
    popUpTo(it.destination.route!!) {
        inclusive = true
    }
}
private fun slideFadeOutTweenContainer(slideDirection :AnimatedContentTransitionScope.SlideDirection) :AnimatedContentTransitionScope<*>.() -> ExitTransition = {
    this.slideOutOfContainer(
        slideDirection,
        tween(500)
//        spring(stiffness = 150f)
    ) + fadeOut(
        tween(500)
    )
}
private fun slideInSpringContainer(slideDirection :AnimatedContentTransitionScope.SlideDirection) :AnimatedContentTransitionScope<*>.() -> EnterTransition = {
    this.slideIntoContainer(
        slideDirection,
        spring(stiffness = Spring.StiffnessLow)
    )
}


