package io.github.kamo.vrcm.data.api;

import io.github.kamo.vrcm.common.UserStatus
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.gson.*

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
        gson {
            this.registerTypeAdapter(UserStatus::class.java, UserStatus.Deserializer)
            this.registerTypeAdapter(UserStatus::class.java, UserStatus.Serializer)
        }
    }
}

