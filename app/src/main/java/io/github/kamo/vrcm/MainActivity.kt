package io.github.kamo.vrcm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.kamo.vrcm.ui.auth.Auth
import io.github.kamo.vrcm.ui.home.Home
import io.github.kamo.vrcm.ui.startup.StartUp
import io.github.kamo.vrcm.ui.theme.VRCMTheme
import io.github.vrchatapi.model.CurrentUser
import io.github.vrchatapi.model.CurrentUserPresence


enum class MainRouteEnum(val route: String) {
    Startup("startup"),
    Auth("auth"),
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
            modifier = Modifier
                .fillMaxSize()
        ) {
            NavHost(
                navController = navController,
                startDestination = MainRouteEnum.Startup.route
            ) {
                composable(MainRouteEnum.Startup.route, exitTransition = { ExitTransition.None }) {
                    StartUp {
                        navController.navigate(it.route) {
                            popUpTo(MainRouteEnum.Startup.route) {
                                inclusive = true
                            }
                        }
                    }
                }

                composable(MainRouteEnum.Auth.route, enterTransition = { EnterTransition.None }) {
                    Auth {
                        navController.navigate(MainRouteEnum.Home.route) {
                            popUpTo(MainRouteEnum.Auth.route) {
                                inclusive = true
                            }
                        }
                    }
                }
                composable(MainRouteEnum.Home.route) {
                    Home {
                        navController.navigate(MainRouteEnum.Startup.route) {
                            popUpTo(MainRouteEnum.Home.route) {
                                inclusive = true
                            }
                        }
                    }
                }
            }
        }
    }
}


