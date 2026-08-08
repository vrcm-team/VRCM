package io.github.vrcmteam.vrcm.presentation.screens.meetup

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

/** 二维码去向的短标识，只用于无障碍朗读；品牌名不翻译，四种语言取值相同。 */
@Composable
private fun MeetupQrTarget.shortLabel(): String = when (this) {
    is MeetupQrTarget.Builtin -> when (type) {
        MeetupQrLinkType.VrchatWeb -> strings.meetupCardQrLabelVrchat
        MeetupQrLinkType.VrcmDeepLink -> strings.meetupCardQrLabelVrcm
    }
    is MeetupQrTarget.ProfileLink -> meetupCardLinkLabel(url)
}

/**
 * 角标图标是区分多个码的唯一标识：VRChat 主页用人像，VRCM 直达用手机（装了应用才跳得动），
 * 资料链接复用资料页的站点图标，认不出的站点退回通用链接图标。
 */
private fun MeetupQrTarget.badgeIcon(): ImageVector = when (this) {
    is MeetupQrTarget.Builtin -> when (type) {
        MeetupQrLinkType.VrchatWeb -> AppIcons.AccountCircle
        MeetupQrLinkType.VrcmDeepLink -> AppIcons.Smartphone
    }
    is MeetupQrTarget.ProfileLink -> WebIcons.selectIcon(url) ?: AppIcons.Link
}

/** 站点标识徽标直径；压在白底边角的安静区上，不遮二维码定位点。 */
private val MeetupQrBadgeSize = 18.dp

/**
 * 白底 + 安静区的二维码槽位；深浅照片上均可扫描。
 * 右上角挂一枚角标图标说明这个码通向哪里——图标比一行文字省地方，
 * 也不会在窄栏里被压得读不出来。
 */
@Composable
internal fun MeetupCardQrCode(
    userId: String,
    target: MeetupQrTarget,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Surface(
            color = Color.White,
            shape = MaterialTheme.shapes.small,
        ) {
            Image(
                painter = rememberQrCodePainter(target.payload(userId)),
                contentDescription = "${strings.meetupCardQrDescription} · ${target.shortLabel()}",
                modifier = Modifier.padding(6.dp).fillMaxWidth().aspectRatio(1f),
            )
        }
        // 角标画在白底之外的角上：二维码右上角是定位点，压住就扫不出来了。
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
                imageVector = target.badgeIcon(),
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.padding(3.dp),
            )
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
    val spacing = Arrangement.spacedBy(8.dp)
    val itemModifier = Modifier.width(size).testTag(MeetupCardTestTags.QrCode)
    if (vertical) {
        FlowColumn(
            modifier = modifier,
            verticalArrangement = spacing,
            horizontalArrangement = spacing,
        ) {
            targets.forEach { target ->
                MeetupCardQrCode(userId, target, itemModifier)
            }
        }
    } else {
        FlowRow(
            modifier = modifier,
            horizontalArrangement = spacing,
            verticalArrangement = spacing,
        ) {
            targets.forEach { target ->
                MeetupCardQrCode(userId, target, itemModifier)
            }
        }
    }
}
