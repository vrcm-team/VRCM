package io.github.vrcmteam.vrcm.storage

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.vrcmteam.vrcm.core.shared.AuthenticationSessionRegistry
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.service.data.AccountDto
import io.github.vrcmteam.vrcm.storage.data.WorldProfileCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class RoomWorldProfileCacheStoreTest {
    @Test
    fun sessionInvalidatedBeforeStateCommitRestoresPreviousWorldCache() = withStore { store ->
        store.save(WorldProfileCache(worldData("old.png"), cachedAtEpochMilliseconds = 1L))
        val sessions = AuthenticationSessionRegistry()
        val token = sessions.authenticate(AccountDto(userId = "usr_owner")).token
        var displayedImage = "old.png"

        val committed = store.saveAndCommitIfCurrent(
            WorldProfileCache(worldData("new.png"), cachedAtEpochMilliseconds = 2L),
            canStart = { sessions.isCurrent(token) },
            commit = {
                sessions.invalidate()
                sessions.commitIfCurrent(token) {
                    displayedImage = "new.png"
                    true
                }
            },
        )

        assertFalse(committed)
        assertEquals("old.png", displayedImage)
        assertEquals("old.png", store.load("wrld_cached")?.world?.imageUrl)
    }

    @Test
    fun invalidatedSessionDoesNotLeaveNewWorldCache() = withStore { store ->
        val committed = store.saveAndCommitIfCurrent(
            WorldProfileCache(worldData("new.png"), cachedAtEpochMilliseconds = 1L),
            canStart = { true },
            commit = { false },
        )

        assertFalse(committed)
        assertNull(store.load("wrld_cached"))
    }

    private fun withStore(block: suspend (WorldProfileCacheStore) -> Unit) = runTest {
        val database = Room.inMemoryDatabaseBuilder<VrcmDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        try {
            block(
                RoomWorldProfileCacheStore(
                    dao = database.cachedBlobDao(),
                    nowMillis = { 1L },
                ),
            )
        } finally {
            database.close()
        }
    }

    private fun worldData(imageUrl: String) = WorldData(
        authorId = "usr_owner",
        authorName = "Owner",
        capacity = 32,
        createdAt = "2026-01-01T00:00:00Z",
        description = "World",
        favorites = 1,
        featured = false,
        heat = 1,
        id = "wrld_cached",
        imageUrl = imageUrl,
        labsPublicationDate = "",
        name = "Cached World",
        namespace = null,
        organization = "vrchat",
        popularity = 1,
        publicationDate = "",
        recommendedCapacity = 16,
        releaseStatus = "private",
        tags = emptyList(),
        thumbnailImageUrl = "$imageUrl.thumb",
        udonProducts = emptyList(),
        unityPackages = emptyList(),
        updatedAt = "2026-01-02T00:00:00Z",
        version = 2,
        visits = 1,
    )
}
