package io.github.vrcmteam.vrcm.presentation.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.compoments.ContentTopInset
import io.github.vrcmteam.vrcm.presentation.compoments.LocalContentTopInset
import io.github.vrcmteam.vrcm.presentation.compoments.withContentTopInset
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 主页顶栏 / 底栏和列表手势接在一起之后的行为：
 * 向下滚收起；向上滚按距离露出；松手时没露完就收回，露完则留下。
 */
@OptIn(ExperimentalTestApi::class)
class HomeBarsScrollBehaviorTest {
    @Test
    fun scrollingDownCollapsesTheBarsBeforeTheListScrolls() = runBarsTest { state, listState ->
        assertEquals(TopBarHeight, onNodeWithTag(FirstItemTag).getBoundsInRoot().top)

        // 拖动量还不够把栏收完：列表一格没滚，松手后栏自己收到底
        dragList(byY = -60f)

        assertEquals(1f, state.hiddenFraction)
        assertEquals(0, listState.firstVisibleItemIndex)
        assertEquals(0, listState.firstVisibleItemScrollOffset)
        // 顶部容器只剩状态栏那一条留在屏幕上，列表让出的高度跟着缩到这一条
        assertEquals(StatusBarHeight, onNodeWithTag(TopBarTag).getBoundsInRoot().bottom)
        assertEquals(StatusBarHeight, onNodeWithTag(FirstItemTag).getBoundsInRoot().top)
        // 底栏整条滑出屏幕下沿
        assertEquals(WindowHeight.dp, onNodeWithTag(BottomBarTag).getBoundsInRoot().top)
    }

    @Test
    fun releasingBeforeTheBarsAreFullyRevealedHidesThemAgain() = runBarsTest { state, _ ->
        dragList(byY = -300f)

        dragList(byY = 70f)

        assertEquals(1f, state.hiddenFraction)
    }

    @Test
    fun fullyRevealedBarsStayUntilTheNextScrollDown() = runBarsTest { state, _ ->
        dragList(byY = -300f)

        dragList(byY = 250f)
        assertEquals(0f, state.hiddenFraction)
        assertEquals(TopBarHeight, onNodeWithTag(TopBarTag).getBoundsInRoot().bottom)

        dragList(byY = -60f)
        assertEquals(1f, state.hiddenFraction)
    }

    private fun runBarsTest(block: ComposeUiTest.(HomeBarsScrollState, LazyListState) -> Unit) =
        runDesktopComposeUiTest(width = 400, height = WindowHeight) {
            val state = HomeBarsScrollState()
            val listState = LazyListState()
            setContent {
                AppTheme(dark = false) {
                    val connection = rememberHomeBarsNestedScrollConnection(state)
                    val (expandedTopPx, rangePx) = with(LocalDensity.current) {
                        TopBarHeight.roundToPx() to (TopBarHeight - StatusBarHeight).toPx()
                    }
                    SideEffect { state.updateCollapseRange(rangePx) }
                    val topInset = remember(state) {
                        ContentTopInset(
                            current = { expandedTopPx - state.collapsed.roundToInt() },
                            expanded = { expandedTopPx },
                        )
                    }
                    Box(Modifier.fillMaxSize()) {
                        // 和主页一样：内容铺满，列表自己把顶部容器盖住的高度让出来
                        CompositionLocalProvider(LocalContentTopInset provides topInset) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().nestedScroll(connection).testTag(ListTag),
                                state = listState,
                                contentPadding = PaddingValues().withContentTopInset(),
                            ) {
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
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(TopBarHeight)
                                .collapseUpwardWith(state)
                                .testTag(TopBarTag),
                        )
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(64.dp)
                                .slideOutDownwardWith(state)
                                .testTag(BottomBarTag),
                        )
                    }
                }
            }
            waitForIdle()
            block(state, listState)
        }

    /** 慢速拖动后松手：不带惯性，松手那一刻栏停在哪里就是哪里。 */
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
        waitForIdle()
    }

    private companion object {
        const val WindowHeight = 800
        val TopBarHeight = 120.dp
        val StatusBarHeight = 24.dp
        const val ListTag = "list"
        const val FirstItemTag = "first-item"
        const val TopBarTag = "top-bar"
        const val BottomBarTag = "bottom-bar"
    }
}
