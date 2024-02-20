package io.github.kamo.vrcm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
    Home("home");
}

class MainActivity : ComponentActivity() {
    init {
        CurrentUser.openapiFields += "contentFilters"
        CurrentUserPresence.openapiFields += "userIcon"
        CurrentUserPresence.openapiFields += "currentAvatarTags"
        CurrentUserPresence.openapiFields += "debugflag"
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
    var isAuthed = false
    VRCMTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
//            var mainRoute by remember { mutableStateOf(MainRouteEnum.StartupAnime) }
//            AnimatedContent(
//                label = "AuthSurfaceChange",
//                targetState = mainRoute,
//                transitionSpec ={ EnterTransition.None.togetherWith(ExitTransition.None) }
//            ){
//                println("AuthAnime $isAuthed")
//                println("MainRouteEnum $it")
//                when (it) {
//
//                    MainRouteEnum.StartupAnime -> StartupAnime {
//                        mainRoute = MainRouteEnum.Auth
//                    }
//                    MainRouteEnum.Auth -> Auth{
//                        isAuthed = false
//                        mainRoute = MainRouteEnum.AuthAnime
//                    }
//                    MainRouteEnum.AuthAnime -> AuthAnime(isAuthed) {
//                        mainRoute  = if (isAuthed) MainRouteEnum.Auth else MainRouteEnum.Home
//                    }
//                    MainRouteEnum.Home -> Home {
//                        isAuthed = true
//                        mainRoute  = MainRouteEnum.AuthAnime
//                    }
//                }
//            }
            NavHost(
                navController = navController,
                startDestination = MainRouteEnum.StartupAnime.route
            ) {
                composable(
                    MainRouteEnum.StartupAnime.route,
                    exitTransition = { ExitTransition.None }) {
                    StartupAnime {
                        navController.navigate(MainRouteEnum.Auth.route, navOptionsBuilder(it))
                    }
                }

                composable(
                    MainRouteEnum.Auth.route,
                    enterTransition = { EnterTransition.None }
                ) {
                    Auth {
                        navController.navigate(MainRouteEnum.AuthAnime.route + "/false", navOptionsBuilder(it))
                    }
                }
                composable(
                    "${MainRouteEnum.AuthAnime.route}/{isAuthed}",
                    arguments = listOf(navArgument("isAuthed") { defaultValue = false }),
                    enterTransition = { fadeIn(tween(500)) },
                    exitTransition = { fadeOut(tween(500)) },
                ) {
                    val isAuthed = it.arguments?.getBoolean("isAuthed") ?: false
                    AuthAnime(isAuthed){
                        val mainRoute = if (isAuthed) MainRouteEnum.Auth else MainRouteEnum.Home
                        navController.navigate(mainRoute.route, navOptionsBuilder(it))
                    }
                }
                composable(
                    MainRouteEnum.Home.route,
                    enterTransition = { fadeIn(tween(1000)) },
                    exitTransition = {
                        fadeOut(tween(500, 500)) + slideOut(tween(1000)) {
                            IntOffset(
                                0,
                                (it.height * 0.6f).toInt()
                            )
                        }
                    }
                ) {
                    Home {
                        navController.navigate(MainRouteEnum.AuthAnime.route + "/true", navOptionsBuilder(it))
                    }
                }
            }
        }
    }
}


private fun navOptionsBuilder(it: NavBackStackEntry): NavOptionsBuilder.() -> Unit = {
        launchSingleTop = true
        popUpTo(it.destination.route!!) {
            inclusive = true
        }
    }



