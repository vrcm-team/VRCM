package io.github.vrcmteam.vrcm.presentation.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * 导航栏里的玻璃按钮常常先禁用（数据没到）再启用。
 * 玻璃面与启用态无关：切换之后它必须还在，否则按钮只剩一个悬空的符号。
 */
@OptIn(ExperimentalTestApi::class)
class GlassControlEnabledToggleTest {
    @Test
    fun navBarGlassButtonKeepsItsSurfaceWhenItBecomesEnabled() = runDesktopComposeUiTest(width = 320, height = 200) {
        var enabled by mutableStateOf(false)
        setContent {
            AppTheme(dark = false) {
                AppScaffold(
                    topBar = {
                        AppNavBar(
                            title = {},
                            actions = {
                                // 不放符号：按钮中心就是纯玻璃面
                                AppIconButton(
                                    onClick = {},
                                    modifier = Modifier.testTag(ButtonTag),
                                    enabled = enabled,
                                ) {}
                            },
                        )
                    },
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding).testTag(ContentTag))
                }
            }
        }
        waitForIdle()
        val pageColor = centerColorOf(ContentTag)
        val disabledSurface = centerColorOf(ButtonTag)
        assertNotEquals(pageColor, disabledSurface, "玻璃面应当画在页面底色之上")

        enabled = true
        waitForIdle()

        assertEquals(disabledSurface, centerColorOf(ButtonTag))
    }

    private fun ComposeUiTest.centerColorOf(tag: String): Color {
        val pixels = onNodeWithTag(tag).captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2]
    }

    private companion object {
        const val ButtonTag = "button"
        const val ContentTag = "content"
    }
}
