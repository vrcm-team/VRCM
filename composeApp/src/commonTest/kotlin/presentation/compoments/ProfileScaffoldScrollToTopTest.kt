package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.ScrollState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileScaffoldScrollToTopTest {
    @Test
    fun outerScrollStartsOnlyAfterInnerScrollCompletes() = runTest {
        val innerScrollState = ScrollState(initial = 120)
        val outerScrollState = ScrollState(initial = 80)
        val innerScrollCanComplete = CompletableDeferred<Unit>()
        var outerScrollStarted = false

        val scrollJob = launch {
            scrollProfileToTopSequentially(
                innerScrollState = innerScrollState,
                outerScrollState = outerScrollState,
                animateToTop = { scrollState ->
                    if (scrollState === innerScrollState) {
                        innerScrollCanComplete.await()
                    } else {
                        outerScrollStarted = true
                    }
                },
            )
        }

        yield()
        assertFalse(outerScrollStarted)

        innerScrollCanComplete.complete(Unit)
        scrollJob.join()
        assertTrue(outerScrollStarted)
    }

    @Test
    fun newRequestCancelsPreviousSequenceBeforeRestarting() = runTest {
        val innerScrollState = ScrollState(initial = 120)
        val outerScrollState = ScrollState(initial = 80)
        val firstOuterStarted = CompletableDeferred<Unit>()
        val firstOuterCancelled = CompletableDeferred<Unit>()
        val secondInnerCanComplete = CompletableDeferred<Unit>()
        val animatedStates = mutableListOf<ScrollState>()
        val controller = ProfileScrollToTopController(
            scope = this,
            animateToTop = { scrollState ->
                animatedStates += scrollState
                when (animatedStates.size) {
                    2 -> {
                        firstOuterStarted.complete(Unit)
                        try {
                            awaitCancellation()
                        } finally {
                            firstOuterCancelled.complete(Unit)
                        }
                    }
                    3 -> secondInnerCanComplete.await()
                }
            },
        )

        val firstRequest = controller.scrollToTop(innerScrollState, outerScrollState)
        firstOuterStarted.await()

        val secondRequest = controller.scrollToTop(innerScrollState, outerScrollState)
        firstOuterCancelled.await()
        yield()

        assertTrue(firstRequest.isCancelled)
        assertEquals(
            listOf(innerScrollState, outerScrollState, innerScrollState),
            animatedStates,
        )

        secondInnerCanComplete.complete(Unit)
        secondRequest.join()
        assertEquals(
            listOf(innerScrollState, outerScrollState, innerScrollState, outerScrollState),
            animatedStates,
        )
    }
}
