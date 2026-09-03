package io.github.vrcmteam.vrcm.presentation.screens.world

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.service.SessionBoundResponse
import io.github.vrcmteam.vrcm.service.data.AccountDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WorldPublicationStateModelTest {
    @Test
    fun actionsRequireTheCurrentOwnerAndSupportedReleaseStatus() {
        assertNull(worldPublicationAction("usr_author", "public", "usr_other"))
        assertNull(worldPublicationAction("usr_author", "hidden", "usr_author"))
        assertNull(worldPublicationAction("usr_author", "all", "usr_author"))
        assertEquals(
            WorldPublicationAction.Publish,
            worldPublicationAction("usr_author", "private", "usr_author"),
        )
        assertEquals(
            WorldPublicationAction.Unpublish,
            worldPublicationAction("usr_author", "public", "usr_author"),
        )
    }

    @Test
    fun unavailablePublishingKeepsTheVerifiedPublishActionVisibleButDisabled() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val token = AccountSessionToken("usr_author", 1)
        val session = MutableStateFlow(authenticated(token))
        val source = FakeWorldPublicationSource(
            worlds = mutableMapOf("wrld_owned" to world(releaseStatus = "private")),
            canPublish = false,
        )
        val model = WorldPublicationStateModel(source, backgroundScope, dispatcher, session)

        model.setTarget("wrld_owned", "usr_author")
        model.acceptVerifiedWorld(source.worlds.getValue("wrld_owned"), token)
        runCurrent()

        assertEquals(WorldPublicationAction.Publish, model.state.value.action)
        assertFalse(model.state.value.canExecute)
        assertEquals(WorldPublicationBlockReason.Unavailable, model.state.value.blockReason)
    }

    @Test
    fun failedPublishRestoresTheActionAndRetryUsesTheRefreshedStatus() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val token = AccountSessionToken("usr_author", 1)
        val session = MutableStateFlow(authenticated(token))
        val source = FakeWorldPublicationSource(
            worlds = mutableMapOf("wrld_owned" to world(releaseStatus = "private")),
            publicationResults = mutableListOf(
                Result.failure(IllegalStateException("offline")),
                Result.success(Unit),
            ),
        )
        val refreshedWorlds = mutableListOf<WorldData>()
        val model = WorldPublicationStateModel(
            source = source,
            scope = backgroundScope,
            requestDispatcher = dispatcher,
            sessionFlow = session,
            onWorldRefreshed = { world ->
                refreshedWorlds += world
                Result.success(Unit)
            },
        )
        val notices = mutableListOf<WorldPublicationNotice>()
        val noticeJob = launch(start = CoroutineStart.UNDISPATCHED) {
            model.notices.collect(notices::add)
        }

        model.setTarget("wrld_owned", "usr_author")
        model.acceptVerifiedWorld(source.worlds.getValue("wrld_owned"), token)
        runCurrent()
        assertTrue(model.state.value.canExecute)

        model.changePublication(WorldPublicationAction.Publish)
        model.changePublication(WorldPublicationAction.Publish)
        runCurrent()

        assertEquals(1, source.publicationCalls.size)
        assertEquals(WorldPublicationAction.Publish, model.state.value.action)
        assertTrue(model.state.value.canExecute)
        assertEquals(
            listOf<WorldPublicationNotice>(
                WorldPublicationNotice.ChangeFailed(
                    action = WorldPublicationAction.Publish,
                    message = "offline",
                )
            ),
            notices,
        )

        model.changePublication(WorldPublicationAction.Publish)
        runCurrent()

        assertEquals(2, source.publicationCalls.size)
        assertEquals("public", refreshedWorlds.single().releaseStatus)
        assertEquals(WorldPublicationAction.Unpublish, model.state.value.action)
        assertTrue(model.state.value.canExecute)
        assertEquals(
            WorldPublicationNotice.Changed(WorldPublicationAction.Publish),
            notices.last(),
        )
        noticeJob.cancel()
    }

    @Test
    fun completedMutationWithUnconfirmedStatusRequiresARefreshBeforeRetry() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val token = AccountSessionToken("usr_author", 1)
        val session = MutableStateFlow(authenticated(token))
        val source = FakeWorldPublicationSource(
            worlds = mutableMapOf("wrld_owned" to world(releaseStatus = "private")),
            applyPublicationChange = false,
        )
        val model = WorldPublicationStateModel(source, backgroundScope, dispatcher, session)
        val notices = mutableListOf<WorldPublicationNotice>()
        val noticeJob = launch(start = CoroutineStart.UNDISPATCHED) {
            model.notices.collect(notices::add)
        }

        model.setTarget("wrld_owned", "usr_author")
        model.acceptVerifiedWorld(source.worlds.getValue("wrld_owned"), token)
        runCurrent()
        model.changePublication(WorldPublicationAction.Publish)
        runCurrent()

        assertEquals(WorldPublicationAction.Publish, model.state.value.action)
        assertFalse(model.state.value.canExecute)
        assertEquals(
            WorldPublicationBlockReason.RefreshRequired,
            model.state.value.blockReason,
        )
        assertEquals(
            listOf<WorldPublicationNotice>(WorldPublicationNotice.RefreshFailed(null)),
            notices,
        )

        model.acceptVerifiedWorld(source.worlds.getValue("wrld_owned"), token)
        runCurrent()

        assertTrue(model.state.value.canExecute)
        assertNull(model.state.value.blockReason)
        noticeJob.cancel()
    }

    @Test
    fun cacheSyncFailureBlocksSuccessUntilTheWorldCanBeRefreshed() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val token = AccountSessionToken("usr_author", 1)
        val session = MutableStateFlow(authenticated(token))
        val source = FakeWorldPublicationSource(
            worlds = mutableMapOf("wrld_owned" to world(releaseStatus = "private")),
        )
        val model = WorldPublicationStateModel(
            source = source,
            scope = backgroundScope,
            requestDispatcher = dispatcher,
            sessionFlow = session,
            onWorldRefreshed = {
                Result.failure(IllegalStateException("cache unavailable"))
            },
        )
        val notices = mutableListOf<WorldPublicationNotice>()
        val noticeJob = launch(start = CoroutineStart.UNDISPATCHED) {
            model.notices.collect(notices::add)
        }

        model.setTarget("wrld_owned", "usr_author")
        model.acceptVerifiedWorld(source.worlds.getValue("wrld_owned"), token)
        runCurrent()
        model.changePublication(WorldPublicationAction.Publish)
        runCurrent()

        assertEquals(WorldPublicationBlockReason.RefreshRequired, model.state.value.blockReason)
        assertFalse(model.state.value.canExecute)
        assertEquals(
            listOf<WorldPublicationNotice>(WorldPublicationNotice.CacheSyncFailed("cache unavailable")),
            notices,
        )
        noticeJob.cancel()
    }

    @Test
    fun accountSwitchRejectsACompletedMutationFromThePreviousSession() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val tokenA = AccountSessionToken("usr_author", 1)
        val tokenB = AccountSessionToken("usr_other", 2)
        val session = MutableStateFlow(authenticated(tokenA))
        val pendingMutation = CompletableDeferred<Result<Unit>>()
        val source = FakeWorldPublicationSource(
            worlds = mutableMapOf("wrld_owned" to world(releaseStatus = "private")),
            pendingPublication = pendingMutation,
        )
        val refreshedWorlds = mutableListOf<WorldData>()
        val model = WorldPublicationStateModel(
            source = source,
            scope = backgroundScope,
            requestDispatcher = dispatcher,
            sessionFlow = session,
            onWorldRefreshed = { world ->
                refreshedWorlds += world
                Result.success(Unit)
            },
        )

        model.setTarget("wrld_owned", "usr_author")
        model.acceptVerifiedWorld(source.worlds.getValue("wrld_owned"), tokenA)
        runCurrent()
        model.changePublication(WorldPublicationAction.Publish)
        runCurrent()
        assertTrue(model.state.value.isChanging)

        session.value = authenticated(tokenB)
        runCurrent()
        pendingMutation.complete(Result.success(Unit))
        runCurrent()

        assertNull(model.state.value.action)
        assertTrue(refreshedWorlds.isEmpty())
    }

    @Test
    fun targetSwitchRejectsACompletedMutationForThePreviousWorld() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val token = AccountSessionToken("usr_author", 1)
        val session = MutableStateFlow(authenticated(token))
        val pendingMutation = CompletableDeferred<Result<Unit>>()
        val source = FakeWorldPublicationSource(
            worlds = mutableMapOf(
                "wrld_old" to world(id = "wrld_old", releaseStatus = "private"),
                "wrld_new" to world(id = "wrld_new", releaseStatus = "public"),
            ),
            pendingPublication = pendingMutation,
        )
        val refreshedWorlds = mutableListOf<WorldData>()
        val model = WorldPublicationStateModel(
            source = source,
            scope = backgroundScope,
            requestDispatcher = dispatcher,
            sessionFlow = session,
            onWorldRefreshed = { world ->
                refreshedWorlds += world
                Result.success(Unit)
            },
        )

        model.setTarget("wrld_old", "usr_author")
        model.acceptVerifiedWorld(source.worlds.getValue("wrld_old"), token)
        runCurrent()
        model.changePublication(WorldPublicationAction.Publish)
        runCurrent()
        assertTrue(model.state.value.isChanging)

        model.setTarget("wrld_new", "usr_author")
        pendingMutation.complete(Result.success(Unit))
        runCurrent()

        assertNull(model.state.value.action)
        assertTrue(refreshedWorlds.isEmpty())

        model.acceptVerifiedWorld(source.worlds.getValue("wrld_new"), token)
        assertEquals(WorldPublicationAction.Unpublish, model.state.value.action)
    }

    @Test
    fun accountSwitchDuringCacheSyncDoesNotPublishStaleNotice() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val tokenA = AccountSessionToken("usr_author", 1)
        val tokenB = AccountSessionToken("usr_other", 2)
        val session = MutableStateFlow(authenticated(tokenA))
        val cacheStarted = CompletableDeferred<Unit>()
        val releaseCache = CompletableDeferred<Unit>()
        val source = FakeWorldPublicationSource(
            worlds = mutableMapOf("wrld_owned" to world(releaseStatus = "private")),
        )
        val model = WorldPublicationStateModel(
            source = source,
            scope = backgroundScope,
            requestDispatcher = dispatcher,
            sessionFlow = session,
            onWorldRefreshed = {
                cacheStarted.complete(Unit)
                withContext(NonCancellable) { releaseCache.await() }
                Result.success(Unit)
            },
        )
        val notices = mutableListOf<WorldPublicationNotice>()
        val noticeJob = launch(start = CoroutineStart.UNDISPATCHED) {
            model.notices.collect(notices::add)
        }

        model.setTarget("wrld_owned", "usr_author")
        model.acceptVerifiedWorld(source.worlds.getValue("wrld_owned"), tokenA)
        runCurrent()
        model.changePublication(WorldPublicationAction.Publish)
        runCurrent()
        cacheStarted.await()

        session.value = authenticated(tokenB)
        runCurrent()
        releaseCache.complete(Unit)
        runCurrent()

        assertTrue(notices.isEmpty())
        assertNull(model.state.value.action)
        noticeJob.cancel()
    }
}

private class FakeWorldPublicationSource(
    val worlds: MutableMap<String, WorldData>,
    private val canPublish: Boolean = true,
    private val publicationResults: MutableList<Result<Unit>> = mutableListOf(),
    private val pendingPublication: CompletableDeferred<Result<Unit>>? = null,
    private val applyPublicationChange: Boolean = true,
) : WorldPublicationSource {
    val publicationCalls = mutableListOf<Pair<String, WorldPublicationAction>>()

    override suspend fun loadWorld(
        sessionToken: AccountSessionToken,
        worldId: String,
    ): SessionBoundResponse<WorldData> {
        val world = worlds[worldId]
        return SessionBoundResponse(
            result = if (world == null) {
                Result.failure(IllegalStateException("missing world"))
            } else {
                Result.success(world)
            },
            sessionToken = sessionToken,
        )
    }

    override suspend fun canPublish(
        sessionToken: AccountSessionToken,
        worldId: String,
    ): SessionBoundResponse<Boolean> = SessionBoundResponse(
        result = Result.success(canPublish),
        sessionToken = sessionToken,
    )

    override suspend fun changePublication(
        sessionToken: AccountSessionToken,
        worldId: String,
        action: WorldPublicationAction,
    ): SessionBoundResponse<Unit> {
        publicationCalls += worldId to action
        val result = pendingPublication?.await()
            ?: publicationResults.removeFirstOrNull()
            ?: Result.success(Unit)
        if (result.isSuccess && applyPublicationChange) {
            worlds[worldId] = worlds.getValue(worldId).copy(
                releaseStatus = when (action) {
                    WorldPublicationAction.Publish -> "public"
                    WorldPublicationAction.Unpublish -> "private"
                }
            )
        }
        return SessionBoundResponse(result, sessionToken)
    }
}

private fun authenticated(token: AccountSessionToken) = AuthenticatedAccount(
    account = AccountDto(userId = token.userId),
    token = token,
)

private fun world(
    id: String = "wrld_owned",
    authorId: String = "usr_author",
    releaseStatus: String,
) = WorldData(
    authorId = authorId,
    authorName = "Author",
    capacity = 16,
    createdAt = "2026-01-01T00:00:00Z",
    description = "Description",
    favorites = 0,
    featured = false,
    heat = 0,
    id = id,
    imageUrl = "https://example.test/world.png",
    labsPublicationDate = "none",
    name = "World",
    namespace = null,
    organization = "vrchat",
    popularity = 0,
    publicationDate = "none",
    recommendedCapacity = 8,
    releaseStatus = releaseStatus,
    tags = emptyList(),
    thumbnailImageUrl = "https://example.test/world-thumbnail.png",
    udonProducts = emptyList(),
    unityPackages = emptyList(),
    updatedAt = "2026-01-01T00:00:00Z",
    version = 1,
    visits = 0,
)
