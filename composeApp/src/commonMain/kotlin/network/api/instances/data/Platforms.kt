package io.github.vrcmteam.vrcm.network.api.instances.data

import cafe.adriel.voyager.core.lifecycle.JavaSerializable
import kotlinx.serialization.Serializable

@Serializable
data class Platforms(
    val android: Int,
    val standalonewindows: Int
) : JavaSerializable