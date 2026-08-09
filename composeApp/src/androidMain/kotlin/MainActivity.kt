package io.github.vrcmteam.vrcm

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.vrcmteam.vrcm.core.shared.AppDeepLinks

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        // 只在全新启动时消费启动 intent。本 Activity 未声明 configChanges，旋转屏幕
        // 或进程恢复都会带着同一个 intent 重跑 onCreate，重复投递会把已经翻到别处的
        // 用户又拽回深链目标页。
        if (savedInstanceState == null) handleDeepLink(intent)
        setContent {
            App(isConfigurationChange = { isChangingConfigurations })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 更新 Activity 持有的 intent，避免重建时又拿到启动时那个旧的。
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data ?: return
        AppDeepLinks.offerUrl(uri.toString())
    }
}
