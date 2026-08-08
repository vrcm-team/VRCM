package io.github.vrcmteam.vrcm.presentation.screens.meetup

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings

/** 二维码只指向当前用户的公开 VRChat 主页，不接受任何其他链接。 */
internal fun meetupCardProfileUrl(userId: String): String {
    require(Regex("usr_[A-Za-z0-9-]+").matches(userId)) {
        "Meetup card QR payload only accepts a VRChat user ID"
    }
    return "https://vrchat.com/home/user/$userId"
}

/** 白底 + 8dp 安静区的稳定 1:1 二维码槽位；深浅照片上均可扫描。 */
@Composable
fun MeetupCardQrCode(userId: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.aspectRatio(1f),
        color = Color.White,
        shape = MaterialTheme.shapes.small,
    ) {
        Image(
            painter = rememberQrCodePainter(meetupCardProfileUrl(userId)),
            contentDescription = strings.meetupCardQrDescription,
            modifier = Modifier.padding(8.dp).fillMaxSize(),
        )
    }
}
