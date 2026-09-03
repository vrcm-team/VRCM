package io.github.vrcmteam.vrcm.network.api.instances.data

import io.github.vrcmteam.vrcm.network.api.attributes.RegionType
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Instance snapshot returned by the close endpoint, including its contract-optional fields. */
@Serializable
data class InstanceCloseResponse(
    val active: Boolean? = null,
    val canRequestInvite: Boolean? = null,
    val capacity: Int? = null,
    val clientNumber: String,
    val closedAt: String? = null,
    val displayName: String? = null,
    val full: Boolean,
    val gameServerVersion: Int? = null,
    val hardClose: Boolean? = null,
    val hasCapacityForYou: Boolean? = null,
    val hidden: String? = null,
    val id: String,
    val instanceId: String,
    val location: String,
    @SerialName("n_users")
    val nUsers: Int,
    val name: String,
    val ownerId: String? = null,
    val permanent: Boolean,
    val photonRegion: String,
    val platforms: Platforms,
    val queueEnabled: Boolean,
    val queueSize: Int,
    val recommendedCapacity: Int,
    val region: RegionType,
    val secureName: String,
    val shortName: String? = null,
    val strict: Boolean,
    val tags: List<String>,
    val type: String,
    val userCount: Int,
    val world: WorldData,
    val worldId: String,
)
