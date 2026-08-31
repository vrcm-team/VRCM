package io.github.vrcmteam.vrcm.presentation.screens.user

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserReportStateMachineTest {
    @Test
    fun duplicateSubmissionIsRejectedUntilTheCurrentRequestFinishes() {
        val stateMachine = UserReportStateMachine()

        assertTrue(stateMachine.tryStart())
        assertEquals(UserReportState.Submitting, stateMachine.state.value)
        assertFalse(stateMachine.tryStart())

        stateMachine.fail()
        assertEquals(UserReportState.Failed, stateMachine.state.value)
        assertTrue(stateMachine.tryStart())

        stateMachine.complete()
        assertEquals(UserReportState.Submitted, stateMachine.state.value)
        assertFalse(stateMachine.tryStart())

        stateMachine.reset()
        assertEquals(UserReportState.Idle, stateMachine.state.value)
    }

    @Test
    fun resetCannotHideAnActiveSubmission() {
        val stateMachine = UserReportStateMachine()

        stateMachine.tryStart()
        stateMachine.reset()

        assertEquals(UserReportState.Submitting, stateMachine.state.value)
    }
}
