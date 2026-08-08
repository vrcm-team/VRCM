package io.github.vrcmteam.vrcm.presentation.screens.meetup

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** 二维码类型的短标识；品牌名不翻译，四种语言取值相同。 */
@Composable
private fun MeetupQrLinkType.shortLabel(): String = when (this) {
    MeetupQrLinkType.VrchatWeb -> strings.meetupCardQrLabelVrchat
    MeetupQrLinkType.VrcmDeepLink -> strings.meetupCardQrLabelVrcm
}

/**
 * 白底 + 安静区的二维码槽位；深浅照片上均可扫描。
 * [showLabel] 时在白底内部附一行短标识——同时展示两个码时必须能分清哪个是哪个，
 * 标识放在白底内而不是卡片背景上，才能在任意照片之上都保持可读。
 */
@Composable
fun MeetupCardQrCode(
    userId: String,
    linkType: MeetupQrLinkType,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
) {
    val label = linkType.shortLabel()
    Surface(
        modifier = modifier,
        color = Color.White,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = rememberQrCodePainter(meetupCardProfileUrl(userId, linkType)),
                contentDescription = "${strings.meetupCardQrDescription} · $label",
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )
            if (showLabel) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                    ),
                    color = Color.Black.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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
    // 只有一个码时不存在混淆，省掉标识让二维码本身更大。
    val showLabel = linkTypes.size > 1
    val spacing = Arrangement.spacedBy(8.dp)
    val itemModifier = Modifier.width(size)
    if (vertical) {
        Column(modifier = modifier, verticalArrangement = spacing) {
            linkTypes.forEach { linkType ->
                MeetupCardQrCode(userId, linkType, itemModifier, showLabel)
            }
        }
    } else {
        Row(modifier = modifier, horizontalArrangement = spacing) {
            linkTypes.forEach { linkType ->
                MeetupCardQrCode(userId, linkType, itemModifier, showLabel)
            }
        }
    }
}
