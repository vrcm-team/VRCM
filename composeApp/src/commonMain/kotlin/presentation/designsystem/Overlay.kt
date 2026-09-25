package io.github.vrcmteam.vrcm.presentation.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// ---------- 覆盖层：sheet 这类整屏模态画在应用自己的组合里，而不是另开平台窗口 ----------
// 这样三端的布局、窗口内边距和动画完全一致，也不受 Android 对话框窗口不铺满系统栏的限制。

@Stable
internal class OverlayEntry(val id: Int) {
    var content: @Composable () -> Unit by mutableStateOf({})
    var locals: CompositionLocalContext? by mutableStateOf(null)
}

@Stable
class AppOverlayHostState internal constructor() {
    internal val entries = mutableStateListOf<OverlayEntry>()
    private var nextId = 0
    internal fun newEntry(): OverlayEntry = OverlayEntry(nextId++)
}

/** 为 null 表示不在 [AppOverlayHost] 里（单独渲染某个组件的测试等），覆盖层退回平台对话框窗口。 */
val LocalAppOverlayHost = staticCompositionLocalOf<AppOverlayHostState?> { null }

/**
 * 覆盖层里的返回键处理由宿主应用注入（设计系统不依赖应用的导航层）。
 * 为 null 时覆盖层不拦截返回。
 */
val LocalAppOverlayBackHandler = staticCompositionLocalOf<(@Composable (enabled: Boolean, onBack: () -> Unit) -> Unit)?> { null }

/** 放在应用根部：[content] 之上依次叠放各个覆盖层，后出现的在上。 */
@Composable
fun AppOverlayHost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = remember { AppOverlayHostState() }
    CompositionLocalProvider(LocalAppOverlayHost provides state) {
        Box(modifier.fillMaxSize()) {
            content()
            state.entries.forEach { entry ->
                key(entry.id) {
                    // 覆盖层在宿主处组合，但沿用调用处的 CompositionLocal（主题、导航器、ViewModel 宿主等）
                    entry.locals?.let { locals -> CompositionLocalProvider(locals) { entry.content() } }
                }
            }
        }
    }
}

/** 把 [content] 交给最近的 [AppOverlayHost] 整屏叠放；离开组合时一并移除。 */
@Composable
internal fun AppOverlay(content: @Composable () -> Unit) {
    val host = LocalAppOverlayHost.current
    if (host == null) {
        Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false), content = content)
        return
    }
    val locals = currentCompositionLocalContext
    val entry = remember(host) { host.newEntry() }
    SideEffect {
        entry.content = content
        entry.locals = locals
    }
    DisposableEffect(host, entry) {
        host.entries += entry
        onDispose { host.entries -= entry }
    }
}
