package io.github.vrcmteam.vrcm.core.shared

import io.github.vrcmteam.vrcm.network.websocket.data.WebSocketEvent
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.service.data.AccountDto
import kotlinx.coroutines.flow.MutableSharedFlow

object SharedFlowCentre {

    val webSocket = MutableSharedFlow<WebSocketEvent>()

    val authed = MutableSharedFlow<AccountDto>()

    val logout = MutableSharedFlow<Unit>()

    val toastText = MutableSharedFlow<ToastText>()

    val toPagerTop = MutableSharedFlow<Unit>()
}