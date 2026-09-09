package io.github.vrcmteam.vrcm.presentation.screens.avatar

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.network.api.files.data.FileData
import io.github.vrcmteam.vrcm.network.api.files.data.FileStatus
import io.github.vrcmteam.vrcm.network.api.files.data.FileVersion
import io.github.vrcmteam.vrcm.service.data.AccountDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AvatarGalleryStateControllerTest {
    private val scopes = mutableListOf<CoroutineScope>()

    @AfterTest
    fun cancelScopes() {
        scopes.forEach { it.cancel() }
    }

    @Test
    fun pagesAppendDistinctFilesInServerOrderAndAdvanceByPageSize() = runBlocking {
        val session = MutableStateFlow<AuthenticatedAccount?>(account("usr_a", 1))
        val loader = ControlledAvatarGalleryLoader()
        val controller = controller(loader, session, pageSize = 2)

        controller.showAvatar("avtr_a")
        loader.complete("avtr_a", 0, listOf(file("file_2", order = 2), file("file_1", order = 1)))
        assertEquals(listOf("file_1", "file_2"), controller.state.value.files.map(FileData::id))
        assertTrue(controller.state.value.hasMore)

        controller.loadMore()
        controller.loadMore()
        assertEquals(1, loader.requests.count { it.avatarId == "avtr_a" && it.offset == 2 })
        loader.complete("avtr_a", 2, listOf(file("file_2", order = 2), file("file_3", order = 3)))
        assertEquals(listOf("file_1", "file_2", "file_3"), controller.state.value.files.map(FileData::id))

        controller.loadMore()
        loader.complete("avtr_a", 4, emptyList())
        assertFalse(controller.state.value.hasMore)
    }

    @Test
    fun targetAndAccountChangesIgnoreLateResponsesAndLogoutClearsGallery() = runBlocking {
        val session = MutableStateFlow<AuthenticatedAccount?>(account("usr_a", 1))
        val loader = ControlledAvatarGalleryLoader()
        val controller = controller(loader, session)

        controller.showAvatar("avtr_old")
        controller.showAvatar("avtr_new")
        loader.complete("avtr_new", 0, listOf(file("file_new")))
        loader.complete("avtr_old", 0, listOf(file("file_old")))
        assertEquals("avtr_new", controller.state.value.avatarId)
        assertEquals(listOf("file_new"), controller.state.value.files.map(FileData::id))

        session.value = account("usr_b", 2)
        yield()
        assertTrue(loader.requests.any { it.avatarId == "avtr_new" && it.offset == 0 && it.requestIndex > 1 })
        loader.complete("avtr_new", 0, listOf(file("file_account_b")))
        assertEquals(listOf("file_account_b"), controller.state.value.files.map(FileData::id))

        session.value = null
        yield()
        assertTrue(controller.state.value.files.isEmpty())
        assertFalse(controller.state.value.isAvailable)
        loader.completeAllPending(listOf(file("file_late")))
        assertTrue(controller.state.value.files.isEmpty())
    }

    @Test
    fun initialAndLoadMoreFailuresExposeRetryableState() = runBlocking {
        val session = MutableStateFlow<AuthenticatedAccount?>(account("usr_a", 1))
        val loader = ControlledAvatarGalleryLoader()
        val controller = controller(loader, session, pageSize = 2)

        controller.showAvatar("avtr_a")
        loader.fail("avtr_a", 0)
        assertTrue(controller.state.value.initialLoadFailed)
        controller.retry()
        loader.complete("avtr_a", 0, listOf(file("file_1"), file("file_2")))
        controller.loadMore()
        loader.fail("avtr_a", 2)
        assertTrue(controller.state.value.loadMoreFailed)
        controller.retry()
        assertEquals(2, loader.requests.count { it.avatarId == "avtr_a" && it.offset == 2 })
    }

    private fun controller(
        loader: ControlledAvatarGalleryLoader,
        session: MutableStateFlow<AuthenticatedAccount?>,
        pageSize: Int = 24,
    ) = AvatarGalleryStateController(
        loader = loader,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also(scopes::add),
        dispatcher = Dispatchers.Unconfined,
        session = session,
        pageSize = pageSize,
    )

    private fun account(userId: String, generation: Long) = AuthenticatedAccount(
        account = AccountDto(userId = userId),
        token = AccountSessionToken(userId = userId, generation = generation),
    )
}

private data class GalleryRequest(
    val avatarId: String,
    val offset: Int,
    val requestIndex: Int,
    val result: CompletableDeferred<Result<List<FileData>>>,
)

private class ControlledAvatarGalleryLoader : AvatarGalleryLoader {
    val requests = mutableListOf<GalleryRequest>()

    override suspend fun load(avatarId: String, n: Int, offset: Int): Result<List<FileData>> {
        val request = GalleryRequest(
            avatarId = avatarId,
            offset = offset,
            requestIndex = requests.size,
            result = CompletableDeferred(),
        )
        requests += request
        return request.result.await()
    }

    fun complete(avatarId: String, offset: Int, files: List<FileData>) {
        requests.last { it.avatarId == avatarId && it.offset == offset && !it.result.isCompleted }
            .result.complete(Result.success(files))
    }

    fun fail(avatarId: String, offset: Int) {
        requests.last { it.avatarId == avatarId && it.offset == offset && !it.result.isCompleted }
            .result.complete(Result.failure(IllegalStateException("offline")))
    }

    fun completeAllPending(files: List<FileData>) {
        requests.filter { !it.result.isCompleted }.forEach { it.result.complete(Result.success(files)) }
    }
}

private fun file(id: String, order: Int? = null) = FileData(
    id = id,
    name = "$id.png",
    ownerId = "usr_owner",
    mimeType = "image/png",
    extension = ".png",
    animationStyle = null,
    tags = listOf("avatargallery"),
    versions = listOf(
        FileVersion(
            version = 1,
            status = FileStatus.Complete,
            createdAt = "2026-01-01T00:00:00Z",
        )
    ),
    order = order,
)
