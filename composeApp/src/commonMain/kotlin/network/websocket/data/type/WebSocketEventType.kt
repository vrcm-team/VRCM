package io.github.vrcmteam.vrcm.network.websocket.data.type

import kotlin.reflect.KClass


/**
 * WebSocket 事件类型
 * @see (https://vrchatapi.github.io/tutorials/websocket/)
 */
sealed class WebSocketEventType(val typeName: String,val contentType: KClass<*>)
