package io.github.vrcmteam.vrcm.presentation.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * sheet 的关闭契约，多个页面依赖它：
 * 用户关闭（点背景）要先滑出再回调；保存中否决关闭时必须留在原地；代码调 hide() 不能再触发一次回调。
 */
@OptIn(ExperimentalTestApi::class)
class AppSheetTest {
    @Test
    fun tappingTheBackdropSlidesTheSheetOutAndThenReportsDismissal() = runDesktopComposeUiTest(width = 400, height = 800) {
        val sheetState = AppSheetState(skipPartiallyExpanded = true)
        var dismissals = 0
        setContent { SheetHost(sheetState) { dismissals++ } }
        waitForIdle()
        assertTrue(sheetState.isVisible)

        onNodeWithTag(HostTag).performTouchInput { click(Offset(centerX, 20f)) }
        waitForIdle()

        assertFalse(sheetState.isVisible)
        assertEquals(1, dismissals)
    }

    @Test
    fun vetoedDismissalKeepsTheSheetOpen() = runDesktopComposeUiTest(width = 400, height = 800) {
        val sheetState = AppSheetState(skipPartiallyExpanded = true) { it != AppSheetValue.Hidden }
        var dismissals = 0
        setContent { SheetHost(sheetState) { dismissals++ } }
        waitForIdle()

        onNodeWithTag(HostTag).performTouchInput { click(Offset(centerX, 20f)) }
        waitForIdle()

        assertTrue(sheetState.isVisible)
        assertEquals(0, dismissals)
    }

    @Test
    fun hidingFromCodeDoesNotReportDismissal() = runDesktopComposeUiTest(width = 400, height = 800) {
        val sheetState = AppSheetState(skipPartiallyExpanded = true)
        var dismissals = 0
        lateinit var scope: CoroutineScope
        setContent {
            scope = rememberCoroutineScope()
            SheetHost(sheetState) { dismissals++ }
        }
        waitForIdle()

        scope.launch { sheetState.hide() }
        waitForIdle()

        assertFalse(sheetState.isVisible)
        assertEquals(0, dismissals)
    }

    @Test
    fun sheetReopensWithAStateThatOutlivesIt() = runDesktopComposeUiTest(width = 400, height = 800) {
        // 调用方常把状态记在 if (visible) 之外：第二次打开时状态里还留着上一次"已收起"的结果
        val sheetState = AppSheetState(skipPartiallyExpanded = true)
        var visible by mutableStateOf(true)
        setContent {
            AppTheme(dark = false, accessibility = AccessibilityPrefs.None) {
                AppOverlayHost(Modifier.testTag(HostTag)) {
                    if (visible) {
                        AppSheet(onDismissRequest = { visible = false }, sheetState = sheetState) {
                            Box(Modifier.fillMaxWidth().height(240.dp).testTag(ContentTag))
                        }
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithTag(HostTag).performTouchInput { click(Offset(centerX, 20f)) }
        waitForIdle()
        assertFalse(visible)

        visible = true
        waitForIdle()

        assertTrue(sheetState.isVisible)
        val top = onNodeWithTag(ContentTag).fetchSemanticsNode().boundsInRoot.top
        assertTrue(top < 800f, "sheet content should be on screen, but its top is at $top")
    }

    @Test
    fun sheetStillOpensWhenItsContentGrowsWhileSlidingIn() = runDesktopComposeUiTest(width = 400, height = 800) {
        val sheetState = AppSheetState(skipPartiallyExpanded = true)
        var contentHeight by mutableStateOf(200.dp)
        mainClock.autoAdvance = false
        setContent {
            AppTheme(dark = false, accessibility = AccessibilityPrefs.None) {
                AppOverlayHost(Modifier.testTag(HostTag)) {
                    AppSheet(onDismissRequest = {}, sheetState = sheetState) {
                        Box(Modifier.fillMaxWidth().height(contentHeight).testTag(ContentTag))
                    }
                }
            }
        }
        // 滑入到一半时内容变高（图片、异步文案加载完）
        mainClock.advanceTimeBy(80)
        contentHeight = 320.dp
        mainClock.autoAdvance = true
        waitForIdle()

        assertTrue(sheetState.isVisible)
        val bounds = onNodeWithTag(ContentTag).fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.bottom <= 800.5f, "sheet should rest fully on screen, but its content ends at ${bounds.bottom}")
    }
}

private const val HostTag = "overlay-host"
private const val ContentTag = "sheet-content"

@androidx.compose.runtime.Composable
private fun SheetHost(sheetState: AppSheetState, onDismissRequest: () -> Unit) {
    AppTheme(dark = false, accessibility = AccessibilityPrefs.None) {
        AppOverlayHost(Modifier.testTag(HostTag)) {
            AppSheet(onDismissRequest = onDismissRequest, sheetState = sheetState) {
                Box(Modifier.fillMaxWidth().height(240.dp))
            }
        }
    }
}
