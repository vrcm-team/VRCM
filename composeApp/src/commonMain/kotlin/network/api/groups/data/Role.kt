package io.github.vrcmteam.vrcm.network.api.groups.data

import kotlinx.serialization.Serializable

@Serializable
data class Role(
    val createdAt: String,
    val defaultRole: Boolean,
    val description: String,
    val groupId: String,
    val id: String,
    val isAddedOnJoin: Boolean,
    val isManagementRole: Boolean,
    val isSelfAssignable: Boolean,
    val name: String,
    val order: Int,
    val permissions: List<String>,
    val requiresPurchase: Boolean,
    val requiresTwoFactor: Boolean
)