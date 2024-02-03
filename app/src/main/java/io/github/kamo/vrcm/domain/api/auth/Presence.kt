package io.github.kamo.vrcm.domain.api.auth

data class Presence(
    val groups: List<Any>,
    val id: String,
    val instance: String,
    val instanceType: String,
    val platform: String,
    val status: String,
    val travelingToInstance: String,
    val travelingToWorld: String,
    val world: String
)