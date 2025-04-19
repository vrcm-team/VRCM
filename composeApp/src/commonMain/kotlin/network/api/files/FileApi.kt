package io.github.vrcmteam.vrcm.network.api.files

import io.github.vrcmteam.vrcm.network.api.attributes.FILES_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.attributes.FILE_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.files.data.FileData
import io.github.vrcmteam.vrcm.network.api.files.data.FileDetailsData
import io.github.vrcmteam.vrcm.network.api.files.data.FileResponse
import io.github.vrcmteam.vrcm.network.api.files.data.FileTagType
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.github.vrcmteam.vrcm.network.extensions.checkSuccessResult
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*


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
     * @param tag 标签，例如"icon"、"emoji"、"sticker"或"gallery"
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
        tag?.let { parameter("tag", it.value) }
        userId?.let { parameter("userId", it) }
        parameter("n", n.coerceIn(1, 100))
        parameter("offset", offset.coerceAtLeast(0))
    }.checkSuccess<List<FileData>>()

    /**
     * 上传文件
     * @param fileBytes 文件字节数组
     * @param fileName 文件名
     * @param mimeType 文件MIME类型
     * @param tagType 文件标签类型
     * @return 上传结果
     */
    suspend fun uploadFile(
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String,
        tagType: FileTagType
    ): Result<FileDetailsData> {
        return try {
            val response = client.submitFormWithBinaryData(
                url = "$FILES_API_PREFIX/create",
                formData = formData {
                    append("file", fileBytes, Headers.build {
                        append(HttpHeaders.ContentType, mimeType)
                        append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                    })
                    append("tag", tagType.value)
                }
            )
            val result = response.checkSuccess<FileDetailsData>()
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 上传图片文件
     * @param fileBytes 图片文件字节数组
     * @param fileName 文件名
     * @param mimeType 文件MIME类型
     * @return 上传结果
     */
    suspend fun uploadImageFile(
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String,
        tagType: FileTagType
    ): Result<FileData> {
        return client.submitFormWithBinaryData(
            url = "${FILE_API_PREFIX}/image",
            formData = formData {
                append("file", fileBytes, Headers.build {
                    append(HttpHeaders.ContentType, mimeType)
                    append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                })
                append("tag", tagType.value)
            }
        ).checkSuccessResult<FileData>()
    }

    /**
     * 获取特定文件ID的文件信息
     * @param fileId 文件ID
     * @return 文件信息
     */
    suspend fun getFileInfo(fileId: String): Result<FileResponse> = runCatching {
        client.get("$FILES_API_PREFIX/$fileId").checkSuccess<FileResponse>()
    }

}
