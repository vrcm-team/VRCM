package io.github.vrcmteam.vrcm.presentation.screens.gallery

import io.github.vrcmteam.vrcm.network.api.files.FileApi
import io.github.vrcmteam.vrcm.network.api.files.data.FileData
import io.github.vrcmteam.vrcm.network.api.files.data.FileTagType
import io.github.vrcmteam.vrcm.network.api.prints.PrintsApi
import io.github.vrcmteam.vrcm.network.api.prints.data.PrintData
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.coroutines.CancellationException

internal interface GalleryDataSource {
    suspend fun isCurrentUserSupporter(): Boolean

    suspend fun getFiles(tagType: FileTagType, n: Int, offset: Int): List<FileData>

    suspend fun getPrints(n: Int, offset: Int): List<PrintData>

    suspend fun uploadImage(
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String,
        tagType: FileTagType,
    ): Result<FileData>

    suspend fun deleteFile(id: String)

    suspend fun deletePrint(id: String)
}

internal class NetworkGalleryDataSource(
    private val authService: AuthService,
    private val fileApi: FileApi,
    private val printsApi: PrintsApi,
) : GalleryDataSource {
    override suspend fun isCurrentUserSupporter(): Boolean = authService.currentUser().isSupporter

    override suspend fun getFiles(tagType: FileTagType, n: Int, offset: Int): List<FileData> =
        authService.reTryAuth {
            runGalleryCatching { fileApi.getFiles(tagType, n = n, offset = offset) }
        }.getOrThrow()

    override suspend fun getPrints(n: Int, offset: Int): List<PrintData> {
        val userId = authService.currentUser().id
        return authService.reTryAuth {
            runGalleryCatching { printsApi.getUserPrints(userId, n = n, offset = offset) }
        }.getOrThrow()
    }

    override suspend fun uploadImage(
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String,
        tagType: FileTagType,
    ): Result<FileData> = runGalleryRequestWithAuthRetry(
        retryAuth = { request -> authService.reTryAuth(request) },
        request = { fileApi.uploadImageFile(fileBytes, fileName, mimeType, tagType) },
    )

    override suspend fun deleteFile(id: String) {
        runGalleryRequestWithAuthRetry(
            retryAuth = { request -> authService.reTryAuth(request) },
            request = { runGalleryCatching { fileApi.deleteFile(id) } },
        ).getOrThrow()
    }

    override suspend fun deletePrint(id: String) {
        runGalleryRequestWithAuthRetry(
            retryAuth = { request -> authService.reTryAuth(request) },
            request = { runGalleryCatching { printsApi.deletePrint(id) } },
        ).getOrThrow()
    }
}

internal suspend fun <T> runGalleryRequestWithAuthRetry(
    retryAuth: suspend (suspend () -> Result<T>) -> Result<T>,
    request: suspend () -> Result<T>,
): Result<T> {
    val result = runGalleryCatching { retryAuth(request) }
        .fold(onSuccess = { it }, onFailure = { Result.failure(it) })

    return when (val failure = result.exceptionOrNull()) {
        is CancellationException -> throw failure
        is Error -> throw failure
        else -> result
    }
}
