package io.github.vrcmteam.vrcm.presentation.screens.gallery

import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GalleryUploadBoundaryTest {
    @Test
    fun directExceptionBecomesARecoverableFailureWithTheSameCause() = runBlocking {
        val failure = IllegalStateException("offline")

        val result = runGalleryRequestWithAuthRetry<String>(
            retryAuth = { request -> request() },
            request = { throw failure },
        )

        assertTrue(result.isFailure)
        assertSame(failure, result.exceptionOrNull())
    }

    @Test
    fun unauthorizedResultCanRetryTheSameUploadRequest() = runBlocking {
        var requestCount = 0

        val result = runGalleryRequestWithAuthRetry(
            retryAuth = { request ->
                val first = request()
                if ((first.exceptionOrNull() as? VRCApiException)?.code == 401) request() else first
            },
            request = {
                requestCount++
                if (requestCount == 1) {
                    Result.failure(VRCApiException("Unauthorized", 401, ""))
                } else {
                    Result.success("uploaded")
                }
            },
        )

        assertEquals("uploaded", result.getOrThrow())
        assertEquals(2, requestCount)
    }

    @Test
    fun unauthorizedUnitRequestCanRetryTheSameOperation() = runBlocking {
        var requestCount = 0

        val result = runGalleryRequestWithAuthRetry(
            retryAuth = { request ->
                val first = request()
                if ((first.exceptionOrNull() as? VRCApiException)?.code == 401) request() else first
            },
            request = {
                requestCount++
                if (requestCount == 1) {
                    Result.failure(VRCApiException("Unauthorized", 401, ""))
                } else {
                    Result.success(Unit)
                }
            },
        )

        assertTrue(result.isSuccess)
        assertEquals(2, requestCount)
    }

    @Test
    fun directAndReturnedCancellationKeepTheirIdentity() = runBlocking {
        val direct = CancellationException("direct")
        val thrownDirect = assertFailsWith<CancellationException> {
            runGalleryRequestWithAuthRetry<String>(
                retryAuth = { request -> request() },
                request = { throw direct },
            )
        }
        val returned = CancellationException("returned")
        val thrownReturned = assertFailsWith<CancellationException> {
            runGalleryRequestWithAuthRetry(
                retryAuth = { request -> request() },
                request = { Result.failure<String>(returned) },
            )
        }

        assertSame(direct, thrownDirect)
        assertSame(returned, thrownReturned)
    }

    @Test
    fun directAndReturnedErrorsKeepTheirIdentity() = runBlocking {
        val direct = GalleryFatalError("direct")
        val thrownDirect = assertFailsWith<GalleryFatalError> {
            runGalleryRequestWithAuthRetry<String>(
                retryAuth = { request -> request() },
                request = { throw direct },
            )
        }
        val returned = GalleryFatalError("returned")
        val thrownReturned = assertFailsWith<GalleryFatalError> {
            runGalleryRequestWithAuthRetry(
                retryAuth = { request -> request() },
                request = { Result.failure<String>(returned) },
            )
        }

        assertSame(direct, thrownDirect)
        assertSame(returned, thrownReturned)
    }
}

private class GalleryFatalError(message: String) : Error(message)
