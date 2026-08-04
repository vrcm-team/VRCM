package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope.ResizeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

@OptIn(ExperimentalSharedTransitionApi::class)
class SharedTextBoundsResizeModeTest {
    @Test
    fun sharedTextBoundsDoNotRemeasureTextDuringTheTransition() {
        assertNotSame(ResizeMode.RemeasureToBounds, SharedTextBoundsResizeMode)
    }

    @Test
    fun groupNameUsesTheSameKeyAcrossListAndProfileModels() {
        assertEquals("grp_123GroupName", groupNameSharedKey("grp_123"))
    }
}
