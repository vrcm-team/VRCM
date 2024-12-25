package io.github.vrcmteam.vrcm.presentation.supports

import io.github.vrcmteam.vrcm.network.api.attributes.RegionType
import org.jetbrains.compose.resources.DrawableResource
import vrcm.composeapp.generated.resources.Res
import vrcm.composeapp.generated.resources.region_eu
import vrcm.composeapp.generated.resources.region_jp
import vrcm.composeapp.generated.resources.region_us

object RegionIcons {

    operator fun get(region: RegionType): DrawableResource =
        when (region) {
            RegionType.Us -> Res.drawable.region_us
            RegionType.Use -> Res.drawable.region_us
            RegionType.Eu -> Res.drawable.region_eu
            RegionType.Jp -> Res.drawable.region_jp
        }

}