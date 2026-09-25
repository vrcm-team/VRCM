package io.github.vrcmteam.vrcm.presentation.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 下拉刷新接上真实的列表手势之后的行为：指示器只回应用户发起的刷新，松手之后内容一定回得来。
 */
@OptIn(ExperimentalTestApi::class)
class PullToRefreshBehaviorTest {
    @Test
    fun releasingPastTheThresholdRefreshesAndHoldsTheContentUntilItFinishes() = runPullTest { host ->
        dragList(byY = 400f)

        assertEquals(1, host.refreshCount)
        assertTrue(firstItemTop() > 0.dp)

        host.isRefreshing = false
        settle()

        assertEquals(0.dp, firstItemTop())
    }

    @Test
    fun releasingBeforeTheThresholdDoesNotRefresh() = runPullTest { host ->
        dragList(byY = 60f)

        assertEquals(0, host.refreshCount)
        assertEquals(0.dp, firstItemTop())
    }

    @Test
    fun aRefreshStartedElsewhereLeavesTheContentInPlace() = runPullTest { host ->
        host.isRefreshing = true
        settle()

        assertEquals(0.dp, firstItemTop())
    }

    @Test
    fun theContentSpringsBackWhenTheRequestedRefreshNeverStarts() = runPullTest(startsRefreshing = false) { host ->
        dragList(byY = 400f)

        assertEquals(1, host.refreshCount)
        assertEquals(0.dp, firstItemTop())
    }

    @Test
    fun mouseWheelOvershootingTheTopDoesNotPullTheContent() = runPullTest { host ->
        // 滚轮没有"松手"：从下面滚回顶时富余的滚动量要是算成下拉，内容拉下去就再也回不来
        onNodeWithTag(ListTag).performMouseInput {
            moveTo(center)
            scroll(2f)
        }
        settle()
        onNodeWithTag(ListTag).performMouseInput { repeat(10) { scroll(-5f) } }
        settle()

        assertEquals(0, host.refreshCount)
        assertEquals(0.dp, firstItemTop())
    }

    /** [startsRefreshing]：调用方收到 onRefresh 后会不会把 isRefreshing 置真（不会 = 请求被忽略）。 */
    private class RefreshHost(private val startsRefreshing: Boolean) {
        var isRefreshing by mutableStateOf(false)
        var refreshCount = 0

        fun refresh() {
            refreshCount++
            if (startsRefreshing) isRefreshing = true
        }
    }

    private fun runPullTest(startsRefreshing: Boolean = true, block: ComposeUiTest.(RefreshHost) -> Unit) =
        runDesktopComposeUiTest(width = 400, height = 800) {
            val host = RefreshHost(startsRefreshing)
            setContent {
                AppTheme(dark = false) {
                    AppPullToRefreshBox(
                        isRefreshing = host.isRefreshing,
                        onRefresh = host::refresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(Modifier.fillMaxSize().testTag(ListTag)) {
                            items(100) { index ->
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(60.dp)
                                        .then(if (index == 0) Modifier.testTag(FirstItemTag) else Modifier),
                                )
                            }
                        }
                    }
                }
            }
            // 刷新期间指示器一直在转，时钟自动前进的话永远等不到空闲：手动走时间
            mainClock.autoAdvance = false
            settle()
            block(host)
        }

    private fun ComposeUiTest.firstItemTop() = onNodeWithTag(FirstItemTag).getBoundsInRoot().top

    /** 慢速拖动后松手：不带惯性。 */
    private fun ComposeUiTest.dragList(byY: Float) {
        val steps = 20
        onNodeWithTag(ListTag).performTouchInput {
            down(center)
            repeat(steps) {
                advanceEventTime(50)
                moveBy(Offset(0f, byY / steps))
            }
            advanceEventTime(300)
            up()
        }
        settle()
    }

    /** 让回弹 / 停靠动画走完。 */
    private fun ComposeUiTest.settle() {
        mainClock.advanceTimeBy(3_000)
        waitForIdle()
    }

    private companion object {
        const val ListTag = "list"
        const val FirstItemTag = "first-item"
    }
}
