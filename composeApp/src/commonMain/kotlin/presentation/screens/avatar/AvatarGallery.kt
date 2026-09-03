package io.github.vrcmteam.vrcm.presentation.screens.avatar

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.files.FileApi
import io.github.vrcmteam.vrcm.network.api.files.data.FileData
import io.github.vrcmteam.vrcm.network.api.files.data.FileStatus
import io.github.vrcmteam.vrcm.network.api.files.data.FileVersion
import io.github.vrcmteam.vrcm.service.AuthService
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
