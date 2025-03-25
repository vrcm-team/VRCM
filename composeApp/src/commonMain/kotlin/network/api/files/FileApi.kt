package io.github.vrcmteam.vrcm.network.api.files

import io.github.vrcmteam.vrcm.network.api.attributes.FILES_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.files.data.FileData
import io.github.vrcmteam.vrcm.network.api.files.data.FileTagType
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
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
    
    /**
     * 获取文件列表
     * @param tag 标签类型，例如ICON、EMOJI、STICKER或GALLERY
     * @param userId 用户ID
     * @param n 每页数量，最大100
     * @param offset 偏移量
     */
    suspend fun getFiles(
        tag: FileTagType? = null,
        userId: String? = null,
        n: Int = 60,
        offset: Int = 0
    ) = client.get(FILES_API_PREFIX) {
        tag?.let { parameter("tag", it.toString()) }
        userId?.let { parameter("userId", it) }
        parameter("n", n.coerceIn(1, 100))
        parameter("offset", offset.coerceAtLeast(0))
    }.checkSuccess<List<FileData>>()

}