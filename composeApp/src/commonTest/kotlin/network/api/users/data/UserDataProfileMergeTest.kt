package io.github.vrcmteam.vrcm.network.api.users.data

import io.github.vrcmteam.vrcm.network.api.attributes.AgeVerificationStatus
import io.github.vrcmteam.vrcm.network.api.attributes.UserState
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.profile.data.ProfileAppearanceData
import kotlin.test.Test
import kotlin.test.assertEquals

class UserDataProfileMergeTest {
    @Test
    fun publicProfileSuppliesFieldsRemovedFromUserResponse() {
        val user = UserData(
            ageVerificationStatus = AgeVerificationStatus.Verified,
            allowAvatarCopying = true,
            dateJoined = "2020-01-01",
            developerType = "none",
            displayName = "Fallback name",
            friendKey = "",
            id = "usr_123",
            isFriend = true,
            lastActivity = "",
            lastLogin = "",
            lastPlatform = "standalonewindows",
            state = UserState.Online,
            status = UserStatus.Active,
            statusDescription = "",
            tags = emptyList(),
        )

        val merged = user.mergePublicProfile(
            ProfileAppearanceData(
                id = "usr_123",
                bio = "Public bio",
                bioLinks = listOf("https://example.invalid"),
                displayName = "Public name",
                iconUrl = "https://example.invalid/icon.png",
                pronouns = "they/them",
            ),
        )

        assertEquals("Public bio", merged.bio)
        assertEquals(listOf("https://example.invalid"), merged.bioLinks)
        assertEquals("Public name", merged.displayName)
        assertEquals("https://example.invalid/icon.png", merged.userIcon)
        assertEquals("https://example.invalid/icon.png", merged.currentAvatarImageUrl)
        assertEquals("they/them", merged.pronouns)
        assertEquals("usr_123", merged.id)
        assertEquals(UserState.Online, merged.state)
    }
}
