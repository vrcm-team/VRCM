package io.github.vrcmteam.vrcm.testing

import io.github.vrcmteam.vrcm.network.api.auth.data.CurrentUserData
import kotlinx.serialization.json.Json

private val currentUserFixtureJson = Json { ignoreUnknownKeys = true }

internal fun currentUserData(
    userId: String,
    currentAvatar: String = "",
    fallbackAvatar: String = "",
): CurrentUserData = currentUserFixtureJson.decodeFromString(
    currentUserJson(userId, currentAvatar, fallbackAvatar)
)

internal fun currentUserJson(
    userId: String,
    currentAvatar: String = "",
    fallbackAvatar: String = "",
): String = """
    {
      "ageVerificationStatus":"verified","ageVerified":true,
      "acceptedPrivacyVersion":0,"acceptedTOSVersion":0,
      "accountDeletionDate":null,"accountDeletionLog":null,"activeFriends":[],
      "allowAvatarCopying":true,"bio":null,"bioLinks":[],
      "currentAvatar":"$currentAvatar","currentAvatarAssetUrl":null,"currentAvatarImageUrl":"",
      "currentAvatarTags":[],"currentAvatarThumbnailImageUrl":"","date_joined":"",
      "developerType":"none","displayName":"Current User","emailVerified":true,
      "fallbackAvatar":"$fallbackAvatar","friendGroupNames":[],"friendKey":"","friends":[],
      "googleId":"","hasBirthday":true,"hasEmail":true,
      "hasLoggedInFromClient":true,"hasPendingEmail":false,
      "hideContentFilterSettings":false,"homeLocation":"","id":"$userId",
      "isFriend":false,"last_activity":"","last_login":"",
      "last_platform":"standalonewindows","obfuscatedEmail":"",
      "obfuscatedPendingEmail":"","oculusId":"","offlineFriends":[],
      "onlineFriends":[],"pastDisplayNames":[],"picoId":"",
      "presence":{
        "avatarThumbnail":null,"displayName":"Current User","groups":[],
        "id":"$userId","instance":"","instanceType":"","isRejoining":null,
        "platform":"standalonewindows","profilePicOverride":null,"status":"active",
        "travelingToInstance":"","travelingToWorld":"","world":""
      },
      "profilePicOverride":"","state":"online","status":"active",
      "statusDescription":"","statusFirstTime":false,"statusHistory":[],
      "steamDetails":{},"steamId":"","tags":[],"twoFactorAuthEnabled":false,
      "twoFactorAuthEnabledDate":null,"unsubscribe":false,"updated_at":"",
      "userIcon":"","userLanguage":null,"userLanguageCode":null,
      "username":"current-user","viveId":"","pronouns":null
    }
""".trimIndent()
