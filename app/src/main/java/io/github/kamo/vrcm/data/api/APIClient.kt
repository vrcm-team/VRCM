package io.github.kamo.vrcm.data.api

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.gson.*


class APIClient(storage: CookiesStorage = AcceptAllCookiesStorage()) {

    private var apiClient: HttpClient = HttpClient {
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

    suspend fun get(urlString: String, requestBuilder: HttpRequestBuilder.() -> Unit = {}) =
        request(urlString, HttpMethod.Get,requestBuilder)

    suspend fun post(urlString: String, requestBuilder: HttpRequestBuilder.() -> Unit= {}) =
        request(urlString, HttpMethod.Post,requestBuilder)

    suspend fun put(urlString: String, requestBuilder: HttpRequestBuilder.() -> Unit= {}) =
        request(urlString, HttpMethod.Put,requestBuilder)

    suspend fun delete(urlString: String, requestBuilder: HttpRequestBuilder.() -> Unit= {}) =
        request(urlString, HttpMethod.Delete,requestBuilder)

    suspend fun patch(urlString: String, requestBuilder: HttpRequestBuilder.() -> Unit= {}) =
        request(urlString, HttpMethod.Patch,requestBuilder)


    private suspend fun request(
        urlString: String,
        method: HttpMethod = HttpMethod.Get,
        requestBuilder: HttpRequestBuilder.() -> Unit = {}
    ) =
        apiClient.request {
//            url(urlString.replaceFirst("https://api.vrchat.cloud/api/1", ""))
            url(urlString)
            this.method = method
            requestBuilder()
        }

}




