package io.github.vrcmteam.vrcm.presentation.screens.meetup.display

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 展示页控制层可见性状态机。
 *
 * 初始隐藏；轻点在显示/隐藏之间切换，控制层已显示时的鼠标或按钮活动重新开始
 * [timeout] 计时，超时后自动隐藏。始终只保留一个挂起的隐藏 Job。
 */
class MeetupControlsState(
    private val scope: CoroutineScope,
    private val timeout: Duration = 3.seconds,
) : AutoCloseable {

    private val mutableVisible = MutableStateFlow(false)

    /** 控制层当前是否可见。 */
    val visible: StateFlow<Boolean> = mutableVisible

    private var hideJob: Job? = null
    private var closed = false

    /** 轻点切换控制层；再次轻点会立即收起并取消隐藏倒计时。 */
    fun onTap() {
        if (closed) return
        if (mutableVisible.value) {
            hide()
        } else {
            showAndScheduleHide()
        }
    }

    /** 控制层已显示时延长停留时间；不会因鼠标移动自行弹出。 */
    fun onActivity() {
        if (closed || !mutableVisible.value) return
        showAndScheduleHide()
    }

    private fun showAndScheduleHide() {
        hideJob?.cancel()
        mutableVisible.value = true
        hideJob = scope.launch {
            delay(timeout)
            mutableVisible.value = false
            hideJob = null
        }
    }

    private fun hide() {
        hideJob?.cancel()
        hideJob = null
        mutableVisible.value = false
    }

    /** 取消挂起的隐藏 Job 并复位为隐藏；可重复调用。 */
    override fun close() {
        closed = true
        hide()
    }
}
