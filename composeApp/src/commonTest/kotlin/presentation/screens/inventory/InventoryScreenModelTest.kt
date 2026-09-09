package io.github.vrcmteam.vrcm.presentation.screens.inventory

import androidx.lifecycle.ViewModelStore
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.network.api.inventory.InventoryItemType
import io.github.vrcmteam.vrcm.network.api.inventory.InventorySortOrder
import io.github.vrcmteam.vrcm.network.api.inventory.data.InventoryData
import io.github.vrcmteam.vrcm.network.api.inventory.data.InventoryItemData
import io.github.vrcmteam.vrcm.testing.MainDispatcherTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryScreenModelTest : MainDispatcherTest() {
    private val models = mutableListOf<InventoryScreenModel>()

    @AfterTest
    fun disposeModels() {
        models.forEach { model ->
            ViewModelStore().apply {
                put("inventory", model)
                clear()
            }
        }
    }

    @Test
    fun filtersResetThePageAndReachTheSourceAsServerQueries() = runTest {
        val token = AccountSessionToken("usr_a", 1L)
        val source = FakeInventorySource(token)
        source.handler = { requestToken, request ->
            val id = request.filters.type?.value ?: "all"
            pageResponse(requestToken, listOf(item("inv_$id")), totalCount = 1)
        }
        val model = model(source, pageSize = 20)
        advanceUntilIdle()

        model.selectType(InventoryItemType.Prop)
        model.selectArchived(InventoryArchivedFilter.Archived)
        model.selectOrder(InventorySortOrder.OldestCreated)
        advanceUntilIdle()

        val latestRequest = source.requests.last().second
        assertEquals(0, latestRequest.offset)
        assertEquals(InventoryItemType.Prop, latestRequest.filters.type)
        assertEquals(InventoryArchivedFilter.Archived, latestRequest.filters.archived)
        assertEquals(InventorySortOrder.OldestCreated, latestRequest.filters.order)
        assertEquals(
            listOf("inv_prop"),
            assertIs<InventoryScreenState.Content>(model.state.value).items.map { it.id },
        )
    }

    @Test
    fun repeatedLoadMoreWhileInFlightCreatesOneRequestAndMergesOverlap() = runTest {
        val token = AccountSessionToken("usr_a", 1L)
        val source = FakeInventorySource(token)
        val pageStarted = CompletableDeferred<Unit>()
        val releasePage = CompletableDeferred<Unit>()
        source.handler = { requestToken, request ->
            if (request.offset == 0) {
                pageResponse(requestToken, listOf(item("inv_1"), item("inv_2")), totalCount = 3)
            } else {
                pageStarted.complete(Unit)
                releasePage.await()
                pageResponse(requestToken, listOf(item("inv_2"), item("inv_3")), totalCount = 3)
            }
        }
        val model = model(source, pageSize = 2)
        advanceUntilIdle()

        model.loadMore()
        pageStarted.await()
        model.loadMore()

        assertEquals(listOf(0, 2), source.requests.map { it.second.offset })
        assertTrue(assertIs<InventoryScreenState.Content>(model.state.value).isLoadingMore)

        releasePage.complete(Unit)
        advanceUntilIdle()

        val content = assertIs<InventoryScreenState.Content>(model.state.value)
        assertEquals(listOf("inv_1", "inv_2", "inv_3"), content.items.map { it.id })
        assertFalse(content.hasMore)
        assertFalse(content.isLoadingMore)
    }

    @Test
    fun loadMoreFailureRetriesTheSameServerOffset() = runTest {
        val token = AccountSessionToken("usr_a", 1L)
        val source = FakeInventorySource(token)
        var appendAttempts = 0
        source.handler = { requestToken, request ->
            if (request.offset == 0) {
                pageResponse(requestToken, listOf(item("inv_1"), item("inv_2")), totalCount = 3)
            } else if (appendAttempts++ == 0) {
                AuthenticatedInventoryPage(Result.failure(IllegalStateException("unavailable")), requestToken)
            } else {
                pageResponse(requestToken, listOf(item("inv_3")), totalCount = 3)
            }
        }
        val model = model(source, pageSize = 2)
        advanceUntilIdle()

        model.loadMore()
        advanceUntilIdle()
        assertTrue(assertIs<InventoryScreenState.Content>(model.state.value).loadMoreError)

        model.retryLoadMore()
        advanceUntilIdle()

        assertEquals(listOf(0, 2, 2), source.requests.map { it.second.offset })
        val content = assertIs<InventoryScreenState.Content>(model.state.value)
        assertEquals(listOf("inv_1", "inv_2", "inv_3"), content.items.map { it.id })
        assertFalse(content.loadMoreError)
    }

    @Test
    fun failedRefreshKeepsContentAndRetryReplacesIt() = runTest {
        val token = AccountSessionToken("usr_a", 1L)
        val source = FakeInventorySource(token)
        var attempts = 0
        source.handler = { requestToken, _ ->
            when (attempts++) {
                0 -> pageResponse(requestToken, listOf(item("inv_old")), totalCount = 1)
                1 -> AuthenticatedInventoryPage(
                    Result.failure(IllegalStateException("refresh unavailable")),
                    requestToken,
                )
                else -> pageResponse(requestToken, listOf(item("inv_new")), totalCount = 1)
            }
        }
        val model = model(source)
        advanceUntilIdle()

        model.refresh()
        advanceUntilIdle()
        val failed = assertIs<InventoryScreenState.Content>(model.state.value)
        assertEquals(listOf("inv_old"), failed.items.map { it.id })
        assertTrue(failed.refreshError)

        model.retry()
        advanceUntilIdle()
        val refreshed = assertIs<InventoryScreenState.Content>(model.state.value)
        assertEquals(listOf("inv_new"), refreshed.items.map { it.id })
        assertFalse(refreshed.refreshError)
    }

    @Test
    fun failedRefreshKeepsThePagingCursorForDisplayedContent() = runTest {
        val token = AccountSessionToken("usr_a", 1L)
        val source = FakeInventorySource(token)
        var initialRequests = 0
        source.handler = { requestToken, request ->
            when {
                request.offset == 0 && initialRequests++ == 0 -> pageResponse(
                    requestToken,
                    listOf(item("inv_1"), item("inv_2")),
                    totalCount = 3,
                )
                request.offset == 0 -> AuthenticatedInventoryPage(
                    Result.failure(IllegalStateException("refresh unavailable")),
                    requestToken,
                )
                else -> pageResponse(requestToken, listOf(item("inv_3")), totalCount = 3)
            }
        }
        val model = model(source, pageSize = 2)
        advanceUntilIdle()

        model.refresh()
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(0, 0, 2), source.requests.map { it.second.offset })
        assertEquals(
            listOf("inv_1", "inv_2", "inv_3"),
            assertIs<InventoryScreenState.Content>(model.state.value).items.map { it.id },
        )
    }

    @Test
    fun lateOldAccountRequestCannotOverwriteTheNewAccount() = runTest {
        val tokenA = AccountSessionToken("usr_a", 1L)
        val tokenB = AccountSessionToken("usr_b", 2L)
        val source = FakeInventorySource(tokenA)
        val requestAStarted = CompletableDeferred<Unit>()
        val releaseRequestA = CompletableDeferred<Unit>()
        source.handler = { requestToken, _ ->
            if (requestToken == tokenA) {
                requestAStarted.complete(Unit)
                releaseRequestA.await()
                pageResponse(requestToken, listOf(item("inv_a")), totalCount = 1)
            } else {
                pageResponse(requestToken, listOf(item("inv_b")), totalCount = 1)
            }
        }
        val model = model(source)
        requestAStarted.await()

        source.sessionTokens.value = tokenB
        advanceUntilIdle()
        releaseRequestA.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            listOf("inv_b"),
            assertIs<InventoryScreenState.Content>(model.state.value).items.map { it.id },
        )
        assertEquals(listOf(tokenA, tokenB), source.requests.map { it.first })
    }

    @Test
    fun logoutClearsLoadedInventoryIntoSessionMissingState() = runTest {
        val token = AccountSessionToken("usr_a", 1L)
        val source = FakeInventorySource(token)
        source.handler = { requestToken, _ ->
            pageResponse(requestToken, listOf(item("inv_private")), totalCount = 1)
        }
        val model = model(source)
        advanceUntilIdle()
        assertIs<InventoryScreenState.Content>(model.state.value)

        source.sessionTokens.value = null
        advanceUntilIdle()

        assertIs<InventoryScreenState.SessionMissing>(model.state.value)
    }

    @Test
    fun unrelatedSameAccountTokenChangeReloadsAfterTheOldRequestFinishes() = runTest {
        val firstToken = AccountSessionToken("usr_a", 1L)
        val replacementToken = AccountSessionToken("usr_a", 2L)
        val source = FakeInventorySource(firstToken)
        val firstRequestStarted = CompletableDeferred<Unit>()
        val releaseFirstRequest = CompletableDeferred<Unit>()
        source.handler = { requestToken, _ ->
            if (requestToken == firstToken) {
                firstRequestStarted.complete(Unit)
                releaseFirstRequest.await()
                pageResponse(requestToken, listOf(item("inv_stale")), totalCount = 1)
            } else {
                pageResponse(requestToken, listOf(item("inv_current")), totalCount = 1)
            }
        }
        val model = model(source)
        firstRequestStarted.await()

        source.sessionTokens.value = replacementToken
        releaseFirstRequest.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(firstToken, replacementToken), source.requests.map { it.first })
        assertEquals(
            listOf("inv_current"),
            assertIs<InventoryScreenState.Content>(model.state.value).items.map { it.id },
        )
    }

    @Test
    fun refreshedTokenFromUnauthorizedRetryCompletesWithoutDuplicateLoad() = runTest {
        val expired = AccountSessionToken("usr_a", 1L)
        val refreshed = AccountSessionToken("usr_a", 2L)
        val source = FakeInventorySource(expired)
        source.handler = { _, _ ->
            source.sessionTokens.value = refreshed
            pageResponse(refreshed, listOf(item("inv_after_retry")), totalCount = 1)
        }

        val model = model(source)
        advanceUntilIdle()

        assertEquals(1, source.requests.size)
        assertEquals(
            listOf("inv_after_retry"),
            assertIs<InventoryScreenState.Content>(model.state.value).items.map { it.id },
        )
    }

    @Test
    fun initialFailureCanRetryForTheCurrentSession() = runTest {
        val token = AccountSessionToken("usr_a", 1L)
        val source = FakeInventorySource(token)
        var attempts = 0
        source.handler = { requestToken, _ ->
            if (attempts++ == 0) {
                AuthenticatedInventoryPage(Result.failure(IllegalStateException("failed")), requestToken)
            } else {
                pageResponse(requestToken, listOf(item("inv_recovered")), totalCount = 1)
            }
        }
        val model = model(source)
        advanceUntilIdle()
        assertIs<InventoryScreenState.Error>(model.state.value)

        model.retry()
        advanceUntilIdle()

        assertEquals(
            listOf("inv_recovered"),
            assertIs<InventoryScreenState.Content>(model.state.value).items.map { it.id },
        )
    }

    private fun model(
        source: InventorySource,
        pageSize: Int = 20,
    ) = InventoryScreenModel(source, pageSize).also(models::add)

    private fun item(id: String) = InventoryItemData(id = id, name = id)

    private fun pageResponse(
        token: AccountSessionToken,
        items: List<InventoryItemData>,
        totalCount: Int,
    ) = AuthenticatedInventoryPage(
        result = Result.success(InventoryData(items, totalCount)),
        sessionToken = token,
    )
}

private class FakeInventorySource(
    initialToken: AccountSessionToken?,
) : InventorySource {
    override val sessionTokens = MutableStateFlow(initialToken)
    val requests = mutableListOf<Pair<AccountSessionToken, InventoryPageRequest>>()
    var handler: suspend (AccountSessionToken, InventoryPageRequest) -> AuthenticatedInventoryPage? =
        { token, _ ->
            AuthenticatedInventoryPage(Result.success(InventoryData()), token)
        }

    override fun isCurrentSession(token: AccountSessionToken): Boolean =
        sessionTokens.value == token

    override suspend fun loadPage(
        sessionToken: AccountSessionToken,
        request: InventoryPageRequest,
    ): AuthenticatedInventoryPage? {
        requests += sessionToken to request
        return handler(sessionToken, request)
    }
}
