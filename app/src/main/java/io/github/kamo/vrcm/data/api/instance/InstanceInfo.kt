package io.github.kamo.vrcm.data.api.instance

import com.google.gson.annotations.SerializedName
import io.github.kamo.vrcm.data.api.InstantsType

data class InstanceInfo(
    val active: Boolean,
    val canRequestInvite: Boolean,
    val capacity: Int,
    val clientNumber: String,
    val closedAt: String? = null,
    val displayName: String? = null,
    val full: Boolean,
    val gameServerVersion: Int,
    val hardClose: String? = null,
    val hasCapacityForYou: Boolean,
    val hidden: String?,
    val id: String,
    val instanceId: String,
    val location: String,
    @SerializedName("n_users")
    val nUsers: Int,
    val name: String,
    val ownerId: String?,
    val permanent: Boolean,
    val photonRegion: String,
    val platforms: Platforms,
    val queueEnabled: Boolean,
    val queueSize: Int,
    val recommendedCapacity: Int,
    val region: String,
    val secureName: String,
    val shortName: String? = null,
    val strict: Boolean,
    val tags: List<String>,
    var type: String,
    val userCount: Int,
    val world: WorldInfo,
    val worldId: String
) {
    val detailedType: InstantsType
        get() {
            return if (type == InstantsType.Group.typeName) {
                val detailedType = instanceId.substringAfter("groupAccessType(").substringBefore(")")
                when (detailedType) {
                    "public" -> InstantsType.GroupPublic
                    "plus" -> InstantsType.GroupPlus
                    else -> InstantsType.Group
                }
            } else {
                InstantsType.entries.first { it.typeName == type }
            }
        }
}

