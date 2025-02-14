package io.github.vrcmteam.vrcm.network.supports

import io.github.vrcmteam.vrcm.network.api.attributes.VRC_API_URL
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

/**
 * 默认的http client配置
 */
val ApiClientDefaultBuilder: HttpClientConfig<*>.() -> Unit = {
    defaultRequest { url.takeFrom(VRC_API_URL) }
    // http timeout超时时间
    install(HttpTimeout) {
        requestTimeoutMillis = 15000
        connectTimeoutMillis = 15000
        socketTimeoutMillis = 15000
    }
    // websocket
    install(WebSockets){
        // json 序列化配置
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
        // ping间隔
        pingInterval = 5.seconds
    }
    // user agent用户代理信息 (就是添加一个header)
    install(UserAgent)
}


