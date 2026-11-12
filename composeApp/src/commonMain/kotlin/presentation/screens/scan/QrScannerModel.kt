package io.github.vrcmteam.vrcm.presentation.screens.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.service.DeviceAuthApprover
import io.github.vrcmteam.vrcm.service.parseDeviceAuthCode
import io.github.vrcmteam.vrcm.service.parseOfficialLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

internal sealed interface QrScanState {
    data object Scanning : QrScanState
    data object ChoosingImage : QrScanState
    data object ReadingImage : QrScanState
    data object ViewingMyCode : QrScanState
    data class Selecting(val batch: QrScanBatch) : QrScanState
    data class ContentLink(val url: String) : QrScanState
    data class DeviceConfirmation(val code: String, val session: AccountSessionToken) : QrScanState
    data class Approving(val confirmation: DeviceConfirmation) : QrScanState
    data object Approved : QrScanState
    data class Failure(val reason: ScanFailure, val confirmation: DeviceConfirmation? = null) : QrScanState
}

internal enum class ScanFailure { Camera, ImageRead, ImageNoCode, UnsupportedCode, Approval, Expired, SessionChanged }

internal class QrScannerModel(
    private val approver: DeviceAuthApprover,
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
        _state.value = when {
            code != null -> sessions.value?.token?.let {
                QrScanState.DeviceConfirmation(code, it)
            } ?: QrScanState.Failure(ScanFailure.SessionChanged)
            parseOfficialLink(value) != null -> QrScanState.ContentLink(value)
            else -> QrScanState.Failure(ScanFailure.UnsupportedCode)
        }
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
            is QrScanState.Approving, QrScanState.ChoosingImage, QrScanState.ReadingImage -> Unit
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
            val result = approver.approve(confirmation.code, confirmation.session)
            _state.value = if (sessions.value?.token?.userId != confirmation.session.userId) {
                QrScanState.Failure(ScanFailure.SessionChanged)
            } else {
                result.fold(
                    onSuccess = { QrScanState.Approved },
                    onFailure = { error ->
                        if ((error as? VRCApiException)?.code == 404) {
                            QrScanState.Failure(ScanFailure.Expired)
                        } else {
                            QrScanState.Failure(ScanFailure.Approval, confirmation)
                        }
                    },
                )
            }
        }
    }
}
