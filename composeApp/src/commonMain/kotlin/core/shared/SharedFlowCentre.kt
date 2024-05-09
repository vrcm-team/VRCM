package io.github.vrcmteam.vrcm.core.shared

import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import kotlinx.coroutines.flow.MutableSharedFlow
import network.websocket.data.WebSocketEvent

object SharedFlowCentre {

    val webSocket = MutableSharedFlow<WebSocketEvent>()

    val authed = MutableSharedFlow<Pair<String?, String?>>()

    val logout = MutableSharedFlow<Unit>()

    val toastText = MutableSharedFlow<ToastText>()

    val toPagerTop = MutableSharedFlow<Unit>()
}