package io.github.vrcmteam.vrcm.presentation.screens.meetup.display

import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Android 沉浸/常亮效果：RESUMED 时加 [WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON]
 * 并隐藏 system bars；ON_STOP 与 dispose 幂等恢复进入前记录的状态。不修改亮度。
 */
@Composable
internal actual fun MeetupPresentationEffect(enabled: Boolean) {
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(activity, lifecycleOwner, enabled) {
        val window = activity?.window
        if (!enabled || window == null) {
            return@DisposableEffect onDispose {}
        }
        val controller = MeetupWindowPresentation(window)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // addObserver 会补发当前状态对应的事件，页面已在前台时立即 acquire
                Lifecycle.Event.ON_RESUME -> controller.acquire()
                Lifecycle.Event.ON_STOP -> controller.release()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.release()
        }
    }
}

/** 获得/释放式窗口沉浸控制；[acquire] 时记录进入前状态，重复调用不覆盖记录。 */
private class MeetupWindowPresentation(private val window: Window) {

    private var active = false
    private var hadKeepScreenOn = false
    private var previousBarsBehavior = 0

    fun acquire() {
        if (active) return
        active = true
        hadKeepScreenOn =
            window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        previousBarsBehavior = insetsController.systemBarsBehavior
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    fun release() {
        if (!active) return
        active = false
        if (!hadKeepScreenOn) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior = previousBarsBehavior
        // 无条件恢复显示：edge-to-edge 下 rootWindowInsets 的可见性读数在部分设备上
        // 不可靠，条件恢复会让退出后系统栏停留在隐藏状态；应用内没有其他隐藏系统栏
        // 的页面，show 幂等无害。
        insetsController.show(WindowInsetsCompat.Type.systemBars())
    }
}
