package io.github.vrcmteam.vrcm.presentation.settings.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import io.github.vrcmteam.vrcm.presentation.settings.LocalSettingsState

@Stable
data class LocaleStrings(
    val startupDialogTitle: String = "New version available",
    val startupDialogUpdate: String = "Update",
    val startupDialogIgnore: String = "Ignore",
    val startupDialogRememberVersion: String = "Ignore This Version Update",
    val authLoginTitle: String = "Login",
    val authLoginButton: String = "LOGIN",
    val authLoginUsername: String = "Username",
    val authLoginPassword: String = "Password",
    val authVerifyTitle: String = "Verify",
    val authVerifyButton: String = "VERIFY",
    val authCurrent: String = "Current",
    val fiendLocationPagerWebsite: String = "Active on the Website",
    val fiendLocationPagerPrivate: String = "Friends in Private Worlds",
    val fiendLocationPagerTraveling: String = "Friends is Traveling",
    val fiendLocationPagerLocation: String = "by Location",
    val fiendListPagerSearch: String = "Search",
    val notificationFriendRequest: String = "wants to be your friend",
    val homeNotificationEmpty: String = "No Notification Yet",
    val homeStatusEdit: String = "Edit Status",
    val homeUpdateStatus: String = "Update Status",
    val stettingLanguage: String = "Language",
    val stettingThemeMode: String = "ThemeMode",
    val stettingSystemThemeMode: String = "System",
    val stettingLightThemeMode: String = "Light",
    val stettingDarkThemeMode: String = "Dark",
    val stettingThemeColor: String = "ThemeColor",
    val stettingLogout: String = "Logout",
    val stettingAbout: String = "About Application",
    val stettingVersion: String = "Version",
    val stettingClearCache: String = "Clear Cache",
    val stettingAlreadyLatest: String = "Already Latest",
    val profileFriendRequestSent: String = "Friend Request Sent",
    val profileSendFriendRequest: String = "Send Friend Request",
    val profileFriendRequestDeleted: String = "Friend Request Deleted",
    val profileDeleteFriendRequest: String = "Delete Friend Request",
    val profileAcceptFriendRequest: String = "Accept Friend Request",
    val profileFriendRequestAccepted: String = "Friend Request Accepted",
    val profileUnfriended: String = "Friend is Unfriended",
    val profileUnfriend: String = "Unfriend",
    val profileViewJsonData: String = "View JSON data",
    val profileViewGallery: String = "View Media Gallery",
    val locationDialogOwner: String = "Owner",
    val locationDialogAuthor: String = "Author",
    val locationDialogDescription: String = "Description",
    val locationDialogTags: String = "Tags",
    val locationInvited: String = "Invited",
    val locationInviteMe: String = "Invite Me",
    val createInstance: String = "Create Instance",
    val accessType: String = "Access Type",
    val regionType: String = "Region",
    val confirm: String = "Confirm",
    val cancel: String = "Cancel",
    val favoriteWorld: String = "Favorite World",
    val favoriteAddSuccess: String = "World has been favorited",
    val favoriteAlreadyExists: String = "World is already in your favorites",
    val favoriteGroupSelect: String = "Select favorite group",
    val favoriteFailed: String = "Failed to favorite world",
    val favoriteSelectGroup: String = "Select Group",
    val favoriteDefaultGroup: String = "Default Group",
    val instanceCreateSuccess: String = "Successfully created instance and sent invite",
    val instanceCreateSuccessButInviteFailed: String = "Instance created successfully but invite failed",
    val instanceCreateFailed: String = "Failed to create instance"
)

val strings: LocaleStrings
    @Composable
    get() {
        return when (LocalSettingsState.current.value.languageTag) {
            LanguageTag.EN -> LocaleStringsEn
            LanguageTag.JA -> LocaleStringsJa
            LanguageTag.ZH_HANS -> LocaleStringsZhHans
            LanguageTag.ZH_HANT -> LocaleStringsZhHant
            else -> LocaleStringsEn
        }
    }

