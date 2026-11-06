package io.github.vrcmteam.vrcm.presentation.screens.scan

import androidx.lifecycle.ViewModelStore
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.service.DeviceAuthApprover
import io.github.vrcmteam.vrcm.service.data.AccountDto
import io.github.vrcmteam.vrcm.testing.MainDispatcherTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.*

class QrScannerModelTest : MainDispatcherTest() {
    private val stores = mutableListOf<ViewModelStore>()
    @AfterTest fun clearModels() { stores.forEach { it.clear() } }

    @Test
    fun scanningRequiresExplicitApprovalAndIgnoresFurtherCameraFrames() {
        val f = fixture()
        f.model.onScanned(deviceLink)
        f.model.onScanned("https://vrchat.com/home/device?code=U4RQANX4")
        assertEquals("F36XWSHN", assertIs<QrScanState.DeviceConfirmation>(f.model.state.value).code)
        assertTrue(f.requests.tryReceive().isFailure)
    }

    @Test
    fun contentLinksUseExistingLinkFlowAndUnsupportedCodesCannotBeApproved() {
        val f = fixture()
        listOf("user/usr_abc", "world/wrld_abc", "avatar/avtr_abc", "group/grp_abc").forEach {
            val url = "https://vrchat.com/home/$it"
            f.model.onScanned(url)
            assertEquals(QrScanState.ContentLink(url), f.model.state.value)
            f.model.scanAgain()
        }
        f.model.onScanned("https://example.com/home/device?code=F36XWSHN")
        f.model.approve()
        assertEquals(ScanFailure.UnsupportedCode, assertIs<QrScanState.Failure>(f.model.state.value).reason)
        assertTrue(f.requests.tryReceive().isFailure)
    }

    @Test
    fun repeatedApprovalCannotCreateConcurrentRequestsOrResumeScanning() = runBlocking {
        val f = fixture()
        f.model.onScanned(deviceLink)
        f.model.approve()
        f.model.approve()
        f.model.scanAgain()
        val request = f.requests.receive()
        assertIs<QrScanState.Approving>(f.model.state.value)
        assertTrue(f.requests.tryReceive().isFailure)
        request.complete(Result.success(Unit))
        yield()
        assertEquals(QrScanState.Approved, f.model.state.value)
        f.model.approve()
        assertTrue(f.requests.tryReceive().isFailure)
    }

    @Test
    fun changingAccountsBeforeConfirmationPreventsApproval() {
        val f = fixture()
        f.model.onScanned(deviceLink)
        f.sessions.value = account("usr_other", 2)
        f.model.approve()
        assertEquals(ScanFailure.SessionChanged, assertIs<QrScanState.Failure>(f.model.state.value).reason)
        assertTrue(f.requests.tryReceive().isFailure)
    }

    @Test
    fun accountChangeWhileWaitingCannotDisplaySuccessForAnotherAccount() = runBlocking {
        val f = fixture()
        f.model.onScanned(deviceLink)
        f.model.approve()
        val request = f.requests.receive()
        f.sessions.value = account("usr_other", 2)
        request.complete(Result.success(Unit))
        yield()
        assertEquals(ScanFailure.SessionChanged, assertIs<QrScanState.Failure>(f.model.state.value).reason)
    }

    @Test
    fun expiredCodeRequiresRescanningRatherThanRetryingTheExpiredApproval() = runBlocking {
        val f = fixture()
        f.model.onScanned(deviceLink)
        f.model.approve()
        f.requests.receive().complete(Result.failure(VRCApiException("Not Found", 404, "expired")))
        yield()
        assertEquals(ScanFailure.Expired, assertIs<QrScanState.Failure>(f.model.state.value).reason)
        f.model.approve()
        assertTrue(f.requests.tryReceive().isFailure)
        f.model.scanAgain()
        f.model.onScanned("https://vrchat.com/home/device?code=U4RQANX4")
        assertEquals("U4RQANX4", assertIs<QrScanState.DeviceConfirmation>(f.model.state.value).code)
    }

    @Test
    fun failedApprovalOnlyRetriesOnAnotherExplicitClick() = runBlocking {
        val f = fixture()
        f.model.onScanned(deviceLink)
        f.model.approve()
        f.requests.receive().complete(Result.failure(IllegalStateException("offline")))
        yield()
        assertEquals("F36XWSHN", assertIs<QrScanState.Failure>(f.model.state.value).confirmation?.code)
        assertTrue(f.requests.tryReceive().isFailure)
        f.model.approve()
        f.requests.receive().complete(Result.success(Unit))
        yield()
        assertEquals(QrScanState.Approved, f.model.state.value)
    }

    @Test
    fun leavingScreenCancelsPendingApproval() = runBlocking {
        val f = fixture()
        f.model.onScanned(deviceLink)
        f.model.approve()
        val request = f.requests.receive()
        stores.single().clear()
        request.complete(Result.success(Unit))
        yield()
        assertIs<QrScanState.Approving>(f.model.state.value)
        Unit
    }

    private fun fixture(): Fixture {
        val sessions = MutableStateFlow<AuthenticatedAccount?>(account("usr_owner", 1))
        val requests = Channel<CompletableDeferred<Result<Unit>>>(Channel.UNLIMITED)
        val approver = DeviceAuthApprover { code, token ->
            assertEquals("F36XWSHN", code)
            assertEquals("usr_owner", token.userId)
            CompletableDeferred<Result<Unit>>().also { requests.send(it) }.await()
        }
        val model = QrScannerModel(approver, sessions)
        stores += ViewModelStore().apply { put("scanner", model) }
        return Fixture(model, sessions, requests)
    }

    private fun account(id: String, generation: Long) = AuthenticatedAccount(AccountDto(userId = id), AccountSessionToken(id, generation))
    private data class Fixture(
        val model: QrScannerModel,
        val sessions: MutableStateFlow<AuthenticatedAccount?>,
        val requests: Channel<CompletableDeferred<Result<Unit>>>,
    )

    private val deviceLink = "https://vrchat.com/home/device?code=F36XWSHN&source=app"
}
