package io.github.vrcmteam.vrcm.presentation.screens.avatar

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarUnityPackage
import io.github.vrcmteam.vrcm.service.SessionBoundResponse
import io.github.vrcmteam.vrcm.service.data.AccountDto
import io.github.vrcmteam.vrcm.testing.MainDispatcherTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AvatarImpostorDeletionStateModelTest : MainDispatcherTest() {
    @Test
    fun onlyAnOwnedAvatarWithAnExistingImpostorCanBeDeleted() = runTest {
        val token = AccountSessionToken("usr_owner", 1)
        val model = model(
            source = RecordingImpostorDeletionSource(),
            session = MutableStateFlow<AuthenticatedAccount?>(authenticated(token)),
        )

        model.setTarget("avtr_owned", "usr_owner", hasImpostor = false)
        assertTrue(model.state.value.isAvailable)
        assertFalse(model.state.value.canDelete)

        model.setTarget("avtr_other", "usr_other", hasImpostor = true)
        assertFalse(model.state.value.isAvailable)
        assertFalse(model.delete())

        model.setTarget("avtr_owned", "usr_owner", hasImpostor = true)
        assertTrue(model.state.value.canDelete)
    }

    @Test
    fun duplicateDeleteIsRejectedAndFailureAllowsAnExplicitRetry() = runTest {
        val token = AccountSessionToken("usr_owner", 1)
        val pendingDelete = CompletableDeferred<SessionBoundResponse<Unit>?>()
        val source = RecordingImpostorDeletionSource(
            deleteHandler = { _, _ -> pendingDelete.await() },
        )
        val session = MutableStateFlow<AuthenticatedAccount?>(authenticated(token))
        val model = model(source, session)
        model.setTarget("avtr_owned", "usr_owner", hasImpostor = true)

        assertTrue(model.delete())
        assertFalse(model.delete())
        runCurrent()
        assertEquals(1, source.deleteCalls.size)
        assertEquals(AvatarImpostorDeletionPhase.Deleting, model.state.value.phase)

        pendingDelete.complete(
            SessionBoundResponse(Result.failure(IllegalStateException("offline")), token)
        )
        runCurrent()

        assertTrue(model.state.value.canDelete)
        assertTrue(model.state.value.deleteFailed)
        assertEquals(1, source.deleteCalls.size)
    }

    @Test
    fun successfulDeleteWaitsForAuthoritativeAvatarBeforeReportingSuccess() = runTest {
        val token = AccountSessionToken("usr_owner", 1)
        val pendingLoad = CompletableDeferred<SessionBoundResponse<AvatarData>?>()
        val source = RecordingImpostorDeletionSource(
            loadHandler = { _, _ -> pendingLoad.await() },
        )
        val session = MutableStateFlow<AuthenticatedAccount?>(authenticated(token))
        val reloaded = mutableListOf<AvatarData>()
        val model = model(source, session, reloaded::add)
        val notices = mutableListOf<AvatarImpostorDeletionNotice>()
        backgroundScope.launchCollect(model.notices, notices)
        model.setTarget("avtr_owned", "usr_owner", hasImpostor = true)

        assertTrue(model.delete())
        runCurrent()

        assertEquals(AvatarImpostorDeletionPhase.Verifying, model.state.value.phase)
        assertTrue(notices.isEmpty())
        pendingLoad.complete(SessionBoundResponse(Result.success(avatar(hasImpostor = false)), token))
        runCurrent()

        assertFalse(model.state.value.hasImpostor)
        assertFalse(model.state.value.canDelete)
        assertEquals(listOf("avtr_owned"), reloaded.map(AvatarData::id))
        assertEquals(listOf<AvatarImpostorDeletionNotice>(AvatarImpostorDeletionNotice.Deleted), notices)
    }

    @Test
    fun failedVerificationRetriesOnlyTheAuthoritativeLoad() = runTest {
        val token = AccountSessionToken("usr_owner", 1)
        var loadAttempt = 0
        val source = RecordingImpostorDeletionSource(
            loadHandler = { responseToken, _ ->
                loadAttempt++
                if (loadAttempt == 1) {
                    SessionBoundResponse(Result.failure(IllegalStateException("offline")), responseToken)
                } else {
                    SessionBoundResponse(Result.success(avatar(hasImpostor = false)), responseToken)
                }
            },
        )
        val session = MutableStateFlow<AuthenticatedAccount?>(authenticated(token))
        val model = model(source, session)
        model.setTarget("avtr_owned", "usr_owner", hasImpostor = true)

        assertTrue(model.delete())
        runCurrent()
        assertTrue(model.state.value.verificationFailed)
        assertFalse(model.state.value.canDelete)
        assertTrue(model.state.value.canRetryVerification)

        assertTrue(model.retryVerification())
        runCurrent()

        assertEquals(1, source.deleteCalls.size)
        assertEquals(2, source.loadCalls.size)
        assertFalse(model.state.value.hasImpostor)
    }

    @Test
    fun anAuthoritativeAvatarThatStillHasAnImpostorDoesNotReportSuccess() = runTest {
        val token = AccountSessionToken("usr_owner", 1)
        val source = RecordingImpostorDeletionSource(
            loadHandler = { responseToken, _ ->
                SessionBoundResponse(Result.success(avatar(hasImpostor = true)), responseToken)
            },
        )
        val session = MutableStateFlow<AuthenticatedAccount?>(authenticated(token))
        val model = model(source, session)
        val notices = mutableListOf<AvatarImpostorDeletionNotice>()
        backgroundScope.launchCollect(model.notices, notices)
        model.setTarget("avtr_owned", "usr_owner", hasImpostor = true)

        model.delete()
        runCurrent()

        assertTrue(model.state.value.hasImpostor)
        assertTrue(model.state.value.canDelete)
        assertEquals(
            listOf<AvatarImpostorDeletionNotice>(AvatarImpostorDeletionNotice.DeleteFailed),
            notices,
        )
    }

    @Test
    fun accountSwitchRejectsALateDeleteResponse() = runTest {
        val oldToken = AccountSessionToken("usr_owner", 1)
        val pendingDelete = CompletableDeferred<SessionBoundResponse<Unit>?>()
        val source = RecordingImpostorDeletionSource(
            deleteHandler = { _, _ -> withContext(NonCancellable) { pendingDelete.await() } },
        )
        val session = MutableStateFlow<AuthenticatedAccount?>(authenticated(oldToken))
        val reloaded = mutableListOf<AvatarData>()
        val model = model(source, session, reloaded::add)
        model.setTarget("avtr_owned", "usr_owner", hasImpostor = true)

        model.delete()
        runCurrent()
        session.value = authenticated(AccountSessionToken("usr_other", 2))
        runCurrent()
        pendingDelete.complete(SessionBoundResponse(Result.success(Unit), oldToken))
        runCurrent()

        assertFalse(model.state.value.isAvailable)
        assertTrue(source.loadCalls.isEmpty())
        assertTrue(reloaded.isEmpty())
    }

    @Test
    fun ambiguousDeleteResultForTheSameAccountRequiresVerificationBeforeAnotherDelete() = runTest {
        val token = AccountSessionToken("usr_owner", 1)
        val source = RecordingImpostorDeletionSource(
            deleteHandler = { _, _ -> null },
        )
        val session = MutableStateFlow<AuthenticatedAccount?>(authenticated(token))
        val model = model(source, session)
        model.setTarget("avtr_owned", "usr_owner", hasImpostor = true)

        model.delete()
        runCurrent()

        assertTrue(model.state.value.verificationFailed)
        assertFalse(model.delete())
        assertTrue(model.retryVerification())
        runCurrent()
        assertEquals(1, source.deleteCalls.size)
        assertFalse(model.state.value.hasImpostor)
    }

    @Test
    fun sameAccountAuthenticationRenewalAcceptsTheRefreshedToken() = runTest {
        val firstToken = AccountSessionToken("usr_owner", 1)
        val renewedToken = AccountSessionToken("usr_owner", 2)
        val session = MutableStateFlow<AuthenticatedAccount?>(authenticated(firstToken))
        val source = RecordingImpostorDeletionSource(
            deleteHandler = { _, _ ->
                session.value = authenticated(renewedToken)
                SessionBoundResponse(Result.success(Unit), renewedToken)
            },
        )
        val model = model(source, session)
        model.setTarget("avtr_owned", "usr_owner", hasImpostor = true)

        model.delete()
        runCurrent()

        assertEquals(listOf(renewedToken), source.loadCalls.map { it.first })
        assertFalse(model.state.value.hasImpostor)
    }

    private fun CoroutineScope.launchCollect(
        flow: Flow<AvatarImpostorDeletionNotice>,
        destination: MutableList<AvatarImpostorDeletionNotice>,
    ) = launch(start = CoroutineStart.UNDISPATCHED) {
        flow.collect(destination::add)
    }

    private fun kotlinx.coroutines.test.TestScope.model(
        source: AvatarImpostorDeletionSource,
        session: MutableStateFlow<AuthenticatedAccount?>,
        onAvatarReloaded: (AvatarData) -> Unit = {},
    ) = AvatarImpostorDeletionStateModel(
        source = source,
        scope = backgroundScope,
        onAvatarReloaded = onAvatarReloaded,
        requestDispatcher = StandardTestDispatcher(testScheduler),
        sessionFlow = session,
    )
}

private class RecordingImpostorDeletionSource(
    private val deleteHandler: suspend (
        AccountSessionToken,
        String,
    ) -> SessionBoundResponse<Unit>? = { token, _ ->
        SessionBoundResponse(Result.success(Unit), token)
    },
    private val loadHandler: suspend (
        AccountSessionToken,
        String,
    ) -> SessionBoundResponse<AvatarData>? = { token, _ ->
        SessionBoundResponse(Result.success(avatar(hasImpostor = false)), token)
    },
) : AvatarImpostorDeletionSource {
    val deleteCalls = mutableListOf<Pair<AccountSessionToken, String>>()
    val loadCalls = mutableListOf<Pair<AccountSessionToken, String>>()

    override suspend fun delete(
        sessionToken: AccountSessionToken,
        avatarId: String,
    ): SessionBoundResponse<Unit>? {
        deleteCalls += sessionToken to avatarId
        return deleteHandler(sessionToken, avatarId)
    }

    override suspend fun load(
        sessionToken: AccountSessionToken,
        avatarId: String,
    ): SessionBoundResponse<AvatarData>? {
        loadCalls += sessionToken to avatarId
        return loadHandler(sessionToken, avatarId)
    }
}

private fun authenticated(token: AccountSessionToken) = AuthenticatedAccount(
    account = AccountDto(userId = token.userId),
    token = token,
)

private fun avatar(hasImpostor: Boolean) = AvatarData(
    id = "avtr_owned",
    name = "Owned",
    authorId = "usr_owner",
    unityPackages = if (hasImpostor) {
        listOf(AvatarUnityPackage(platform = "standalonewindows", variant = "impostor"))
    } else {
        listOf(AvatarUnityPackage(platform = "standalonewindows", variant = "standard"))
    },
)
