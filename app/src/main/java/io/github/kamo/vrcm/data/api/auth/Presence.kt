package io.github.kamo.vrcm.data.api.auth

data class Presence(
    val avatarThumbnail: String,
    val displayName: String,
    val groups: List<String>,
    val id: String,
    val instance: String,
    val instanceType: String,
    val isRejoining: String,
    val platform: String,
    val profilePicOverride: String,
    val status: String,
    val travelingToInstance: String,
    val travelingToWorld: String,
    val world: String
)