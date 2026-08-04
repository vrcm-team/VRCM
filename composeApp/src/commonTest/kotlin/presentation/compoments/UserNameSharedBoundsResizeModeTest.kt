package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope.ResizeMode
import kotlin.test.Test
import kotlin.test.assertNotSame

@OptIn(ExperimentalSharedTransitionApi::class)
class UserNameSharedBoundsResizeModeTest {
    @Test
    fun userNameBoundsDoNotRemeasureTextDuringTheTransition() {
        assertNotSame(ResizeMode.RemeasureToBounds, UserNameBoundsResizeMode)
    }
}
