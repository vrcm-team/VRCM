package io.github.kamo.vrcm.domain.api

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.gson.*

//const val userAgent
object APIClient {
    private val apiClient = HttpClient {
        defaultRequest {
            url {
                protocol = URLProtocol.HTTPS
                host = "api.vrchat.cloud"
                path("api/1", "")
            }
        }
        install(ContentNegotiation) { gson() }
        install(UserAgent)
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


