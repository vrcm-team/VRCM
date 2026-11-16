package io.github.vrcmteam.vrcm.presentation.screens.scan

import androidx.lifecycle.ViewModelStore
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.network.api.auth.data.DeviceAuthInfoData
import io.github.vrcmteam.vrcm.network.api.auth.data.DeviceAuthGeolocationData
import io.github.vrcmteam.vrcm.service.DeviceAuthGateway
import io.github.vrcmteam.vrcm.service.SessionBoundResponse
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
    fun choosingAnImagePausesLiveFramesAndCancellingResumesTheCamera() {
        val f = fixture()
        f.model.chooseImage()
        f.model.onScanned(deviceLink)
        f.model.chooseImage()
        f.model.scanAgain()
        assertEquals(QrScanState.ChoosingImage, f.model.state.value)
        f.model.imageSelectionCancelled()
        f.model.onScanned(deviceLink)
        assertIs<QrScanState.DeviceConfirmation>(f.model.state.value)
    }

    @Test
    fun imageApprovalUsesTheSameConfirmationAndRejectsCompetingInputs() = runBlocking {
        val decoded = CompletableDeferred<QrScanBatch>()
        val f = fixture(QrImageDecoder { decoded.await() })
        f.model.chooseImage()
        f.model.readImage { byteArrayOf(1) }
        f.model.onScanned("https://vrchat.com/home/user/usr_wrong")
        f.model.readImage { error("Duplicate reads must not start") }
        f.model.imageSelectionCancelled()
        f.model.scanAgain()
        assertEquals(QrScanState.ReadingImage, f.model.state.value)
        decoded.complete(batch(deviceLink))
        yield()
        assertEquals("F36XWSHN", assertIs<QrScanState.DeviceConfirmation>(f.model.state.value).code)
        assertTrue(f.requests.tryReceive().isFailure)
        f.model.approve()
        f.requests.receive().complete(Result.success(Unit))
        yield()
        assertEquals(QrScanState.Approved, f.model.state.value)
    }

    @Test
    fun imageContentLinksUseExistingNavigationAndImageFailuresCanBeRetried() = runBlocking {
        var decoded: String? = "https://vrchat.com/home/avatar/avtr_test"
        val f = fixture(QrImageDecoder { batch(*listOfNotNull(decoded).toTypedArray()) })
        f.model.chooseImage()
        f.model.readImage { byteArrayOf(1) }
        yield()
        assertEquals(QrScanState.ContentLink(decoded!!), f.model.state.value)
        f.model.scanAgain()
        decoded = null
        f.model.chooseImage()
        f.model.readImage { byteArrayOf(1) }
        yield()
        assertEquals(ScanFailure.ImageNoCode, assertIs<QrScanState.Failure>(f.model.state.value).reason)
        f.model.scanAgain()
        f.model.chooseImage()
        f.model.readImage { error("Unreadable file") }
        yield()
        assertEquals(ScanFailure.ImageRead, assertIs<QrScanState.Failure>(f.model.state.value).reason)
        f.model.scanAgain()
        assertEquals(QrScanState.Scanning, f.model.state.value)
    }

    @Test
    fun leavingScreenCancelsImageRecognitionWithoutShowingAnError() = runBlocking {
        val decoding = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val f = fixture(QrImageDecoder {
            decoding.complete(Unit)
            try { CompletableDeferred<QrScanBatch>().await() } finally { cancelled.complete(Unit) }
        })
        f.model.chooseImage()
        f.model.readImage { byteArrayOf(1) }
        decoding.await()
        stores.single().clear()
        cancelled.await()
        assertEquals(QrScanState.ReadingImage, f.model.state.value)
        assertTrue(f.requests.tryReceive().isFailure)
    }

    @Test
    fun personalCodePausesCaptureAndResumesOnlyWhenReturningToTheScanner() {
        val f = fixture()
        assertEquals("usr_owner", f.model.showMyCode())
        f.model.onScanned(deviceLink)
        f.model.chooseImage()
        assertNull(f.model.showMyCode())
        assertEquals(QrScanState.ViewingMyCode, f.model.state.value)
        f.model.resumeFromMyCode()
        f.model.onCandidates(batch(deviceLink, "https://vrchat.com/home/user/usr_other"))
        assertEquals("usr_owner", f.model.showMyCode())
        f.model.selectCode(0)
        assertEquals(QrScanState.ViewingMyCode, f.model.state.value)
        f.model.resumeFromMyCode()
        f.model.onScanned(deviceLink)
        assertIs<QrScanState.DeviceConfirmation>(f.model.state.value)
    }

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

    @Test
    fun multipleCameraCodesWaitForAValidSelectionAndIgnoreFurtherFrames() {
        val f = fixture()
        val candidates = batch("https://vrchat.com/home/user/usr_other", deviceLink)
        f.model.onCandidates(candidates)
        f.model.onScanned(deviceLink)
        f.model.onCandidates(batch("https://vrchat.com/home/avatar/avtr_wrong"))
        f.model.approve()
        f.model.selectCode(-1)
        f.model.selectCode(2)
        assertEquals(QrScanState.Selecting(candidates), f.model.state.value)
        assertTrue(f.requests.tryReceive().isFailure)
        f.model.selectCode(1)
        assertEquals("F36XWSHN", assertIs<QrScanState.DeviceConfirmation>(f.model.state.value).code)
        f.model.selectCode(0)
        assertIs<QrScanState.DeviceConfirmation>(f.model.state.value)
        assertTrue(f.requests.tryReceive().isFailure)
    }

    @Test
    fun albumSelectionRoutesOnlyTheChosenCodeAndCanReturnToCapture() = runBlocking {
        val candidates = batch(deviceLink, "https://vrchat.com/home/world/wrld_chosen")
        val decoding = CompletableDeferred<QrScanBatch>()
        val f = fixture(QrImageDecoder { decoding.await() })
        f.model.chooseImage()
        f.model.readImage { byteArrayOf(1) }
        f.model.onCandidates(batch(deviceLink))
        assertEquals(QrScanState.ReadingImage, f.model.state.value)
        decoding.complete(candidates)
        yield()
        assertEquals(QrScanState.Selecting(candidates), f.model.state.value)
        f.model.selectCode(1)
        assertEquals(QrScanState.ContentLink(candidates.codes[1].value), f.model.state.value)
        f.model.scanAgain()
        f.model.onCandidates(candidates)
        f.model.scanAgain()
        assertEquals(QrScanState.Scanning, f.model.state.value)
        f.model.onCandidates(candidates)
        f.model.chooseImage()
        f.model.selectCode(0)
        assertEquals(QrScanState.ChoosingImage, f.model.state.value)
        f.model.imageSelectionCancelled()
        assertEquals(QrScanState.Scanning, f.model.state.value)
    }

    @Test
    fun approvalWaitsForDetailsAndAnExplicitClickWhileIgnoringCompetingActions() = runBlocking {
        val result = CompletableDeferred<Result<DeviceAuthInfoData>>()
        var loads = 0
        val f = fixture(infoLoader = { code, session ->
            assertEquals("F36XWSHN", code)
            loads++
            SessionBoundResponse(result.await(), session)
        })
        f.model.onScanned(deviceLink)
        assertIs<QrScanState.LoadingDeviceInfo>(f.model.state.value)
        f.model.approve()
        f.model.retryDeviceInfo()
        f.model.onScanned("https://vrchat.com/home/device?code=U4RQANX4")
        f.model.chooseImage()
        f.model.scanAgain()
        assertNull(f.model.showMyCode())
        assertIs<QrScanState.LoadingDeviceInfo>(f.model.state.value)
        assertEquals(1, loads)
        assertTrue(f.requests.tryReceive().isFailure)
        result.complete(Result.success(deviceInfo))
        yield()
        val confirmation = assertIs<QrScanState.DeviceConfirmation>(f.model.state.value)
        assertEquals(deviceInfo, confirmation.info)
        assertEquals("F36XWSHN", confirmation.code)
        assertTrue(f.requests.tryReceive().isFailure)
        f.model.approve()
        f.requests.receive().complete(Result.success(Unit))
        yield()
        assertEquals(QrScanState.Approved, f.model.state.value)
    }

    @Test
    fun detailsFailureRequiresExplicitReloadBeforeApproval() = runBlocking {
        var loads = 0
        val f = fixture(infoLoader = { _, session ->
            loads++
            SessionBoundResponse(if (loads == 1) Result.failure(IllegalStateException("offline"))
                else Result.success(deviceInfo), session)
        })
        f.model.onScanned(deviceLink)
        val failure = assertIs<QrScanState.Failure>(f.model.state.value)
        assertEquals(ScanFailure.DeviceInfo, failure.reason)
        assertNull(failure.confirmation)
        f.model.approve()
        assertTrue(f.requests.tryReceive().isFailure)
        assertEquals(1, loads)
        f.model.retryDeviceInfo()
        assertEquals(deviceInfo, assertIs<QrScanState.DeviceConfirmation>(f.model.state.value).info)
        assertEquals(2, loads)
        assertTrue(f.requests.tryReceive().isFailure)
    }

    @Test
    fun expiredDetailsRequireANewScanAndCannotBeApprovedOrRetried() {
        var loads = 0
        val f = fixture(infoLoader = { _, session ->
            loads++
            SessionBoundResponse(Result.failure(VRCApiException("Not Found", 404, "expired")), session)
        })
        f.model.onScanned(deviceLink)
        assertEquals(QrScanState.Failure(ScanFailure.Expired), f.model.state.value)
        f.model.approve()
        f.model.retryDeviceInfo()
        assertEquals(1, loads)
        assertTrue(f.requests.tryReceive().isFailure)
        f.model.scanAgain()
        assertEquals(QrScanState.Scanning, f.model.state.value)
    }

    @Test
    fun leavingScreenCancelsPendingDetails() = runBlocking {
        val cancelled = CompletableDeferred<Unit>()
        val f = fixture(infoLoader = { _, _ ->
            try { CompletableDeferred<SessionBoundResponse<DeviceAuthInfoData>>().await() }
            finally { cancelled.complete(Unit) }
        })
        f.model.onScanned(deviceLink)
        stores.single().clear()
        cancelled.await()
        assertIs<QrScanState.LoadingDeviceInfo>(f.model.state.value)
        assertTrue(f.requests.tryReceive().isFailure)
    }

    @Test
    fun staleDetailsCannotBeShownEvenAfterSigningBackIntoTheSameAccount() = runBlocking {
        val result = CompletableDeferred<Unit>()
        val f = fixture(infoLoader = { _, session ->
            result.await()
            SessionBoundResponse(Result.success(deviceInfo), session)
        })
        f.model.onScanned(deviceLink)
        f.sessions.value = account("usr_other", 2)
        f.sessions.value = account("usr_owner", 3)
        result.complete(Unit)
        yield()
        assertEquals(QrScanState.Failure(ScanFailure.SessionChanged), f.model.state.value)
        f.model.approve()
        assertTrue(f.requests.tryReceive().isFailure)
    }

    @Test
    fun refreshedAuthenticationFromDetailsIsUsedForApproval() = runBlocking {
        val refreshed = CompletableDeferred<AccountSessionToken>()
        val f = fixture(infoLoader = { _, _ ->
            SessionBoundResponse(Result.success(deviceInfo), refreshed.await())
        })
        f.model.onScanned(deviceLink)
        f.sessions.value = account("usr_owner", 2)
        refreshed.complete(f.sessions.value!!.token)
        yield()
        assertEquals(f.sessions.value!!.token, assertIs<QrScanState.DeviceConfirmation>(f.model.state.value).session)
        f.model.approve()
        f.requests.receive().complete(Result.success(Unit))
        yield()
        assertEquals(QrScanState.Approved, f.model.state.value)
    }

    @Test
    fun retryingDetailsAfterAccountChangeCannotRequestAnotherAccountsLogin() {
        var loads = 0
        val f = fixture(infoLoader = { _, session ->
            loads++
            SessionBoundResponse(Result.failure(IllegalStateException("offline")), session)
        })
        f.model.onScanned(deviceLink)
        f.sessions.value = account("usr_other", 2)
        f.model.retryDeviceInfo()
        assertEquals(QrScanState.Failure(ScanFailure.SessionChanged), f.model.state.value)
        assertEquals(1, loads)
        assertTrue(f.requests.tryReceive().isFailure)
    }

    private fun batch(vararg values: String) = QrScanBatch(values.map { QrDetection(it, 0.5f, 0.5f) })

    private fun fixture(imageDecoder: QrImageDecoder = QrImageDecoder { batch() },
        infoLoader: suspend (String, AccountSessionToken) -> SessionBoundResponse<DeviceAuthInfoData>? =
            { _, session -> SessionBoundResponse(Result.success(deviceInfo), session) }): Fixture {
        val sessions = MutableStateFlow<AuthenticatedAccount?>(account("usr_owner", 1))
        val requests = Channel<CompletableDeferred<Result<Unit>>>(Channel.UNLIMITED)
        val gateway = object : DeviceAuthGateway {
            override suspend fun info(userCode: String, session: AccountSessionToken) = infoLoader(userCode, session)
            override suspend fun approve(userCode: String, session: AccountSessionToken): SessionBoundResponse<Unit> {
                assertEquals("F36XWSHN", userCode)
                assertEquals("usr_owner", session.userId)
                val result = CompletableDeferred<Result<Unit>>().also { requests.send(it) }.await()
                return SessionBoundResponse(result, session)
            }
        }
        val model = QrScannerModel(gateway, sessions, imageDecoder)
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
    private val deviceInfo = DeviceAuthInfoData(1792964421294,
        DeviceAuthGeolocationData(city = "Toronto", country = "CA", region = "Ontario", regionCode = "ON"))
}
