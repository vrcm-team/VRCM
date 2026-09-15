package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.network.api.groups.data.LimitedGroup
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SearchItemRendererLongClickTest {
    @Test
    fun worldAvatarAndGroupRowsForwardLongClicks() = runComposeUiTest {
        var worldLongClicks = 0
        var avatarLongClicks = 0
        var groupLongClicks = 0
        setContent {
            LazyColumn {
                renderWorldItems(
                    worlds = listOf(hiddenWorld()),
                    onWorldLongClick = { worldLongClicks++ },
                ) { _, _ -> }
                renderAvatarItems(
                    avatars = listOf(
                        AvatarData(
                            id = "avtr_test",
                            name = "Avatar row",
                            releaseStatus = "hidden",
                        )
                    ),
                    onAvatarLongClick = { avatarLongClicks++ },
                ) { _, _ -> }
                renderGroupItems(
                    groups = listOf(LimitedGroup(id = "grp_test", name = "Group row")),
                    onGroupLongClick = { groupLongClicks++ },
                ) { _, _ -> }
            }
        }

        onNodeWithText("fvrt_world").performTouchInput { longClick() }
        onNodeWithText("avtr_test").performTouchInput { longClick() }
        onNodeWithText("Group row").performTouchInput { longClick() }

        assertEquals(1, worldLongClicks)
        assertEquals(1, avatarLongClicks)
        assertEquals(1, groupLongClicks)
    }

    private fun hiddenWorld() = WorldData(
        authorId = "",
        authorName = "",
        capacity = 0,
        createdAt = null,
        description = null,
        favorites = null,
        featured = null,
        heat = 0,
        id = "???",
        favoriteId = "fvrt_world",
        imageUrl = "",
        labsPublicationDate = "",
        name = "Hidden world",
        namespace = null,
        organization = "",
        popularity = 0,
        publicationDate = "",
        recommendedCapacity = 0,
        releaseStatus = "hidden",
        tags = emptyList(),
        thumbnailImageUrl = null,
        udonProducts = emptyList(),
        unityPackages = emptyList(),
        updatedAt = null,
        version = null,
        visits = null,
    )
}
