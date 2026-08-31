package io.github.vrcmteam.vrcm.presentation.screens.avatar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteData
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteGroupData
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.presentation.favorites.FavoriteEntrySource
import io.github.vrcmteam.vrcm.presentation.screens.avatar.data.AvatarProfileVo
import io.github.vrcmteam.vrcm.service.data.AccountDto
import io.github.vrcmteam.vrcm.testing.MainDispatcherTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AvatarDeletionTest : MainDispatcherTest() {
    private val models = mutableListOf<AvatarProfileScreenModel>()

    @AfterTest
    fun disposeModels() {
        models.forEach(::disposeDeletionViewModel)
    }

    @Test
    fun deleteActionRequiresValidatedOwnershipAndAllowsTheCurrentAvatar() = runBlocking {
        val fixture = fixture(
            avatars = listOf(
                deletionAvatar(id = "avtr_owned", authorId = "usr_owner"),
                deletionAvatar(id = "avtr_other", authorId = "usr_other"),
                deletionAvatar(id = "avtr_hidden", authorId = "usr_owner", releaseStatus = "hidden"),
            ),
            currentAvatarId = "avtr_owned",
        )

        assertFalse(fixture.model.deletionState.value.canDelete)

        fixture.open("avtr_owned")
        yield()
        assertTrue(fixture.model.deletionState.value.canDelete)

        fixture.open("avtr_other")
        yield()
        assertFalse(fixture.model.deletionState.value.canDelete)

        fixture.open("avtr_hidden")
        yield()
        assertFalse(fixture.model.deletionState.value.canDelete)
    }

    @Test
    fun confirmationBindsTheExactSessionAvatarAndName() = runBlocking {
        val token = AccountSessionToken("usr_owner", generation = 1)
        val fixture = fixture(
            avatars = listOf(
                deletionAvatar(
                    id = "avtr_owned",
                    authorId = "usr_owner",
                    name = "Exact snapshot name",
                )
            ),
            token = token,
        )
        fixture.open("avtr_owned")
        yield()

        fixture.model.requestAvatarDeletion()

        assertEquals(
            AvatarDeletionTarget(token, "avtr_owned", "Exact snapshot name"),
            fixture.model.deletionState.value.confirmation,
        )

        fixture.session.value = authenticatedAccount(
            AccountSessionToken("usr_owner", generation = 2)
        )
        yield()
        assertNull(fixture.model.deletionState.value.confirmation)

        fixture.model.confirmAvatarDeletion()
        assertTrue(fixture.deleter.requests.isEmpty())
    }

    @Test
    fun repeatedConfirmationStartsOnlyOneRequest() = runBlocking {
        val fixture = fixture(listOf(deletionAvatar()))
        fixture.open("avtr_owned")
        yield()
        fixture.model.requestAvatarDeletion()

        fixture.model.confirmAvatarDeletion()
        fixture.model.confirmAvatarDeletion()

        assertEquals(1, fixture.deleter.requests.size)
        assertTrue(fixture.model.deletionState.value.isDeleting)
    }

    @Test
    fun avatarSelectionAndDeletionAreMutuallyExclusive() = runBlocking {
        val selectingFixture = fixture(listOf(deletionAvatar()))
        selectingFixture.open("avtr_owned")
        yield()

        selectingFixture.model.selectAvatar()
        assertEquals(listOf("avtr_owned"), selectingFixture.selector.selectedAvatarIds)
        assertTrue(selectingFixture.model.actionState.value.isSelecting)
        assertFalse(selectingFixture.model.deletionState.value.canDelete)

        selectingFixture.model.requestAvatarDeletion()
        assertNull(selectingFixture.model.deletionState.value.confirmation)
        assertTrue(selectingFixture.deleter.requests.isEmpty())
        selectingFixture.selector.completeSelection(Result.success(Unit))
        yield()

        val deletingFixture = fixture(listOf(deletionAvatar()))
        deletingFixture.open("avtr_owned")
        yield()
        deletingFixture.model.requestAvatarDeletion()

        deletingFixture.model.selectAvatar()
        assertTrue(deletingFixture.selector.selectedAvatarIds.isEmpty())

        deletingFixture.model.confirmAvatarDeletion()
        deletingFixture.model.selectAvatar()
        assertEquals(1, deletingFixture.deleter.requests.size)
        assertTrue(deletingFixture.selector.selectedAvatarIds.isEmpty())
    }

    @Test
    fun mismatchedDeletionResponsesAreRejected() = runBlocking {
        val responses = listOf(
            deletionAvatar(id = "avtr_different", releaseStatus = "hidden"),
            deletionAvatar(authorId = "usr_other", releaseStatus = "hidden"),
            deletionAvatar(releaseStatus = "private"),
        )

        responses.forEach { response ->
            val fixture = fixture(listOf(deletionAvatar()))
            fixture.open("avtr_owned")
            yield()
            fixture.model.requestAvatarDeletion()
            fixture.model.confirmAvatarDeletion()

            fixture.deleter.completeSuccess(0, response)
            yield()

            assertEquals(
                AvatarDeletionFailure.InvalidResponse,
                fixture.model.deletionState.value.failure,
            )
            assertEquals("private", fixture.model.avatarProfileState.value?.releaseStatus)
            assertTrue(fixture.store.results.value.avatars.isEmpty())
        }
    }

    @Test
    fun renewedSessionCanAcceptTheAuthoritativeDeletionResponse() = runBlocking {
        val originalToken = AccountSessionToken("usr_owner", generation = 1)
        val renewedToken = AccountSessionToken("usr_owner", generation = 2)
        val fixture = fixture(
            avatars = listOf(deletionAvatar()),
            token = originalToken,
        )
        val notices = mutableListOf<AvatarProfileNotice>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            fixture.model.notices.collect(notices::add)
        }
        fixture.open("avtr_owned")
        yield()
        fixture.model.requestAvatarDeletion()
        fixture.model.confirmAvatarDeletion()

        fixture.session.value = authenticatedAccount(renewedToken)
        fixture.deleter.completeSuccess(
            index = 0,
            avatar = deletionAvatar(releaseStatus = "hidden"),
            responseToken = renewedToken,
        )
        yield()

        assertEquals("hidden", fixture.model.avatarProfileState.value?.releaseStatus)
        assertEquals(listOf<AvatarProfileNotice>(AvatarProfileNotice.Deleted), notices)
        assertEquals(setOf("avtr_owned"), fixture.store.results.value.deletedAvatarIds(renewedToken))
        collector.cancel()
    }

    @Test
    fun unrelatedSessionChangesRejectLateSuccess() = runBlocking {
        val originalToken = AccountSessionToken("usr_owner", generation = 1)
        val replacements = listOf<AuthenticatedAccount?>(
            authenticatedAccount(AccountSessionToken("usr_owner", generation = 2)),
            authenticatedAccount(AccountSessionToken("usr_other", generation = 3)),
            null,
        )

        replacements.forEach { replacement ->
            val fixture = fixture(
                avatars = listOf(deletionAvatar()),
                token = originalToken,
            )
            val notices = mutableListOf<AvatarProfileNotice>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                fixture.model.notices.collect(notices::add)
            }
            fixture.open("avtr_owned")
            yield()
            fixture.model.requestAvatarDeletion()
            fixture.model.confirmAvatarDeletion()

            fixture.session.value = replacement
            fixture.deleter.completeSuccess(
                index = 0,
                avatar = deletionAvatar(releaseStatus = "hidden"),
                responseToken = originalToken,
            )
            yield()

            assertEquals("private", fixture.model.avatarProfileState.value?.releaseStatus)
            assertTrue(notices.isEmpty())
            assertTrue(fixture.store.results.value.avatars.isEmpty())
            assertFalse(fixture.model.deletionState.value.isDeleting)
            collector.cancel()
        }
    }

    @Test
    fun lateSuccessForThePreviousPageDoesNotReplaceTheCurrentAvatar() = runBlocking {
        val token = AccountSessionToken("usr_owner", generation = 1)
        val fixture = fixture(
            avatars = listOf(
                deletionAvatar(id = "avtr_owned", name = "Old"),
                deletionAvatar(id = "avtr_new", name = "New"),
            ),
            token = token,
        )
        val notices = mutableListOf<AvatarProfileNotice>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            fixture.model.notices.collect(notices::add)
        }
        fixture.open("avtr_owned")
        yield()
        fixture.model.requestAvatarDeletion()
        fixture.model.confirmAvatarDeletion()

        fixture.open("avtr_new")
        yield()
        fixture.deleter.completeSuccess(
            index = 0,
            avatar = deletionAvatar(id = "avtr_owned", name = "Old", releaseStatus = "hidden"),
            responseToken = token,
        )
        yield()

        assertEquals("avtr_new", fixture.model.avatarProfileState.value?.avatarId)
        assertEquals("New", fixture.model.avatarProfileState.value?.avatarName)
        assertTrue(notices.isEmpty())
        assertEquals(setOf("avtr_owned"), fixture.store.results.value.deletedAvatarIds(token))
        collector.cancel()
    }

    @Test
    fun failedDeletionKeepsTheConfirmationAndCanBeRetried() = runBlocking {
        val fixture = fixture(listOf(deletionAvatar()))
        val notices = mutableListOf<AvatarProfileNotice>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            fixture.model.notices.collect(notices::add)
        }
        fixture.open("avtr_owned")
        yield()
        fixture.model.requestAvatarDeletion()
        fixture.model.confirmAvatarDeletion()

        fixture.deleter.completeFailure(
            index = 0,
            error = VRCApiException("Forbidden", 403, "forbidden"),
        )
        yield()

        assertEquals(AvatarDeletionFailure.Forbidden, fixture.model.deletionState.value.failure)
        assertNotNull(fixture.model.deletionState.value.confirmation)
        assertFalse(fixture.model.deletionState.value.isDeleting)
        assertEquals("private", fixture.model.avatarProfileState.value?.releaseStatus)

        fixture.model.confirmAvatarDeletion()
        assertEquals(2, fixture.deleter.requests.size)
        fixture.deleter.completeSuccess(
            index = 1,
            avatar = deletionAvatar(releaseStatus = "hidden"),
        )
        yield()

        assertEquals(listOf<AvatarProfileNotice>(AvatarProfileNotice.Deleted), notices)
        assertEquals("hidden", fixture.model.avatarProfileState.value?.releaseStatus)
        collector.cancel()
    }

    @Test
    fun requestFailuresMapToActionableCategories() {
        val cases = mapOf(
            400 to AvatarDeletionFailure.BadRequest,
            401 to AvatarDeletionFailure.Unauthorized,
            403 to AvatarDeletionFailure.Forbidden,
            404 to AvatarDeletionFailure.NotFound,
            500 to AvatarDeletionFailure.Unexpected,
        )

        cases.forEach { (status, expected) ->
            assertEquals(
                expected,
                VRCApiException("Request failed", status, "body").toAvatarDeletionFailure(),
            )
        }
        assertEquals(
            AvatarDeletionFailure.Unexpected,
            IllegalStateException("offline").toAvatarDeletionFailure(),
        )
    }

    @Test
    fun resultStoreOnlyExposesValidatedResultsForTheExactSession() {
        val firstToken = AccountSessionToken("usr_owner", generation = 1)
        val secondToken = AccountSessionToken("usr_owner", generation = 2)
        var currentToken: AccountSessionToken? = firstToken
        val store = AvatarDeletionResultStore { currentToken }

        assertFalse(store.record(firstToken, deletionAvatar(releaseStatus = "private")))
        assertFalse(
            store.record(
                firstToken,
                deletionAvatar(authorId = "usr_other", releaseStatus = "hidden"),
            )
        )
        assertTrue(store.record(firstToken, deletionAvatar(releaseStatus = "hidden")))
        assertEquals(setOf("avtr_owned"), store.results.value.deletedAvatarIds(firstToken))

        currentToken = secondToken
        assertTrue(store.results.value.deletedAvatarIds(secondToken).isEmpty())
        assertFalse(store.record(firstToken, deletionAvatar(releaseStatus = "hidden")))
        assertTrue(
            store.record(
                secondToken,
                deletionAvatar(id = "avtr_second", releaseStatus = "hidden"),
            )
        )
        assertEquals(setOf("avtr_second"), store.results.value.deletedAvatarIds(secondToken))
        assertTrue(store.results.value.deletedAvatarIds(firstToken).isEmpty())
    }

    private fun fixture(
        avatars: List<AvatarData>,
        token: AccountSessionToken = AccountSessionToken("usr_owner", generation = 1),
        currentAvatarId: String = "avtr_current",
    ): AvatarDeletionFixture = AvatarDeletionFixture(
        avatars = avatars,
        token = token,
        currentAvatarId = currentAvatarId,
    ).also { models += it.model }
}

private class AvatarDeletionFixture(
    avatars: List<AvatarData>,
    token: AccountSessionToken,
    currentAvatarId: String,
) {
    private val avatarsById = avatars.associateBy { it.id }
    val session = MutableStateFlow<AuthenticatedAccount?>(authenticatedAccount(token))
    val deleter = ControlledAvatarDeleter { session.value?.token }
    val store = AvatarDeletionResultStore { session.value?.token }
    val selector = DeletionAvatarSelector(token.userId, currentAvatarId)
    val model = AvatarProfileScreenModel(
        avatarProfileLoader = StaticAvatarProfileLoader(avatarsById),
        avatarSelector = selector,
        favoriteEntrySource = DeletionFavoriteEntrySource(),
        requestDispatcher = Dispatchers.Unconfined,
        favoriteSession = session,
        avatarDeleter = deleter,
        avatarDeletionResults = store,
    )

    fun open(avatarId: String) {
        model.refreshAvatarData(AvatarProfileVo(avatarsById.getValue(avatarId)))
    }
}

private data class PendingAvatarDeletion(
    val sessionToken: AccountSessionToken,
    val avatarId: String,
    val response: CompletableDeferred<AuthenticatedAvatarDeletion?> = CompletableDeferred(),
)

private class ControlledAvatarDeleter(
    private val currentToken: () -> AccountSessionToken?,
) : AvatarDeleter {
    val requests = mutableListOf<PendingAvatarDeletion>()

    override fun isCurrentSession(token: AccountSessionToken): Boolean = currentToken() == token

    override suspend fun delete(
        sessionToken: AccountSessionToken,
        avatarId: String,
    ): AuthenticatedAvatarDeletion? {
        val request = PendingAvatarDeletion(sessionToken, avatarId)
        requests += request
        return request.response.await()
    }

    fun completeSuccess(
        index: Int,
        avatar: AvatarData,
        responseToken: AccountSessionToken = requests[index].sessionToken,
    ) {
        requests[index].response.complete(
            AuthenticatedAvatarDeletion(Result.success(avatar), responseToken)
        )
    }

    fun completeFailure(
        index: Int,
        error: Throwable,
        responseToken: AccountSessionToken = requests[index].sessionToken,
    ) {
        requests[index].response.complete(
            AuthenticatedAvatarDeletion(Result.failure(error), responseToken)
        )
    }
}

private class StaticAvatarProfileLoader(
    private val avatars: Map<String, AvatarData>,
) : AvatarProfileLoader {
    override suspend fun load(avatarId: String): Result<AvatarData> =
        avatars[avatarId]?.let { Result.success(it) }
            ?: Result.failure(IllegalArgumentException("Unknown avatar: $avatarId"))
}

private class DeletionAvatarSelector(
    userId: String,
    currentAvatarId: String,
) : AvatarSelector {
    private val mutableCurrentUser = MutableStateFlow(
        AvatarUserContext(userId, currentAvatarId)
    )
    override val currentUser: StateFlow<AvatarUserContext?> = mutableCurrentUser
    val selectedAvatarIds = mutableListOf<String>()
    private val selection = CompletableDeferred<Result<Unit>>()

    override suspend fun select(avatarId: String): Result<Unit> {
        selectedAvatarIds += avatarId
        return selection.await().onSuccess {
            mutableCurrentUser.value = mutableCurrentUser.value.copy(
                currentAvatarId = avatarId
            )
        }
    }

    fun completeSelection(result: Result<Unit>) {
        selection.complete(result)
    }
}

private class DeletionFavoriteEntrySource : FavoriteEntrySource {
    private val favorites = MutableStateFlow<Map<FavoriteGroupData, List<FavoriteData>>>(emptyMap())

    override fun favoritesByGroup(
        type: FavoriteType,
    ): StateFlow<Map<FavoriteGroupData, List<FavoriteData>>> = favorites

    override suspend fun load(type: FavoriteType): Result<Unit> = Result.success(Unit)
}

private fun authenticatedAccount(token: AccountSessionToken) = AuthenticatedAccount(
    account = AccountDto(userId = token.userId),
    token = token,
)

private fun deletionAvatar(
    id: String = "avtr_owned",
    authorId: String = "usr_owner",
    name: String = "Owned avatar",
    releaseStatus: String = "private",
) = AvatarData(
    id = id,
    name = name,
    authorId = authorId,
    authorName = "Owner",
    releaseStatus = releaseStatus,
)

private fun disposeDeletionViewModel(viewModel: ViewModel) {
    ViewModelStore().apply {
        put("deletion-test", viewModel)
        clear()
    }
}
