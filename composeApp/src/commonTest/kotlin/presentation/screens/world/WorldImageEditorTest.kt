package io.github.vrcmteam.vrcm.presentation.screens.world

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.service.data.AccountDto
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WorldImageEditorTest {
    @Test
    fun updateCarriesRenewedSessionAcrossAllStagesAndReturnsAuthoritativeRefresh() = runBlocking {
        val initial = AccountSessionToken("usr_owner", 1)
        val afterUpload = AccountSessionToken("usr_owner", 2)
        val afterAssignment = AccountSessionToken("usr_owner", 3)
        val authoritative = worldData(
            imageUrl = "https://cdn.example/authoritative.png",
            thumbnailImageUrl = "https://cdn.example/thumbnail.png",
        )
        val editor = RecordingWorldImageEditor(
            uploadResult = Result.success(
                SessionBoundValue("https://api.vrchat.cloud/api/1/file/file_image/2/file", afterUpload)
            ),
            assignmentResult = Result.success(SessionBoundValue(Unit, afterAssignment)),
            refreshResult = Result.success(WorldImageUpdate(authoritative, afterAssignment)),
        )

        val result = editor.updateImage(initial, authoritative.id, testImage()).getOrThrow()

        assertEquals(authoritative, result.world)
        assertEquals(listOf(initial), editor.uploadTokens)
        assertEquals(listOf(afterUpload), editor.assignmentTokens)
        assertEquals(listOf(afterAssignment), editor.refreshTokens)
    }

    @Test
    fun uploadFailureStopsBeforeWorldMutation() = runBlocking {
        val editor = RecordingWorldImageEditor(
            uploadResult = Result.failure(IllegalStateException("upload")),
            assignmentResult = Result.success(
                SessionBoundValue(Unit, AccountSessionToken("usr_owner", 1))
            ),
            refreshResult = Result.success(
                WorldImageUpdate(worldData(), AccountSessionToken("usr_owner", 1))
            ),
        )

        val result = editor.updateImage(
            AccountSessionToken("usr_owner", 1),
            "wrld_owned",
            testImage(),
        )

        assertIs<WorldImageUpdateFailure.Upload>(result.exceptionOrNull())
        assertTrue(editor.assignmentTokens.isEmpty())
        assertTrue(editor.refreshTokens.isEmpty())
    }

    @Test
    fun assignmentFailureDoesNotPretendTheUploadedFileChangedTheWorld() = runBlocking {
        val token = AccountSessionToken("usr_owner", 1)
        val editor = RecordingWorldImageEditor(
            uploadResult = Result.success(
                SessionBoundValue("https://api.vrchat.cloud/api/1/file/file_image/2/file", token)
            ),
            assignmentResult = Result.failure(IllegalStateException("assignment")),
            refreshResult = Result.success(WorldImageUpdate(worldData(), token)),
        )

        val result = editor.updateImage(token, "wrld_owned", testImage())

        assertIs<WorldImageUpdateFailure.Assignment>(result.exceptionOrNull())
        assertTrue(editor.refreshTokens.isEmpty())
    }

    @Test
    fun resultGateRejectsOldSessionGenerationEvenForTheSameUser() {
        val currentToken = AccountSessionToken("usr_owner", 8)
        val world = worldData()
        val profile = WorldProfileVo(world)
        val currentSession = AuthenticatedAccount(
            account = AccountDto(userId = "usr_owner"),
            token = currentToken,
        )

        assertTrue(
            isCurrentWorldImageUpdate(
                profile,
                currentSession,
                WorldImageUpdate(world, currentToken),
            )
        )
        assertFalse(
            isCurrentWorldImageUpdate(
                profile,
                currentSession,
                WorldImageUpdate(world, currentToken.copy(generation = 7)),
            )
        )
        assertFalse(
            isCurrentWorldImageUpdate(
                profile,
                currentSession,
                WorldImageUpdate(world.copy(id = "wrld_other"), currentToken),
            )
        )
    }

    @Test
    fun extensionCannotDisguiseAnotherWorldImageFormat() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())

        assertEquals(
            WorldImageValidation.UnsupportedFormat,
            validateWorldImage("world.png", jpeg),
        )
    }
}

private class RecordingWorldImageEditor(
    private val uploadResult: Result<SessionBoundValue<String>>,
    private val assignmentResult: Result<SessionBoundValue<Unit>>,
    private val refreshResult: Result<WorldImageUpdate>,
) : WorldImageEditor {
    val uploadTokens = mutableListOf<AccountSessionToken>()
    val assignmentTokens = mutableListOf<AccountSessionToken>()
    val refreshTokens = mutableListOf<AccountSessionToken>()

    override suspend fun uploadImage(
        sessionToken: AccountSessionToken,
        image: WorldImageFile,
    ): Result<SessionBoundValue<String>> {
        uploadTokens += sessionToken
        return uploadResult
    }

    override suspend fun assignImage(
        sessionToken: AccountSessionToken,
        worldId: String,
        imageUrl: String,
    ): Result<SessionBoundValue<Unit>> {
        assignmentTokens += sessionToken
        return assignmentResult
    }

    override suspend fun refreshWorld(
        sessionToken: AccountSessionToken,
        worldId: String,
    ): Result<WorldImageUpdate> {
        refreshTokens += sessionToken
        return refreshResult
    }
}

private fun testImage() = WorldImageFile(
    bytes = byteArrayOf(1, 2, 3),
    fileName = "world.png",
    mimeType = "image/png",
)

private fun worldData(
    imageUrl: String = "https://cdn.example/original.png",
    thumbnailImageUrl: String? = "https://cdn.example/thumbnail.png",
) = WorldData(
    authorId = "usr_owner",
    authorName = "Owner",
    capacity = 32,
    createdAt = "2026-01-01T00:00:00Z",
    description = "World",
    favorites = 1,
    featured = false,
    heat = 1,
    id = "wrld_owned",
    imageUrl = imageUrl,
    labsPublicationDate = "",
    name = "Owned World",
    namespace = null,
    organization = "vrchat",
    popularity = 1,
    publicationDate = "",
    recommendedCapacity = 16,
    releaseStatus = "private",
    tags = emptyList(),
    thumbnailImageUrl = thumbnailImageUrl,
    udonProducts = emptyList(),
    unityPackages = emptyList(),
    updatedAt = "2026-01-02T00:00:00Z",
    version = 2,
    visits = 1,
)
