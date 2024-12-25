package io.github.vrcmteam.vrcm.presentation.supports

import io.github.vrcmteam.vrcm.network.api.attributes.RegionType
import io.github.vrcmteam.vrcm.network.api.attributes.RegionType.*
import org.jetbrains.compose.resources.DrawableResource
import vrcm.composeapp.generated.resources.Res
import vrcm.composeapp.generated.resources.region_eu
import vrcm.composeapp.generated.resources.region_jp
import vrcm.composeapp.generated.resources.region_us

object RegionIcons {

    operator fun get(region: RegionType): DrawableResource =
        when (region) {
            Us -> Res.drawable.region_us
            Use -> Res.drawable.region_us
            Eu -> Res.drawable.region_eu
            Jp -> Res.drawable.region_jp
            Unknown -> Res.drawable.region_us
        }

}