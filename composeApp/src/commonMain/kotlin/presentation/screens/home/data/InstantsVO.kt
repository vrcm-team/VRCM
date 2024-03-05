package io.github.vrcmteam.vrcm.presentation.screens.home.data

import io.github.vrcmteam.vrcm.network.api.attributes.AccessType

data class InstantsVO(
    val worldName: String = "",
    val worldImageUrl: String? = null,
    val accessType: AccessType? = null,
    val regionIconUrl: String? = null,
    val userCount: String = "",
)