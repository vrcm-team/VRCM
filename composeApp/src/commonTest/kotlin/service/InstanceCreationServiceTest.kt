package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.AccessType
import io.github.vrcmteam.vrcm.network.api.attributes.RegionType
import io.github.vrcmteam.vrcm.network.api.instances.data.InstanceCreationOptions
import io.github.vrcmteam.vrcm.network.api.instances.data.InstanceData
import io.github.vrcmteam.vrcm.network.api.instances.data.Platforms
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.service.data.AccountDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class InstanceCreationServiceTest {
    @Test
    fun duplicateSubmissionIsRejectedAndServerInstanceIsReturned() = runTest {
        val request = ControlledInstanceCreationRequest()
        val service = InstanceCreationService(request)
        try {
            SharedFlowCentre.emitAuthenticated(AccountDto(userId = "usr_a"))
            val first = async(start = CoroutineStart.UNDISPATCHED) { service.create(options()) }
            val token = request.started.await()

            assertEquals(InstanceCreationResult.InFlight, service.create(options()))
            val authoritative = instance(id = "instance_from_server")
            request.complete(
                AuthenticatedInstanceCreationResponse(Result.success(authoritative), token)
            )

            val result = assertIs<InstanceCreationResult.Created>(first.await())
            assertEquals(authoritative, result.instance)
        } finally {
            SharedFlowCentre.emitLogout()
        }
    }

    @Test
    fun sameAccountAuthenticationRenewalAcceptsResponseBoundToNewToken() = runTest {
        val request = ControlledInstanceCreationRequest()
        val service = InstanceCreationService(request)
        try {
            SharedFlowCentre.emitAuthenticated(AccountDto(userId = "usr_a"))
            val create = async(start = CoroutineStart.UNDISPATCHED) { service.create(options()) }
            request.started.await()

            SharedFlowCentre.emitAuthenticated(AccountDto(userId = "usr_a"))
            val renewedToken = SharedFlowCentre.currentSession.value!!.token
            request.complete(
                AuthenticatedInstanceCreationResponse(
                    Result.success(instance()),
                    renewedToken,
                )
            )

            assertIs<InstanceCreationResult.Created>(create.await())
        } finally {
            SharedFlowCentre.emitLogout()
        }
    }

    @Test
    fun accountSwitchDiscardsLateCreateResponse() = runTest {
        val request = ControlledInstanceCreationRequest()
        val service = InstanceCreationService(request)
        try {
            SharedFlowCentre.emitAuthenticated(AccountDto(userId = "usr_a"))
            val create = async(start = CoroutineStart.UNDISPATCHED) { service.create(options()) }
            val oldToken = request.started.await()

            SharedFlowCentre.emitAuthenticated(AccountDto(userId = "usr_b"))
            request.complete(
                AuthenticatedInstanceCreationResponse(Result.success(instance()), oldToken)
            )

            assertEquals(InstanceCreationResult.SessionChanged, create.await())
        } finally {
            SharedFlowCentre.emitLogout()
        }
    }

    @Test
    fun logoutDiscardsLateCreateResponse() = runTest {
        val request = ControlledInstanceCreationRequest()
        val service = InstanceCreationService(request)
        SharedFlowCentre.emitAuthenticated(AccountDto(userId = "usr_a"))
        val create = async(start = CoroutineStart.UNDISPATCHED) { service.create(options()) }
        val oldToken = request.started.await()

        SharedFlowCentre.emitLogout()
        request.complete(
            AuthenticatedInstanceCreationResponse(Result.success(instance()), oldToken)
        )

        assertEquals(InstanceCreationResult.SessionChanged, create.await())
    }

    private fun options() = InstanceCreationOptions(
        worldId = "wrld_test",
        accessType = AccessType.Public,
        region = RegionType.Us,
    )

    private fun instance(id: String = "instance_server") = InstanceData(
        active = true,
        canRequestInvite = false,
        capacity = 40,
        clientNumber = "1",
        displayName = "Server Name",
        full = false,
        hidden = null,
        id = id,
        instanceId = id,
        location = "wrld_test:$id",
        nUsers = 0,
        name = id,
        ownerId = null,
        permanent = false,
        photonRegion = "us",
        platforms = Platforms(),
        queueEnabled = false,
        queueSize = 0,
        recommendedCapacity = 20,
        region = RegionType.Us,
        secureName = "secure",
        strict = false,
        tags = emptyList(),
        type = "public",
        userCount = 0,
        world = world(),
        worldId = "wrld_test",
    )

    private fun world() = WorldData(
        authorId = "usr_author",
        authorName = "Author",
        capacity = 40,
        createdAt = null,
        description = null,
        favorites = null,
        featured = null,
        heat = 0,
        id = "wrld_test",
        imageUrl = "",
        labsPublicationDate = "",
        name = "World",
        namespace = null,
        organization = "",
        popularity = 0,
        publicationDate = "",
        recommendedCapacity = 20,
        releaseStatus = "public",
        tags = emptyList(),
        thumbnailImageUrl = null,
        udonProducts = emptyList(),
        unityPackages = emptyList(),
        updatedAt = null,
        version = null,
        visits = null,
    )
}

private class ControlledInstanceCreationRequest : InstanceCreationRequest {
    val started = CompletableDeferred<AccountSessionToken>()
    private val completion = CompletableDeferred<AuthenticatedInstanceCreationResponse?>()

    override suspend fun create(
        sessionToken: AccountSessionToken,
        options: InstanceCreationOptions,
    ): AuthenticatedInstanceCreationResponse? {
        started.complete(sessionToken)
        return completion.await()
    }

    fun complete(response: AuthenticatedInstanceCreationResponse?) {
        completion.complete(response)
    }
}
