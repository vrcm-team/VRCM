package io.github.vrcmteam.vrcm.presentation.screens.meetup.display

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIApplication
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController
import platform.UIKit.setNeedsUpdateOfHomeIndicatorAutoHidden

/**
 * iOS 沉浸/常亮效果：RESUMED 时禁用 idle timer 并隐藏状态栏/home indicator；
 * ON_STOP（含应用退后台）与 dispose 幂等恢复进入前记录的状态。不修改 Info.plist。
 */
@Composable
internal actual fun MeetupPresentationEffect(enabled: Boolean) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, enabled) {
        if (!enabled) {
            return@DisposableEffect onDispose {}
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // addObserver 会补发当前状态对应的事件，页面已在前台时立即 acquire
                Lifecycle.Event.ON_RESUME -> IosMeetupPresentationState.acquire()
                Lifecycle.Event.ON_STOP -> IosMeetupPresentationState.release()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            IosMeetupPresentationState.release()
        }
    }
}

/**
 * 沉浸展示单例状态：获得/释放式切换 idle timer，
 * 并通知 root controller 刷新状态栏与 home indicator 偏好。
 */
object IosMeetupPresentationState {

    private var active = false
    private var previousIdleTimerDisabled = false

    /** root controller 据此决定是否隐藏状态栏与 home indicator。 */
    var immersive: Boolean = false
        private set

    /** 由 root controller 注册，用于触发系统 UI 偏好刷新。 */
    internal var onChanged: (() -> Unit)? = null

    /** 进入沉浸：记录并禁用 idle timer；重复调用不覆盖已记录的状态。 */
    fun acquire() {
        if (active) return
        active = true
        val application = UIApplication.sharedApplication
        previousIdleTimerDisabled = application.idleTimerDisabled
        application.idleTimerDisabled = true
        immersive = true
        onChanged?.invoke()
    }

    /** 退出沉浸：恢复进入前的 idle timer 设置并还原系统 UI；可重复调用。 */
    fun release() {
        if (!active) return
        active = false
        UIApplication.sharedApplication.idleTimerDisabled = previousIdleTimerDisabled
        immersive = false
        onChanged?.invoke()
    }
}

/**
 * 薄 root wrapper：以 child controller 方式全屏承载 Compose 内容，
 * 仅负责把 [IosMeetupPresentationState.immersive] 映射为状态栏/home indicator 偏好，
 * 状态栏样式仍委托给 Compose controller。
 */
@OptIn(ExperimentalForeignApi::class)
class MeetupPresentationRootViewController(
    private val content: UIViewController,
) : UIViewController(nibName = null, bundle = null) {

    init {
        IosMeetupPresentationState.onChanged = {
            setNeedsStatusBarAppearanceUpdate()
            setNeedsUpdateOfHomeIndicatorAutoHidden()
        }
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        addChildViewController(content)
        content.view.setFrame(view.bounds)
        content.view.autoresizingMask =
            UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
        view.addSubview(content.view)
        content.didMoveToParentViewController(this)
    }

    override fun prefersStatusBarHidden(): Boolean = IosMeetupPresentationState.immersive

    // 绑定里 prefersHomeIndicatorAutoHidden/childViewControllerForStatusBarStyle 是
    // category 扩展、无法 override；声明同名成员即可生成相同 selector，运行时由 UIKit 命中。
    @Suppress("unused")
    fun prefersHomeIndicatorAutoHidden(): Boolean = IosMeetupPresentationState.immersive

    @Suppress("unused")
    fun childViewControllerForStatusBarStyle(): UIViewController? = content
}
