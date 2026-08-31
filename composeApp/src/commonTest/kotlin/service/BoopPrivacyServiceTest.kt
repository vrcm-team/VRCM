package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoopPrivacyServiceTest {
    @Test
    fun successfulUpdateUsesServerValueInsteadOfRequestedValue() = runTest {
        val account = FakeBoopPrivacyAccount(snapshot("usr_self", generation = 1, isEnabled = false))
        var requestedValue: Boolean? = null
        val service = service(account) { token, userId, isEnabled ->
            requestedValue = isEnabled
            response(token, userId, isEnabled = false)
        }

        val result = service.update(isEnabled = true)

        assertEquals(true, requestedValue)
        assertEquals(BoopPrivacyUpdateResult.Updated(isEnabled = false), result)
        assertEquals(false, account.current?.isEnabled)
        assertEquals(listOf("usr_self" to false), account.appliedUpdates)
        assertNull(service.updatingUserId.value)
    }

    @Test
    fun failureKeepsAuthoritativeValueAndAllowsRetry() = runTest {
        val account = FakeBoopPrivacyAccount(snapshot("usr_self", generation = 1, isEnabled = true))
        val failure = IllegalStateException("update rejected")
        var attempt = 0
        val service = service(account) { token, userId, _ ->
            attempt++
            if (attempt == 1) {
                AuthenticatedBoopPrivacyResponse(Result.failure(failure), token)
            } else {
                response(token, userId, isEnabled = false)
            }
        }

        val failed = service.update(isEnabled = false)

        assertEquals(failure, assertIs<BoopPrivacyUpdateResult.Failed>(failed).error)
        assertEquals(true, account.current?.isEnabled)
        assertTrue(account.appliedUpdates.isEmpty())
        assertNull(service.updatingUserId.value)

        val retried = service.update(isEnabled = false)

        assertEquals(BoopPrivacyUpdateResult.Updated(isEnabled = false), retried)
        assertEquals(false, account.current?.isEnabled)
        assertNull(service.updatingUserId.value)
    }

    @Test
    fun repeatedSubmissionIsRejectedWhileRequestIsInFlight() = runTest {
        val account = FakeBoopPrivacyAccount(snapshot("usr_self", generation = 1, isEnabled = true))
        val requestStarted = CompletableDeferred<Unit>()
        val releaseRequest = CompletableDeferred<Unit>()
        val service = service(account) { token, userId, _ ->
            requestStarted.complete(Unit)
            releaseRequest.await()
            response(token, userId, isEnabled = false)
        }
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            service.update(isEnabled = false)
        }
        requestStarted.await()

        val duplicate = service.update(isEnabled = false)

        assertEquals(BoopPrivacyUpdateResult.InFlight, duplicate)
        assertEquals("usr_self", service.updatingUserId.value)
        releaseRequest.complete(Unit)
        assertEquals(BoopPrivacyUpdateResult.Updated(false), first.await())
    }

    @Test
    fun responseFromRefreshedSessionIsAccepted() = runTest {
        val original = snapshot("usr_self", generation = 1, isEnabled = true)
        val refreshedToken = AccountSessionToken("usr_self", generation = 2)
        val account = FakeBoopPrivacyAccount(original)
        val service = service(account) { _, userId, _ ->
            account.current = original.copy(sessionToken = refreshedToken)
            response(refreshedToken, userId, isEnabled = false)
        }

        val result = service.update(isEnabled = false)

        assertEquals(BoopPrivacyUpdateResult.Updated(false), result)
        assertEquals(false, account.current?.isEnabled)
        assertEquals(refreshedToken, account.current?.sessionToken)
    }

    @Test
    fun accountSwitchDiscardsOldResponseWithoutClearingNewMutation() = runTest {
        val account = FakeBoopPrivacyAccount(snapshot("usr_a", generation = 1, isEnabled = true))
        val oldStarted = CompletableDeferred<Unit>()
        val newStarted = CompletableDeferred<Unit>()
        val releaseOld = CompletableDeferred<Unit>()
        val releaseNew = CompletableDeferred<Unit>()
        val service = service(account) { token, userId, _ ->
            if (userId == "usr_a") {
                oldStarted.complete(Unit)
                releaseOld.await()
            } else {
                newStarted.complete(Unit)
                releaseNew.await()
            }
            response(token, userId, isEnabled = false)
        }
        val oldRequest = async(start = CoroutineStart.UNDISPATCHED) {
            service.update(isEnabled = false)
        }
        oldStarted.await()
        account.current = snapshot("usr_b", generation = 2, isEnabled = true)
        val newRequest = async(start = CoroutineStart.UNDISPATCHED) {
            service.update(isEnabled = false)
        }
        newStarted.await()

        releaseOld.complete(Unit)
        assertEquals(BoopPrivacyUpdateResult.SessionChanged, oldRequest.await())
        assertEquals("usr_b", service.updatingUserId.value)
        assertNull(account.appliedUpdates.firstOrNull())

        releaseNew.complete(Unit)
        assertEquals(BoopPrivacyUpdateResult.Updated(false), newRequest.await())
        assertEquals(listOf("usr_b" to false), account.appliedUpdates)
        assertNull(service.updatingUserId.value)
    }

    @Test
    fun sameAccountTokenReplacementDiscardsOldResponse() = runTest {
        val original = snapshot("usr_self", generation = 1, isEnabled = true)
        val account = FakeBoopPrivacyAccount(original)
        val requestStarted = CompletableDeferred<Unit>()
        val releaseRequest = CompletableDeferred<Unit>()
        val service = service(account) { token, userId, _ ->
            requestStarted.complete(Unit)
            releaseRequest.await()
            response(token, userId, isEnabled = false)
        }
        val update = async(start = CoroutineStart.UNDISPATCHED) {
            service.update(isEnabled = false)
        }
        requestStarted.await()
        account.current = original.copy(
            sessionToken = AccountSessionToken("usr_self", generation = 2),
        )

        releaseRequest.complete(Unit)

        assertEquals(BoopPrivacyUpdateResult.SessionChanged, update.await())
        assertEquals(true, account.current?.isEnabled)
        assertNull(account.appliedUpdates.firstOrNull())
        assertNull(service.updatingUserId.value)
    }

    @Test
    fun logoutTurnsLateFailureIntoSessionChange() = runTest {
        val account = FakeBoopPrivacyAccount(snapshot("usr_self", generation = 1, isEnabled = true))
        val requestStarted = CompletableDeferred<Unit>()
        val releaseRequest = CompletableDeferred<Unit>()
        val service = service(account) { token, _, _ ->
            requestStarted.complete(Unit)
            releaseRequest.await()
            AuthenticatedBoopPrivacyResponse(
                result = Result.failure(IllegalStateException("late failure")),
                sessionToken = token,
            )
        }
        val update = async(start = CoroutineStart.UNDISPATCHED) {
            service.update(isEnabled = false)
        }
        requestStarted.await()
        account.current = null

        releaseRequest.complete(Unit)

        assertEquals(BoopPrivacyUpdateResult.SessionChanged, update.await())
        assertNull(service.updatingUserId.value)
    }

    private fun service(
        account: FakeBoopPrivacyAccount,
        request: suspend (
            token: AccountSessionToken,
            userId: String,
            isEnabled: Boolean,
        ) -> AuthenticatedBoopPrivacyResponse?,
    ) = BoopPrivacyService(
        accountAccess = account,
        request = BoopPrivacyRequest(request),
    )

    private fun snapshot(
        userId: String,
        generation: Long,
        isEnabled: Boolean,
    ) = BoopPrivacyAccountSnapshot(
        sessionToken = AccountSessionToken(userId, generation),
        userId = userId,
        isEnabled = isEnabled,
    )

    private fun response(
        token: AccountSessionToken,
        userId: String,
        isEnabled: Boolean,
    ) = AuthenticatedBoopPrivacyResponse(
        result = Result.success(BoopPrivacyServerUpdate(userId, isEnabled)),
        sessionToken = token,
    )
}

private class FakeBoopPrivacyAccount(
    var current: BoopPrivacyAccountSnapshot?,
) : BoopPrivacyAccountAccess {
    val appliedUpdates = mutableListOf<Pair<String, Boolean>>()

    override fun snapshot(): BoopPrivacyAccountSnapshot? = current

    override fun isCurrentSession(sessionToken: AccountSessionToken): Boolean =
        current?.sessionToken == sessionToken

    override fun applyServerUpdate(
        sessionToken: AccountSessionToken,
        userId: String,
        isEnabled: Boolean,
    ): Boolean {
        val snapshot = current ?: return false
        if (snapshot.sessionToken != sessionToken || snapshot.userId != userId) return false
        current = snapshot.copy(isEnabled = isEnabled)
        appliedUpdates += userId to isEnabled
        return true
    }
}
