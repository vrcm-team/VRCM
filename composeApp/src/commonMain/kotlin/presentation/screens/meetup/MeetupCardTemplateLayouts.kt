package io.github.vrcmteam.vrcm.presentation.screens.meetup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import io.github.vrcmteam.vrcm.presentation.screens.meetup.animation.AnimatedDecoration
import org.koin.compose.koinInject
import io.github.vrcmteam.vrcm.service.meetup.DecorationSlot
import io.github.vrcmteam.vrcm.service.meetup.ResolvedDecoration
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardTemplate
import io.github.vrcmteam.vrcm.storage.meetup.MeetupOrientation

/** 按模板与方向渲染身份信息层；三套模板共享同一批槽位组件。 */
@Composable
internal fun MeetupCardTemplateContent(
    state: MeetupCardUiState,
    orientation: MeetupOrientation,
    modifier: Modifier = Modifier,
) {
    when (state.config.template) {
        MeetupCardTemplate.InfoBar -> InfoBarTemplate(state, orientation, modifier)
        MeetupCardTemplate.Spotlight -> SpotlightTemplate(state, orientation, modifier)
        MeetupCardTemplate.SideTag -> SideTagTemplate(state, orientation, modifier)
    }
}

/** 资料栏：照片区与信息面板明确分区，可稳定容纳全部字段与二维码。 */
@Composable
private fun InfoBarTemplate(
    state: MeetupCardUiState,
    orientation: MeetupOrientation,
    modifier: Modifier,
) {
    val panel: @Composable (Modifier, Boolean) -> Unit = { panelModifier, includeQr ->
        Surface(
            modifier = panelModifier,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MeetupAvatarBlock(state)
                        MeetupNameplate(
                            state = state,
                            color = MeetupNameColor(onPhoto = false),
                        )
                    }
                    MeetupFieldsFlow(state, onPhoto = false)
                    MeetupShortText(state, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (includeQr && state.config.showQrCode) {
                    MeetupCardQrCode(
                        userId = state.ownerUserId,
                        linkType = state.config.qrLinkType,
                        modifier = Modifier.padding(start = 16.dp).requiredSize(92.dp),
                    )
                }
            }
        }
    }
    when (orientation) {
        // 竖屏空间窄：二维码浮在面板上方右侧，不与名字抢横向空间。
        MeetupOrientation.Portrait -> Column(modifier = modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.weight(1f))
            if (state.config.showQrCode) {
                MeetupCardQrCode(
                    userId = state.ownerUserId,
                    linkType = state.config.qrLinkType,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(end = 20.dp, bottom = 10.dp)
                        .requiredSize(96.dp),
                )
            }
            panel(Modifier.fillMaxWidth(), false)
        }
        MeetupOrientation.Landscape -> Row(modifier = modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.weight(1f))
            panel(Modifier.fillMaxHeight().widthIn(max = 420.dp), true)
        }
    }
}

/** 聚光：照片全屏铺设，信息位于底部高对比遮罩，优先远距离识别。 */
@Composable
private fun SpotlightTemplate(
    state: MeetupCardUiState,
    orientation: MeetupOrientation,
    modifier: Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f)),
                    ),
                ),
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp)) {
                // 竖屏空间窄：二维码放在内容上方右侧，名字保持整行宽度。
                if (state.config.showQrCode && orientation == MeetupOrientation.Portrait) {
                    MeetupCardQrCode(
                        userId = state.ownerUserId,
                        linkType = state.config.qrLinkType,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(bottom = 12.dp)
                            .requiredSize(96.dp),
                    )
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MeetupAvatarBlock(state)
                        MeetupNameplate(
                            state = state,
                            color = MeetupNameColor(onPhoto = true),
                            large = orientation == MeetupOrientation.Portrait,
                        )
                        MeetupFieldsFlow(state, onPhoto = true)
                        MeetupShortText(state, color = Color.White.copy(alpha = 0.92f))
                    }
                    if (state.config.showQrCode && orientation == MeetupOrientation.Landscape) {
                        MeetupCardQrCode(
                            userId = state.ownerUserId,
                            linkType = state.config.qrLinkType,
                            modifier = Modifier.padding(start = 16.dp).requiredSize(96.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 侧签：信息沿起始侧集中，保留中央主体区域。 */
@Composable
private fun SideTagTemplate(
    state: MeetupCardUiState,
    orientation: MeetupOrientation,
    modifier: Modifier,
) {
    val bandWidth = when (orientation) {
        MeetupOrientation.Portrait -> 180.dp
        MeetupOrientation.Landscape -> 248.dp
    }
    Row(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(bandWidth)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent),
                    ),
                ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MeetupAvatarBlock(state)
                MeetupNameplate(state = state, color = MeetupNameColor(onPhoto = true))
                MeetupFieldsFlow(state, onPhoto = true)
                MeetupShortText(state, color = Color.White.copy(alpha = 0.92f))
                Spacer(modifier = Modifier.weight(1f))
                if (state.config.showQrCode) {
                    MeetupCardQrCode(
                        userId = state.ownerUserId,
                        linkType = state.config.qrLinkType,
                        modifier = Modifier.requiredSize(88.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MeetupNameColor(onPhoto: Boolean): Color =
    if (onPhoto) Color.White else MaterialTheme.colorScheme.onSurface

/** 头像与可选的官方头像框；关闭头像时头像框也随之不显示。 */
@Composable
private fun MeetupAvatarBlock(state: MeetupCardUiState) {
    if (!state.config.showAvatar) return
    val avatarUrl = state.config.profile.avatarUrl.takeIf(String::isNotBlank) ?: return
    val frame = state.decorations[DecorationSlot.IconFrame]
        .takeIf { state.config.showIconFrame }
    Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            imageLoader = koinInject<ImageLoader>(),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(CircleShape),
        )
        frame?.let { decoration ->
            AnimatedDecoration(
                decoration = decoration,
                contentScale = ContentScale.Fit,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** Display Name 与官方铭牌特效/渐变；名字始终完整显示，最多两行。 */
@Composable
private fun MeetupNameplate(
    state: MeetupCardUiState,
    color: Color,
    large: Boolean = false,
) {
    val nameplate = state.decorations[DecorationSlot.NameplateEffect]
        .takeIf { state.config.showNameplateEffect }
    val gradient = nameplate?.let { decoration ->
        val start = parseHexColor(decoration.gradientStart)
        val end = parseHexColor(decoration.gradientEnd)
        if (start != null && end != null) {
            Brush.horizontalGradient(listOf(start.copy(alpha = 0.85f), end.copy(alpha = 0.85f)))
        } else {
            null
        }
    }
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .let { base -> gradient?.let { base.background(it) } ?: base },
    ) {
        nameplate?.let { decoration ->
            AnimatedDecoration(
                decoration = decoration,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        Text(
            text = state.displayName,
            style = displayNameStyle(state.displayName, large),
            // 有铭牌装饰时固定使用高对比前景色。
            color = if (nameplate != null) Color.White else color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(
                horizontal = if (nameplate != null) 10.dp else 0.dp,
                vertical = if (nameplate != null) 4.dp else 0.dp,
            ),
        )
    }
}

/** 在 Material 离散字号档位间按名字长度选择，不随视口连续缩放。 */
@Composable
private fun displayNameStyle(name: String, large: Boolean): TextStyle {
    val base = when {
        name.length <= 14 -> if (large) {
            MaterialTheme.typography.displaySmall
        } else {
            MaterialTheme.typography.headlineMedium
        }
        name.length <= 24 -> MaterialTheme.typography.headlineSmall
        else -> MaterialTheme.typography.titleLarge
    }
    return base.copy(
        fontWeight = FontWeight.Bold,
        shadow = Shadow(color = Color.Black.copy(alpha = 0.45f), offset = Offset(0f, 2f), blurRadius = 6f),
    )
}

/** 可选字段按优先级换行排布，不覆盖名字与二维码。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeetupFieldsFlow(state: MeetupCardUiState, onPhoto: Boolean) {
    val config = state.config
    val fields = buildList {
        if (config.showPronouns && config.profile.pronouns.isNotBlank()) {
            add(config.profile.pronouns)
        }
        if (config.showLanguages && config.profile.languages.isNotEmpty()) {
            add(config.profile.languages.joinToString(" / "))
        }
        if (config.showStatus && config.profile.status.isNotBlank()) {
            add(config.profile.status)
        }
        if (config.showStatusDescription && config.profile.statusDescription.isNotBlank()) {
            add(config.profile.statusDescription)
        }
    }
    if (fields.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        fields.forEach { field ->
            Surface(
                color = if (onPhoto) {
                    Color.Black.copy(alpha = 0.4f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = if (onPhoto) {
                    Color.White
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = field,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** 自定义短句：最多两行，超限在编辑器侧已被拒绝。 */
@Composable
private fun MeetupShortText(state: MeetupCardUiState, color: Color) {
    if (!state.config.showShortText || state.config.shortText.isBlank()) return
    Text(
        text = state.config.shortText,
        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
        color = color,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 解析 #RRGGBB / #AARRGGBB；非法值返回 null 让调用方放弃渐变。 */
internal fun parseHexColor(value: String): Color? {
    val hex = value.trim().removePrefix("#")
    val argb = when (hex.length) {
        6 -> hex.toLongOrNull(16)?.or(0xFF000000L)
        8 -> hex.toLongOrNull(16)
        else -> null
    } ?: return null
    return Color(argb)
}
