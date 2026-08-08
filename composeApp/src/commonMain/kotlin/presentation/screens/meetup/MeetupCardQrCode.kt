package io.github.vrcmteam.vrcm.presentation.screens.meetup

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowColumn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.presentation.supports.WebIcons
import io.github.vrcmteam.vrcm.storage.meetup.MeetupQrLinkType
import io.ktor.http.URLBuilder

/** 身份卡上一个二维码槽位的内容来源。 */
internal sealed interface MeetupQrTarget {
    /** 内置链接：当前用户的 VRChat 主页或 VRCM 直达。 */
    data class Builtin(val type: MeetupQrLinkType) : MeetupQrTarget

    /** 用户 VRChat 资料里已有的外部链接。 */
    data class ProfileLink(val url: String) : MeetupQrTarget
}

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

/**
 * 资料链接二维码的内容：只接受 http(s) 网址。
 * 取值范围已由 `resolvedQrProfileLinks()` 限定在用户自己的 VRChat 资料链接内，
 * 这里再挡一次协议，避免把 javascript: 之类的内容编进可扫描的码里。
 */
internal fun meetupCardProfileLinkUrl(url: String): String {
    val trimmed = url.trim()
    require(trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
        "Meetup card QR payload only accepts http(s) profile links"
    }
    return trimmed
}

/** 二维码实际编码的内容。 */
private fun MeetupQrTarget.payload(userId: String): String = when (this) {
    is MeetupQrTarget.Builtin -> meetupCardProfileUrl(userId, type)
    is MeetupQrTarget.ProfileLink -> meetupCardProfileLinkUrl(url)
}

/**
 * 资料链接的短标识：站点域名足以让人认出这是哪个平台。
 * 资料链接是用户自由填写的文本，解析不出主机名时原样显示，不能让编辑器崩掉。
 */
internal fun meetupCardLinkLabel(url: String): String =
    runCatching { URLBuilder(url).host.removePrefix("www.") }
        .getOrNull()
        ?.takeIf(String::isNotBlank)
        ?: url

/** 二维码类型的短标识；品牌名不翻译，四种语言取值相同。 */
@Composable
private fun MeetupQrTarget.shortLabel(): String = when (this) {
    is MeetupQrTarget.Builtin -> when (type) {
        MeetupQrLinkType.VrchatWeb -> strings.meetupCardQrLabelVrchat
        MeetupQrLinkType.VrcmDeepLink -> strings.meetupCardQrLabelVrcm
    }
    is MeetupQrTarget.ProfileLink -> meetupCardLinkLabel(url)
}

/** 资料链接复用资料页的站点图标；认不出的站点退回通用链接图标。 */
private fun MeetupQrTarget.badgeIcon(): ImageVector? = when (this) {
    is MeetupQrTarget.Builtin -> null
    is MeetupQrTarget.ProfileLink -> WebIcons.selectIcon(url) ?: AppIcons.Link
}

/** 站点标识徽标直径；压在白底边角的安静区上，不遮二维码定位点。 */
private val MeetupQrBadgeSize = 18.dp

/**
 * 白底 + 安静区的二维码槽位；深浅照片上均可扫描。
 * [showLabel] 时在白底内部附一行短标识——同时展示多个码时必须能分清哪个是哪个，
 * 标识放在白底内而不是卡片背景上，才能在任意照片之上都保持可读。
 * 资料链接的码额外在右上角挂一枚站点图标徽标，扫之前就知道通向哪里。
 */
@Composable
internal fun MeetupCardQrCode(
    userId: String,
    target: MeetupQrTarget,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
) {
    val label = target.shortLabel()
    Box(modifier = modifier) {
        Surface(
            color = Color.White,
            shape = MaterialTheme.shapes.small,
        ) {
            Column(
                modifier = Modifier.padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = rememberQrCodePainter(target.payload(userId)),
                    contentDescription = "${strings.meetupCardQrDescription} · $label",
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
                if (showLabel) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = Color.Black.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        target.badgeIcon()?.let { icon ->
            // 徽标画在白底之外的角上：二维码右上角是定位点，压住就扫不出来了。
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = MeetupQrBadgeSize / 3, y = -MeetupQrBadgeSize / 3)
                    .size(MeetupQrBadgeSize),
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 2.dp,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.padding(3.dp),
                )
            }
        }
    }
}

/**
 * 同时展示多个二维码：内置链接在前，资料链接按选中顺序排在后面。
 * 用 Flow 布局排布，码变多时换行/换列而不是溢出卡片；
 * [vertical] 用于侧签等窄栏。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeetupCardQrCodes(
    userId: String,
    linkTypes: List<MeetupQrLinkType>,
    size: Dp,
    profileLinks: List<String> = emptyList(),
    vertical: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val targets = linkTypes.map(MeetupQrTarget::Builtin) +
        profileLinks.map(MeetupQrTarget::ProfileLink)
    if (targets.isEmpty()) return
    // 多个码并列时必须能分清；只有 VRCM 直达时也要标注——没装 VRCM 的人
    // 扫了打不开，有个标识至少知道原因，而不是以为码坏了。
    val onlyVrchatWeb = (targets.singleOrNull() as? MeetupQrTarget.Builtin)
        ?.type == MeetupQrLinkType.VrchatWeb
    val showLabel = !onlyVrchatWeb
    val spacing = Arrangement.spacedBy(8.dp)
    val itemModifier = Modifier.width(size)
    if (vertical) {
        FlowColumn(
            modifier = modifier,
            verticalArrangement = spacing,
            horizontalArrangement = spacing,
        ) {
            targets.forEach { target ->
                MeetupCardQrCode(userId, target, itemModifier, showLabel)
            }
        }
    } else {
        FlowRow(
            modifier = modifier,
            horizontalArrangement = spacing,
            verticalArrangement = spacing,
        ) {
            targets.forEach { target ->
                MeetupCardQrCode(userId, target, itemModifier, showLabel)
            }
        }
    }
}
