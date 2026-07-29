package io.github.vrcmteam.vrcm.network.supports

import io.github.vrcmteam.vrcm.network.api.attributes.VRC_API_URL
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

private const val RATE_LIMIT_MAX_RETRIES = 5
private const val RATE_LIMIT_BASE_DELAY_MILLIS = 1_000L
private const val RATE_LIMIT_MAX_DELAY_MILLIS = 60_000L

/**
 * 默认的http client配置
 */
val ApiClientDefaultBuilder: HttpClientConfig<*>.() -> Unit = {
    defaultRequest { url.takeFrom(VRC_API_URL) }
    // retry five times with exponential backoff.
    // Retrying mutations automatically could repeat a user action after an uncertain response.
    install(HttpRequestRetry) {
        retryIf(maxRetries = RATE_LIMIT_MAX_RETRIES) { request, response ->
            request.method in setOf(HttpMethod.Get, HttpMethod.Head) &&
                response.status == HttpStatusCode.TooManyRequests
        }
        delayMillis { retryCount ->
            val retryAfterMillis = response?.headers?.get(HttpHeaders.RetryAfter)
                ?.trim()
                ?.toLongOrNull()
                ?.takeIf { it >= 0L }
                ?.coerceAtMost(RATE_LIMIT_MAX_DELAY_MILLIS / 1_000L)
                ?.times(1_000L)

            retryAfterMillis ?: (RATE_LIMIT_BASE_DELAY_MILLIS shl (retryCount - 1))
                .coerceAtMost(RATE_LIMIT_MAX_DELAY_MILLIS)
        }
    }
    // http timeout超时时间
    // 注意：socketTimeoutMillis 会同时作用于 HTTP 和 WebSocket 连接
    // WebSocket 是长连接，socket 超时必须大于 pingInterval，否则会被提前断开
    install(HttpTimeout) {
        requestTimeoutMillis = 15000
        connectTimeoutMillis = 15000
        socketTimeoutMillis = 120_000
    }
    // websocket
    install(WebSockets){
        // json 序列化配置
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
        // ping间隔（保持连接活跃，必须小于 socketTimeoutMillis）
        pingInterval = 30.seconds
    }
    // user agent用户代理信息 (就是添加一个header)
    install(UserAgent)
}


