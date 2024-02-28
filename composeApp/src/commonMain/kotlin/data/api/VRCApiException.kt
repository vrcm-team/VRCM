package io.github.vrcmteam.vrcm.data.api

class VRCApiException(override val message: String, val code: Int = -1) : RuntimeException(message)