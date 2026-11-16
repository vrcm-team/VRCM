package io.github.vrcmteam.vrcm.presentation.screens.user

import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileUpdateStateMachineTest {
    @Test
    fun duplicateStartIsRejectedAndFailureAllowsRetry() {
        val stateMachine = ProfileUpdateStateMachine()

        assertTrue(stateMachine.tryStart())
        assertFalse(stateMachine.tryStart())
        assertTrue(stateMachine.state.value.isSaving)

        stateMachine.fail()

        assertFalse(stateMachine.state.value.isSaving)
        assertEquals(1, stateMachine.state.value.completedRequestId)
        assertNull(stateMachine.state.value.savedProfile)
        assertTrue(stateMachine.tryStart())
    }

    @Test
    fun completionPublishesTheServerAcceptedProfile() {
        val stateMachine = ProfileUpdateStateMachine()
        val savedProfile = UserProfileVo(id = "usr_self", bio = "Saved", bioLinks = listOf("https://example.com/normalized"))

        assertTrue(stateMachine.tryStart())
        stateMachine.complete(savedProfile)

        assertFalse(stateMachine.state.value.isSaving)
        assertEquals(1, stateMachine.state.value.completedRequestId)
        assertEquals(savedProfile, stateMachine.state.value.savedProfile)
    }
}
