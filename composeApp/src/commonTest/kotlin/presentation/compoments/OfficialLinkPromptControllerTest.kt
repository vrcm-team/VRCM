package io.github.vrcmteam.vrcm.presentation.compoments

import io.github.vrcmteam.vrcm.service.OfficialLinkRequest
import io.github.vrcmteam.vrcm.service.OfficialLinkType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OfficialLinkPromptControllerTest {
    @Test
    fun clipboardAcceptsSupportedOfficialIds() = runTest {
        val controller = OfficialLinkPromptController<String>(
            scope = this,
            resolve = { Result.success("resolved") },
            onResolved = {},
            onExternalConsumed = {},
        )
        controller.updateAuthentication(true)
        val ids = listOf(
            "usr_abc-123" to OfficialLinkType.User,
            "wrld_abc-123" to OfficialLinkType.World,
            "grp_abc-123" to OfficialLinkType.Group,
            "avtr_abc-123" to OfficialLinkType.Avatar,
        )

        ids.forEach { (id, expectedType) ->
            controller.inspectClipboard(id)

            val confirmation = assertIs<OfficialLinkPromptState.ClipboardConfirmation>(
                controller.state.value,
            )
            assertEquals(expectedType, confirmation.operation.target.type)
            assertEquals(id, confirmation.operation.target.id)
        }
    }

    @Test
    fun externalLinkSuppressesClipboardInspectionForTheSameForegroundEvent() {
        val gate = OfficialLinkClipboardInspectionGate()

        assertFalse(
            gate.shouldInspect(
                foregroundGeneration = 1,
                isAuthenticated = true,
                hasExternalRequest = true,
            ),
        )
        assertFalse(
            gate.shouldInspect(
                foregroundGeneration = 1,
                isAuthenticated = true,
                hasExternalRequest = false,
            ),
        )
        assertTrue(
            gate.shouldInspect(
                foregroundGeneration = 2,
                isAuthenticated = true,
                hasExternalRequest = false,
            ),
        )
    }

    @Test
    fun authenticationDoesNotConsumeAClipboardInspection() {
        val gate = OfficialLinkClipboardInspectionGate()

        assertFalse(
            gate.shouldInspect(
                foregroundGeneration = 1,
                isAuthenticated = false,
                hasExternalRequest = false,
            ),
        )
        assertTrue(
            gate.shouldInspect(
                foregroundGeneration = 1,
                isAuthenticated = true,
                hasExternalRequest = false,
            ),
        )
        assertFalse(
            gate.shouldInspect(
                foregroundGeneration = 1,
                isAuthenticated = true,
                hasExternalRequest = false,
            ),
        )
    }

    @Test
    fun recreatedControllerKeepsTheClipboardConfirmation() = runTest {
        val firstController = OfficialLinkPromptController<String>(
            scope = this,
            resolve = { Result.success("resolved") },
            onResolved = {},
            onExternalConsumed = {},
        )
        firstController.updateAuthentication(true)
        val url = "https://vrchat.com/home/user/usr_saved"
        firstController.inspectClipboard(url)
        assertIs<OfficialLinkPromptState.ClipboardConfirmation>(firstController.state.value)

        val recreatedController = OfficialLinkPromptController<String>(
            scope = this,
            resolve = { Result.success("resolved") },
            onResolved = {},
            onExternalConsumed = {},
            initialSnapshot = firstController.snapshot()
                .toSaveableValues()
                .toPromptSnapshot(),
        )
        recreatedController.updateAuthentication(true)

        assertIs<OfficialLinkPromptState.ClipboardConfirmation>(recreatedController.state.value)
    }

    @Test
    fun recreatedControllerTurnsInterruptedClipboardResolutionBackIntoConfirmation() = runTest {
        val completion = CompletableDeferred<Result<String>>()
        val firstController = OfficialLinkPromptController(
            scope = this,
            resolve = { completion.await() },
            onResolved = {},
            onExternalConsumed = {},
        )
        firstController.updateAuthentication(true)
        firstController.inspectClipboard("https://vrchat.com/home/user/usr_saved")
        firstController.confirmClipboard()
        runCurrent()
        val snapshot = firstController.snapshot()
            .toSaveableValues()
            .toPromptSnapshot()
        firstController.updateAuthentication(false)

        val recreatedController = OfficialLinkPromptController<String>(
            scope = this,
            resolve = { Result.success("resolved") },
            onResolved = {},
            onExternalConsumed = {},
            initialSnapshot = snapshot,
        )
        recreatedController.updateAuthentication(true)

        assertIs<OfficialLinkPromptState.ClipboardConfirmation>(recreatedController.state.value)
    }

    @Test
    fun recreatedControllerKeepsClipboardFailureRetryable() = runTest {
        val firstController = OfficialLinkPromptController<String>(
            scope = this,
            resolve = { Result.failure(IllegalStateException("network")) },
            onResolved = {},
            onExternalConsumed = {},
        )
        firstController.updateAuthentication(true)
        firstController.inspectClipboard("https://vrchat.com/home/world/wrld_saved")
        firstController.confirmClipboard()
        runCurrent()
        val snapshot = firstController.snapshot()
            .toSaveableValues()
            .toPromptSnapshot()

        val recreatedController = OfficialLinkPromptController<String>(
            scope = this,
            resolve = { Result.success("resolved") },
            onResolved = {},
            onExternalConsumed = {},
            initialSnapshot = snapshot,
        )
        recreatedController.updateAuthentication(true)

        val failure = assertIs<OfficialLinkPromptState.Failure>(recreatedController.state.value)
        assertEquals(OfficialLinkType.World, failure.operation?.target?.type)
    }

    @Test
    fun recreatedExternalFailureRebindsTheInboxRequestWithoutAutomaticRetry() = runTest {
        val url = "https://vrchat.com/home/user/usr_saved"
        val firstController = OfficialLinkPromptController<String>(
            scope = this,
            resolve = { Result.failure(IllegalStateException("network")) },
            onResolved = {},
            onExternalConsumed = {},
        )
        firstController.updateAuthentication(true)
        firstController.openExternal(OfficialLinkRequest(1, url))
        runCurrent()
        val snapshot = firstController.snapshot()
            .toSaveableValues()
            .toPromptSnapshot()

        var resolveCount = 0
        val resolved = mutableListOf<String>()
        val consumed = mutableListOf<OfficialLinkRequest>()
        val recreatedController = OfficialLinkPromptController(
            scope = this,
            resolve = {
                resolveCount++
                Result.success("resolved")
            },
            onResolved = resolved::add,
            onExternalConsumed = consumed::add,
            initialSnapshot = snapshot,
        )
        recreatedController.updateAuthentication(true)
        val restoredRequest = OfficialLinkRequest(7, url)
        recreatedController.openExternal(restoredRequest)

        val failure = assertIs<OfficialLinkPromptState.Failure>(recreatedController.state.value)
        assertEquals(7, failure.operation?.externalRequest?.id)
        assertEquals(0, resolveCount)

        recreatedController.retry()
        runCurrent()

        assertEquals(1, resolveCount)
        assertEquals(listOf("resolved"), resolved)
        assertEquals(listOf(restoredRequest), consumed)
    }

    @Test
    fun reopeningTheSameExternalLinkAfterFailureStartsANewResolution() = runTest {
        val url = "https://vrchat.com/home/user/usr_reopened"
        var resolveCount = 0
        val resolved = mutableListOf<String>()
        val consumed = mutableListOf<OfficialLinkRequest>()
        val controller = OfficialLinkPromptController(
            scope = this,
            resolve = {
                resolveCount++
                if (resolveCount == 1) {
                    Result.failure(IllegalStateException("network"))
                } else {
                    Result.success("resolved")
                }
            },
            onResolved = resolved::add,
            onExternalConsumed = consumed::add,
        )
        controller.updateAuthentication(true)
        controller.openExternal(OfficialLinkRequest(1, url))
        runCurrent()
        assertIs<OfficialLinkPromptState.Failure>(controller.state.value)

        val reopenedRequest = OfficialLinkRequest(2, url)
        controller.openExternal(reopenedRequest)
        runCurrent()

        assertEquals(2, resolveCount)
        assertEquals(listOf("resolved"), resolved)
        assertEquals(listOf(reopenedRequest), consumed)
        assertEquals(OfficialLinkPromptState.Idle, controller.state.value)
    }

    @Test
    fun newerExternalRequestCancelsTheOldResolutionAndWinsNavigation() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        val firstCancelled = CompletableDeferred<Unit>()
        val firstCompletion = CompletableDeferred<Result<String>>()
        val secondCompletion = CompletableDeferred<Result<String>>()
        val resolved = mutableListOf<String>()
        val consumed = mutableListOf<OfficialLinkRequest>()
        val controller = OfficialLinkPromptController(
            scope = this,
            resolve = { target ->
                when (target.type) {
                    OfficialLinkType.User -> try {
                        firstStarted.complete(Unit)
                        firstCompletion.await()
                    } finally {
                        firstCancelled.complete(Unit)
                    }
                    OfficialLinkType.World -> secondCompletion.await()
                    else -> error("Unexpected target: $target")
                }
            },
            onResolved = resolved::add,
            onExternalConsumed = consumed::add,
        )
        controller.updateAuthentication(true)
        val first = OfficialLinkRequest(1, "https://vrchat.com/home/user/usr_first")
        val second = OfficialLinkRequest(2, "https://vrchat.com/home/world/wrld_second")

        controller.openExternal(first)
        firstStarted.await()
        controller.openExternal(second)
        runCurrent()

        assertTrue(firstCancelled.isCompleted)
        secondCompletion.complete(Result.success("second"))
        runCurrent()

        assertEquals(listOf("second"), resolved)
        assertEquals(listOf(second), consumed)
        assertEquals(OfficialLinkPromptState.Idle, controller.state.value)
    }

    @Test
    fun logoutCancelsResolutionWithoutConsumingOrNavigating() = runTest {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val completion = CompletableDeferred<Result<String>>()
        val resolved = mutableListOf<String>()
        val consumed = mutableListOf<OfficialLinkRequest>()
        val controller = OfficialLinkPromptController(
            scope = this,
            resolve = {
                try {
                    started.complete(Unit)
                    completion.await()
                } finally {
                    cancelled.complete(Unit)
                }
            },
            onResolved = resolved::add,
            onExternalConsumed = consumed::add,
        )
        controller.updateAuthentication(true)
        controller.openExternal(
            OfficialLinkRequest(1, "https://vrchat.com/home/user/usr_first"),
        )
        started.await()

        controller.updateAuthentication(false)
        runCurrent()

        assertTrue(cancelled.isCompleted)
        assertTrue(resolved.isEmpty())
        assertTrue(consumed.isEmpty())
        assertEquals(OfficialLinkPromptState.Idle, controller.state.value)
    }

    @Test
    fun sourceRequestChangeRejectsAResultBeforeTheControllerReceivesTheNewRequest() = runTest {
        val started = CompletableDeferred<Unit>()
        val completion = CompletableDeferred<Result<String>>()
        val resolved = mutableListOf<String>()
        val consumed = mutableListOf<OfficialLinkRequest>()
        var currentExternalRequestId: Long? = 1L
        val controller = OfficialLinkPromptController(
            scope = this,
            resolve = {
                started.complete(Unit)
                completion.await()
            },
            onResolved = resolved::add,
            onExternalConsumed = consumed::add,
            isOperationCurrent = { operation ->
                operation.externalRequest?.id == currentExternalRequestId
            },
        )
        controller.updateAuthentication(true)
        controller.openExternal(
            OfficialLinkRequest(1, "https://vrchat.com/home/user/usr_first"),
        )
        started.await()

        currentExternalRequestId = 2L
        completion.complete(Result.success("stale"))
        runCurrent()

        assertTrue(resolved.isEmpty())
        assertTrue(consumed.isEmpty())
    }

    @Test
    fun sourceLogoutRejectsAResultBeforeTheControllerReceivesAuthenticationUpdate() = runTest {
        val started = CompletableDeferred<Unit>()
        val completion = CompletableDeferred<Result<String>>()
        val resolved = mutableListOf<String>()
        val consumed = mutableListOf<OfficialLinkRequest>()
        var sourceIsAuthenticated = true
        val controller = OfficialLinkPromptController(
            scope = this,
            resolve = {
                started.complete(Unit)
                completion.await()
            },
            onResolved = resolved::add,
            onExternalConsumed = consumed::add,
            isOperationCurrent = { sourceIsAuthenticated },
        )
        controller.updateAuthentication(true)
        controller.openExternal(
            OfficialLinkRequest(1, "https://vrchat.com/home/user/usr_first"),
        )
        started.await()

        sourceIsAuthenticated = false
        completion.complete(Result.success("stale"))
        runCurrent()

        assertTrue(resolved.isEmpty())
        assertTrue(consumed.isEmpty())
    }

    @Test
    fun aNewClipboardTargetReplacesExternalFailureAndClearsTheOldError() = runTest {
        val consumed = mutableListOf<OfficialLinkRequest>()
        val controller = OfficialLinkPromptController<String>(
            scope = this,
            resolve = { Result.failure(IllegalStateException("network")) },
            onResolved = {},
            onExternalConsumed = consumed::add,
        )
        controller.updateAuthentication(true)
        val external = OfficialLinkRequest(
            1,
            "https://vrchat.com/home/user/usr_external",
        )
        controller.openExternal(external)
        runCurrent()
        assertIs<OfficialLinkPromptState.Failure>(controller.state.value)

        controller.inspectClipboard("https://vrchat.com/home/world/wrld_clipboard")
        val confirmation = assertIs<OfficialLinkPromptState.ClipboardConfirmation>(
            controller.state.value,
        )

        assertEquals(OfficialLinkType.World, confirmation.operation.target.type)
        assertEquals(listOf(external), consumed)

        controller.confirmClipboard()
        runCurrent()
        assertIs<OfficialLinkPromptState.Failure>(controller.state.value)

        controller.inspectClipboard("https://vrchat.com/home/group/grp_new")
        val nextConfirmation = assertIs<OfficialLinkPromptState.ClipboardConfirmation>(
            controller.state.value,
        )
        assertEquals(OfficialLinkType.Group, nextConfirmation.operation.target.type)
    }
}
