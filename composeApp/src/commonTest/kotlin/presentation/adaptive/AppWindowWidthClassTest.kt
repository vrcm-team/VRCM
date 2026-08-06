package io.github.vrcmteam.vrcm.presentation.adaptive

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class AppWindowWidthClassTest {

    @Test
    fun classifiesWindowWidthAtLayoutBoundaries() {
        assertEquals(AppWindowWidthClass.Compact, appWindowWidthClass(599.dp))
        assertEquals(AppWindowWidthClass.Medium, appWindowWidthClass(600.dp))
        assertEquals(AppWindowWidthClass.Medium, appWindowWidthClass(839.dp))
        assertEquals(AppWindowWidthClass.Expanded, appWindowWidthClass(840.dp))
    }
}
