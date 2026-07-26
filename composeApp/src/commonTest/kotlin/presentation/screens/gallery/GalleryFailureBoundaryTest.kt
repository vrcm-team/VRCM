package io.github.vrcmteam.vrcm.presentation.screens.gallery

import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GalleryFailureBoundaryTest {
    @Test
    fun onlyOrdinaryExceptionsAreContainedInAResult() {
        val ordinary = IllegalStateException("ordinary")
        val result = runGalleryCatching<String> { throw ordinary }

        assertTrue(result.isFailure)
        assertSame(ordinary, result.exceptionOrNull())

        val cancellation = CancellationException("cancelled")
        val thrownCancellation = assertFailsWith<CancellationException> {
            runGalleryCatching<String> { throw cancellation }
        }
        assertSame(cancellation, thrownCancellation)

        val fatal = GalleryBoundaryFatalError()
        val thrownFatal = assertFailsWith<GalleryBoundaryFatalError> {
            runGalleryCatching<String> { throw fatal }
        }
        assertSame(fatal, thrownFatal)
    }
}

private class GalleryBoundaryFatalError : Error("fatal")
