package io.github.vrcmteam.vrcm.network.supports

import io.github.vrcmteam.vrcm.network.api.attributes.VRC_API_URL
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/**
 * 默认的http client配置
 */
@OptIn(ExperimentalSerializationApi::class)
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
        pingInterval = 20_000L
    }
    // user agent用户代理信息 (就是添加一个header)
    install(UserAgent)
    // api的json序列化器
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
            prettyPrint = true
            isLenient = true
        })
    }
}


