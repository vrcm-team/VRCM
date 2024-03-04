package io.github.vrcmteam.vrcm.network.supports

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
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
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        })
    }
}


