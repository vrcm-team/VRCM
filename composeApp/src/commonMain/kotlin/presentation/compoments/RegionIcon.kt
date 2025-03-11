package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.attributes.RegionType
import io.github.vrcmteam.vrcm.presentation.supports.RegionIcons
import org.jetbrains.compose.resources.painterResource

@Composable
fun RegionIcon(
    modifier: Modifier = Modifier,
    size: Dp = 15.dp,
    region: RegionType,
) {
    Image(
        painter = painterResource(RegionIcons[region]) ,
        contentDescription = "RegionIcon",
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(
                1.dp,
                MaterialTheme.colorScheme.surfaceContainerHighest,
                CircleShape
            ),
        contentScale = ContentScale.FillWidth
    )
}