package io.github.vrcmteam.vrcm.presentation.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ContainerTransformTokenTest {
    @Test
    fun sameItemAtDifferentCompositionPositionsUsesDifferentTokens() {
        assertNotEquals(
            containerTransformToken(seed = "usr_test", positionKey = 1L),
            containerTransformToken(seed = "usr_test", positionKey = 2L),
        )
    }

    @Test
    fun sameItemAtTheSameCompositionPositionKeepsItsToken() {
        assertEquals(
            containerTransformToken(seed = "usr_test", positionKey = 7L),
            containerTransformToken(seed = "usr_test", positionKey = 7L),
        )
    }

    @Test
    fun differentItemsAtTheSameCompositionPositionUseDifferentTokens() {
        assertNotEquals(
            containerTransformToken(seed = "usr_first", positionKey = 1L),
            containerTransformToken(seed = "usr_second", positionKey = 1L),
        )
    }
}
