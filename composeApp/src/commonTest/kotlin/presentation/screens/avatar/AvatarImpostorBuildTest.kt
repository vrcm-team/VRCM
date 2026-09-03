package io.github.vrcmteam.vrcm.presentation.screens.avatar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarImpostorQueueStats
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarImpostorServiceStatus
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarUnityPackage
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteData
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteGroupData
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.presentation.favorites.FavoriteEntrySource
import io.github.vrcmteam.vrcm.presentation.screens.avatar.data.AvatarProfileVo
import io.github.vrcmteam.vrcm.service.SessionBoundResponse
import io.github.vrcmteam.vrcm.service.data.AccountDto
import io.github.vrcmteam.vrcm.testing.MainDispatcherTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AvatarImpostorBuildTest : MainDispatcherTest() {
    private val models = mutableListOf<ViewModel>()

    @AfterTest
    fun disposeModels() {
        models.forEach(::clearModel)
    }

    @Test
    fun ownedAvatarEnqueuesOnceAndShowsServerStatusAndEstimate() = runBlocking {
        val initial = token("usr_owner", 1)
        val renewed = token("usr_owner", 2)
        val session = MutableStateFlow(authenticated(initial))
        val selector = SessionAvatarSelector("usr_owner")
        val builder = ControlledImpostorBuilder(session)
        val model = model(session, selector, builder, avatar(hasImpostor = false))

        model.enqueueImpostor()
        model.enqueueImpostor()
        yield()

        assertEquals(listOf("avtr_owned"), builder.enqueuedAvatarIds)
        assertTrue(model.impostorState.value.isSubmitting)
        assertFalse(model.impostorState.value.canBuild)

        session.value = authenticated(renewed)
        builder.completeEnqueue(
            renewed,
            Result.success(serviceStatus(subjectId = "avtr_owned", requesterUserId = "usr_owner")),
        )
        yield()

        assertEquals("queued", model.impostorState.value.taskState)
        assertTrue(model.impostorState.value.isLoadingQueueEstimate)
        assertEquals(1, builder.queueStatsRequests)

        builder.completeQueueStats(
            renewed,
            Result.success(AvatarImpostorQueueStats(estimatedServiceDurationSeconds = 125)),
        )
        yield()

        assertEquals(125, model.impostorState.value.estimatedQueueSeconds)
        assertFalse(model.impostorState.value.isLoadingQueueEstimate)
        assertFalse(model.impostorState.value.canBuild)
        assertNull(model.impostorState.value.failure)
    }

    @Test
    fun queueStatsRenewalKeepsOperationLockedUntilResponseBindsNewSession() = runBlocking {
        val ownerToken = token("usr_owner", 1)
        val renewedToken = token("usr_owner", 2)
        val session = MutableStateFlow(authenticated(ownerToken))
        val selector = SessionAvatarSelector("usr_owner")
        val builder = ControlledImpostorBuilder(session)
        val model = model(session, selector, builder, avatar(hasImpostor = false))

        model.enqueueImpostor()
        yield()
        builder.completeEnqueue(
            ownerToken,
            Result.success(serviceStatus(subjectId = "avtr_owned", requesterUserId = "usr_owner")),
        )
        yield()

        assertTrue(model.impostorState.value.isLoadingQueueEstimate)
        session.value = authenticated(renewedToken)
        yield()

        assertTrue(model.impostorState.value.isLoadingQueueEstimate)
        assertFalse(model.impostorState.value.canBuild)

        builder.completeQueueStats(
            renewedToken,
            Result.failure(VRCApiException("Unauthorized", 401, "expired")),
        )
        yield()

        assertTrue(model.impostorState.value.queueEstimateFailed)
        assertFalse(model.impostorState.value.canBuild)
    }

    @Test
    fun impostorPackageUsesRebuildStateAndIsNotShownAsAPlayablePlatform() = runBlocking {
        val data = avatar(hasImpostor = true)
        val profile = AvatarProfileVo(data)
        val linkedProfile = AvatarProfileVo(
            avatar(hasImpostor = false).copy(
                unityPackages = listOf(
                    AvatarUnityPackage(
                        platform = "standalonewindows",
                        impostorUrl = "https://api.vrchat.cloud/api/1/file/impostor",
                    )
                )
            )
        )

        assertTrue(profile.hasImpostor)
        assertTrue(linkedProfile.hasImpostor)
        assertEquals(listOf("standalonewindows"), profile.platformInfos.map { it.platform })
    }

    @Test
    fun nonOwnerCannotSubmitAnImpostorTask() = runBlocking {
        val session = MutableStateFlow(authenticated(token("usr_viewer", 1)))
        val selector = SessionAvatarSelector("usr_viewer")
        val builder = ControlledImpostorBuilder(session)
        val model = model(session, selector, builder, avatar(hasImpostor = false))

        model.enqueueImpostor()
        yield()

        assertFalse(model.impostorState.value.canBuild)
        assertTrue(builder.enqueuedAvatarIds.isEmpty())
    }

    @Test
    fun accountSwitchDropsLateEnqueueResponse() = runBlocking {
        val ownerToken = token("usr_owner", 1)
        val session = MutableStateFlow(authenticated(ownerToken))
        val selector = SessionAvatarSelector("usr_owner")
        val builder = ControlledImpostorBuilder(session)
        val model = model(session, selector, builder, avatar(hasImpostor = false))

        model.enqueueImpostor()
        yield()
        val otherToken = token("usr_other", 2)
        session.value = authenticated(otherToken)
        selector.switchUser("usr_other")
        builder.completeEnqueue(
            ownerToken,
            Result.success(serviceStatus(subjectId = "avtr_owned", requesterUserId = "usr_owner")),
        )
        yield()

        assertNull(model.impostorState.value.taskState)
        assertFalse(model.impostorState.value.isSubmitting)
        assertEquals(0, builder.queueStatsRequests)
    }

    @Test
    fun mismatchedServiceResponseIsRejectedWithoutLoadingQueueStats() = runBlocking {
        val ownerToken = token("usr_owner", 1)
        val session = MutableStateFlow(authenticated(ownerToken))
        val selector = SessionAvatarSelector("usr_owner")
        val builder = ControlledImpostorBuilder(session)
        val model = model(session, selector, builder, avatar(hasImpostor = false))

        model.enqueueImpostor()
        yield()
        builder.completeEnqueue(
            ownerToken,
            Result.success(serviceStatus(subjectId = "avtr_other", requesterUserId = "usr_owner")),
        )
        yield()

        assertEquals(AvatarImpostorFailure.InvalidResponse, model.impostorState.value.failure)
        assertNull(model.impostorState.value.taskState)
        assertTrue(model.impostorState.value.canBuild)
        assertEquals(0, builder.queueStatsRequests)
    }

    private suspend fun model(
        session: StateFlow<AuthenticatedAccount?>,
        selector: SessionAvatarSelector,
        builder: AvatarImpostorBuilder,
        avatar: AvatarData,
    ): AvatarProfileScreenModel {
        val model = AvatarProfileScreenModel(
            avatarProfileLoader = AvatarProfileLoader { Result.success(avatar) },
            avatarSelector = selector,
            favoriteEntrySource = EmptyFavoriteSource,
            requestDispatcher = Dispatchers.Unconfined,
            favoriteSession = session,
            avatarImpostorDeletionSource = EmptyDeletionSourceForBuildTest,
            avatarImpostorBuilder = builder,
        ).also(models::add)
        model.refreshAvatarData(AvatarProfileVo(avatarId = avatar.id))
        yield()
        assertTrue(model.impostorState.value.hasImpostor == AvatarProfileVo(avatar).hasImpostor)
        return model
    }
}

private class ControlledImpostorBuilder(
    private val session: StateFlow<AuthenticatedAccount?>,
) : AvatarImpostorBuilder {
    private val enqueueResponse =
        CompletableDeferred<AuthenticatedAvatarImpostorResult<AvatarImpostorServiceStatus>?>()
    private val queueStatsResponse =
        CompletableDeferred<AuthenticatedAvatarImpostorResult<AvatarImpostorQueueStats>?>()
    val enqueuedAvatarIds = mutableListOf<String>()
    var queueStatsRequests = 0
        private set

    override suspend fun enqueue(
        sessionToken: AccountSessionToken,
        avatarId: String,
    ): AuthenticatedAvatarImpostorResult<AvatarImpostorServiceStatus>? {
        enqueuedAvatarIds += avatarId
        return enqueueResponse.await()
    }

    override suspend fun queueStats(
        sessionToken: AccountSessionToken,
    ): AuthenticatedAvatarImpostorResult<AvatarImpostorQueueStats>? {
        queueStatsRequests++
        return queueStatsResponse.await()
    }

    override fun isCurrentSession(sessionToken: AccountSessionToken): Boolean =
        session.value?.token == sessionToken

    fun completeEnqueue(
        token: AccountSessionToken,
        result: Result<AvatarImpostorServiceStatus>,
    ) {
        enqueueResponse.complete(AuthenticatedAvatarImpostorResult(result, token))
    }

    fun completeQueueStats(
        token: AccountSessionToken,
        result: Result<AvatarImpostorQueueStats>,
    ) {
        queueStatsResponse.complete(AuthenticatedAvatarImpostorResult(result, token))
    }
}

private class SessionAvatarSelector(userId: String) : AvatarSelector {
    private val user = MutableStateFlow(
        AvatarUserContext(userId = userId, currentAvatarId = "avtr_current")
    )
    override val currentUser: StateFlow<AvatarUserContext?> = user

    override suspend fun select(avatarId: String): Result<Unit> = Result.success(Unit)

    fun switchUser(userId: String) {
        user.value = AvatarUserContext(userId, "avtr_other")
    }
}

private data object EmptyFavoriteSource : FavoriteEntrySource {
    private val favorites = MutableStateFlow<Map<FavoriteGroupData, List<FavoriteData>>>(emptyMap())

    override fun favoritesByGroup(
        type: FavoriteType,
    ): StateFlow<Map<FavoriteGroupData, List<FavoriteData>>> = favorites

    override suspend fun load(type: FavoriteType): Result<Unit> = Result.success(Unit)
}

private data object EmptyDeletionSourceForBuildTest : AvatarImpostorDeletionSource {
    override suspend fun delete(
        sessionToken: AccountSessionToken,
        avatarId: String,
    ): SessionBoundResponse<Unit>? = null

    override suspend fun load(
        sessionToken: AccountSessionToken,
        avatarId: String,
    ): SessionBoundResponse<AvatarData>? = null
}

private fun avatar(hasImpostor: Boolean): AvatarData = AvatarData(
    id = "avtr_owned",
    name = "Owned",
    authorId = "usr_owner",
    releaseStatus = "private",
    unityPackages = buildList {
        add(AvatarUnityPackage(platform = "standalonewindows"))
        if (hasImpostor) {
            add(
                AvatarUnityPackage(
                    platform = "standalonewindows",
                    variant = "impostor",
                    impostorizerVersion = "1.0.0",
                )
            )
        }
    },
)

private fun serviceStatus(
    subjectId: String,
    requesterUserId: String,
) = AvatarImpostorServiceStatus(
    createdAt = "2026-09-03T00:00:00Z",
    id = "service_1",
    requesterUserId = requesterUserId,
    state = "queued",
    subjectId = subjectId,
    subjectType = "avatar",
    type = "avatar-impostor",
    updatedAt = "2026-09-03T00:00:00Z",
)

private fun token(userId: String, generation: Long) = AccountSessionToken(userId, generation)

private fun authenticated(token: AccountSessionToken) = AuthenticatedAccount(
    account = AccountDto(userId = token.userId),
    token = token,
)

private fun clearModel(viewModel: ViewModel) {
    ViewModelStore().apply {
        put("test", viewModel)
        clear()
    }
}
