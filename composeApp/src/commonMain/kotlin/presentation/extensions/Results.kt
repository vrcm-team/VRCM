package io.github.vrcmteam.vrcm.presentation.extensions

import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.ktor.util.network.*

inline fun <T> Result<T>.onApiFailure(name: String, messageCall: (message: String) -> Unit): Result<T> =
    onFailure {
        "Failed to $name: ${
            when (it) {
                is UnresolvedAddressException -> "Unable to connect to the network"
                is VRCApiException -> "[${it.code}]${it.message}: ${it.bodyText}"
                else -> it.message ?: "Unknown Error"
            }
        }".let(messageCall)
        it.printStackTrace()
    }