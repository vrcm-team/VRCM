package io.github.vrcmteam.vrcm.presentation.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import io.github.vrcmteam.vrcm.presentation.screens.auth.AuthAnimeScreen
import io.github.vrcmteam.vrcm.presentation.screens.auth.AuthScreen
import io.github.vrcmteam.vrcm.presentation.screens.auth.StartupAnimeScreen
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.avatar.data.AvatarProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.gallery.GalleryScreen
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PrintImageEditorScreen
import io.github.vrcmteam.vrcm.presentation.screens.group.GroupProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.group.data.GroupProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.home.HomeScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.CardListDetailScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.CardScreenType
import io.github.vrcmteam.vrcm.presentation.screens.user.FriendNetworkScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.MutualFriendsScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.world.RecentWorldsScreen
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class AppRoutePanePolicyTest {
    @Test
    fun homeIsTheListPane() {
        assertEquals(AppRoutePane.List, HomeScreen.pane)
    }

    @Test
    fun profileAndDrillDownRoutesStayInTheDetailPane() {
        val routes = listOf(
            UserProfileScreen(UserProfileVo(id = "usr_test")),
            WorldProfileScreen(WorldProfileVo(worldId = "wrld_test")),
            GroupProfileScreen(GroupProfileVo(groupId = "grp_test")),
            AvatarProfileScreen(AvatarProfileVo(avatarId = "avtr_test")),
            MutualFriendsScreen(userId = "usr_test", userName = "Test User"),
            RecentWorldsScreen,
            CardListDetailScreen(
                title = "Worlds",
                items = emptyList(),
                sectionKey = "worlds",
                screenType = CardScreenType.WORLD,
            ),
        )

        assertEquals(List(routes.size) { AppRoutePane.Detail }, routes.map(AppRoute::pane))
    }

    @Test
    fun independentTasksUseTheFullWindow() {
        val routes = listOf(
            GalleryScreen,
            PrintImageEditorScreen(sessionId = "session_test"),
            FriendNetworkScreen,
            StartupAnimeScreen,
            AuthScreen,
            AuthAnimeScreen(isAuthed = true),
        )

        assertEquals(List(routes.size) { AppRoutePane.FullWindow }, routes.map(AppRoute::pane))
    }

    @Test
    fun onlyListAndDetailRoutesParticipateInTheAdaptiveScene() {
        assertTrue(HomeScreen.adaptivePaneMetadata().isNotEmpty())
        assertTrue(
            UserProfileScreen(UserProfileVo(id = "usr_test"))
                .adaptivePaneMetadata()
                .isNotEmpty()
        )
        assertTrue(GalleryScreen.adaptivePaneMetadata().isEmpty())
        assertTrue(FriendNetworkScreen.adaptivePaneMetadata().isEmpty())
    }
}
