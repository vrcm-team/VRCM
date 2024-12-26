package io.github.vrcmteam.vrcm.network.api.files

import io.ktor.client.*
import io.ktor.client.request.*



class FileApi(private val client: HttpClient) {

    companion object {
        private val regex = Regex("file_[\\w-]+")
        private val versionRegex = Regex("/[0-9]+/")
        fun findFileId(fileUrl: String): String {
            val match = regex.find(fileUrl)
            return match?.groupValues?.get(0).orEmpty()
        }

        fun findFileVersion(fileUrl: String): String {
            val versionMatch = versionRegex.find(fileUrl, 30)
            return versionMatch?.groupValues?.last()?.replace("/", "") ?: "1"
        }
        fun convertFileUrl(fileUrl: String, fileSize: Int = 1024): String {
            if (fileUrl.isEmpty()) return ""
            val fileId = findFileId(fileUrl)
            val fileVersion = findFileVersion(fileUrl)
            return "https://api.vrchat.cloud/api/1/image/$fileId/$fileVersion/$fileSize"
        }
    }

    suspend fun findImageFileLocal(fileUrl: String, fileSize: Int = 128): String {
        return runCatching {
            val response = client.get {
                val findFileId = findFileId(fileUrl)
                val findFileVersion = findFileVersion(fileUrl)
                url("https://api.vrchat.cloud/api/1/image/$findFileId/$findFileVersion/$fileSize")
            }
            return response.headers["Location"] ?: fileUrl
        } .getOrDefault(fileUrl)
    }

}