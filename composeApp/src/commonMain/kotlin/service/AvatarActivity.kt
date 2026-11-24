package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.network.api.auth.data.CurrentUserData
import io.github.vrcmteam.vrcm.network.api.files.FileApi
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData

internal data class AccountAvatarActivity(
    val token: AccountSessionToken,
    val event: FriendActivityEventDraft,
    val needsIconValidation: Boolean = false,
)

/** Compares matching image sources so changes in which fields the API supplies do not imply a swap. */
internal fun FriendData.avatarActivitySince(
    previous: FriendData?,
    token: AccountSessionToken,
    occurredAtMillis: Long,
): AccountAvatarActivity? {
    if (previous == null) return null
    val banner = bannerUrl.takeIf { bannerType == "avatarBanner" }
    val previousBanner = previous.bannerUrl.takeIf { previous.bannerType == "avatarBanner" }
    val icon = profileIconUrl.ifBlank { userIcon }
    val previousIcon = previous.profileIconUrl.ifBlank { previous.userIcon }

    val images = changedAvatarImages(previousBanner, banner)
        ?: changedAvatarImages(previous.currentAvatarImageUrl, currentAvatarImageUrl)
        ?: changedAvatarImages(previous.currentAvatarThumbnailImageUrl, currentAvatarThumbnailImageUrl)
    val (before, after) = images ?: changedAvatarImages(previousIcon, icon) ?: return null
    return AccountAvatarActivity(
        token = token,
        event = avatarActivity(id, displayName, iconUrl, occurredAtMillis, before, after),
        needsIconValidation = images == null,
    )
}

private fun changedAvatarImages(before: String?, after: String?): Pair<String, String>? {
    if (before.isNullOrBlank() || after.isNullOrBlank()) return null
    val previousFile = FileApi.findFileId(before)
    val currentFile = FileApi.findFileId(after)
    return (before to after).takeIf {
        previousFile.isNotEmpty() && currentFile.isNotEmpty() && previousFile != currentFile
    }
}

internal fun CurrentUserData.avatarActivitySince(
    previous: CurrentUserData,
    token: AccountSessionToken,
    occurredAtMillis: Long,
): AccountAvatarActivity? {
    if (currentAvatar.isBlank() || previous.currentAvatar.isBlank() || currentAvatar == previous.currentAvatar) {
        return null
    }
    return AccountAvatarActivity(
        token,
        avatarActivity(id, displayName, iconUrl, occurredAtMillis, previous.avatarPreview(), avatarPreview()),
    )
}

private fun CurrentUserData.avatarPreview(): String? =
    currentAvatarImageUrl.ifBlank { currentAvatarThumbnailImageUrl.orEmpty() }.takeIf(String::isNotBlank)

private fun avatarActivity(
    userId: String,
    displayName: String,
    profileImageUrl: String,
    occurredAtMillis: Long,
    previousImage: String?,
    currentImage: String?,
) = FriendActivityEventDraft(
    userId = userId,
    displayName = displayName,
    profileImageUrl = profileImageUrl,
    type = FriendActivityEventType.AvatarChanged,
    occurredAtMillis = occurredAtMillis,
    previousValue = previousImage,
    currentValue = currentImage,
)
