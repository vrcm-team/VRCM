package io.github.vrcmteam.vrcm.network.api.avatars.data

import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class AvatarDataSavedStateSerializationTest {
    @Test
    fun avatarDataRoundTripsThroughSavedState() {
        val avatar = AvatarData(
            id = "avtr_test",
            name = "Test",
            tags = listOf("tag_a"),
            unityPackages = listOf(AvatarUnityPackage(platform = "android")),
        )

        val savedState = encodeToSavedState(AvatarData.serializer(), avatar)
        val restored = decodeFromSavedState(AvatarData.serializer(), savedState)

        assertEquals(avatar, restored)
    }
}
