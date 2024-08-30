package network.api.groups.data

import kotlinx.serialization.Serializable

@Serializable
data class MyMember(
    val groupId: String,
    val has2FA: Boolean,
    val id: String,
    val isRepresenting: Boolean,
    val isSubscribedToAnnouncements: Boolean,
    val joinedAt: String,
    val lastPostReadAt: String,
    val mRoleIds: List<String>,
    val membershipStatus: String,
    val permissions: List<String>,
    val roleIds: List<String>,
    val userId: String,
    val visibility: String
)