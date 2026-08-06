package io.github.vrcmteam.vrcm.presentation.screens.group.data

import io.github.vrcmteam.vrcm.network.api.groups.data.Gallery
import io.github.vrcmteam.vrcm.network.api.groups.data.GroupData
import io.github.vrcmteam.vrcm.network.api.groups.data.LimitedGroup
import io.github.vrcmteam.vrcm.network.api.groups.data.MyMember
import io.github.vrcmteam.vrcm.network.api.groups.data.Role
import io.github.vrcmteam.vrcm.network.api.users.data.LimitedUserGroup
import kotlinx.serialization.Serializable

@Serializable
data class GroupProfileVo(
    val groupId: String,
    val name: String = "",
    val shortCode: String = "",
    val discriminator: String = "",
    val ageVerificationBetaCode: String? = null,
    val ageVerificationBetaSlots: Int? = null,
    val ageVerificationSlotsAvailable: Boolean? = null,
    val allowGroupJoinPrompt: Boolean? = null,
    val description: String = "",
    val rules: String? = null,
    val privacy: String = "",
    val joinState: String = "",
    val membershipStatus: String = "",
    val isVerified: Boolean = false,
    val isSearchable: Boolean? = null,
    val badges: List<String> = emptyList(),
    val iconUrl: String? = null,
    val bannerUrl: String? = null,
    val iconId: String? = null,
    val bannerId: String? = null,
    val memberCount: Int = 0,
    val onlineMemberCount: Int = 0,
    val memberCountSyncedAt: String? = null,
    val tags: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val links: List<String> = emptyList(),
    val galleries: List<Gallery> = emptyList(),
    val roles: List<Role> = emptyList(),
    val myMember: MyMember? = null,
    val ownerId: String? = null,
    val transferTargetId: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val lastPostCreatedAt: String? = null,
) {

    constructor(group: GroupData) : this(
        groupId = group.id,
        name = group.name,
        shortCode = group.shortCode,
        discriminator = group.discriminator,
        ageVerificationBetaCode = group.ageVerificationBetaCode,
        ageVerificationBetaSlots = group.ageVerificationBetaSlots,
        ageVerificationSlotsAvailable = group.ageVerificationSlotsAvailable,
        allowGroupJoinPrompt = group.allowGroupJoinPrompt,
        description = group.description,
        rules = group.rules,
        privacy = group.privacy,
        joinState = group.joinState,
        membershipStatus = group.membershipStatus,
        isVerified = group.isVerified,
        badges = group.badges,
        iconUrl = group.iconUrl,
        bannerUrl = group.bannerUrl,
        iconId = group.iconId,
        bannerId = group.bannerId,
        memberCount = group.memberCount,
        onlineMemberCount = group.onlineMemberCount,
        memberCountSyncedAt = group.memberCountSyncedAt,
        tags = group.tags,
        languages = group.languages,
        links = group.links,
        galleries = group.galleries,
        roles = group.roles.orEmpty(),
        myMember = group.myMember,
        ownerId = group.ownerId,
        transferTargetId = group.transferTargetId,
        createdAt = group.createdAt,
        updatedAt = group.updatedAt,
        lastPostCreatedAt = group.lastPostCreatedAt,
    )

    constructor(group: LimitedGroup) : this(
        groupId = group.id,
        name = group.name,
        shortCode = group.shortCode,
        discriminator = group.discriminator,
        description = group.description,
        rules = group.rules,
        membershipStatus = group.membershipStatus,
        isSearchable = group.isSearchable,
        iconUrl = group.iconUrl,
        bannerUrl = group.bannerUrl,
        iconId = group.iconId,
        bannerId = group.bannerId,
        memberCount = group.memberCount,
        tags = group.tags,
        galleries = group.galleries,
        ownerId = group.ownerId,
        createdAt = group.createdAt,
    )

    constructor(group: LimitedUserGroup) : this(
        groupId = group.groupId,
        name = group.name,
        shortCode = group.shortCode,
        description = group.description,
        iconUrl = group.iconUrl,
        bannerUrl = group.bannerUrl,
        memberCount = group.memberCount,
    )
}
