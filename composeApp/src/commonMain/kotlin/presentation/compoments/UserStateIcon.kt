package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.presentation.extensions.drawSateCircle
import io.github.vrcmteam.vrcm.presentation.theme.GameColor

@Composable
fun UserStateIcon(
    modifier: Modifier = Modifier,
    iconUrl: String?,
    userStatus: UserStatus
) {
     AImage(
        modifier = Modifier
            .drawSateCircle(GameColor.Status.fromValue(userStatus))
            .then(modifier)
            .aspectRatio(1f)
            .clip(CircleShape),
        imageData = iconUrl,
        contentDescription = "UserStateIcon"
    )
}