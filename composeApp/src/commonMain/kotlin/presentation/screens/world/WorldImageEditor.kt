package io.github.vrcmteam.vrcm.presentation.screens.world

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.network.api.files.FileApi
import io.github.vrcmteam.vrcm.network.api.files.data.FileTagType
import io.github.vrcmteam.vrcm.network.api.worlds.WorldsApi
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldUpdateData
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.coroutines.CancellationException

internal object WorldImageLimits {
    const val MAX_FILE_BYTES: Long = 10L * 1024L * 1024L
    val ALLOWED_EXTENSIONS = listOf("jpg", "jpeg", "png", "webp")
}

internal data class WorldImageFile(
    val bytes: ByteArray,
    val fileName: String,
    val mimeType: String,
)

internal sealed interface WorldImageValidation {
    data class Valid(val image: WorldImageFile) : WorldImageValidation
    data object FileTooLarge : WorldImageValidation
    data object UnsupportedFormat : WorldImageValidation
}

private enum class WorldImageFormat(
    val extensions: Set<String>,
    val mimeType: String,
) {
    Jpeg(setOf("jpg", "jpeg"), "image/jpeg"),
    Png(setOf("png"), "image/png"),
    WebP(setOf("webp"), "image/webp"),
}

internal fun validateWorldImage(
    fileName: String,
    bytes: ByteArray,
): WorldImageValidation {
    if (bytes.size.toLong() > WorldImageLimits.MAX_FILE_BYTES) {
        return WorldImageValidation.FileTooLarge
    }

    val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    val declaredFormat = WorldImageFormat.entries.firstOrNull { extension in it.extensions }
        ?: return WorldImageValidation.UnsupportedFormat
    val detectedFormat = when {
        bytes.hasPrefix(0xFF, 0xD8, 0xFF) -> WorldImageFormat.Jpeg
        bytes.hasPrefix(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) ->
            WorldImageFormat.Png
        bytes.hasAsciiAt(0, "RIFF") && bytes.hasAsciiAt(8, "WEBP") -> WorldImageFormat.WebP
        else -> null
    }
    if (detectedFormat != declaredFormat) return WorldImageValidation.UnsupportedFormat

    return WorldImageValidation.Valid(
        WorldImageFile(
            bytes = bytes,
            fileName = fileName,
            mimeType = detectedFormat.mimeType,
        )
    )
}

internal data class SessionBoundValue<T>(
    val value: T,
    val sessionToken: AccountSessionToken,
)

/** Server-authoritative world detail returned after an image update. */
data class WorldImageUpdate(
    val world: WorldData,
    val sessionToken: AccountSessionToken,
)

internal class WorldImageSessionChanged : Exception("Account session changed")

internal sealed class WorldImageUpdateFailure(
    message: String,
    cause: Throwable,
) : Exception(message, cause) {
    class Upload(cause: Throwable) :
        WorldImageUpdateFailure("World image upload failed", cause)

    class Assignment(cause: Throwable) :
        WorldImageUpdateFailure("World image assignment failed", cause)

    class Refresh(cause: Throwable) :
        WorldImageUpdateFailure("World refresh after image update failed", cause)
}

internal interface WorldImageEditor {
    suspend fun uploadImage(
        sessionToken: AccountSessionToken,
        image: WorldImageFile,
    ): Result<SessionBoundValue<String>>

    suspend fun assignImage(
        sessionToken: AccountSessionToken,
        worldId: String,
        imageUrl: String,
    ): Result<SessionBoundValue<Unit>>

    suspend fun refreshWorld(
        sessionToken: AccountSessionToken,
        worldId: String,
    ): Result<WorldImageUpdate>

    suspend fun updateImage(
        sessionToken: AccountSessionToken,
        worldId: String,
        image: WorldImageFile,
    ): Result<WorldImageUpdate> {
        val upload = uploadImage(sessionToken, image).getOrElse { cause ->
            cause.rethrowIfCancellation()
            return Result.failure(WorldImageUpdateFailure.Upload(cause))
        }
        val assignment = assignImage(upload.sessionToken, worldId, upload.value)
            .getOrElse { cause ->
                cause.rethrowIfCancellation()
                return Result.failure(WorldImageUpdateFailure.Assignment(cause))
            }
        return refreshWorld(assignment.sessionToken, worldId).fold(
            onSuccess = Result.Companion::success,
            onFailure = { cause ->
                cause.rethrowIfCancellation()
                Result.failure(WorldImageUpdateFailure.Refresh(cause))
            },
        )
    }
}

internal class NetworkWorldImageEditor(
    private val worldsApi: WorldsApi,
    private val fileApi: FileApi,
    private val authService: AuthService,
) : WorldImageEditor {
    override suspend fun uploadImage(
        sessionToken: AccountSessionToken,
        image: WorldImageFile,
    ): Result<SessionBoundValue<String>> = sessionBound(sessionToken) {
        val uploaded = fileApi.uploadImageFile(
            fileBytes = image.bytes,
            fileName = image.fileName,
            mimeType = image.mimeType,
            tagType = FileTagType.WorldImage,
        ).getOrThrow()
        val version = uploaded.versions.maxOfOrNull { it.version }
            ?: error("Uploaded world image has no file version")
        FileApi.originalFileUrl(uploaded.id, version)
    }

    override suspend fun assignImage(
        sessionToken: AccountSessionToken,
        worldId: String,
        imageUrl: String,
    ): Result<SessionBoundValue<Unit>> = sessionBound(sessionToken) {
        worldsApi.updateWorld(worldId, WorldUpdateData(imageUrl = imageUrl))
        Unit
    }

    override suspend fun refreshWorld(
        sessionToken: AccountSessionToken,
        worldId: String,
    ): Result<WorldImageUpdate> = sessionBound(sessionToken) {
        worldsApi.getWorldById(worldId)
    }.map { response ->
        WorldImageUpdate(response.value, response.sessionToken)
    }

    private suspend fun <T> sessionBound(
        sessionToken: AccountSessionToken,
        block: suspend () -> T,
    ): Result<SessionBoundValue<T>> {
        val response = authService.runSessionBoundCatching(sessionToken, block)
            ?: return Result.failure(WorldImageSessionChanged())
        return response.result.map { value ->
            SessionBoundValue(value, response.sessionToken)
        }
    }
}

private fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) throw this
}

private fun ByteArray.hasPrefix(vararg expected: Int): Boolean =
    size >= expected.size && expected.indices.all { index ->
        this[index].toInt() and 0xFF == expected[index]
    }

private fun ByteArray.hasAsciiAt(offset: Int, expected: String): Boolean =
    size >= offset + expected.length && expected.indices.all { index ->
        this[offset + index].toInt() and 0xFF == expected[index].code
    }
