package io.github.kamo.vrcm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
            MainContent()
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
            NavHost(
                navController = navController,
                startDestination = MainRouteEnum.StartupAnime.route
            ) {
                composable(MainRouteEnum.StartupAnime.route, exitTransition = { ExitTransition.None }) {
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
                    enterTransition = { EnterTransition.None }
                ) {
                    val isAuthed = it.arguments?.getBoolean("isAuthed") ?: false
                    AuthAnime(isAuthed){
                        val mainRoute = if (isAuthed) MainRouteEnum.Auth else MainRouteEnum.Home
                        navController.navigate(mainRoute.route, navOptionsBuilder(it))
                    }
                }
                composable(
                    MainRouteEnum.Home.route,
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



