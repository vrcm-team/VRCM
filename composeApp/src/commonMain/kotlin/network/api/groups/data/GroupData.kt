package io.github.vrcmteam.vrcm.network.api.groups.data

import kotlinx.serialization.Serializable

@Serializable
data class GroupData(
    val badges: List<String>,
    val bannerId: String?,
    val bannerUrl: String,
    val createdAt: String,
    val description: String,
    val discriminator: String,
    val galleries: List<Gallery>,
    val iconId: String?,
    val iconUrl: String,
    val id: String,
    val isVerified: Boolean,
    val joinState: String,
    val languages: List<String>,
    val lastPostCreatedAt: String?,
    val links: List<String>,
    val memberCount: Int,
    val memberCountSyncedAt: String,
    val membershipStatus: String,
    val myMember: MyMember?,
    val name: String,
    val onlineMemberCount: Int,
    val ownerId: String,
    val privacy: String,
    val roles: List<Role>?,
    val rules: String,
    val shortCode: String,
    val tags: List<String>,
)