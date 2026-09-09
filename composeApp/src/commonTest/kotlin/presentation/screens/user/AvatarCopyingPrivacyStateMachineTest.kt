package io.github.vrcmteam.vrcm.presentation.screens.user

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AvatarCopyingPrivacyStateMachineTest {
    @Test
    fun failedLoadCanRetryWithoutStartingDuplicateRequests() {
        val stateMachine = AvatarCopyingPrivacyStateMachine()
        stateMachine.bindAccount("usr_a", isAllowed = null)

        val firstRequest = assertNotNull(stateMachine.tryStartLoad("usr_a"))

        assertTrue(stateMachine.state.value.isLoading)
        assertNull(stateMachine.tryStartLoad("usr_a"))
        assertTrue(stateMachine.failLoad(firstRequest))
        assertTrue(stateMachine.state.value.loadFailed)
        assertFalse(stateMachine.state.value.isLoading)
        assertNotNull(stateMachine.tryStartLoad("usr_a"))
    }

    @Test
    fun sameAccountRefreshKeepsUpdateInFlightAndFailurePreservesPreviousValue() {
        val stateMachine = AvatarCopyingPrivacyStateMachine()
        stateMachine.bindAccount("usr_a", isAllowed = true)
        val request = assertNotNull(
            stateMachine.tryStartUpdate("usr_a", requestedValue = false),
        )

        stateMachine.bindAccount("usr_a", isAllowed = true)

        assertTrue(stateMachine.state.value.isSaving)
        assertNull(stateMachine.tryStartUpdate("usr_a", requestedValue = false))
        assertTrue(stateMachine.failUpdate(request))
        assertEquals(true, stateMachine.state.value.isAllowed)
        assertTrue(stateMachine.state.value.updateFailed)

        stateMachine.bindAccount("usr_a", isAllowed = true)

        assertFalse(stateMachine.state.value.updateFailed)
        assertNotNull(stateMachine.tryStartUpdate("usr_a", requestedValue = false))
    }

    @Test
    fun accountSwitchRejectsLateCompletionWithoutTouchingNewRequest() {
        val stateMachine = AvatarCopyingPrivacyStateMachine()
        stateMachine.bindAccount("usr_a", isAllowed = true)
        val oldRequest = assertNotNull(
            stateMachine.tryStartUpdate("usr_a", requestedValue = false),
        )
        stateMachine.bindAccount("usr_b", isAllowed = false)
        val newRequest = assertNotNull(
            stateMachine.tryStartUpdate("usr_b", requestedValue = true),
        )

        assertFalse(stateMachine.completeUpdate(oldRequest, isAllowed = false))
        assertEquals("usr_b", stateMachine.state.value.accountUserId)
        assertEquals(false, stateMachine.state.value.isAllowed)
        assertTrue(stateMachine.state.value.isSaving)

        assertTrue(stateMachine.completeUpdate(newRequest, isAllowed = true))
        assertEquals(true, stateMachine.state.value.isAllowed)
        assertFalse(stateMachine.state.value.isSaving)
    }

    @Test
    fun logoutClearsValueAndInvalidatesInFlightRequest() {
        val stateMachine = AvatarCopyingPrivacyStateMachine()
        stateMachine.bindAccount("usr_a", isAllowed = true)
        val request = assertNotNull(
            stateMachine.tryStartUpdate("usr_a", requestedValue = false),
        )

        stateMachine.bindAccount(userId = null, isAllowed = null)

        assertNull(stateMachine.state.value.accountUserId)
        assertNull(stateMachine.state.value.isAllowed)
        assertFalse(stateMachine.state.value.isSaving)
        assertFalse(stateMachine.failUpdate(request))
    }
}
