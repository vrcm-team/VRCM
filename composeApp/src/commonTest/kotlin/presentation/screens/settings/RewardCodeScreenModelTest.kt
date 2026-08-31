package io.github.vrcmteam.vrcm.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.network.api.inventory.data.RewardRedemption
import io.github.vrcmteam.vrcm.network.api.inventory.data.RewardRedemptionResult
import io.github.vrcmteam.vrcm.service.AuthenticatedRewardRedemptionResponse
import io.github.vrcmteam.vrcm.service.RewardCodeRedeemer
import io.github.vrcmteam.vrcm.service.data.AccountDto
import io.github.vrcmteam.vrcm.testing.MainDispatcherTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RewardCodeScreenModelTest : MainDispatcherTest() {
    private val models = mutableListOf<RewardCodeScreenModel>()

    @AfterTest
    fun disposeModels() {
        models.forEach(::clearViewModel)
    }

    @Test
    fun blankCodeDoesNotStartARequest() {
        val fixture = fixture()

        fixture.model.updateCode("   ")
        fixture.model.submit()

        assertEquals(RewardCodeFailure.EmptyCode, fixture.model.state.value.failure)
        assertTrue(fixture.redeemer.requests.tryReceive().isFailure)
    }

    @Test
    fun duplicateSubmissionIsIgnoredAndAnotherCodeCanBeRedeemedAfterSuccess() = runBlocking {
        val fixture = fixture()
        fixture.model.updateCode("first-code")

        fixture.model.submit()
        fixture.model.submit()
        val first = fixture.redeemer.requests.receive()
        assertTrue(fixture.redeemer.requests.tryReceive().isFailure)
        first.complete(successResponse(first.token, "badge"))
        yield()

        assertFalse(fixture.model.state.value.isSubmitting)
        assertEquals("", fixture.model.state.value.code)
        assertEquals("badge", fixture.model.state.value.rewards?.single()?.type)

        fixture.model.updateCode("second-code")
        fixture.model.submit()
        val second = fixture.redeemer.requests.receive()
        second.complete(successResponse(second.token, "item"))
        yield()

        assertEquals(listOf("first-code", "second-code"), fixture.redeemer.codes)
        assertEquals("item", fixture.model.state.value.rewards?.single()?.type)
    }

    @Test
    fun failedRequestKeepsTheCodeAvailableForRetry() = runBlocking {
        val fixture = fixture()
        fixture.model.updateCode("retry-code")
        fixture.model.submit()
        val request = fixture.redeemer.requests.receive()

        request.complete(
            AuthenticatedRewardRedemptionResponse(
                result = Result.failure(IllegalStateException("rejected")),
                sessionToken = request.token,
            )
        )
        yield()

        val state = fixture.model.state.value
        assertEquals("retry-code", state.code)
        assertEquals(RewardCodeFailure.RequestFailed, state.failure)
        assertFalse(state.isSubmitting)
    }

    @Test
    fun accountSwitchClearsSensitiveStateAndOldResponseCannotOverwriteIt() = runBlocking {
        val tokenA = AccountSessionToken("usr_a", 1)
        val fixture = fixture(tokenA)
        fixture.model.updateCode("account-a-code")
        fixture.model.submit()
        val request = fixture.redeemer.requests.receive()

        val tokenB = AccountSessionToken("usr_b", 2)
        fixture.sessions.value = authenticated(tokenB)
        yield()

        val switched = fixture.model.state.value
        assertEquals(tokenB, switched.sessionToken)
        assertEquals("", switched.code)
        assertNull(switched.rewards)
        assertNull(switched.failure)
        assertFalse(switched.isSubmitting)

        request.complete(successResponse(tokenA, "badge"))
        yield()

        assertEquals(tokenB, fixture.model.state.value.sessionToken)
        assertNull(fixture.model.state.value.rewards)
    }

    @Test
    fun sameAccountTokenChangePreservesInputAndRewardsUntilLogout() = runBlocking {
        val tokenA = AccountSessionToken("usr_a", 1)
        val fixture = fixture(tokenA)
        fixture.model.updateCode("completed-code")
        fixture.model.submit()
        val completedRequest = fixture.redeemer.requests.receive()
        completedRequest.complete(successResponse(tokenA, "badge"))
        yield()
        fixture.model.updateCode("not-yet-submitted")

        val tokenB = AccountSessionToken("usr_a", 2)
        fixture.sessions.value = authenticated(tokenB)
        yield()

        val renewed = fixture.model.state.value
        assertEquals(tokenB, renewed.sessionToken)
        assertEquals("not-yet-submitted", renewed.code)
        assertEquals("badge", renewed.rewards?.single()?.type)

        fixture.sessions.value = null
        yield()

        val loggedOut = fixture.model.state.value
        assertNull(loggedOut.sessionToken)
        assertEquals("", loggedOut.code)
        assertNull(loggedOut.rewards)
        assertNull(loggedOut.failure)
    }

    @Test
    fun unrelatedOldTokenResponseEndsLoadingAndKeepsCodeForRetry() = runBlocking {
        val tokenA = AccountSessionToken("usr_a", 1)
        val fixture = fixture(tokenA)
        fixture.model.updateCode("retry-after-renewal")
        fixture.model.submit()
        val request = fixture.redeemer.requests.receive()

        val unrelatedToken = AccountSessionToken("usr_a", 2)
        fixture.sessions.value = authenticated(unrelatedToken)
        yield()
        request.complete(successResponse(tokenA, "badge"))
        yield()

        val state = fixture.model.state.value
        assertEquals(unrelatedToken, state.sessionToken)
        assertEquals("retry-after-renewal", state.code)
        assertNull(state.rewards)
        assertNull(state.failure)
        assertFalse(state.isSubmitting)
    }

    @Test
    fun responseAfterUnauthorizedRenewalIsAcceptedWithTheNewToken() = runBlocking {
        val tokenBeforeRenewal = AccountSessionToken("usr_a", 1)
        val fixture = fixture(tokenBeforeRenewal)
        fixture.model.updateCode("renew-code")
        fixture.model.submit()
        val request = fixture.redeemer.requests.receive()

        val renewedToken = AccountSessionToken("usr_a", 2)
        fixture.sessions.value = authenticated(renewedToken)
        yield()
        assertTrue(fixture.model.state.value.isSubmitting)
        assertEquals("renew-code", fixture.model.state.value.code)

        request.complete(successResponse(renewedToken, "item"))
        yield()

        val state = fixture.model.state.value
        assertEquals(renewedToken, state.sessionToken)
        assertEquals("", state.code)
        assertEquals("item", state.rewards?.single()?.type)
        assertNull(state.failure)
        assertFalse(state.isSubmitting)
    }

    @Test
    fun successfulUiStateDoesNotRetainTheResponseCode() = runBlocking {
        val fixture = fixture()
        val sensitiveCode = "one-time-sensitive-code"
        fixture.model.updateCode(sensitiveCode)
        fixture.model.submit()
        val request = fixture.redeemer.requests.receive()

        request.complete(
            successResponse(
                token = request.token,
                type = "badge",
                redemptionCode = sensitiveCode,
            )
        )
        yield()

        val state = fixture.model.state.value
        assertEquals("", state.code)
        assertEquals("badge", state.rewards?.single()?.type)
        assertFalse(state.toString().contains(sensitiveCode))
    }

    private fun fixture(
        token: AccountSessionToken = AccountSessionToken("usr_a", 1),
    ): RewardCodeFixture {
        val sessions = MutableStateFlow<AuthenticatedAccount?>(authenticated(token))
        val redeemer = ControlledRewardCodeRedeemer()
        val model = RewardCodeScreenModel(
            redeemer = redeemer,
            sessions = sessions,
            requestDispatcher = Dispatchers.Main,
        )
        models += model
        return RewardCodeFixture(model, sessions, redeemer)
    }
}

private data class RewardCodeFixture(
    val model: RewardCodeScreenModel,
    val sessions: MutableStateFlow<AuthenticatedAccount?>,
    val redeemer: ControlledRewardCodeRedeemer,
)

private class ControlledRewardCodeRedeemer : RewardCodeRedeemer {
    val requests = Channel<ControlledRewardRequest>(Channel.UNLIMITED)
    val codes = mutableListOf<String>()

    override suspend fun redeem(
        sessionToken: AccountSessionToken,
        code: String,
    ): AuthenticatedRewardRedemptionResponse? {
        codes += code
        val request = ControlledRewardRequest(sessionToken)
        requests.send(request)
        return request.response.await()
    }
}

private class ControlledRewardRequest(val token: AccountSessionToken) {
    val response = CompletableDeferred<AuthenticatedRewardRedemptionResponse?>()

    fun complete(result: AuthenticatedRewardRedemptionResponse) {
        response.complete(result)
    }
}

private fun successResponse(
    token: AccountSessionToken,
    type: String,
    redemptionCode: String? = null,
) = AuthenticatedRewardRedemptionResponse(
    result = Result.success(
        listOf(
            RewardRedemptionResult(
                redeemedRewards = listOf(RewardRedemption(type = type)),
                redemptionCode = redemptionCode,
            )
        )
    ),
    sessionToken = token,
)

private fun authenticated(token: AccountSessionToken) = AuthenticatedAccount(
    account = AccountDto(userId = token.userId),
    token = token,
)

private fun clearViewModel(viewModel: ViewModel) {
    ViewModelStore().apply {
        put("test", viewModel)
        clear()
    }
}
