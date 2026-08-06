package io.github.vrcmteam.vrcm.presentation.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.SceneStrategyScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class AppListDetailSceneStrategyTest {
    @Test
    fun compactDefersToNavDisplayWhileExpandedMergesListAndDetail() {
        val entries = listOf(
            NavEntry(
                key = "list",
                metadata = AppListDetailSceneStrategy.listPane(sceneKey = "test-scene"),
            ) {},
            NavEntry(
                key = "detail",
                metadata = AppListDetailSceneStrategy.detailPane(sceneKey = "test-scene"),
            ) {},
        )

        val compactScene = calculateScene(entries, maxHorizontalPartitions = 1)
        val expandedScene = calculateScene(entries, maxHorizontalPartitions = 2)

        assertNull(compactScene)
        assertNotNull(expandedScene)
        assertEquals(listOf("list", "detail"), expandedScene.entries.map(NavEntry<String>::contentKey))
    }

    @Test
    fun expandedDefersToNavDisplayWhenTheSuffixHasOnlyDetail() {
        val entries = listOf(
            NavEntry(key = "full-window") {},
            NavEntry(
                key = "detail",
                metadata = AppListDetailSceneStrategy.detailPane(sceneKey = "test-scene"),
            ) {},
        )

        val scene = calculateScene(entries, maxHorizontalPartitions = 2)

        assertNull(scene)
    }

    @Test
    fun popLatestKeepsTheImmediatelyPreviousDetailEntry() {
        val entries = listOf(
            NavEntry(
                key = "list",
                metadata = AppListDetailSceneStrategy.listPane(sceneKey = "test-scene"),
            ) {},
            NavEntry(
                key = "detail-a",
                metadata = AppListDetailSceneStrategy.detailPane(sceneKey = "test-scene"),
            ) {},
            NavEntry(
                key = "detail-b",
                metadata = AppListDetailSceneStrategy.detailPane(sceneKey = "test-scene"),
            ) {},
        )

        val scene = assertNotNull(calculateScene(entries, maxHorizontalPartitions = 2))

        assertEquals(
            listOf("list", "detail-a"),
            scene.previousEntries.map(NavEntry<String>::contentKey),
        )
    }

    private fun calculateScene(
        entries: List<NavEntry<String>>,
        maxHorizontalPartitions: Int,
    ) = AppListDetailSceneStrategy<String>(
        backNavigationBehavior = BackNavigationBehavior.PopLatest,
        directive = PaneScaffoldDirective.Default.copy(
            maxHorizontalPartitions = maxHorizontalPartitions,
        ),
    ).run {
        SceneStrategyScope<String>().calculateScene(entries)
    }
}
