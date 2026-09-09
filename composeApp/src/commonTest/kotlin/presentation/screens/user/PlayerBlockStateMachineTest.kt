package io.github.vrcmteam.vrcm.presentation.screens.user

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerBlockStateMachineTest {
    @Test
    fun failedUpdateKeepsKnownStateAndAllowsRetry() {
        val machine = loadedMachine(isBlocked = false)

        val failedUpdate = assertNotNull(machine.tryStartUpdate(blocked = true))
        assertTrue(machine.state.value.isUpdating)
        assertFalse(machine.state.value.isBlocked ?: true)
        assertNull(machine.tryStartUpdate(blocked = true))

        assertTrue(machine.failUpdate(failedUpdate))
        assertFalse(machine.state.value.isUpdating)
        assertFalse(machine.state.value.isBlocked ?: true)

        val retry = assertNotNull(machine.tryStartUpdate(blocked = true))
        assertTrue(machine.completeUpdate(retry))
        assertTrue(machine.state.value.isBlocked == true)
    }

    @Test
    fun failedInitialLoadCanBeRetried() {
        val machine = PlayerBlockStateMachine()
        val failedLoad = assertNotNull(machine.tryStartLoad())

        assertTrue(machine.failLoad(failedLoad))
        assertTrue(machine.state.value.loadFailed)
        assertNull(machine.state.value.isBlocked)

        val retry = assertNotNull(machine.tryStartLoad())
        assertTrue(machine.completeLoad(retry, isBlocked = true))
        assertEquals(PlayerBlockState(isBlocked = true), machine.state.value)
    }

    @Test
    fun accountInvalidationRejectsAnOlderCompletion() {
        val machine = PlayerBlockStateMachine()
        val request = assertNotNull(machine.tryStartLoad())

        machine.invalidate()

        assertFalse(machine.completeLoad(request, isBlocked = true))
        assertEquals(
            PlayerBlockState(isSessionAvailable = false),
            machine.state.value,
        )
    }

    private fun loadedMachine(isBlocked: Boolean): PlayerBlockStateMachine =
        PlayerBlockStateMachine().also { machine ->
            val request = assertNotNull(machine.tryStartLoad())
            assertTrue(machine.completeLoad(request, isBlocked))
        }
}
