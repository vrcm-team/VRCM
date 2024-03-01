package io.github.vrcmteam.vrcm

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


enum class MainRouteEnum(val route: String) {
    StartupAnime("startupAnime"),
    Auth("auth"),
    AuthAnime("authAnime"),
    Home("home"),
    Profile("profile");
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            App()
        }
    }
}

//@Composable
//fun MainContent() {
//    val navController = rememberNavController()
//    VRCMTheme {
//        Surface(
//            modifier = Modifier.fillMaxSize()
//        ) {
//            val buildNavigate: (NavBackStackEntry) ->((MainRouteEnum, Boolean, List<Any>) -> Unit )=
//                {
//                    { mainRouter, isPop, arguments ->
//                        val routePath =
//                            "${mainRouter.route}/${arguments.joinToString("/") { it.toString() }}"
//                        if (isPop) {
//                            navController.navigate(routePath, navPopBuilder(it))
//                        } else {
//                            navController.navigate(routePath)
//                        }
//                    }
//                }
//
//            val popBackStack = { navController.popBackStack() }
//            NavHost(
//                navController = navController,
//                startDestination = MainRouteEnum.StartupAnime.route
//            ) {
//                composable(
//                    MainRouteEnum.StartupAnime.route,
//                    exitTransition = { ExitTransition.None }
//                ) {
//                    StartupAnime {
//                        navController.navigate(MainRouteEnum.Auth.route, navPopBuilder(it))
//                    }
//                }
//
//                composable(
//                    MainRouteEnum.Auth.route,
//                    enterTransition = { EnterTransition.None },
//                ) {
//                    Auth {
//                        navController.navigate(
//                            MainRouteEnum.AuthAnime.route + "/false",
//                            navPopBuilder(it)
//                        )
//                    }
//                }
//                composable(
//                    "${MainRouteEnum.AuthAnime.route}/{isAuthed}",
//                    arguments = listOf(navArgument("isAuthed") { defaultValue = false }),
//                    enterTransition = { fadeIn(tween(500)) },
//                    exitTransition = { fadeOut(tween(500)) },
//                ) {
//                    val isAuthed = it.arguments?.getBoolean("isAuthed") ?: false
//                    AuthAnime(isAuthed) {
//                        val mainRoute = if (isAuthed) MainRouteEnum.Auth else MainRouteEnum.Home
//                        navController.navigate(mainRoute.route, navPopBuilder(it))
//                    }
//                }
//                composable(
//                    MainRouteEnum.Home.route,
//                    enterTransition = { fadeIn(tween(600,300)) },
//                    exitTransition = {
//                        if (!targetState.destination.route!!.startsWith(MainRouteEnum.AuthAnime.route)) {
//                            slideFadeOutTweenContainer(AnimatedContentTransitionScope.SlideDirection.Left)()
//                        } else
//                            fadeOut(tween(600)) +
//                                    slideOut(tween(600)) { IntOffset(0, (it.height * 0.2f).toInt()) }
//                    },
//                ) {
//                    Home(onNavigate = buildNavigate(it))
//                }
//                composable(
//                    "${MainRouteEnum.Profile.route}/{friendId}",
//                    enterTransition = { fadeIn(tween(600,300)) },
//                    popExitTransition = slideFadeOutTweenContainer(AnimatedContentTransitionScope.SlideDirection.Right),
//                ) {
//                    Profile(
//                        userId = it.arguments?.getString("friendId")!!,
//                        popBackStack = { popBackStack() },
//                        onNavigate = buildNavigate(it)
//                    )
//                }
//            }
//        }
//    }
//}

//private fun navPopBuilder(it: NavBackStackEntry): NavOptionsBuilder.() -> Unit = {
//    launchSingleTop = true
//    popUpTo(it.destination.route!!) {
//        inclusive = true
//    }
//}
private fun slideFadeOutTweenContainer(slideDirection :AnimatedContentTransitionScope.SlideDirection) :AnimatedContentTransitionScope<*>.() -> ExitTransition = {
    this.slideOutOfContainer(
        slideDirection,
        tween(400)
//        spring(150f)
    )
}
private fun slideInSpringContainer(slideDirection :AnimatedContentTransitionScope.SlideDirection) :AnimatedContentTransitionScope<*>.() -> EnterTransition = {
    this.slideIntoContainer(
        slideDirection,
        spring(stiffness = Spring.StiffnessLow)
    )
}


