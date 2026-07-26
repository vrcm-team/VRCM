package io.github.vrcmteam.vrcm.presentation.screens.avatar

import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.screens.avatar.data.AvatarProfileVo
import io.github.vrcmteam.vrcm.testing.MainDispatcherTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AvatarProfileRequestTest : MainDispatcherTest() {
    @Test
    fun olderSuccessCannotOverwriteTheLatestAvatar() = runBlocking {
        val loader = ControlledAvatarProfileLoader()
        val model = AvatarProfileScreenModel(loader, Dispatchers.Unconfined)

        model.refreshAvatarData(AvatarProfileVo(avatarId = "avtr_a", avatarName = "Initial A"))
        model.refreshAvatarData(AvatarProfileVo(avatarId = "avtr_b", avatarName = "Initial B"))
        assertTrue(model.isLoading.value)

        loader.completeSuccess("avtr_b", avatarName = "Remote B")
        assertEquals("avtr_b", model.avatarProfileState.value?.avatarId)
        assertEquals("Remote B", model.avatarProfileState.value?.avatarName)
        assertFalse(model.isLoading.value)

        loader.completeSuccess("avtr_a", avatarName = "Remote A")
        assertEquals("avtr_b", model.avatarProfileState.value?.avatarId)
        assertEquals("Remote B", model.avatarProfileState.value?.avatarName)
        assertFalse(model.isLoading.value)
    }

    @Test
    fun olderFailureDoesNotStopLoadingOrEmitToastForTheLatestRequest() = runBlocking {
        val loader = ControlledAvatarProfileLoader()
        val model = AvatarProfileScreenModel(loader, Dispatchers.Unconfined)
        val toasts = mutableListOf<ToastText>()
        val toastCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            SharedFlowCentre.toastText.collect(toasts::add)
        }

        model.refreshAvatarData(AvatarProfileVo(avatarId = "avtr_a"))
        model.refreshAvatarData(AvatarProfileVo(avatarId = "avtr_b"))
        loader.completeFailure("avtr_a")
        yield()

        assertTrue(model.isLoading.value)
        assertTrue(toasts.isEmpty())

        loader.completeSuccess("avtr_b", avatarName = "Remote B")
        assertEquals("avtr_b", model.avatarProfileState.value?.avatarId)
        assertFalse(model.isLoading.value)
        toastCollector.cancel()
    }
}

private class ControlledAvatarProfileLoader : AvatarProfileLoader {
    private val requests = mutableMapOf<String, CompletableDeferred<Result<AvatarData>>>()

    override suspend fun load(avatarId: String): Result<AvatarData> =
        requests.getOrPut(avatarId) { CompletableDeferred() }.await()

    fun completeSuccess(avatarId: String, avatarName: String) {
        requests.getValue(avatarId).complete(
            Result.success(AvatarData(id = avatarId, name = avatarName))
        )
    }

    fun completeFailure(avatarId: String) {
        requests.getValue(avatarId).complete(Result.failure(IllegalStateException("stale failure")))
    }
}
