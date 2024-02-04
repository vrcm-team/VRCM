package io.github.kamo.vrcm.data.api.file

import io.ktor.client.HttpClient
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.request
import io.ktor.http.cookies

internal const val FILE_API_SUFFIX = "file"
class FileAPI(private val client: HttpClient) {


   suspend fun getFileLocal(fileUrl:String) =
        client.get(fileUrl).request.url.toString()

}