package io.github.vrcmteam.vrcm.presentation.screens.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.network.api.auth.data.DeviceAuthInfoData
import io.github.vrcmteam.vrcm.service.DeviceAuthGateway
import io.github.vrcmteam.vrcm.service.parseDeviceAuthCode
import io.github.vrcmteam.vrcm.service.parseOfficialLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

internal data class DeviceAuthRequest(val code: String, val session: AccountSessionToken)

internal sealed interface QrScanState {
    data object Scanning : QrScanState
    data object ChoosingImage : QrScanState
    data object ReadingImage : QrScanState
    data object ViewingMyCode : QrScanState
    data class Selecting(val batch: QrScanBatch) : QrScanState
    data class ContentLink(val url: String) : QrScanState
    data class LoadingDeviceInfo(val request: DeviceAuthRequest) : QrScanState
    data class DeviceConfirmation(val code: String, val session: AccountSessionToken, val info: DeviceAuthInfoData) : QrScanState
    data class Approving(val confirmation: DeviceConfirmation) : QrScanState
    data object Approved : QrScanState
    data class Failure(val reason: ScanFailure, val confirmation: DeviceConfirmation? = null,
        val infoRequest: DeviceAuthRequest? = null) : QrScanState
}

internal enum class ScanFailure { Camera, ImageRead, ImageNoCode, UnsupportedCode, DeviceInfo, Approval, Expired, SessionChanged }

internal class QrScannerModel(
    private val deviceAuth: DeviceAuthGateway,
    private val sessions: StateFlow<AuthenticatedAccount?>,
    private val imageDecoder: QrImageDecoder,
) : ViewModel() {
    private val _state = MutableStateFlow<QrScanState>(QrScanState.Scanning)
    val state = _state.asStateFlow()

    fun onScanned(value: String) {
        if (_state.value != QrScanState.Scanning) return
        resolveCode(value)
    }

    fun onCandidates(batch: QrScanBatch) {
        if (_state.value != QrScanState.Scanning) return
        acceptBatch(batch)
    }

    private fun acceptBatch(batch: QrScanBatch) {
        when (batch.codes.size) {
            0 -> _state.value = QrScanState.Failure(ScanFailure.ImageNoCode)
            1 -> resolveCode(batch.codes.single().value)
            else -> _state.value = QrScanState.Selecting(batch)
        }
    }

    fun selectCode(index: Int) {
        val selection = _state.value as? QrScanState.Selecting ?: return
        val code = selection.batch.codes.getOrNull(index) ?: return
        resolveCode(code.value)
    }

    private fun resolveCode(value: String) {
        val code = parseDeviceAuthCode(value)
        if (code != null) {
            val session = sessions.value?.token
            if (session == null) _state.value = QrScanState.Failure(ScanFailure.SessionChanged)
            else loadDeviceInfo(DeviceAuthRequest(code, session))
            return
        }
        _state.value = when {
            parseOfficialLink(value) != null -> QrScanState.ContentLink(value)
            else -> QrScanState.Failure(ScanFailure.UnsupportedCode)
        }
    }

    private fun loadDeviceInfo(request: DeviceAuthRequest) {
        if (sessions.value?.token != request.session) {
            _state.value = QrScanState.Failure(ScanFailure.SessionChanged)
            return
        }
        _state.value = QrScanState.LoadingDeviceInfo(request)
        viewModelScope.launch {
            val response = deviceAuth.info(request.code, request.session)
            _state.value = if (response == null || sessions.value?.token != response.sessionToken) {
                QrScanState.Failure(ScanFailure.SessionChanged)
            } else response.result.fold(
                onSuccess = { QrScanState.DeviceConfirmation(request.code, response.sessionToken, it) },
                onFailure = {
                    if ((it as? VRCApiException)?.code == 404) QrScanState.Failure(ScanFailure.Expired)
                    else QrScanState.Failure(ScanFailure.DeviceInfo,
                        infoRequest = request.copy(session = response.sessionToken))
                },
            )
        }
    }

    fun retryDeviceInfo() {
        val failure = _state.value as? QrScanState.Failure ?: return
        failure.infoRequest?.let(::loadDeviceInfo)
    }

    fun chooseImage() {
        if (_state.value == QrScanState.Scanning || _state.value is QrScanState.Selecting ||
            _state.value == QrScanState.Failure(ScanFailure.Camera)) {
            _state.value = QrScanState.ChoosingImage
        }
    }

    fun imageSelectionCancelled() {
        if (_state.value == QrScanState.ChoosingImage) _state.value = QrScanState.Scanning
    }

    /** Selection and decoding have exclusive ownership of scan results until they finish. */
    fun readImage(read: suspend () -> ByteArray) {
        if (_state.value != QrScanState.ChoosingImage) return
        _state.value = QrScanState.ReadingImage
        viewModelScope.launch {
            try {
                acceptBatch(imageDecoder.decode(read()))
            } catch (cause: CancellationException) {
                throw cause
            } catch (_: Exception) {
                _state.value = QrScanState.Failure(ScanFailure.ImageRead)
            }
        }
    }

    fun showMyCode(): String? {
        if (_state.value != QrScanState.Scanning && _state.value !is QrScanState.Selecting) return null
        val userId = sessions.value?.token?.userId ?: run {
            _state.value = QrScanState.Failure(ScanFailure.SessionChanged)
            return null
        }
        _state.value = QrScanState.ViewingMyCode
        return userId
    }

    fun resumeFromMyCode() {
        if (_state.value == QrScanState.ViewingMyCode) _state.value = QrScanState.Scanning
    }

    fun cameraFailed() {
        if (_state.value == QrScanState.Scanning) {
            _state.value = QrScanState.Failure(ScanFailure.Camera)
        }
    }

    fun scanAgain() {
        when (_state.value) {
            is QrScanState.LoadingDeviceInfo, is QrScanState.Approving,
            QrScanState.ChoosingImage, QrScanState.ReadingImage -> Unit
            else -> _state.value = QrScanState.Scanning
        }
    }

    fun approve() {
        val confirmation = when (val current = _state.value) {
            is QrScanState.DeviceConfirmation -> current
            is QrScanState.Failure -> current.confirmation
            else -> null
        } ?: return
        if (sessions.value?.token != confirmation.session) {
            _state.value = QrScanState.Failure(ScanFailure.SessionChanged)
            return
        }
        _state.value = QrScanState.Approving(confirmation)
        viewModelScope.launch {
            val response = deviceAuth.approve(confirmation.code, confirmation.session)
            _state.value = if (response == null || sessions.value?.token != response.sessionToken) {
                QrScanState.Failure(ScanFailure.SessionChanged)
            } else {
                response.result.fold(
                    onSuccess = { QrScanState.Approved },
                    onFailure = { error ->
                        if ((error as? VRCApiException)?.code == 404) {
                            QrScanState.Failure(ScanFailure.Expired)
                        } else {
                            QrScanState.Failure(ScanFailure.Approval, confirmation.copy(session = response.sessionToken))
                        }
                    },
                )
            }
        }
    }
}
