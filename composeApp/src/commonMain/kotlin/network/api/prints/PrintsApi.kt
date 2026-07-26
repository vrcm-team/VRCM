package io.github.vrcmteam.vrcm.network.api.prints

import io.github.vrcmteam.vrcm.network.api.attributes.PRINTS_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.prints.data.PrintData
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class PrintsApi(private val client: HttpClient) {

    suspend fun getUserPrints(
        userId: String,
        n: Int = 100,
        offset: Int = 0,
    ): List<PrintData> =
        client.get("$PRINTS_API_PREFIX/user/$userId") {
            parameter("n", n.coerceIn(1, 100))
            parameter("offset", offset.coerceAtLeast(0))
        }.checkSuccess()

    suspend fun getPrint(printId: String): PrintData =
        client.get("$PRINTS_API_PREFIX/$printId").checkSuccess()

    suspend fun uploadPrint(
        imageBytes: ByteArray,
        fileName: String,
        note: String = "",
        worldId: String? = null,
        worldName: String? = null,
    ): PrintData {
        require(fileName.endsWith(".png", ignoreCase = true) && imageBytes.hasPngSignature()) {
            "Print image must be a PNG file"
        }
        return client.submitFormWithBinaryData(
            url = PRINTS_API_PREFIX,
            formData = formData {
                append("image", imageBytes, Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Image.PNG.toString())
                    append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                })
                append("note", note)
                append("timestamp", Clock.System.now().toString())
                worldId?.let { append("worldId", it) }
                worldName?.let { append("worldName", it) }
            }
        ).checkSuccess()
    }

    /**
     * 删除指定拍立得
     * @param printId 拍立得ID
     */
    suspend fun deletePrint(printId: String) {
        client.delete("$PRINTS_API_PREFIX/$printId").checkSuccess<Unit>()
    }

    private fun ByteArray.hasPngSignature(): Boolean = size >= PNG_SIGNATURE.size &&
            PNG_SIGNATURE.indices.all { index -> this[index] == PNG_SIGNATURE[index] }

    private companion object {
        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
    }
}
