package io.github.vrcmteam.vrcm.network.supports

class VRCApiException(val description: String, val code: Int = -1, val bodyText: String) :
    RuntimeException(bodyText.ifEmpty { description })