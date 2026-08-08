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
 * 初始隐藏；任意交互（轻点、触摸、鼠标活动）显示控制层并重新开始 [timeout] 计时，
 * 超时后自动隐藏。始终只保留一个挂起的隐藏 Job，新的交互先取消旧 Job 再重新计时。
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

    /** 展示控制层并重置隐藏倒计时；[close] 之后调用不再生效。 */
    fun onInteraction() {
        if (closed) return
        hideJob?.cancel()
        mutableVisible.value = true
        hideJob = scope.launch {
            delay(timeout)
            mutableVisible.value = false
        }
    }

    /** 取消挂起的隐藏 Job 并复位为隐藏；可重复调用。 */
    override fun close() {
        closed = true
        hideJob?.cancel()
        hideJob = null
        mutableVisible.value = false
    }
}
