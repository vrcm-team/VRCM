package io.github.vrcmteam.vrcm.presentation.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.extensions.simpleCombinedClickable
import kotlin.test.Test
import kotlin.test.assertEquals

/** 首页头像的手势契约：单击进资料页、长按进身份牌，二者互不触发。 */
@OptIn(ExperimentalTestApi::class)
class HomeMeetupCardGestureTest {
    @Test
    fun clickAndLongClickDispatchDifferentActions() = runComposeUiTest {
        var clicks = 0
        var longClicks = 0
        setContent {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .simpleCombinedClickable(
                        onLongClick = { longClicks++ },
                        onClick = { clicks++ },
                    )
                    .testTag("home-user-avatar"),
            )
        }

        onNodeWithTag("home-user-avatar").performClick()
        assertEquals(1, clicks)
        assertEquals(0, longClicks)

        onNodeWithTag("home-user-avatar").performTouchInput { longClick() }
        assertEquals(1, clicks)
        assertEquals(1, longClicks)
    }
}
