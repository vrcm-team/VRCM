package io.github.vrcmteam.vrcm.network.api.auth.data

import kotlinx.serialization.Serializable

/** Origin of a pending device login; createdAt is an epoch timestamp in milliseconds. */
@Serializable
data class DeviceAuthInfoData(
    val createdAt: Long,
    val geolocation: DeviceAuthGeolocationData? = null,
)

@Serializable
data class DeviceAuthGeolocationData(
    val city: String? = null,
    val country: String? = null,
    val region: String? = null,
    val regionCode: String? = null,
)
