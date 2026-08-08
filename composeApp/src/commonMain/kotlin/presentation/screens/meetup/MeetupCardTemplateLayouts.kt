package io.github.vrcmteam.vrcm.presentation.screens.meetup

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.presentation.screens.meetup.animation.AnimatedDecoration
import org.koin.compose.koinInject
import io.github.vrcmteam.vrcm.presentation.supports.LanguageIcons
import io.github.vrcmteam.vrcm.presentation.theme.GameColor
import io.github.vrcmteam.vrcm.service.meetup.DecorationSlot
import io.github.vrcmteam.vrcm.service.meetup.ResolvedDecoration
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardTemplate
import io.github.vrcmteam.vrcm.storage.meetup.resolvedQrLinkTypes
import io.github.vrcmteam.vrcm.storage.meetup.resolvedQrProfileLinks
import io.github.vrcmteam.vrcm.storage.meetup.templateFor
import io.github.vrcmteam.vrcm.storage.meetup.MeetupOrientation

/** 供布局回归测试定位关键槽位；模板改版时这些名字必须跟着语义走。 */
internal object MeetupCardTestTags {
    const val Nameplate = "meetup-card-nameplate"
    const val QrCodes = "meetup-card-qr"
    const val Fields = "meetup-card-fields"
    const val SideBand = "meetup-card-side-band"
}

/** 用户选择的主题色；照片会盖住底层背景，因此模板前景也要用它才可见。 */
private val MeetupCardUiState.accentColor: Color get() = Color(config.accentArgb)

/** 主题色上的可读前景色。 */
private fun Color.contrastingContent(): Color =
    if (luminance() > 0.5f) Color.Black.copy(alpha = 0.86f) else Color.White

/** 深色遮罩混入主题色，让主题色在照片之上依然成立。 */
private fun Color.tintedScrim(alpha: Float): Color =
    lerp(Color.Black, this, 0.28f).copy(alpha = alpha)

/** 按模板与方向渲染身份信息层；三套模板共享同一批槽位组件。 */
@Composable
internal fun MeetupCardTemplateContent(
    state: MeetupCardUiState,
    orientation: MeetupOrientation,
    modifier: Modifier = Modifier,
) {
    when (state.config.templateFor(orientation)) {
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
    val panel: @Composable (Modifier) -> Unit = { panelModifier ->
        Surface(
            modifier = panelModifier,
            // 资料面板整体带一点主题色，避免主题色设置看不出效果。
            color = lerp(MaterialTheme.colorScheme.surface, state.accentColor, 0.14f)
                .copy(alpha = 0.94f),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 名字独占整行，不与二维码抢横向空间。
                MeetupNameplateBlock(state, onPhoto = false)
                MeetupInfoAndQrRow(
                    state = state,
                    onPhoto = false,
                    shortTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    when (orientation) {
        MeetupOrientation.Portrait -> Column(modifier = modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.weight(1f))
            panel(Modifier.fillMaxWidth())
        }
        MeetupOrientation.Landscape -> Row(modifier = modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.weight(1f))
            panel(Modifier.fillMaxHeight().widthIn(max = 420.dp))
        }
    }
}

/** 语言字段与短句和二维码作为同一 Row 的同级子元素并列，标签顶部对齐。 */
@Composable
private fun MeetupInfoAndQrRow(
    state: MeetupCardUiState,
    onPhoto: Boolean,
    shortTextColor: Color,
    qrSize: Dp = MeetupQrSize,
) {
    Row(verticalAlignment = Alignment.Top) {
        Column(
            modifier = Modifier.weight(1f).testTag(MeetupCardTestTags.Fields),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MeetupFieldsFlow(state, onPhoto = onPhoto)
            MeetupShortText(state, color = shortTextColor)
        }
        if (state.config.showQrCode) {
            MeetupCardQrCodes(
                userId = state.ownerUserId,
                linkTypes = state.config.resolvedQrLinkTypes(),
                size = qrSize,
                profileLinks = state.config.resolvedQrProfileLinks(),
                // 二维码最多占两列，再多的码换行；否则窄卡片上状态与语言那一列会被挤没。
                modifier = Modifier
                    .padding(start = 16.dp)
                    .widthIn(max = qrSize * 2 + 8.dp)
                    .testTag(MeetupCardTestTags.QrCodes),
            )
        }
    }
}

/** 二维码保持够扫的最小尺寸即可，不与身份信息抢版面。 */
private val MeetupQrSize = 68.dp

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
                        colors = listOf(
                            Color.Transparent,
                            state.accentColor.tintedScrim(0.86f),
                        ),
                    ),
                ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 名字与状态等副行独占铭牌；语言与二维码在下方同一 Row 并列。
                MeetupNameplateBlock(
                    state = state,
                    onPhoto = true,
                    large = orientation == MeetupOrientation.Portrait,
                )
                MeetupInfoAndQrRow(
                    state = state,
                    onPhoto = true,
                    shortTextColor = Color.White.copy(alpha = 0.92f),
                )
            }
        }
    }
}

/**
 * 侧签：信息沿起始侧竖向集中，保留中央主体区域。
 * 铭牌为 Column(头像, Row(状态副行, 名字))，两列文字竖排；字段与二维码排在其下方。
 */
@Composable
private fun SideTagTemplate(
    state: MeetupCardUiState,
    orientation: MeetupOrientation,
    modifier: Modifier,
) {
    val bandWidth = when (orientation) {
        MeetupOrientation.Portrait -> 168.dp
        MeetupOrientation.Landscape -> 200.dp
    }
    Row(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .testTag(MeetupCardTestTags.SideBand)
                .fillMaxHeight()
                .width(bandWidth)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            state.accentColor.tintedScrim(0.78f),
                            Color.Transparent,
                        ),
                    ),
                ),
        ) {
            // 竖排文字高度由名字长度决定，限制上限避免顶掉下方字段与二维码。
            val nameplateMaxHeight = maxHeight * 0.55f
            Column(
                modifier = Modifier.fillMaxHeight().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 书签式竖向铭牌：头像在上，状态副行与名字两列竖排文字在下。
                MeetupNameplateBlock(
                    state = state,
                    onPhoto = true,
                    vertical = true,
                    modifier = Modifier.heightIn(max = nameplateMaxHeight),
                )
                MeetupFieldsFlow(state, onPhoto = true)
                MeetupShortText(state, color = Color.White.copy(alpha = 0.92f))
                Spacer(modifier = Modifier.weight(1f))
                if (state.config.showQrCode) {
                    MeetupCardQrCodes(
                        userId = state.ownerUserId,
                        linkTypes = state.config.resolvedQrLinkTypes(),
                        size = MeetupQrSize,
                        profileLinks = state.config.resolvedQrProfileLinks(),
                        vertical = true,
                        modifier = Modifier.testTag(MeetupCardTestTags.QrCodes),
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
    Box(
        modifier = Modifier.size(72.dp),
        contentAlignment = Alignment.Center,
    ) {
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

/**
 * 铭牌块：官方铭牌特效与渐变包裹整块内容，与 VRChat 铭牌结构一致。
 * 横排为 Row(头像, Column(名字, 副行))；[vertical] 时版式为
 * Column(头像, Row(副行, 名字))，副行与名字各自顺时针旋转 90 度竖向排布，
 * 横版特效素材同样旋转铺满，头像保持正脸。名字始终完整显示。
 * 副行内容见 [MeetupNameplateMeta]。
 */
@Composable
private fun MeetupNameplateBlock(
    state: MeetupCardUiState,
    onPhoto: Boolean,
    large: Boolean = false,
    vertical: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val nameplate = state.decorations[DecorationSlot.NameplateEffect]
        .takeIf { state.config.showNameplateEffect }
    val officialColors = nameplate?.let { decoration ->
        val start = parseHexColor(decoration.gradientStart)
        val end = parseHexColor(decoration.gradientEnd)
        if (start != null && end != null) {
            listOf(start.copy(alpha = 0.85f), end.copy(alpha = 0.85f))
        } else {
            null
        }
    }
    // 没有官方铭牌渐变时用主题色铺底，让名字真正落在一块"铭牌"上。
    val accent = state.accentColor
    val colors = officialColors
        ?: listOf(accent.copy(alpha = 0.92f), accent.copy(alpha = 0.66f))
    // 渐变方向跟随铭牌朝向：竖向铭牌自上而下。
    val gradient = if (vertical) {
        Brush.verticalGradient(colors)
    } else {
        Brush.horizontalGradient(colors)
    }
    // 铭牌上固定使用与底色对比的前景色。
    val nameColor = if (officialColors != null) Color.White else accent.contrastingContent()
    Box(
        modifier = modifier
            .testTag(MeetupCardTestTags.Nameplate)
            .clip(MaterialTheme.shapes.small)
            .background(gradient),
    ) {
        nameplate?.let { decoration ->
            AnimatedDecoration(
                decoration = decoration,
                contentScale = ContentScale.Crop,
                // 官方素材是横版设计：竖向铭牌把它顺时针旋转 90 度后铺满。
                modifier = Modifier.matchParentSize()
                    .let { base -> if (vertical) base.rotateClockwise() else base },
            )
        }
        // 铭牌现在总有底色，内容始终留出内边距。
        val contentModifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        if (vertical) {
            // 版式为 Column(头像, Row(副行, 名字))；两者各自旋转 90 度竖排，
            // 于是副行与名字成为并排的两列，头像仍在最上方保持正脸。
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = contentModifier,
            ) {
                MeetupAvatarBlock(state)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MeetupNameplateMeta(
                        state = state,
                        color = nameColor,
                        modifier = Modifier.rotateClockwise(),
                    )
                    Text(
                        text = state.displayName,
                        style = displayNameStyle(state.displayName, large),
                        color = nameColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.rotateClockwise(),
                    )
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = contentModifier,
            ) {
                MeetupAvatarBlock(state)
                Column {
                    Text(
                        text = state.displayName,
                        style = displayNameStyle(state.displayName, large),
                        color = nameColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MeetupNameplateMeta(state = state, color = nameColor)
                }
            }
        }
    }
}

/**
 * 铭牌副行：状态圆点与状态描述依次排在人称代词前面，紧贴名字。
 * 竖排铭牌由调用方整体旋转，这一行随之成为人称代词所在的那一列。
 */
@Composable
private fun MeetupNameplateMeta(
    state: MeetupCardUiState,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val config = state.config
    val status = config.profile.status
        .takeIf { config.showStatus && it.isNotBlank() }
        ?.let { value -> UserStatus.entries.firstOrNull { it.value == value } }
    val statusDescription = config.profile.statusDescription
        .takeIf { config.showStatusDescription && it.isNotBlank() }
    val pronouns = config.profile.pronouns
        .takeIf { config.showPronouns && it.isNotBlank() }
    if (status == null && statusDescription == null && pronouns == null) return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        status?.let { value ->
            // 与好友列表同一套状态色标：铭牌上只留圆点，不再重复一遍状态文案。
            Box(
                modifier = Modifier
                    .size(MeetupStatusDotSize)
                    .background(GameColor.Status.fromValue(value), CircleShape)
                    .border(1.dp, color.copy(alpha = 0.35f), CircleShape),
            )
        }
        statusDescription?.let { value ->
            // 状态描述是这行里唯一可能很长的一段，压缩空间由它让出。
            MeetupNameplateMetaText(value, color, Modifier.weight(1f, fill = false))
        }
        if ((status != null || statusDescription != null) && pronouns != null) {
            MeetupNameplateMetaText("·", color, alpha = 0.5f)
        }
        pronouns?.let { value -> MeetupNameplateMetaText(value, color) }
    }
}

/** 铭牌副行文本的统一样式。 */
@Composable
private fun MeetupNameplateMetaText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    alpha: Float = 0.82f,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = color.copy(alpha = alpha),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** 状态圆点直径；与铭牌副行的文字高度相称。 */
private val MeetupStatusDotSize = 10.dp

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

/**
 * 语言用资料页同一套国旗图标呈现，比语言码更省横向空间；
 * 没有对应国旗的语言退回文字胶囊，不静默丢字段。
 * 人称代词与状态已并入铭牌块。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeetupFieldsFlow(state: MeetupCardUiState, onPhoto: Boolean) {
    val config = state.config
    if (!config.showLanguages || config.profile.languages.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        config.profile.languages.forEach { language ->
            val flag = LanguageIcons.getFlag(language)
            if (flag == null) {
                MeetupFieldChip(text = language, onPhoto = onPhoto)
            } else {
                Image(
                    imageVector = flag,
                    contentDescription = language,
                    contentScale = ContentScale.FillBounds,
                    // 照片之上白色系国旗容易糊掉边界，统一描一圈细边。
                    modifier = Modifier
                        .size(width = MeetupFlagWidth, height = MeetupFlagHeight)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .border(
                            width = 1.dp,
                            color = if (onPhoto) {
                                Color.White.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = MaterialTheme.shapes.extraSmall,
                        ),
                )
            }
        }
    }
}

/** 国旗尺寸沿用 [LanguageIcons] 素材的 22:16 比例。 */
private val MeetupFlagWidth = 22.dp
private val MeetupFlagHeight = 16.dp

/** 字段胶囊：照片之上用半透明黑底保证可读。 */
@Composable
private fun MeetupFieldChip(text: String, onPhoto: Boolean) {
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
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
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
