package io.github.vrcmteam.vrcm.presentation.screens.meetup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** 旋转铭牌必须占据高宽互换后的空间，否则侧签栏会重叠或留白。 */
@OptIn(ExperimentalTestApi::class)
class RotateClockwiseTest {
    @Test
    fun rotatedContentReportsSwappedSize() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.rotateClockwise().testTag("rotated")) {
                Box(modifier = Modifier.size(width = 120.dp, height = 40.dp))
            }
        }

        val bounds = onNodeWithTag("rotated").getBoundsInRoot()
        val width = bounds.right.value - bounds.left.value
        val height = bounds.bottom.value - bounds.top.value
        assertTrue(abs(width - 40f) < 1f, "Expected rotated width 40dp but was $width")
        assertTrue(abs(height - 120f) < 1f, "Expected rotated height 120dp but was $height")
    }
}
