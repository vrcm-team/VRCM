package io.github.kamo.vrcm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.kamo.vrcm.ui.auth.Auth
import io.github.kamo.vrcm.ui.auth.AuthViewModel
import io.github.kamo.vrcm.ui.startup.StartUp
import io.github.kamo.vrcm.ui.theme.VRCMTheme
import io.github.vrchatapi.model.CurrentUser
import io.github.vrchatapi.model.CurrentUserPresence
import kotlinx.coroutines.delay


class MainActivity : ComponentActivity() {
    init {
        CurrentUser.openapiFields += "contentFilters"
        CurrentUserPresence.openapiFields += "userIcon"
        CurrentUserPresence.openapiFields += "currentAvatarTags"
        CurrentUserPresence.openapiFields += "debugflag"
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val authViewModel: AuthViewModel by viewModels<AuthViewModel>()
        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            VRCMTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = "startup"
                    ) {
                        composable("startup", exitTransition = { ExitTransition.None }) {
                            StartUp {
                                navController.navigate("auth") {
                                    popUpTo("startup") {
                                        inclusive = true
                                    }
                                }
                            }
                        }

                        composable("auth", enterTransition = { EnterTransition.None }) {
                            Auth(authViewModel) {
                                navController.navigate("home") {
                                    popUpTo("auth") {
                                        inclusive = true
                                    }
                                }
                            }
                        }
                        composable("home") {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                            ) {
                                LaunchedEffect(Int){
                                    authViewModel.reset()
                                    delay(1000)
                                    navController.navigate("startup") {
                                        popUpTo("home") {
                                            inclusive = true
                                        }
                                    }
                                }
                                Text(
                                    modifier = Modifier
                                        .align(Alignment.Center),
                                    text = "home"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}