package io.github.vrcmteam.vrcm.presentation.screens.settings

import io.github.vrcmteam.vrcm.network.api.invite.data.InviteMessageData
import io.github.vrcmteam.vrcm.network.api.invite.data.InviteMessageType
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.testing.MainDispatcherTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InviteMessageSlotsModelTest : MainDispatcherTest() {
    @Test
    fun accountChangeReplacesContentAndDiscardsTheOlderLoadResult() = runTest {
        val firstSession = InviteMessageSession("usr_first", 1)
        val secondSession = InviteMessageSession("usr_second", 2)
        val firstLoad = CompletableDeferred<Unit>()
        val source = FakeInviteMessageSlotsSource(firstSession).apply {
            loadHandler = { session, type ->
                if (session == firstSession) {
                    withContext(NonCancellable) { firstLoad.await() }
                    success(session, listOf(message(type, "stale")))
                } else {
                    success(session, listOf(message(type, "current")))
                }
            }
        }
        val model = createModel(source)

        source.sessionFlow.value = secondSession
        assertEquals("current", model.state.value.messages.single().message)

        firstLoad.complete(Unit)

        assertEquals(secondSession, model.state.value.session)
        assertEquals("current", model.state.value.messages.single().message)
        assertFalse(model.state.value.loadFailed)
    }

    @Test
    fun tokenRenewalDuringLoadAcceptsTheResponseBoundToTheRenewedSession() = runTest {
        val firstSession = InviteMessageSession("usr_same", 1)
        val renewedSession = InviteMessageSession("usr_same", 2)
        val source = FakeInviteMessageSlotsSource(firstSession)
        source.loadHandler = { requestSession, type ->
            assertEquals(firstSession, requestSession)
            source.sessionFlow.value = renewedSession
            success(renewedSession, listOf(message(type, "Renewed load")))
        }

        val model = createModel(source)

        assertEquals(renewedSession, model.state.value.session)
        assertEquals("Renewed load", model.state.value.messages.single().message)
        assertFalse(model.state.value.isLoading)
        assertEquals(1, source.loadCalls)
    }

    @Test
    fun unrelatedSameAccountTokenChangeReloadsAfterDiscardingTheOlderLoadResponse() = runTest {
        val firstSession = InviteMessageSession("usr_same", 1)
        val currentSession = InviteMessageSession("usr_same", 2)
        val firstLoad = CompletableDeferred<Unit>()
        val source = FakeInviteMessageSlotsSource(firstSession).apply {
            loadHandler = { requestSession, type ->
                if (requestSession == firstSession) {
                    withContext(NonCancellable) { firstLoad.await() }
                    success(firstSession, listOf(message(type, "Stale")))
                } else {
                    success(currentSession, listOf(message(type, "Current")))
                }
            }
        }
        val model = createModel(source)

        source.sessionFlow.value = currentSession
        assertEquals(currentSession, model.state.value.session)
        assertEquals(1, source.loadCalls)

        firstLoad.complete(Unit)

        assertEquals(currentSession, model.state.value.session)
        assertEquals("Current", model.state.value.messages.single().message)
        assertFalse(model.state.value.isLoading)
        assertFalse(model.state.value.loadFailed)
        assertEquals(2, source.loadCalls)
    }

    @Test
    fun unrelatedSameAccountTokenChangeReloadsWhenTheOlderLoadReturnsNull() = runTest {
        val firstSession = InviteMessageSession("usr_same", 1)
        val currentSession = InviteMessageSession("usr_same", 2)
        val firstLoad = CompletableDeferred<Unit>()
        val source = FakeInviteMessageSlotsSource(firstSession).apply {
            loadHandler = { requestSession, type ->
                if (requestSession == firstSession) {
                    withContext(NonCancellable) { firstLoad.await() }
                    null
                } else {
                    success(currentSession, listOf(message(type, "Current after null")))
                }
            }
        }
        val model = createModel(source)

        source.sessionFlow.value = currentSession
        firstLoad.complete(Unit)

        assertEquals(currentSession, model.state.value.session)
        assertEquals("Current after null", model.state.value.messages.single().message)
        assertFalse(model.state.value.isLoading)
        assertNull(model.state.value.pendingMutation)
        assertEquals(2, source.loadCalls)
    }

    @Test
    fun typeSwitchDiscardsTheOlderCollectionResult() = runTest {
        val session = InviteMessageSession("usr_current", 1)
        val firstLoad = CompletableDeferred<Unit>()
        val source = FakeInviteMessageSlotsSource(session).apply {
            loadHandler = { requestSession, type ->
                if (type == InviteMessageType.Message) {
                    withContext(NonCancellable) { firstLoad.await() }
                    success(requestSession, listOf(message(type, "Old type")))
                } else {
                    success(requestSession, listOf(message(type, "Selected type")))
                }
            }
        }
        val model = createModel(source)

        model.selectType(InviteMessageType.Response)
        assertEquals("Selected type", model.state.value.messages.single().message)

        firstLoad.complete(Unit)

        assertEquals(InviteMessageType.Response, model.state.value.selectedType)
        assertEquals(InviteMessageType.Response, model.state.value.messages.single().messageType)
        assertEquals("Selected type", model.state.value.messages.single().message)
    }

    @Test
    fun duplicateUpdateIsRejectedWhileTheFirstRequestIsInFlight() = runTest {
        val session = InviteMessageSession("usr_current", 1)
        val updateGate = CompletableDeferred<Unit>()
        val source = FakeInviteMessageSlotsSource(session).apply {
            loadHandler = { requestSession, type ->
                success(requestSession, listOf(message(type, "Before")))
            }
            updateHandler = { requestSession, type, slot, value ->
                updateCalls++
                updateGate.await()
                success(requestSession, listOf(message(type, value, slot)))
            }
        }
        val model = createModel(source)

        assertTrue(model.updateMessage(slot = 0, value = "After"))
        assertFalse(model.updateMessage(slot = 0, value = "Duplicate"))
        assertEquals(1, source.updateCalls)

        updateGate.complete(Unit)

        assertNull(model.state.value.pendingMutation)
        assertEquals("After", model.state.value.messages.single().message)
        assertEquals(InviteMessageFeedbackKind.Updated, model.state.value.feedback?.kind)
    }

    @Test
    fun tokenRenewalDuringMutationAcceptsTheResponseBoundToTheRenewedSession() = runTest {
        val firstSession = InviteMessageSession("usr_same", 1)
        val renewedSession = InviteMessageSession("usr_same", 2)
        val source = FakeInviteMessageSlotsSource(firstSession).apply {
            loadHandler = { requestSession, type ->
                success(requestSession, listOf(message(type, "Before")))
            }
        }
        source.updateHandler = { _, type, slot, value ->
            source.updateCalls++
            source.sessionFlow.value = renewedSession
            success(renewedSession, listOf(message(type, value, slot)))
        }
        val model = createModel(source)

        assertTrue(model.updateMessage(slot = 0, value = "After renewal"))

        assertEquals(renewedSession, model.state.value.session)
        assertEquals("After renewal", model.state.value.messages.single().message)
        assertEquals(InviteMessageFeedbackKind.Updated, model.state.value.feedback?.kind)
        assertNull(model.state.value.pendingMutation)
        assertEquals(1, source.updateCalls)
        assertEquals(1, source.loadCalls)
    }

    @Test
    fun currentRateLimitFailureBecomesCooldownFeedbackWithoutDroppingContent() = runTest {
        val session = InviteMessageSession("usr_current", 1)
        val source = FakeInviteMessageSlotsSource(session).apply {
            loadHandler = { requestSession, type ->
                success(requestSession, listOf(message(type, "Keep me")))
            }
            updateHandler = { requestSession, _, _, _ ->
                InviteMessageSourceResult(
                    result = Result.failure(VRCApiException("Too Many Requests", 429, "cooldown")),
                    session = requestSession,
                )
            }
        }
        val model = createModel(source)

        assertTrue(model.updateMessage(slot = 0, value = "New value"))

        assertEquals("Keep me", model.state.value.messages.single().message)
        assertEquals(InviteMessageFeedbackKind.Cooldown, model.state.value.feedback?.kind)
        assertNull(model.state.value.pendingMutation)
    }

    @Test
    fun tokenChangeDiscardsAnOlderMutationFailureAndItsFeedback() = runTest {
        val firstSession = InviteMessageSession("usr_same", 1)
        val secondSession = InviteMessageSession("usr_same", 2)
        val updateGate = CompletableDeferred<Unit>()
        val source = FakeInviteMessageSlotsSource(firstSession).apply {
            loadHandler = { session, type ->
                success(
                    session,
                    listOf(message(type, if (session == firstSession) "First" else "Second")),
                )
            }
            updateHandler = { session, _, _, _ ->
                withContext(NonCancellable) { updateGate.await() }
                InviteMessageSourceResult(
                    result = Result.failure(VRCApiException("Too Many Requests", 429, "cooldown")),
                    session = session,
                )
            }
        }
        val model = createModel(source)

        assertTrue(model.updateMessage(slot = 0, value = "Pending"))
        source.sessionFlow.value = secondSession
        updateGate.complete(Unit)

        assertEquals(secondSession, model.state.value.session)
        assertEquals("Second", model.state.value.messages.single().message)
        assertNull(model.state.value.pendingMutation)
        assertNull(model.state.value.feedback)
    }

    @Test
    fun resetUsesTheReturnedDefaultWithoutInventingANewCooldown() = runTest {
        val session = InviteMessageSession("usr_current", 1)
        val source = FakeInviteMessageSlotsSource(session).apply {
            loadHandler = { requestSession, type ->
                success(requestSession, listOf(message(type, "Customized")))
            }
            resetHandler = { requestSession, type, slot ->
                success(
                    requestSession,
                    listOf(
                        message(
                            type = type,
                            text = "Default",
                            slot = slot,
                            canBeUpdated = true,
                            cooldownMinutes = 0,
                        )
                    ),
                )
            }
        }
        val model = createModel(source)

        assertTrue(model.resetMessage(0))

        val reset = model.state.value.messages.single()
        assertEquals("Default", reset.message)
        assertTrue(reset.canBeUpdated)
        assertEquals(0, reset.remainingCooldownMinutes)
        assertEquals(InviteMessageFeedbackKind.Reset, model.state.value.feedback?.kind)
    }

    private fun createModel(source: InviteMessageSlotsSource) =
        InviteMessageSlotsModel(source, Dispatchers.Unconfined)
}

private class FakeInviteMessageSlotsSource(
    initialSession: InviteMessageSession?,
) : InviteMessageSlotsSource {
    val sessionFlow = MutableStateFlow(initialSession)
    override val sessions: Flow<InviteMessageSession?> = sessionFlow
    var loadCalls = 0
    var updateCalls = 0

    var loadHandler: suspend (
        InviteMessageSession,
        InviteMessageType,
    ) -> InviteMessageSourceResult<List<InviteMessageData>>? = { session, _ ->
        success(session, emptyList())
    }
    var updateHandler: suspend (
        InviteMessageSession,
        InviteMessageType,
        Int,
        String,
    ) -> InviteMessageSourceResult<List<InviteMessageData>>? = { _, _, _, _ ->
        error("Update handler is not configured")
    }
    var resetHandler: suspend (
        InviteMessageSession,
        InviteMessageType,
        Int,
    ) -> InviteMessageSourceResult<List<InviteMessageData>>? = { _, _, _ ->
        error("Reset handler is not configured")
    }

    override fun isCurrent(session: InviteMessageSession): Boolean = sessionFlow.value == session

    override suspend fun load(
        session: InviteMessageSession,
        messageType: InviteMessageType,
    ): InviteMessageSourceResult<List<InviteMessageData>>? {
        loadCalls++
        return loadHandler(session, messageType)
    }

    override suspend fun update(
        session: InviteMessageSession,
        messageType: InviteMessageType,
        slot: Int,
        message: String,
    ) = updateHandler(session, messageType, slot, message)

    override suspend fun reset(
        session: InviteMessageSession,
        messageType: InviteMessageType,
        slot: Int,
    ) = resetHandler(session, messageType, slot)
}

private fun success(
    session: InviteMessageSession,
    messages: List<InviteMessageData>,
) = InviteMessageSourceResult(Result.success(messages), session)

private fun message(
    type: InviteMessageType,
    text: String,
    slot: Int = 0,
    canBeUpdated: Boolean = true,
    cooldownMinutes: Int = 0,
) = InviteMessageData(
    canBeUpdated = canBeUpdated,
    id = "${type.pathValue}_$slot",
    message = text,
    messageType = type,
    remainingCooldownMinutes = cooldownMinutes,
    slot = slot,
    updatedAt = "2026-08-31T03:00:00.000Z",
)
