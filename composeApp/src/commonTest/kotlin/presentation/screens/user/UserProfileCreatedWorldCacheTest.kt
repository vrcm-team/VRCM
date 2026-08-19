package io.github.vrcmteam.vrcm.presentation.screens.user

import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.storage.data.WorldDetailRevision
import kotlin.test.Test
import kotlin.test.assertEquals

class UserProfileCreatedWorldCacheTest {
    @Test
    fun unchangedWorldKeepsCachedDescriptionWithoutDetailRefresh() {
        val cached = world(id = "wrld_same", description = "Cached description", updatedAt = "2026-08-01", version = 2)
        val summary = cached.copy(description = null, visits = 200)

        val plan = planCreatedWorldRefresh(
            summaries = listOf(summary),
            cachedWorlds = listOf(cached),
            cachedDetailRevisions = mapOf(cached.id to cached.detailRevision()),
        )

        assertEquals("Cached description", plan.worlds.single().description)
        assertEquals(200, plan.worlds.single().visits)
        assertEquals(emptyList(), plan.worldIdsToRefresh)
    }

    @Test
    fun changedAndNewWorldsRefreshIncrementallyWithoutBlankingCachedDescription() {
        val unchanged = world(id = "wrld_same", description = "Same", updatedAt = "2026-08-01", version = 1)
        val changed = world(id = "wrld_changed", description = "Stale", updatedAt = "2026-08-01", version = 1)
        val removed = world(id = "wrld_removed", description = "Removed", updatedAt = "2026-08-01", version = 1)
        val summaries = listOf(
            unchanged.copy(description = null),
            changed.copy(description = null, updatedAt = "2026-08-02", version = 2),
            world(id = "wrld_new", description = null, updatedAt = "2026-08-02", version = 1),
        )

        val plan = planCreatedWorldRefresh(
            summaries = summaries,
            cachedWorlds = listOf(unchanged, changed, removed),
            cachedDetailRevisions = listOf(unchanged, changed, removed)
                .associate { it.id to it.detailRevision() },
        )

        assertEquals(listOf("wrld_same", "wrld_changed", "wrld_new"), plan.worlds.map(WorldData::id))
        assertEquals("Stale", plan.worlds[1].description)
        assertEquals(listOf("wrld_changed", "wrld_new"), plan.worldIdsToRefresh)
        assertEquals(setOf("wrld_same", "wrld_changed"), plan.detailRevisions.keys)
    }

    @Test
    fun failedDetailRequestKeepsOldRevisionSoNextRefreshRetriesIt() {
        val cached = world(id = "wrld_changed", description = "Stale", updatedAt = "2026-08-01", version = 1)
        val summary = cached.copy(description = null, updatedAt = "2026-08-02", version = 2)
        val oldRevision = cached.detailRevision()
        val plan = planCreatedWorldRefresh(
            summaries = listOf(summary),
            cachedWorlds = listOf(cached),
            cachedDetailRevisions = mapOf(cached.id to oldRevision),
        )

        val failedResult = mergeCreatedWorldDetails(plan, refreshedWorlds = emptyList())
        val successfulResult = mergeCreatedWorldDetails(
            plan,
            refreshedWorlds = listOf(summary.copy(description = "Fresh")),
        )

        assertEquals("Stale", failedResult.worlds.single().description)
        assertEquals(oldRevision, failedResult.detailRevisions.getValue(cached.id))
        assertEquals("Fresh", successfulResult.worlds.single().description)
        assertEquals(WorldDetailRevision("2026-08-02", 2), successfulResult.detailRevisions.getValue(cached.id))
    }

    private fun world(
        id: String,
        description: String?,
        updatedAt: String?,
        version: Int?,
    ) = WorldData(
        authorId = "usr_author",
        authorName = "Author",
        capacity = 16,
        createdAt = "2026-01-01",
        description = description,
        favorites = 10,
        featured = false,
        heat = 0,
        id = id,
        imageUrl = "https://example.com/$id.png",
        labsPublicationDate = "none",
        name = id,
        namespace = null,
        organization = "vrchat",
        popularity = 1,
        publicationDate = "2026-01-01",
        recommendedCapacity = 8,
        releaseStatus = "public",
        tags = emptyList(),
        thumbnailImageUrl = null,
        udonProducts = emptyList(),
        unityPackages = emptyList(),
        updatedAt = updatedAt,
        version = version,
        visits = 100,
    )
}
