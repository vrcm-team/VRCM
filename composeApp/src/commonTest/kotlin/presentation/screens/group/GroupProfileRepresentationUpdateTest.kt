package io.github.vrcmteam.vrcm.presentation.screens.group

import androidx.lifecycle.ViewModelStore
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.groups.GroupsApi
import io.github.vrcmteam.vrcm.network.api.groups.data.GroupData
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.presentation.screens.group.data.GroupProfileVo
import io.github.vrcmteam.vrcm.service.data.AccountDto
import io.github.vrcmteam.vrcm.storage.GroupProfileCacheStore
import io.github.vrcmteam.vrcm.storage.data.GroupProfileCache
import io.github.vrcmteam.vrcm.testing.MainDispatcherTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import org.koin.core.logger.EmptyLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupProfileRepresentationUpdateTest : MainDispatcherTest() {
    @Test
    fun cacheFailureDoesNotReverseAnAcceptedUpdate() = runBlocking {
        val account = AccountDto(REPRESENTATION_USER_ID, username = "cache-failure")
        SharedFlowCentre.emitAuthenticated(account)
        val cache = BlockingGroupProfileCacheStore(
            saveFailure = IllegalStateException("cache unavailable")
        )
        val fixture = createFixture(cache) { request ->
            when (request.method) {
                HttpMethod.Put -> respondJson(
                    """{"success":{"message":"updated","status_code":200}}"""
                )

                HttpMethod.Get -> respondJson(representationGroupJson(isRepresenting = true))
                else -> error("Unexpected request: ${request.method} ${request.url}")
            }
        }
        try {
            fixture.model.loadGroupData(representationProfile(isRepresenting = false))
            cache.loadStarted.await()

            fixture.model.updateRepresentation(
                isRepresenting = true,
                failureMessage = "update failed",
                sessionChangedMessage = "session changed",
            )
            cache.saveAttempted.await()
            awaitUntil { !fixture.model.isRepresentationUpdating.value }

            assertTrue(fixture.model.groupProfileState.value!!.myMember!!.isRepresenting)
            assertEquals(1, cache.saveCount)
        } finally {
            fixture.close()
            SharedFlowCentre.emitLogout()
        }
    }

    @Test
    fun sameUserNewSessionRejectsTheOldResponseBeforeUiAndCachePublication() = runBlocking {
        val account = AccountDto(REPRESENTATION_USER_ID, username = "same-user-session")
        SharedFlowCentre.emitAuthenticated(account)
        val cache = BlockingGroupProfileCacheStore()
        val putStarted = CompletableDeferred<Unit>()
        val releasePut = CompletableDeferred<Unit>()
        val fixture = createFixture(cache) { request ->
            when (request.method) {
                HttpMethod.Put -> {
                    putStarted.complete(Unit)
                    releasePut.await()
                    respondJson("""{"success":{"message":"updated","status_code":200}}""")
                }

                HttpMethod.Get -> respondJson(representationGroupJson(isRepresenting = true))
                else -> error("Unexpected request: ${request.method} ${request.url}")
            }
        }
        try {
            fixture.model.loadGroupData(representationProfile(isRepresenting = false))
            cache.loadStarted.await()
            fixture.model.updateRepresentation(
                isRepresenting = true,
                failureMessage = "update failed",
                sessionChangedMessage = "session changed",
            )
            putStarted.await()

            SharedFlowCentre.emitAuthenticated(account)
            releasePut.complete(Unit)
            awaitUntil { !fixture.model.isRepresentationUpdating.value }

            assertFalse(fixture.model.groupProfileState.value!!.myMember!!.isRepresenting)
            assertEquals(0, cache.saveCount)
        } finally {
            releasePut.complete(Unit)
            fixture.close()
            SharedFlowCentre.emitLogout()
        }
    }

    private fun createFixture(
        cache: BlockingGroupProfileCacheStore,
        handler: MockRequestHandler,
    ): RepresentationModelFixture {
        val client = HttpClient(MockEngine) {
            engine { addHandler(handler) }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val authService = createRepresentationAuthService(
            client = client,
            account = checkNotNull(SharedFlowCentre.currentSession.value).account,
        )
        return RepresentationModelFixture(
            model = GroupProfileScreenModel(
                groupsApi = GroupsApi(client),
                usersApi = UsersApi(client),
                authService = authService,
                logger = EmptyLogger(),
                groupProfileCacheStore = cache,
            ),
            client = client,
        )
    }

    private fun representationProfile(isRepresenting: Boolean): GroupProfileVo =
        GroupProfileVo(
            Json.decodeFromString<GroupData>(
                representationGroupJson(isRepresenting = isRepresenting)
            )
        )
}

private class BlockingGroupProfileCacheStore(
    private val saveFailure: Throwable? = null,
) : GroupProfileCacheStore {
    val loadStarted = CompletableDeferred<Unit>()
    val saveAttempted = CompletableDeferred<Unit>()
    var saveCount = 0
        private set

    override suspend fun load(groupId: String): GroupProfileCache? {
        loadStarted.complete(Unit)
        awaitCancellation()
    }

    override suspend fun save(cache: GroupProfileCache) {
        saveCount++
        saveAttempted.complete(Unit)
        saveFailure?.let { throw it }
    }

    override suspend fun clearAll() = Unit
}

private class RepresentationModelFixture(
    val model: GroupProfileScreenModel,
    private val client: HttpClient,
) {
    fun close() {
        ViewModelStore().apply {
            put("representation", model)
            clear()
        }
        client.close()
    }
}

private suspend fun awaitUntil(predicate: () -> Boolean) {
    withTimeout(3_000) {
        while (!predicate()) yield()
    }
}
