package io.github.kamo.vrcm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.kamo.vrcm.ui.auth.Auth
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
        enableEdgeToEdge()
        setContent {
            VRCMTheme {
                Auth()
            }
        }
    }
}




