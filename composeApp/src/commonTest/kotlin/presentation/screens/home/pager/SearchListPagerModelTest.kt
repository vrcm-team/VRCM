package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.di.supports.PersistentCookiesStorage
import io.github.vrcmteam.vrcm.network.api.auth.AuthApi
import io.github.vrcmteam.vrcm.network.api.groups.GroupsApi
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.worlds.WorldsApi
import io.github.vrcmteam.vrcm.presentation.screens.home.compoments.SortOption
import io.github.vrcmteam.vrcm.presentation.screens.home.data.WorldSearchOptions
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.data.AccountDto
import io.github.vrcmteam.vrcm.storage.AccountDao
import io.github.vrcmteam.vrcm.testing.MainDispatcherTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.koin.core.logger.EmptyLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SearchListPagerModelTest : MainDispatcherTest() {
    @Test
    fun sameUserAuthenticationRetryKeepsSuccessfulSearchResult() = runBlocking {
        val account = AccountDto(
            userId = "usr_same",
            username = "same-user",
            password = "password",
        )
        var authRequestCount = 0
        var groupRequestCount = 0
        val fixture = createFixture(account = account) { request ->
            when (request.url.encodedPath) {
                "/auth/user" -> {
                    authRequestCount++
                    jsonResponse(currentUserJson(account))
                }
                "/groups" -> {
                    groupRequestCount++
                    if (groupRequestCount == 1) {
                        respond(
                            content = "unauthorized",
                            status = HttpStatusCode.Unauthorized,
                        )
                    } else {
                        jsonResponse(groupJson("grp_retried"))
                    }
                }
                else -> error("Unexpected request: ${request.url}")
            }
        }
        fixture.model.setSearchType(3)
        fixture.model.setSearchText("reauth")

        assertTrue(fixture.model.refreshSearchList())

        assertEquals(2, groupRequestCount)
        assertTrue(authRequestCount > 0)
        assertEquals(listOf("grp_retried"), fixture.model.groupSearchList.value.map { it.id })
        fixture.close()
    }

    @Test
    fun changingAuthenticatedUserClearsSearchResults() = runBlocking {
        val fixture = createFixture(
            account = AccountDto(userId = "usr_old", username = "old-user"),
        ) {
            jsonResponse(groupJson("grp_old_account"))
        }
        fixture.model.setSearchType(3)
        fixture.model.setSearchText("account")
        assertTrue(fixture.model.refreshSearchList())

        SharedFlowCentre.authed.emit(
            AccountDto(userId = "usr_new", username = "new-user"),
        )

        assertTrue(fixture.model.groupSearchList.value.isEmpty())
        fixture.close()
    }

    @Test
    fun clearingGroupQueryClearsResultsAndRejectsLateResponse() = runBlocking {
        val lateRequestStarted = CompletableDeferred<Unit>()
        val releaseLateRequest = CompletableDeferred<Unit>()
        var requestCount = 0
        val fixture = createFixture {
            requestCount++
            if (requestCount == 1) {
                jsonResponse(groupJson("grp_existing"))
            } else {
                lateRequestStarted.complete(Unit)
                releaseLateRequest.await()
                jsonResponse(groupJson("grp_late"))
            }
        }
        fixture.model.setSearchType(3)
        fixture.model.setSearchText("group")
        assertTrue(fixture.model.refreshSearchList())
        val lateSearch = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.model.refreshSearchList()
        }
        lateRequestStarted.await()

        fixture.model.setSearchText("")

        assertTrue(fixture.model.groupSearchList.value.isEmpty())
        releaseLateRequest.complete(Unit)
        lateSearch.await()
        assertTrue(fixture.model.groupSearchList.value.isEmpty())
        fixture.close()
    }

    @Test
    fun olderGroupQueryCannotOverwriteNewerResults() = runBlocking {
        val oldStarted = CompletableDeferred<Unit>()
        val releaseOld = CompletableDeferred<Unit>()
        val fixture = createFixture { request ->
            when (request.url.parameters["query"]) {
                "old" -> {
                    oldStarted.complete(Unit)
                    releaseOld.await()
                    jsonResponse(groupJson("grp_old"))
                }
                "new" -> jsonResponse(groupJson("grp_new"))
                else -> jsonResponse("[]")
            }
        }
        fixture.model.setSearchType(3)
        fixture.model.setSearchText("old")
        val oldSearch = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.model.refreshSearchList()
        }
        oldStarted.await()

        fixture.model.setSearchText("new")
        assertTrue(fixture.model.refreshSearchList())
        assertEquals(listOf("grp_new"), fixture.model.groupSearchList.value.map { it.id })

        releaseOld.complete(Unit)
        oldSearch.await()

        assertEquals(listOf("grp_new"), fixture.model.groupSearchList.value.map { it.id })
        fixture.close()
    }

    @Test
    fun returningToAQueryStartsANewGenerationRequest() = runBlocking {
        val firstRequestStarted = CompletableDeferred<Unit>()
        val releaseFirstRequest = CompletableDeferred<Unit>()
        var requestCount = 0
        val fixture = createFixture {
            requestCount++
            if (requestCount == 1) {
                firstRequestStarted.complete(Unit)
                releaseFirstRequest.await()
                jsonResponse(groupJson("grp_old"))
            } else {
                jsonResponse(groupJson("grp_current"))
            }
        }
        fixture.model.setSearchType(3)
        fixture.model.setSearchText("same")
        val oldSearch = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.model.refreshSearchList()
        }
        firstRequestStarted.await()

        fixture.model.setSearchText("different")
        fixture.model.setSearchText("same")
        assertTrue(fixture.model.refreshSearchList())
        assertEquals(listOf("grp_current"), fixture.model.groupSearchList.value.map { it.id })

        releaseFirstRequest.complete(Unit)
        oldSearch.await()

        assertEquals(2, requestCount)
        assertEquals(listOf("grp_current"), fixture.model.groupSearchList.value.map { it.id })
        fixture.close()
    }

    @Test
    fun failedQueryCanBeRetriedWithoutChangingItsKey() = runBlocking {
        var requestCount = 0
        val fixture = createFixture {
            requestCount++
            if (requestCount == 1) {
                respond(
                    content = "initial search failed",
                    status = HttpStatusCode.InternalServerError,
                )
            } else {
                jsonResponse(groupJson("grp_retried"))
            }
        }
        fixture.model.setSearchType(3)
        fixture.model.setSearchText("retry")

        assertEquals(false, fixture.model.refreshSearchList())
        assertTrue(fixture.model.refreshSearchList())

        assertEquals(2, requestCount)
        assertEquals(listOf("grp_retried"), fixture.model.groupSearchList.value.map { it.id })
        fixture.close()
    }

    @Test
    fun cancellingRefreshCancelsTheApiRequest() = runBlocking {
        val requestStarted = CompletableDeferred<Unit>()
        val requestCancelled = CompletableDeferred<Unit>()
        val fixture = createFixture {
            requestStarted.complete(Unit)
            try {
                CompletableDeferred<Unit>().await()
            } catch (cause: CancellationException) {
                requestCancelled.complete(Unit)
                throw cause
            }
            jsonResponse("[]")
        }
        fixture.model.setSearchType(3)
        fixture.model.setSearchText("cancel-me")
        val refresh = launch(start = CoroutineStart.UNDISPATCHED) {
            fixture.model.refreshSearchList()
        }
        requestStarted.await()

        refresh.cancelAndJoin()

        withTimeout(1_000) { requestCancelled.await() }
        fixture.close()
    }

    @Test
    fun resultFromPreviouslySelectedTabIsDiscarded() = runBlocking {
        val groupStarted = CompletableDeferred<Unit>()
        val releaseGroup = CompletableDeferred<Unit>()
        val fixture = createFixture { request ->
            if (request.url.parameters["query"] == "group") {
                groupStarted.complete(Unit)
                releaseGroup.await()
                jsonResponse(groupJson("grp_stale"))
            } else {
                jsonResponse("[]")
            }
        }
        fixture.model.setSearchType(3)
        fixture.model.setSearchText("group")
        val groupSearch = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.model.refreshSearchList()
        }
        groupStarted.await()

        fixture.model.setSearchType(0)
        fixture.model.setSearchText("user")
        assertTrue(fixture.model.refreshSearchList())
        releaseGroup.complete(Unit)
        groupSearch.await()

        assertTrue(fixture.model.groupSearchList.value.isEmpty())
        fixture.close()
    }

    @Test
    fun apiCancellationIsPropagated() = runBlocking {
        val cancellation = CancellationException("api cancelled")
        val fixture = createFixture { throw cancellation }
        fixture.model.setSearchType(3)
        fixture.model.setSearchText("cancelled")

        assertFailsWith<CancellationException> {
            fixture.model.refreshSearchList()
        }

        fixture.close()
    }

    @Test
    fun apiErrorIsPropagated() = runBlocking {
        val fatal = AssertionError("fatal")
        val fixture = createFixture { throw fatal }
        fixture.model.setSearchType(3)
        fixture.model.setSearchText("fatal")

        assertFailsWith<AssertionError> {
            fixture.model.refreshSearchList()
        }

        fixture.close()
    }

    @Test
    fun oldWorldOptionsCannotOverwriteNewerResults() = runBlocking {
        val oldStarted = CompletableDeferred<Unit>()
        val releaseOld = CompletableDeferred<Unit>()
        val fixture = createFixture { request ->
            when (request.url.parameters["sort"]) {
                "popularity" -> {
                    oldStarted.complete(Unit)
                    releaseOld.await()
                    jsonResponse(worldJson("wrld_old"))
                }
                "heat" -> jsonResponse(worldJson("wrld_new"))
                else -> jsonResponse("[]")
            }
        }
        fixture.model.setSearchType(1)
        fixture.model.setSearchText("world")
        val oldSearch = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.model.refreshSearchList()
        }
        oldStarted.await()

        fixture.model.updateWorldSearchOptions(
            WorldSearchOptions(sortOption = SortOption.Heat),
        )
        awaitUntil { fixture.model.worldSearchList.value.singleOrNull()?.id == "wrld_new" }
        releaseOld.complete(Unit)
        oldSearch.await()

        assertEquals(listOf("wrld_new"), fixture.model.worldSearchList.value.map { it.id })
        fixture.close()
    }

    @Test
    fun groupPagesUseTwentyItemOffsetsAndAppendWithoutDuplicates() = runBlocking {
        val requests = mutableListOf<Pair<String?, String?>>()
        val secondPageStarted = CompletableDeferred<Unit>()
        val releaseSecondPage = CompletableDeferred<Unit>()
        val fixture = createFixture { request ->
            val n = request.url.parameters["n"]
            val offset = request.url.parameters["offset"]
            requests += n to offset
            when (offset) {
                "0" -> jsonResponse(groupsJson((0 until 20).map { "grp_$it" }))
                "20" -> {
                    secondPageStarted.complete(Unit)
                    releaseSecondPage.await()
                    jsonResponse(groupsJson(listOf("grp_19", "grp_20")))
                }
                else -> jsonResponse("[]")
            }
        }
        fixture.model.setSearchType(3)
        fixture.model.setSearchText("paged")

        assertTrue(fixture.model.refreshSearchList())
        assertEquals(20, fixture.model.groupSearchList.value.size)
        assertTrue(fixture.model.groupHasMore.value)

        val secondPage = assertNotNull(fixture.model.loadMoreGroups())
        secondPageStarted.await()
        assertNull(fixture.model.loadMoreGroups())
        assertEquals(
            listOf<Pair<String?, String?>>("20" to "0", "20" to "20"),
            requests,
        )
        releaseSecondPage.complete(Unit)
        secondPage.join()

        assertEquals((0..20).map { "grp_$it" }, fixture.model.groupSearchList.value.map { it.id })
        assertEquals(false, fixture.model.groupHasMore.value)
        fixture.close()
    }

    @Test
    fun oldGroupPageCannotAppendAfterQueryChanges() = runBlocking {
        val oldPageStarted = CompletableDeferred<Unit>()
        val releaseOldPage = CompletableDeferred<Unit>()
        val fixture = createFixture { request ->
            val query = request.url.parameters["query"]
            val offset = request.url.parameters["offset"]
            when {
                query == "old" && offset == "0" ->
                    jsonResponse(groupsJson((0 until 20).map { "old_$it" }))
                query == "old" && offset == "20" -> {
                    oldPageStarted.complete(Unit)
                    releaseOldPage.await()
                    jsonResponse(groupJson("old_20"))
                }
                query == "new" -> jsonResponse(groupJson("new_0"))
                else -> jsonResponse("[]")
            }
        }
        fixture.model.setSearchType(3)
        fixture.model.setSearchText("old")
        fixture.model.refreshSearchList()
        val oldPage = assertNotNull(fixture.model.loadMoreGroups())
        oldPageStarted.await()

        fixture.model.setSearchText("new")
        fixture.model.refreshSearchList()
        releaseOldPage.complete(Unit)
        oldPage.join()

        assertEquals(listOf("new_0"), fixture.model.groupSearchList.value.map { it.id })
        assertEquals(false, fixture.model.groupHasMore.value)
        fixture.close()
    }

    @Test
    fun shortFirstGroupPageStopsFurtherRequests() = runBlocking {
        var requestCount = 0
        val fixture = createFixture {
            requestCount++
            jsonResponse(groupJson("only"))
        }
        fixture.model.setSearchType(3)
        fixture.model.setSearchText("short")
        fixture.model.refreshSearchList()

        assertNull(fixture.model.loadMoreGroups())

        assertEquals(1, requestCount)
        assertEquals(false, fixture.model.groupHasMore.value)
        fixture.close()
    }

    @Test
    fun failedGroupPageRequiresExplicitRetry() = runBlocking {
        var requestCount = 0
        var loadMoreAttempts = 0
        val fixture = createFixture { request ->
            requestCount++
            if (request.url.parameters["offset"] == "0") {
                jsonResponse(groupsJson((0 until 20).map { "grp_$it" }))
            } else {
                loadMoreAttempts++
                if (loadMoreAttempts == 1) {
                    respond(
                        content = "load more failed",
                        status = HttpStatusCode.InternalServerError,
                    )
                } else {
                    jsonResponse(groupJson("grp_20"))
                }
            }
        }
        fixture.model.setSearchType(3)
        fixture.model.setSearchText("failed-page")
        fixture.model.refreshSearchList()

        assertNotNull(fixture.model.loadMoreGroups()).join()

        assertTrue(fixture.model.groupLoadMoreFailed.value)
        assertNull(fixture.model.loadMoreGroups())
        assertNotNull(fixture.model.retryLoadMoreGroups()).join()

        assertEquals(3, requestCount)
        assertEquals((0..20).map { "grp_$it" }, fixture.model.groupSearchList.value.map { it.id })
        assertEquals(false, fixture.model.groupLoadMoreFailed.value)
        fixture.close()
    }
}

private class SearchModelFixture(
    val model: SearchListPagerModel,
    private val client: HttpClient,
) {
    fun close() {
        model.onDispose()
        client.close()
    }
}

private fun createFixture(
    account: AccountDto? = null,
    handler: MockRequestHandler,
): SearchModelFixture {
    val logger = EmptyLogger()
    val client = HttpClient(MockEngine) {
        engine { addHandler(handler) }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
    val accountDao = AccountDao(MapSettings()).also { dao ->
        account?.let(dao::saveAccountInfo)
    }
    val authService = AuthService(
        authApi = AuthApi(client),
        accountDao = accountDao,
        cookiesStorage = PersistentCookiesStorage(logger),
    )
    return SearchModelFixture(
        model = SearchListPagerModel(
            usersApi = UsersApi(client),
            worldsApi = WorldsApi(client),
            groupsApi = GroupsApi(client),
            authService = authService,
            logger = logger,
        ),
        client = client,
    )
}

private fun MockRequestHandleScope.jsonResponse(content: String) = respond(
    content = content,
    status = HttpStatusCode.OK,
    headers = headersOf(HttpHeaders.ContentType, "application/json"),
)

private fun groupJson(id: String): String = """
    [{"id":"$id","name":"$id"}]
""".trimIndent()

private fun currentUserJson(account: AccountDto): String = """
    {
      "requiresTwoFactorAuth":null,
      "ageVerificationStatus":"verified","ageVerified":true,
      "acceptedPrivacyVersion":0,"acceptedTOSVersion":0,
      "accountDeletionDate":null,"accountDeletionLog":null,"activeFriends":[],
      "allowAvatarCopying":true,"bio":null,"bioLinks":[],
      "currentAvatar":"","currentAvatarAssetUrl":null,"currentAvatarImageUrl":"",
      "currentAvatarTags":[],"currentAvatarThumbnailImageUrl":"","date_joined":"",
      "developerType":"none","displayName":"${account.username}","emailVerified":true,
      "fallbackAvatar":"","friendGroupNames":[],"friendKey":"","friends":[],
      "googleId":"","hasBirthday":true,"hasEmail":true,
      "hasLoggedInFromClient":true,"hasPendingEmail":false,
      "hideContentFilterSettings":false,"homeLocation":"","id":"${account.userId}",
      "isFriend":false,"last_activity":"","last_login":"",
      "last_platform":"standalonewindows","obfuscatedEmail":"",
      "obfuscatedPendingEmail":"","oculusId":"","offlineFriends":[],
      "onlineFriends":[],"pastDisplayNames":[],"picoId":"",
      "presence":{
        "avatarThumbnail":null,"displayName":"${account.username}","groups":[],
        "id":"${account.userId}","instance":"","instanceType":"",
        "isRejoining":null,"platform":"standalonewindows","profilePicOverride":null,
        "status":"active","travelingToInstance":"","travelingToWorld":"","world":""
      },
      "profilePicOverride":"","state":"online","status":"active",
      "statusDescription":"","statusFirstTime":false,"statusHistory":[],
      "steamDetails":{},"steamId":"","tags":[],"twoFactorAuthEnabled":false,
      "twoFactorAuthEnabledDate":null,"unsubscribe":false,"updated_at":"",
      "userIcon":"","userLanguage":null,"userLanguageCode":null,
      "username":"${account.username}","viveId":"","pronouns":null
    }
""".trimIndent()

private fun groupsJson(ids: List<String>): String =
    ids.joinToString(prefix = "[", postfix = "]") { id ->
        "{\"id\":\"$id\",\"name\":\"$id\"}"
    }

private fun worldJson(id: String): String = """
    [{
      "authorId":"usr_author","authorName":"Author","capacity":16,"created_at":null,
      "description":null,"favorites":0,"featured":false,"heat":0,"id":"$id",
      "imageUrl":"","labsPublicationDate":"","name":"$id","namespace":null,
      "organization":"","popularity":0,"publicationDate":"","recommendedCapacity":16,
      "releaseStatus":"public","tags":[],"thumbnailImageUrl":null,"udonProducts":[],
      "unityPackages":[],"updated_at":null,"version":1,"visits":0
    }]
""".trimIndent()

private suspend fun awaitUntil(predicate: () -> Boolean) {
    withTimeout(1_000) {
        while (!predicate()) yield()
    }
}
