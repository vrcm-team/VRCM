package io.github.vrcmteam.vrcm.presentation.screens.avatar

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.files.FileApi
import io.github.vrcmteam.vrcm.network.api.files.data.FileData
import io.github.vrcmteam.vrcm.network.api.files.data.FileStatus
import io.github.vrcmteam.vrcm.network.api.files.data.FileVersion
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

internal object AvatarGalleryLimits {
    const val MAX_FILE_BYTES: Long = 50L * 1024L * 1024L
    val ALLOWED_EXTENSIONS = listOf("jpg", "jpeg", "png", "webp", "heic", "heif")
}

data class AvatarGalleryTarget(
    val avatarId: String,
    val ownerUserId: String,
    val sessionToken: AccountSessionToken,
) {
    init {
        require(avatarId.isNotBlank()) { "Avatar Gallery target requires an avatar ID" }
        require(ownerUserId.isNotBlank()) { "Avatar Gallery target requires an owner ID" }
        require(ownerUserId == sessionToken.userId) {
            "Avatar Gallery target must belong to the active account"
        }
    }
}

data class AvatarGalleryUpdate(
    val avatarId: String,
    val files: List<FileData>,
    val sessionToken: AccountSessionToken,
)

internal data class AvatarGalleryPendingRefresh(
    val target: AvatarGalleryTarget,
    val uploadedFile: FileData,
    val sessionToken: AccountSessionToken,
)

internal sealed class AvatarGalleryUploadFailure(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Upload(cause: Throwable) :
        AvatarGalleryUploadFailure("Avatar Gallery upload failed", cause)

    class Refresh(
        val pending: AvatarGalleryPendingRefresh,
        cause: Throwable,
    ) : AvatarGalleryUploadFailure("Avatar Gallery refresh failed", cause)

    class SessionChanged :
        AvatarGalleryUploadFailure("Account session changed during Avatar Gallery upload")

    class Permission :
        AvatarGalleryUploadFailure("Avatar Gallery upload is not allowed for this avatar")
}

internal interface AvatarGalleryUploader {
    suspend fun uploadAndRefresh(
        target: AvatarGalleryTarget,
        imageBytes: ByteArray,
        fileName: String,
        mimeType: String,
        onUploadProgress: suspend (bytesSent: Long, totalBytes: Long?) -> Unit,
        onRefreshing: () -> Unit,
    ): Result<AvatarGalleryUpdate>

    suspend fun refresh(
        pending: AvatarGalleryPendingRefresh,
        onRefreshing: () -> Unit,
    ): Result<AvatarGalleryUpdate>
}

internal class NetworkAvatarGalleryUploader(
    private val fileApi: FileApi,
    private val authService: AuthService,
) : AvatarGalleryUploader {
    override suspend fun uploadAndRefresh(
        target: AvatarGalleryTarget,
        imageBytes: ByteArray,
        fileName: String,
        mimeType: String,
        onUploadProgress: suspend (bytesSent: Long, totalBytes: Long?) -> Unit,
        onRefreshing: () -> Unit,
    ): Result<AvatarGalleryUpdate> {
        if (!isCurrentOwner(target)) return Result.failure(AvatarGalleryUploadFailure.Permission())

        val uploaded = sessionBound(target.sessionToken) {
            fileApi.uploadAvatarGalleryImage(
                fileBytes = imageBytes,
                fileName = fileName,
                mimeType = mimeType,
                avatarId = target.avatarId,
                onProgress = onUploadProgress,
            ).getOrThrow()
        }.getOrElse { cause ->
            cause.rethrowIfCancellation()
            return Result.failure(
                if (cause is AvatarGalleryUploadFailure.SessionChanged) cause
                else AvatarGalleryUploadFailure.Upload(cause),
            )
        }

        if (uploaded.value.ownerId != target.ownerUserId) {
            return Result.failure(AvatarGalleryUploadFailure.Permission())
        }

        val pending = AvatarGalleryPendingRefresh(
            target = target,
            uploadedFile = uploaded.value,
            sessionToken = uploaded.sessionToken,
        )
        return refresh(pending, onRefreshing)
    }

    override suspend fun refresh(
        pending: AvatarGalleryPendingRefresh,
        onRefreshing: () -> Unit,
    ): Result<AvatarGalleryUpdate> {
        if (!isCurrentOwner(pending.target.copy(sessionToken = pending.sessionToken))) {
            return Result.failure(AvatarGalleryUploadFailure.SessionChanged())
        }
        onRefreshing()
        val refreshed = sessionBound(pending.sessionToken) {
            fileApi.getAvatarGalleryFiles(pending.target.avatarId)
        }
        return refreshed.fold(
            onSuccess = { response ->
                authoritativeAvatarGalleryUpdate(
                    pending = pending,
                    files = response.value,
                    sessionToken = response.sessionToken,
                )
            },
            onFailure = { cause ->
                cause.rethrowIfCancellation()
                Result.failure(
                    if (cause is AvatarGalleryUploadFailure.SessionChanged) cause
                    else AvatarGalleryUploadFailure.Refresh(pending, cause),
                )
            },
        )
    }

    private fun isCurrentOwner(target: AvatarGalleryTarget): Boolean =
        target.ownerUserId == target.sessionToken.userId &&
            io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre.isCurrentSession(
                target.sessionToken,
            )

    private suspend fun <T> sessionBound(
        token: AccountSessionToken,
        block: suspend () -> T,
    ): Result<SessionBoundValue<T>> {
        val response = authService.runSessionBoundCatching(token, block)
            ?: return Result.failure(AvatarGalleryUploadFailure.SessionChanged())
        return response.result.map { SessionBoundValue(it, response.sessionToken) }
    }

}

internal fun authoritativeAvatarGalleryUpdate(
    pending: AvatarGalleryPendingRefresh,
    files: List<FileData>,
    sessionToken: AccountSessionToken,
): Result<AvatarGalleryUpdate> {
    if (files.none { it.id == pending.uploadedFile.id }) {
        return Result.failure(
            AvatarGalleryUploadFailure.Refresh(
                pending,
                IllegalStateException(
                    "Uploaded Avatar Gallery file ${pending.uploadedFile.id} is not visible yet",
                ),
            )
        )
    }
    return Result.success(
        AvatarGalleryUpdate(
            avatarId = pending.target.avatarId,
            files = files,
            sessionToken = sessionToken,
        )
    )
}

private data class SessionBoundValue<T>(
    val value: T,
    val sessionToken: AccountSessionToken,
)

private fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) throw this
}

internal fun interface AvatarGalleryLoader {
    suspend fun load(avatarId: String, n: Int, offset: Int): Result<List<FileData>>
}

internal class NetworkAvatarGalleryLoader(
    private val fileApi: FileApi,
    private val authService: AuthService,
) : AvatarGalleryLoader {
    override suspend fun load(avatarId: String, n: Int, offset: Int): Result<List<FileData>> =
        authService.reTryAuthCatching {
            fileApi.getAvatarGalleryFiles(avatarId = avatarId, n = n, offset = offset)
        }
}

internal data class AvatarGalleryState(
    val avatarId: String = "",
    val files: List<FileData> = emptyList(),
    val isAvailable: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val initialLoadFailed: Boolean = false,
    val loadMoreFailed: Boolean = false,
)

internal class AvatarGalleryStateController(
    private val loader: AvatarGalleryLoader,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val session: StateFlow<AuthenticatedAccount?> = SharedFlowCentre.currentSession,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
) {
    private val mutableState = MutableStateFlow(AvatarGalleryState())
    val state: StateFlow<AvatarGalleryState> = mutableState.asStateFlow()

    private val latestRequestToken = MutableStateFlow(0L)
    private var nextOffset = 0
    private var observedSessionToken: AccountSessionToken? = session.value?.token

    init {
        require(pageSize in 1..100)
        scope.launch {
            session.collect { account ->
                val sessionToken = account?.token
                if (sessionToken == observedSessionToken) return@collect
                observedSessionToken = sessionToken
                latestRequestToken.updateAndGet { it + 1 }
                nextOffset = 0
                val avatarId = mutableState.value.avatarId
                mutableState.value = AvatarGalleryState(avatarId = avatarId)
                if (sessionToken != null && avatarId.isNotBlank()) {
                    requestPage(reset = true)
                }
            }
        }
    }

    fun showAvatar(avatarId: String) {
        if (avatarId == mutableState.value.avatarId) return
        latestRequestToken.updateAndGet { it + 1 }
        nextOffset = 0
        mutableState.value = AvatarGalleryState(avatarId = avatarId)
        if (avatarId.isNotBlank() && session.value != null) {
            requestPage(reset = true)
        }
    }

    fun loadMore() {
        val current = mutableState.value
        if (!current.hasMore || current.isLoading || current.isLoadingMore || current.loadMoreFailed) return
        requestPage(reset = false)
    }

    fun retry() {
        val current = mutableState.value
        when {
            current.initialLoadFailed -> requestPage(reset = true)
            current.loadMoreFailed -> requestPage(reset = false)
        }
    }

    private fun requestPage(reset: Boolean) {
        val avatarId = mutableState.value.avatarId.takeIf { it.isNotBlank() } ?: return
        val sessionToken = session.value?.token ?: return
        val offset = if (reset) 0 else nextOffset
        val requestToken = latestRequestToken.updateAndGet { it + 1 }

        mutableState.value = if (reset) {
            AvatarGalleryState(
                avatarId = avatarId,
                isAvailable = true,
                isLoading = true,
            )
        } else {
            mutableState.value.copy(
                isLoadingMore = true,
                loadMoreFailed = false,
            )
        }

        scope.launch(dispatcher) {
            loader.load(avatarId = avatarId, n = pageSize, offset = offset)
                .onSuccess { page ->
                    if (!isCurrent(requestToken, avatarId, sessionToken)) return@onSuccess
                    val currentFiles = if (reset) emptyList() else mutableState.value.files
                    nextOffset = offset + page.size
                    mutableState.value = mutableState.value.copy(
                        files = (currentFiles + page)
                            .distinctBy(FileData::id)
                            .sortedWith(compareBy { it.order ?: Int.MAX_VALUE }),
                        isLoading = false,
                        isLoadingMore = false,
                        hasMore = page.size == pageSize,
                        initialLoadFailed = false,
                        loadMoreFailed = false,
                    )
                }
                .onFailure {
                    if (!isCurrent(requestToken, avatarId, sessionToken)) return@onFailure
                    mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        hasMore = !reset && mutableState.value.hasMore,
                        initialLoadFailed = reset,
                        loadMoreFailed = !reset,
                    )
                }
        }
    }

    private fun isCurrent(
        requestToken: Long,
        avatarId: String,
        sessionToken: AccountSessionToken,
    ): Boolean = requestToken == latestRequestToken.value &&
        avatarId == mutableState.value.avatarId &&
        sessionToken == session.value?.token

    private companion object {
        const val DEFAULT_PAGE_SIZE = 24
    }
}

internal fun FileData.latestGalleryVersion(): FileVersion? = versions
    .asSequence()
    .filter { !it.deleted && it.status == FileStatus.Complete }
    .maxByOrNull(FileVersion::version)
