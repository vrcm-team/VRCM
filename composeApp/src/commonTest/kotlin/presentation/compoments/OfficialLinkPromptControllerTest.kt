package io.github.vrcmteam.vrcm.presentation.compoments

import io.github.vrcmteam.vrcm.service.OfficialLinkRequest
import io.github.vrcmteam.vrcm.service.OfficialLinkType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OfficialLinkPromptControllerTest {
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
