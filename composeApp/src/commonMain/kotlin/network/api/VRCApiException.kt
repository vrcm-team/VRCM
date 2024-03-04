package io.github.vrcmteam.vrcm.network.api

class VRCApiException(override val message: String, val code: Int = -1) : RuntimeException(message)