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

internal sealed interface QrScanState {
    data object Scanning : QrScanState
    data class ContentLink(val url: String) : QrScanState
    data class DeviceConfirmation(val code: String, val session: AccountSessionToken) : QrScanState
    data class Approving(val confirmation: DeviceConfirmation) : QrScanState
    data object Approved : QrScanState
    data class Failure(val reason: ScanFailure, val confirmation: DeviceConfirmation? = null) : QrScanState
}

internal enum class ScanFailure { Camera, UnsupportedCode, Approval, Expired, SessionChanged }

internal class QrScannerModel(
    private val approver: DeviceAuthApprover,
    private val sessions: StateFlow<AuthenticatedAccount?>,
) : ViewModel() {
    private val _state = MutableStateFlow<QrScanState>(QrScanState.Scanning)
    val state = _state.asStateFlow()

    fun onScanned(value: String) {
        if (_state.value != QrScanState.Scanning) return
        val code = parseDeviceAuthCode(value)
        _state.value = when {
            code != null -> sessions.value?.token?.let {
                QrScanState.DeviceConfirmation(code, it)
            } ?: QrScanState.Failure(ScanFailure.SessionChanged)
            parseOfficialLink(value) != null -> QrScanState.ContentLink(value)
            else -> QrScanState.Failure(ScanFailure.UnsupportedCode)
        }
    }

    fun cameraFailed() {
        if (_state.value == QrScanState.Scanning) {
            _state.value = QrScanState.Failure(ScanFailure.Camera)
        }
    }

    fun scanAgain() {
        if (_state.value !is QrScanState.Approving) _state.value = QrScanState.Scanning
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
