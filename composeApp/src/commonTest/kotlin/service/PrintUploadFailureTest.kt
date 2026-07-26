package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class PrintUploadFailureTest {
    @Test
    fun apiStatusCodesMapToActionableFailures() {
        assertIs<PrintUploadFailure.Authentication>(apiFailure(401).toPrintUploadFailure())
        assertIs<PrintUploadFailure.Permission>(apiFailure(403).toPrintUploadFailure())
        assertIs<PrintUploadFailure.Server>(apiFailure(503).toPrintUploadFailure())
    }

    @Test
    fun ioAndUnexpectedFailuresRemainTyped() {
        assertIs<PrintUploadFailure.Network>(IOException("offline").toPrintUploadFailure())
        assertIs<PrintUploadFailure.Unknown>(IllegalStateException("unexpected").toPrintUploadFailure())
    }

    @Test
    fun directUnexpectedFailureIsContained() = runBlocking {
        val cause = IllegalStateException("unexpected")

        val result = printUploadResult<String> { throw cause }

        val failure = assertIs<PrintUploadFailure.Unknown>(result.exceptionOrNull())
        assertSame(cause, failure.cause)
    }

    @Test
    fun returnedIoFailureMapsToNetworkAndKeepsCause() = runBlocking {
        val cause = IOException("offline")

        val result = printUploadResult<String> { Result.failure(cause) }

        val failure = assertIs<PrintUploadFailure.Network>(result.exceptionOrNull())
        assertSame(cause, failure.cause)
    }

    @Test
    fun directCancellationIsRethrown() = runBlocking {
        val cancellation = CancellationException("cancelled")

        val thrown = assertFailsWith<CancellationException> {
            printUploadResult<String> { throw cancellation }
        }

        assertSame(cancellation, thrown)
    }

    @Test
    fun returnedCancellationIsRethrown() = runBlocking {
        val cancellation = CancellationException("cancelled")

        val thrown = assertFailsWith<CancellationException> {
            printUploadResult<String> { Result.failure(cancellation) }
        }

        assertSame(cancellation, thrown)
    }

    @Test
    fun directFatalFailureIsRethrown() = runBlocking {
        val fatal = AssertionError("fatal")

        val thrown = assertFailsWith<AssertionError> {
            printUploadResult<String> { throw fatal }
        }

        assertSame(fatal, thrown)
    }

    @Test
    fun returnedFatalFailureIsRethrown() = runBlocking {
        val fatal = AssertionError("fatal")

        val thrown = assertFailsWith<AssertionError> {
            printUploadResult<String> { Result.failure(fatal) }
        }

        assertSame(fatal, thrown)
    }

    private fun apiFailure(status: Int) = VRCApiException(
        description = "status-$status",
        code = status,
        bodyText = "{\"error\":\"sensitive server response\"}",
    )
}
