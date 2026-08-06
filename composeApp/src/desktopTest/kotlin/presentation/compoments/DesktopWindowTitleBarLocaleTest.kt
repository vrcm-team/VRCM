package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowState
import io.github.vrcmteam.vrcm.presentation.settings.LocalSettingsState
import io.github.vrcmteam.vrcm.presentation.settings.data.SettingsVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.LanguageTag
import io.github.vrcmteam.vrcm.presentation.settings.theme.ThemeColor
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DesktopWindowTitleBarLocaleTest {
    @Test
    fun windowControlLabelsUseTheLatestLanguage() = runComposeUiTest {
        val settings = mutableStateOf(
            SettingsVo(
                isDarkTheme = null,
                languageTag = LanguageTag.EN,
                themeColor = ThemeColor.Default,
            ),
        )
        lateinit var composeWindow: ComposeWindow
        SwingUtilities.invokeAndWait {
            composeWindow = ComposeWindow()
        }
        val frameWindowScope = object : FrameWindowScope {
            override val window = composeWindow
        }
        val windowState = WindowState()

        try {
            setContent {
                CompositionLocalProvider(LocalSettingsState provides settings) {
                    MaterialTheme {
                        with(frameWindowScope) {
                            DesktopWindowTitleBar(
                                windowState = windowState,
                                onCloseRequest = {},
                            )
                        }
                    }
                }
            }

            assertEquals(1, onAllNodesWithContentDescription("Minimize").fetchSemanticsNodes().size)
            assertEquals(1, onAllNodesWithContentDescription("Maximize").fetchSemanticsNodes().size)
            assertEquals(1, onAllNodesWithContentDescription("Close").fetchSemanticsNodes().size)
            assertEquals(
                0,
                onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
                    .fetchSemanticsNodes().size,
            )

            runOnIdle {
                settings.value = settings.value.copy(languageTag = LanguageTag.JA)
            }
            waitForIdle()

            assertEquals(0, onAllNodesWithContentDescription("Minimize").fetchSemanticsNodes().size)
            assertEquals(0, onAllNodesWithContentDescription("Maximize").fetchSemanticsNodes().size)
            assertEquals(0, onAllNodesWithContentDescription("Close").fetchSemanticsNodes().size)
            assertEquals(1, onAllNodesWithContentDescription("最小化").fetchSemanticsNodes().size)
            assertEquals(1, onAllNodesWithContentDescription("最大化").fetchSemanticsNodes().size)
            assertEquals(1, onAllNodesWithContentDescription("閉じる").fetchSemanticsNodes().size)
        } finally {
            SwingUtilities.invokeAndWait {
                frameWindowScope.window.dispose()
            }
        }
    }
}
