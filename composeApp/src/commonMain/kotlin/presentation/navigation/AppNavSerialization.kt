package io.github.vrcmteam.vrcm.presentation.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import io.github.vrcmteam.vrcm.presentation.screens.auth.AuthAnimeScreen
import io.github.vrcmteam.vrcm.presentation.screens.auth.AuthScreen
import io.github.vrcmteam.vrcm.presentation.screens.auth.StartupAnimeScreen
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.gallery.GalleryPickerScreen
import io.github.vrcmteam.vrcm.presentation.screens.gallery.GalleryScreen
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PrintImageEditorScreen
import io.github.vrcmteam.vrcm.presentation.screens.group.GroupProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.home.HomeScreen
import io.github.vrcmteam.vrcm.presentation.screens.meetup.MeetupCardDisplayRoute
import io.github.vrcmteam.vrcm.presentation.screens.meetup.MeetupCardEditorRoute
import io.github.vrcmteam.vrcm.presentation.screens.notification.NotificationScreen
import io.github.vrcmteam.vrcm.presentation.screens.settings.NotificationSettingsScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.CardListDetailScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.FriendNetworkScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.MutualFriendsScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.world.RecentWorldsScreen
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldProfileScreen
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

internal val appSavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(StartupAnimeScreen::class, StartupAnimeScreen.serializer())
            subclass(AuthScreen::class, AuthScreen.serializer())
            subclass(AuthAnimeScreen::class, AuthAnimeScreen.serializer())
            subclass(HomeScreen::class, HomeScreen.serializer())
            subclass(GalleryScreen::class, GalleryScreen.serializer())
            subclass(GalleryPickerScreen::class, GalleryPickerScreen.serializer())
            subclass(PrintImageEditorScreen::class, PrintImageEditorScreen.serializer())
            subclass(FriendNetworkScreen::class, FriendNetworkScreen.serializer())
            subclass(UserProfileScreen::class, UserProfileScreen.serializer())
            subclass(MutualFriendsScreen::class, MutualFriendsScreen.serializer())
            subclass(AvatarProfileScreen::class, AvatarProfileScreen.serializer())
            subclass(GroupProfileScreen::class, GroupProfileScreen.serializer())
            subclass(WorldProfileScreen::class, WorldProfileScreen.serializer())
            subclass(RecentWorldsScreen::class, RecentWorldsScreen.serializer())
            subclass(CardListDetailScreen::class, CardListDetailScreen.serializer())
            subclass(MeetupCardDisplayRoute::class, MeetupCardDisplayRoute.serializer())
            subclass(MeetupCardEditorRoute::class, MeetupCardEditorRoute.serializer())
            subclass(NotificationSettingsScreen::class, NotificationSettingsScreen.serializer())
            subclass(NotificationScreen::class, NotificationScreen.serializer())
        }
    }
}
