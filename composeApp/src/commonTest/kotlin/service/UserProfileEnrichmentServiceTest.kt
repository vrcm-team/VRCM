package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.network.api.attributes.AgeVerificationStatus
import io.github.vrcmteam.vrcm.network.api.attributes.FriendRequestStatus
import io.github.vrcmteam.vrcm.network.api.attributes.UserState
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.users.data.UserData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class UserProfileEnrichmentServiceTest {
    @Test
    fun concurrentDuplicateRequestsShareOneFetchAndReuseAccountCache() = runTest {
        val token = AccountSessionToken("usr_account", 1)
        val fetchStarted = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()
        var fetchCount = 0
        val service = service(token) { userId ->
            fetchCount++
            fetchStarted.complete(Unit)
            releaseFetch.await()
            user(userId)
        }

        val requests = List(6) {
            async { service.fetchProfiles(token, listOf("usr_target")) }
        }
        fetchStarted.await()
        releaseFetch.complete(Unit)
        val results = requests.awaitAll()
        val cached = service.fetchProfiles(token, listOf("usr_target"))

        assertEquals(1, fetchCount)
        assertEquals(List(6) { setOf("usr_target") }, results.map { it.keys })
        assertEquals("Target usr_target", cached.getValue("usr_target").displayName)
    }

    @Test
    fun oneFailedProfileDoesNotDiscardSuccessfulProfilesOrImmediatelyRetry() = runTest {
        val token = AccountSessionToken("usr_account", 1)
        val requestCounts = mutableMapOf<String, Int>()
        val service = service(token) { userId ->
            requestCounts[userId] = requestCounts.getOrElse(userId) { 0 } + 1
            if (userId == "usr_failed") error("profile unavailable")
            user(userId)
        }

        val first = service.fetchProfiles(token, listOf("usr_ok", "usr_failed"))
        val second = service.fetchProfiles(token, listOf("usr_ok", "usr_failed"))

        assertEquals(setOf("usr_ok"), first.keys)
        assertEquals(first, second)
        assertEquals(mapOf("usr_ok" to 1, "usr_failed" to 1), requestCounts)
    }

    @Test
    fun profileFetchesRespectConfiguredConcurrencyLimit() = runTest {
        val token = AccountSessionToken("usr_account", 1)
        val twoRequestsStarted = CompletableDeferred<Unit>()
        val releaseFetches = CompletableDeferred<Unit>()
        var activeRequests = 0
        var maximumActiveRequests = 0
        val service = service(token, maxConcurrentRequests = 2) { userId ->
            activeRequests++
            maximumActiveRequests = maxOf(maximumActiveRequests, activeRequests)
            if (activeRequests == 2) twoRequestsStarted.complete(Unit)
            releaseFetches.await()
            activeRequests--
            user(userId)
        }

        val request = async {
            service.fetchProfiles(token, List(6) { "usr_$it" })
        }
        twoRequestsStarted.await()

        assertEquals(2, maximumActiveRequests)
        releaseFetches.complete(Unit)
        assertEquals(6, request.await().size)
        assertEquals(2, maximumActiveRequests)
    }

    @Test
    fun switchingAccountInvalidatesCachedProfiles() = runTest {
        val accountA = AccountSessionToken("usr_account_a", 1)
        val accountB = AccountSessionToken("usr_account_b", 2)
        val currentToken = MutableStateFlow<AccountSessionToken?>(accountA)
        var fetchCount = 0
        val service = UserProfileEnrichmentService(
            fetchUser = { userId ->
                fetchCount++
                user(userId, displayName = "${currentToken.value?.userId}:$fetchCount")
            },
            currentSessionToken = { currentToken.value },
            requestScope = backgroundScope,
            sessionTokens = currentToken,
        )

        val accountAProfile = service.fetchProfiles(accountA, listOf("usr_target"))
        currentToken.value = accountB
        runCurrent()
        val staleAccountResult = service.fetchProfiles(accountA, listOf("usr_target"))
        val accountBProfile = service.fetchProfiles(accountB, listOf("usr_target"))

        assertEquals("usr_account_a:1", accountAProfile.getValue("usr_target").displayName)
        assertFalse(staleAccountResult.containsKey("usr_target"))
        assertEquals("usr_account_b:2", accountBProfile.getValue("usr_target").displayName)
        assertEquals(2, fetchCount)
    }

    private fun kotlinx.coroutines.test.TestScope.service(
        token: AccountSessionToken,
        maxConcurrentRequests: Int = 4,
        fetchUser: suspend (String) -> UserData,
    ) = UserProfileEnrichmentService(
        fetchUser = fetchUser,
        currentSessionToken = { token },
        requestScope = backgroundScope,
        maxConcurrentRequests = maxConcurrentRequests,
    )

    private fun user(
        id: String,
        displayName: String = "Target $id",
    ) = UserData(
        ageVerificationStatus = AgeVerificationStatus.Verified,
        allowAvatarCopying = true,
        bio = "",
        bioLinks = emptyList(),
        currentAvatarImageUrl = "",
        currentAvatarTags = emptyList(),
        currentAvatarThumbnailImageUrl = "",
        dateJoined = "2020-01-01",
        developerType = "none",
        displayName = displayName,
        friendKey = "",
        friendRequestStatus = FriendRequestStatus.Null,
        id = id,
        instanceId = "",
        isFriend = false,
        lastActivity = "",
        lastLogin = "",
        lastPlatform = "standalonewindows",
        location = "offline",
        note = "",
        profilePicOverride = "",
        state = UserState.Offline,
        status = UserStatus.Offline,
        statusDescription = "",
        tags = emptyList(),
        travelingToInstance = null,
        travelingToLocation = null,
        travelingToWorld = null,
        userIcon = "",
        worldId = "",
        pronouns = null,
    )
}
