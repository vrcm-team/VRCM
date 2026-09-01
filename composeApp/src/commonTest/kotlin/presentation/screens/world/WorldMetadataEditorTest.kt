package io.github.vrcmteam.vrcm.presentation.screens.world

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldUpdateData
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.service.data.AccountDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WorldMetadataEditorTest {
    @Test
    fun clearingListsCreatesExplicitEmptyFieldsWithoutResendingUnchangedValues() {
        val current = ownedWorld(
            rawTags = listOf("author_tag_one"),
            allowedDomains = listOf("https://example.com"),
        )

        val change = worldMetadataChange(
            current = current,
            draft = draftFor(current).copy(tags = "", allowedDomains = ""),
        )

        assertEquals(
            WorldMetadataChange.Update(
                WorldUpdateData(tags = emptyList(), urlList = emptyList())
            ),
            change,
        )
    }

    @Test
    fun accountSwitchCancelsPendingSaveAndRejectsItsLateResponse() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val world = MutableStateFlow<WorldProfileVo?>(ownedWorld())
        val ready = MutableStateFlow(true)
        val session = MutableStateFlow<AuthenticatedAccount?>(authenticated("usr_owner", 1))
        val editor = ControlledWorldEditor()
        val accepted = mutableListOf<WorldData>()
        val model = WorldMetadataEditStateModel(
            editor = editor,
            scope = backgroundScope,
            world = world,
            metadataReady = ready,
            session = session,
            onAcceptedUpdate = { _, updated -> accepted += updated },
            dispatcher = dispatcher,
        )
        advanceUntilIdle()

        model.save(draftFor(requireNotNull(world.value)).copy(name = "After"))
        model.save(draftFor(requireNotNull(world.value)).copy(name = "Duplicate"))
        advanceUntilIdle()

        assertEquals(1, editor.requests.size)

        session.value = authenticated("usr_other", 2)
        advanceUntilIdle()

        assertFalse(model.state.value.canEdit)
        assertFalse(model.state.value.isSaving)
        editor.complete(
            WorldMetadataUpdateResponse(
                result = Result.success(worldData(name = "After")),
                sessionToken = AccountSessionToken("usr_owner", 1),
            )
        )

        assertTrue(accepted.isEmpty())
    }

    @Test
    fun sameAccountReauthenticationAcceptsResponseBoundToRefreshedSession() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val world = MutableStateFlow<WorldProfileVo?>(ownedWorld())
        val ready = MutableStateFlow(true)
        val session = MutableStateFlow<AuthenticatedAccount?>(authenticated("usr_owner", 1))
        val editor = ControlledWorldEditor()
        val accepted = mutableListOf<WorldData>()
        val model = WorldMetadataEditStateModel(
            editor = editor,
            scope = backgroundScope,
            world = world,
            metadataReady = ready,
            session = session,
            onAcceptedUpdate = { _, updated -> accepted += updated },
            dispatcher = dispatcher,
        )
        advanceUntilIdle()

        model.save(draftFor(requireNotNull(world.value)).copy(name = "After"))
        runCurrent()
        assertEquals(1, editor.requests.size)
        session.value = authenticated("usr_owner", 2)
        runCurrent()
        editor.complete(
            WorldMetadataUpdateResponse(
                result = Result.success(worldData(name = "After")),
                sessionToken = AccountSessionToken("usr_owner", 2),
            )
        )
        advanceUntilIdle()

        assertEquals(listOf("After"), accepted.map(WorldData::name))
    }

    @Test
    fun invalidRecommendedCapacityIsRejectedBeforeStartingARequest() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val current = ownedWorld()
        val editor = ControlledWorldEditor()
        val model = WorldMetadataEditStateModel(
            editor = editor,
            scope = backgroundScope,
            world = MutableStateFlow(current),
            metadataReady = MutableStateFlow(true),
            session = MutableStateFlow(authenticated("usr_owner", 1)),
            onAcceptedUpdate = { _, _ -> },
            dispatcher = dispatcher,
        )
        val notices = mutableListOf<WorldMetadataEditNotice>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            model.notices.collect(notices::add)
        }
        advanceUntilIdle()

        model.save(draftFor(current).copy(capacity = "10", recommendedCapacity = "11"))
        runCurrent()

        assertTrue(editor.requests.isEmpty())
        assertIs<WorldMetadataEditNotice.InvalidRecommendedCapacity>(notices.single())
    }

    private fun ownedWorld(
        rawTags: List<String> = listOf("author_tag_original"),
        allowedDomains: List<String> = listOf("https://original.example"),
    ) = WorldProfileVo(
        worldId = "wrld_owned",
        worldName = "Before",
        worldDescription = "Description",
        authorID = "usr_owner",
        capacity = 16,
        recommendedCapacity = 8,
        rawTags = rawTags,
        allowedDomains = allowedDomains,
    )

    private fun draftFor(world: WorldProfileVo) = WorldMetadataDraft(
        name = world.worldName,
        description = world.worldDescription,
        capacity = world.capacity.toString(),
        recommendedCapacity = world.recommendedCapacity.toString(),
        tags = world.rawTags.joinToString("\n"),
        allowedDomains = world.allowedDomains.joinToString("\n"),
    )

    private fun authenticated(userId: String, generation: Long) = AuthenticatedAccount(
        account = AccountDto(userId = userId),
        token = AccountSessionToken(userId, generation),
    )

    private fun worldData(name: String) = WorldData(
        authorId = "usr_owner",
        authorName = "Owner",
        capacity = 16,
        createdAt = null,
        description = "Description",
        favorites = 1,
        featured = false,
        heat = 0,
        id = "wrld_owned",
        imageUrl = "https://example.com/world.png",
        labsPublicationDate = "none",
        name = name,
        namespace = null,
        organization = "vrchat",
        popularity = 0,
        publicationDate = "none",
        recommendedCapacity = 8,
        releaseStatus = "private",
        tags = listOf("author_tag_original"),
        thumbnailImageUrl = null,
        udonProducts = emptyList(),
        unityPackages = emptyList(),
        updatedAt = null,
        version = 2,
        visits = 1,
        urlList = listOf("https://original.example"),
    )
}

private class ControlledWorldEditor : WorldEditor {
    data class Request(
        val sessionToken: AccountSessionToken,
        val worldId: String,
        val update: WorldUpdateData,
    )

    val requests = mutableListOf<Request>()
    private val result = CompletableDeferred<WorldMetadataUpdateResponse?>()

    override suspend fun updateMetadata(
        sessionToken: AccountSessionToken,
        worldId: String,
        update: WorldUpdateData,
    ): WorldMetadataUpdateResponse? {
        requests += Request(sessionToken, worldId, update)
        return result.await()
    }

    fun complete(response: WorldMetadataUpdateResponse?) {
        result.complete(response)
    }
}
