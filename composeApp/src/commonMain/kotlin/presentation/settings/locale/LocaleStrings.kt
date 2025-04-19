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
    val users: String = "Users",
    val worlds: String = "Worlds",
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
    val favoriteAddSuccess: String = "Add Favorite Success",
    val favoriteAddFailed: String = "Add Favorite Failed",
    val favoriteAlreadyExists: String = "it is already in your favorites",
    val favoriteGroupSelect: String = "Select favorite group",
    val favoriteSelectGroup: String = "Select Group",
    val favoriteDefaultGroup: String = "Default Group",
    val favoriteRemoveSuccess: String = "Remove Favorite Success",
    val favoriteRemoveFailed: String = "Remove Favorite Failed",
    val favoriteMoveFailed: String = "Move Favorite Failed",
    val favoriteMoveSuccess: String = "Move Favorite Success",
    val favoriteMoveToAnotherGroup: String = "Move Favorite",
    val selectFavoriteGroup: String = "Select Favorite Group",
    val remove: String = "Remove",
    val instanceCreateSuccess: String = "Successfully created instance and sent invite",
    val instanceCreateSuccessButInviteFailed: String = "Instance created successfully but invite failed",
    val instanceCreateFailed: String = "Failed to create instance",

    // 世界搜索相关
    val worldSearchAdvancedOptions: String = "Advanced Search Options",
    val worldSearchFeaturedOnly: String = "Featured Worlds Only",
    val worldSearchSortBy: String = "Sort By",
    val worldSearchOrder: String = "Order",
    val worldSearchDescending: String = "Descending",
    val worldSearchAscending: String = "Ascending",
    val worldSearchResultCount: String = "Results Count",
    val worldSearchResultsFormat: String = "%d results",

    // 排序选项
    val worldSearchSortPopularity: String = "Popularity",
    val worldSearchSortHeat: String = "Heat",
    val worldSearchSortTrust: String = "Trust",
    val worldSearchSortShuffle: String = "Shuffle",
    val worldSearchSortRandom: String = "Random",
    val worldSearchSortFavorites: String = "Favorites",
    val worldSearchSortCreated: String = "Created",
    val worldSearchSortUpdated: String = "Updated",
    val worldSearchSortRelevance: String = "Relevance",
    val worldSearchSortName: String = "Name",

    // FriendListPager
    val friendListPagerAllFriends: String = "All Friends",
    val friendListPagerAllWorlds: String = "All Worlds",

    // WorldProfileScreen
    val worldProfileDescription: String = "World Description",
    val worldProfileAuthorTags: String = "Author Tags",
    val worldProfileCapacity: String = "Capacity",
    val worldProfileOnlineUsers: String = "Total Online Users",
    val worldProfileVisits: String = "Visits",
    val worldProfileFavorites: String = "Favorites",
    val worldProfileHeat: String = "Heat",
    val worldProfilePopularity: String = "Popularity",
    val worldProfileVersion: String = "Version",
    val worldProfileUpdateDate: String = "Update Date",
    val worldProfileLabReleaseDate: String = "Lab Release Date",
    val unknown: String = "Unknown",

    // CreateInstanceDialog
    val createInstanceStandardAccessType: String = "Standard Access Type",
    val createInstanceEnableQueue: String = "Enable Queue Function",

    // GalleryTabPager
    val galleryTabNoFiles: String = "No %s Files",
    val galleryTabUploading: String = "Uploading image...",
    val galleryTabUploadImage: String = "Upload Image",
    val galleryTabLoadFailed: String = "Loading Failed",

    // GalleryScreen
    val galleryScreenTitle: String = "Gallery",
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
