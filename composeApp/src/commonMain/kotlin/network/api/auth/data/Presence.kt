package io.github.vrcmteam.vrcm.network.api.auth.data

import cafe.adriel.voyager.core.lifecycle.JavaSerializable
import kotlinx.serialization.Serializable

@Serializable
data class Presence(
    val avatarThumbnail: String?,
    val displayName: String?,
    val groups: List<String>,
    val id: String,
    val instance: String,
    val instanceType: String,
    val isRejoining: String?,
    val platform: String,
    val profilePicOverride: String?,
    val status: String,
    val travelingToInstance: String,
    val travelingToWorld: String,
    val world: String
) : JavaSerializable