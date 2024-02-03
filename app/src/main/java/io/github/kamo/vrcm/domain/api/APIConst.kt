package io.github.kamo.vrcm.domain.api

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.gson.*


class APIClient(storage: CookiesStorage = AcceptAllCookiesStorage()) {

    private var apiClient : HttpClient = HttpClient {
        defaultRequest {
            url {
                protocol = URLProtocol.HTTPS
                host = "api.vrchat.cloud"
                path("api/1", "")
            }
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.ALL
        }
        install(ContentNegotiation) { gson() }
        install(UserAgent)
        install(HttpCookies) {
            this.storage = storage
        }
    }

    suspend fun get(requestBuilder: HttpRequestBuilder.() -> Unit) = apiClient.get { requestBuilder() }

    suspend fun post(requestBuilder: HttpRequestBuilder.() -> Unit) =
        apiClient.post {
            requestBuilder()
        }

    suspend fun put(requestBuilder: HttpRequestBuilder.() -> Unit) =
        apiClient.put {
            requestBuilder()
        }

    suspend fun delete(requestBuilder: HttpRequestBuilder.() -> Unit) =
        apiClient.delete {
            requestBuilder()
        }

    suspend fun patch(requestBuilder: HttpRequestBuilder.() -> Unit) =
        apiClient.patch {
            requestBuilder()
        }


    suspend fun request(method: HttpMethod = HttpMethod.Get, requestBuilder: HttpRequestBuilder.() -> Unit) =
        apiClient.request {
            this.method = method
            requestBuilder()
        }

}




