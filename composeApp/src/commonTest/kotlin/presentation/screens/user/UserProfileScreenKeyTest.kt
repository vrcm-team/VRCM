package io.github.vrcmteam.vrcm.presentation.screens.user

import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class UserProfileScreenKeyTest {
    @Test
    fun profilesForDifferentUsersHaveDifferentScreenKeys() {
        val firstProfile = UserProfileScreen(UserProfileVo(id = "usr_first"))
        val secondProfile = UserProfileScreen(UserProfileVo(id = "usr_second"))

        assertNotEquals(firstProfile.key, secondProfile.key)
    }

    @Test
    fun reopeningTheSameUserUsesTheSameScreenKey() {
        val firstProfile = UserProfileScreen(UserProfileVo(id = "usr_example"))
        val secondProfile = UserProfileScreen(UserProfileVo(id = "usr_example"))

        assertEquals(firstProfile.key, secondProfile.key)
    }
}
