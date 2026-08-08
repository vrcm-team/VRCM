package io.github.vrcmteam.vrcm.presentation.screens.meetup

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.storage.meetup.MeetupQrLinkType

/** 二维码只指向当前用户的公开 VRChat 主页或 VRCM 直达链接，不接受任何其他内容。 */
internal fun meetupCardProfileUrl(
    userId: String,
    linkType: MeetupQrLinkType = MeetupQrLinkType.VrchatWeb,
): String {
    require(Regex("usr_[A-Za-z0-9-]+").matches(userId)) {
        "Meetup card QR payload only accepts a VRChat user ID"
    }
    return when (linkType) {
        MeetupQrLinkType.VrchatWeb -> "https://vrchat.com/home/user/$userId"
        MeetupQrLinkType.VrcmDeepLink -> "vrcm://user/$userId"
    }
}

/** 白底 + 8dp 安静区的稳定 1:1 二维码槽位；深浅照片上均可扫描。 */
@Composable
fun MeetupCardQrCode(
    userId: String,
    linkType: MeetupQrLinkType,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.aspectRatio(1f),
        color = Color.White,
        shape = MaterialTheme.shapes.small,
    ) {
        Image(
            painter = rememberQrCodePainter(meetupCardProfileUrl(userId, linkType)),
            contentDescription = strings.meetupCardQrDescription,
            modifier = Modifier.padding(8.dp).fillMaxSize(),
        )
    }
}

/** 同时展示多种链接类型的二维码；纵向排布用于侧签等窄栏。 */
@Composable
fun MeetupCardQrCodes(
    userId: String,
    linkTypes: List<MeetupQrLinkType>,
    size: Dp,
    vertical: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (linkTypes.isEmpty()) return
    val spacing = Arrangement.spacedBy(8.dp)
    if (vertical) {
        Column(modifier = modifier, verticalArrangement = spacing) {
            linkTypes.forEach { linkType ->
                MeetupCardQrCode(userId, linkType, Modifier.requiredSize(size))
            }
        }
    } else {
        Row(modifier = modifier, horizontalArrangement = spacing) {
            linkTypes.forEach { linkType ->
                MeetupCardQrCode(userId, linkType, Modifier.requiredSize(size))
            }
        }
    }
}
