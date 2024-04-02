package io.github.vrcmteam.vrcm.core.shared

import kotlinx.coroutines.flow.MutableSharedFlow
import network.websocket.data.WebSocketEvent

object SharedFlowCentre {

    val webSocket = MutableSharedFlow<WebSocketEvent>()

    val authed = MutableSharedFlow<Pair<String?, String?>>()
}