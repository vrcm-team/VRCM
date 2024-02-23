package io.github.kamo.vrcm.data.api;

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.URLProtocol
import io.ktor.http.path
import io.ktor.serialization.gson.gson

val ApiClientDefaultBuilder: HttpClientConfig<*>.() -> Unit = {
    defaultRequest {
        url {
            protocol = URLProtocol.HTTPS
            host = "api.vrchat.cloud"
            path("api/1", "")
        }
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 15000
        connectTimeoutMillis = 15000
        socketTimeoutMillis = 15000
    }
    install(UserAgent)
    install(ContentNegotiation) { gson () }
}

