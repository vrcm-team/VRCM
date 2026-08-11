package io.github.vrcmteam.vrcm.presentation.screens.user

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BioLinksUpdateStateMachineTest {
    @Test
    fun duplicateStartIsRejectedAndFailureAllowsRetry() {
        val stateMachine = BioLinksUpdateStateMachine()

        assertTrue(stateMachine.tryStart())
        assertFalse(stateMachine.tryStart())
        assertTrue(stateMachine.state.value.isSaving)

        stateMachine.fail()

        assertFalse(stateMachine.state.value.isSaving)
        assertEquals(1, stateMachine.state.value.completedRequestId)
        assertNull(stateMachine.state.value.savedLinks)
        assertTrue(stateMachine.tryStart())
    }

    @Test
    fun completionPublishesTheServerAcceptedLinks() {
        val stateMachine = BioLinksUpdateStateMachine()
        val serverLinks = listOf("https://example.com/normalized")

        assertTrue(stateMachine.tryStart())
        stateMachine.complete(serverLinks)

        assertFalse(stateMachine.state.value.isSaving)
        assertEquals(1, stateMachine.state.value.completedRequestId)
        assertEquals(serverLinks, stateMachine.state.value.savedLinks)
    }
}
