package io.github.vrcmteam.vrcm

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        currentActivity = this
        setContent { App(isConfigurationChange = { isChangingConfigurations }) }
    }
    override fun onDestroy() { if (currentActivity === this) currentActivity = null; super.onDestroy() }
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }
    companion object {
        private const val REQUEST_NOTIFICATIONS = 1
        private var currentActivity: MainActivity? = null
        fun requestNotificationPermissionFromCurrentActivity() = currentActivity?.requestNotificationPermission()
    }
}
