package io.github.vrcmteam.vrcm.network.api.groups.data

import kotlinx.serialization.Serializable

@Serializable
data class Gallery(
    val createdAt: String,
    val description: String,
    val id: String,
    val membersOnly: Boolean,
    val name: String,
    val updatedAt: String
)