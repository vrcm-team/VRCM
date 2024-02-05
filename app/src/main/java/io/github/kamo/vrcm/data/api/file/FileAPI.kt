package io.github.kamo.vrcm.data.api.file

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

internal const val FILE_API_SUFFIX = "file"
class FileAPI(private val client: HttpClient) {

    companion object{
        fun findFileId(fileUrl:String):String{
            val regex = Regex("file_[\\w-]+")
            val versionRegex = Regex("/[0-9]+/")
            val match = regex.find(fileUrl)
            return match?.groupValues?.get(0) ?: ""
        }
        fun findFileVersion(fileUrl:String):String{
            val versionRegex = Regex("/[0-9]+/")
            val versionMatch = versionRegex.find(fileUrl)
            return versionMatch?.groupValues?.get(0)?.replace("/", "") ?: "1"
        }
    }

   suspend fun findImageFileLocal(fileUrl:String,fileSize: Int = 128) =
        client.get{
            val findFileId = findFileId(fileUrl)
            val findFileVersion = findFileVersion(fileUrl)
            url("https://api.vrchat.cloud/api/1/image/$findFileId/$findFileVersion/$fileSize")
        }.request.url.toString()

}