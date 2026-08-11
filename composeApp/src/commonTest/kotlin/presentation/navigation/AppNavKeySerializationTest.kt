package io.github.vrcmteam.vrcm.presentation.navigation

import androidx.navigation3.runtime.NavKey
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
import io.github.vrcmteam.vrcm.presentation.screens.meetup.MeetupCardDisplayRoute
import io.github.vrcmteam.vrcm.presentation.screens.meetup.MeetupCardEditorRoute
import io.github.vrcmteam.vrcm.presentation.screens.notification.NotificationScreen
import io.github.vrcmteam.vrcm.presentation.screens.settings.NotificationSettingsScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.CardListDetailScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.CardScreenType
import io.github.vrcmteam.vrcm.presentation.screens.user.FriendNetworkScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.MutualFriendsScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.world.RecentWorldsScreen
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.test.Test
import kotlin.test.assertNotNull

@OptIn(ExperimentalSerializationApi::class)
class AppNavKeySerializationTest {
    @Test
    fun everyAppRouteHasAPolymorphicNavKeySerializer() {
        val routes = listOf(
            StartupAnimeScreen,
            AuthScreen,
            AuthAnimeScreen(isAuthed = true),
            HomeScreen,
            GalleryScreen,
            PrintImageEditorScreen(sessionId = "session_test"),
            FriendNetworkScreen,
            UserProfileScreen(UserProfileVo(id = "usr_test")),
            MutualFriendsScreen(userId = "usr_test", userName = "Test User"),
            AvatarProfileScreen(AvatarProfileVo(avatarId = "avtr_test")),
            GroupProfileScreen(GroupProfileVo(groupId = "grp_test")),
            WorldProfileScreen(WorldProfileVo(worldId = "wrld_test")),
            RecentWorldsScreen,
            CardListDetailScreen(
                title = "Worlds",
                items = emptyList(),
                sectionKey = "worlds",
                screenType = CardScreenType.WORLD,
            ),
            MeetupCardDisplayRoute(ownerUserId = "usr_test"),
            MeetupCardEditorRoute(ownerUserId = "usr_test"),
            NotificationSettingsScreen,
            NotificationScreen(targetNotificationId = "notification_test"),
        )

        routes.forEach { route ->
            assertNotNull(
                appSavedStateConfiguration.serializersModule.getPolymorphic(NavKey::class, route),
                "Missing NavKey serializer for ${route::class.qualifiedName}",
            )
        }
    }
}
