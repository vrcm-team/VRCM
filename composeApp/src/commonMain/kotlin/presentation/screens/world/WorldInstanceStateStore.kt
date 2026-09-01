package io.github.vrcmteam.vrcm.presentation.screens.world

import io.github.vrcmteam.vrcm.network.api.attributes.AccessType
import io.github.vrcmteam.vrcm.network.api.instances.data.InstanceCloseResponse
import io.github.vrcmteam.vrcm.network.api.instances.data.InstanceData
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceVo
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceVo.Owner
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class LoadedInstanceOwner(
    val ownerId: String?,
    val owner: MutableStateFlow<Owner?>,
)

/** Serializes instance refresh commits with authoritative close results. */
internal class WorldInstanceStateStore(
    private val profileState: MutableStateFlow<WorldProfileVo?>,
) {
    private val commitMutex = Mutex()
    private val locationRevisions = mutableMapOf<String, Long>()
    private val closedLocations = mutableSetOf<String>()

    suspend fun refreshInstance(
        worldId: String,
        instanceId: String,
        initialOwner: Owner?,
        fetch: suspend () -> InstanceData,
    ): LoadedInstanceOwner? {
        val location = "$worldId:$instanceId"
        val expectedRevision = commitMutex.withLock { locationRevisions[location] ?: 0L }
        val response = fetch()

        return commitMutex.withLock {
            if ((locationRevisions[location] ?: 0L) != expectedRevision ||
                location in closedLocations
            ) {
                return@withLock null
            }
            if (response.worldId != worldId || response.instanceId != instanceId ||
                response.location != location
            ) {
                return@withLock null
            }
            val profile = profileState.value?.takeIf { it.worldId == worldId }
                ?: return@withLock null
            if (!response.active || response.closedAt != null) {
                closedLocations += location
                profileState.value = profile.copy(
                    instances = profile.instances.filterNot { it.location == location },
                )
                return@withLock null
            }

            val owner = MutableStateFlow(initialOwner)
            val updated = profile.instances
                .filterNot { it.id == response.id || it.location == location }
                .plus(InstanceVo(response, owner))
            profileState.value = profile.copy(instances = updated)
            LoadedInstanceOwner(response.ownerId, owner)
        }
    }

    suspend fun applyClose(
        target: InstanceCloseTarget,
        response: InstanceCloseResponse,
        canCommit: () -> Boolean = { true },
    ): Boolean = commitMutex.withLock {
        if (!canCommit()) return@withLock false
        val profile = profileState.value?.takeIf { it.worldId == target.worldId }
            ?: return@withLock false
        locationRevisions[target.location] = (locationRevisions[target.location] ?: 0L) + 1L
        if (response.isClosed) {
            closedLocations += target.location
        }
        profileState.value = profile.applyInstanceCloseResponse(target, response)
        true
    }
}

internal val InstanceCloseResponse.isClosed: Boolean
    get() = active == false || closedAt != null

internal fun WorldProfileVo.applyInstanceCloseResponse(
    target: InstanceCloseTarget,
    response: InstanceCloseResponse,
): WorldProfileVo {
    if (worldId != target.worldId) return this
    val updatedInstances = if (response.isClosed) {
        instances.filterNot { it.location == target.location }
    } else {
        instances.map { current ->
            if (current.location == target.location) response.mergeWith(current) else current
        }
    }
    return copy(instances = updatedInstances)
}

internal fun InstanceData.asInstanceCloseResponse() = InstanceCloseResponse(
    active = active,
    canRequestInvite = canRequestInvite,
    capacity = capacity,
    clientNumber = clientNumber,
    closedAt = closedAt,
    displayName = displayName,
    full = full,
    gameServerVersion = gameServerVersion,
    hardClose = hardClose,
    hasCapacityForYou = hasCapacityForYou,
    hidden = hidden,
    id = id,
    instanceId = instanceId,
    location = location,
    nUsers = nUsers,
    name = name,
    ownerId = ownerId,
    permanent = permanent,
    photonRegion = photonRegion,
    platforms = platforms,
    queueEnabled = queueEnabled,
    queueSize = queueSize,
    recommendedCapacity = recommendedCapacity,
    region = region,
    secureName = secureName,
    shortName = shortName,
    strict = strict,
    tags = tags,
    type = type,
    userCount = userCount,
    world = world,
    worldId = worldId,
)

private fun InstanceCloseResponse.mergeWith(current: InstanceVo): InstanceVo = current.copy(
    id = id,
    instanceId = instanceId,
    worldId = worldId,
    location = location,
    ownerId = ownerId ?: current.ownerId,
    instanceName = name,
    currentUsers = nUsers,
    pcUsers = platforms.standaloneWindows,
    androidUsers = platforms.android,
    iosUsers = platforms.ios,
    queueEnabled = queueEnabled,
    queueSize = queueSize,
    isActive = active ?: current.isActive,
    isFull = full,
    hasCapacity = hasCapacityForYou ?: current.hasCapacity,
    regionType = region,
    regionName = region.name,
    accessType = mergedAccessType(current.accessType),
)

private fun InstanceCloseResponse.mergedAccessType(current: AccessType): AccessType = when (type) {
    AccessType.Group.value -> when (instanceId.substringAfter("groupAccessType(").substringBefore(")")) {
        AccessType.GroupPublic.value -> AccessType.GroupPublic
        AccessType.GroupPlus.value -> AccessType.GroupPlus
        AccessType.GroupMembers.value -> AccessType.GroupMembers
        else -> AccessType.Group
    }

    AccessType.Private.value -> when (canRequestInvite) {
        true -> AccessType.InvitePlus
        false -> AccessType.Invite
        null -> current
    }

    AccessType.FriendPlus.value -> AccessType.FriendPlus
    AccessType.Friend.value -> AccessType.Friend
    else -> AccessType.Public
}
