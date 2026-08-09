package io.github.vrcmteam.vrcm.presentation.screens.meetup

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
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
import io.github.vrcmteam.vrcm.presentation.screens.meetup.animation.DecorationVisual
import io.github.vrcmteam.vrcm.presentation.screens.meetup.animation.DecorationVisualImage
import org.koin.compose.koinInject
import io.github.vrcmteam.vrcm.presentation.supports.LanguageIcons
import io.github.vrcmteam.vrcm.presentation.theme.GameColor
import io.github.vrcmteam.vrcm.service.meetup.DecorationSlot
import io.github.vrcmteam.vrcm.service.meetup.ResolvedDecoration
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardTemplate
import io.github.vrcmteam.vrcm.storage.meetup.MeetupGroupDisplayStyle
import io.github.vrcmteam.vrcm.storage.meetup.MeetupGroupSnapshot
import io.github.vrcmteam.vrcm.storage.meetup.resolvedGroupDisplayStyle
import io.github.vrcmteam.vrcm.storage.meetup.resolvedQrLinkTypes
import io.github.vrcmteam.vrcm.storage.meetup.resolvedQrProfileLinks
import io.github.vrcmteam.vrcm.storage.meetup.templateFor
import io.github.vrcmteam.vrcm.storage.meetup.MeetupOrientation

/** 供布局回归测试定位关键槽位；模板改版时这些名字必须跟着语义走。 */
internal object MeetupCardTestTags {
    const val Nameplate = "meetup-card-nameplate"
    const val InfoBarMedia = "meetup-card-info-bar-media"
    const val InfoBarPanel = "meetup-card-info-bar-panel"
    const val GroupBanner = "meetup-card-group-banner"
    const val GroupBannerImage = "meetup-card-group-banner-image"
    const val GroupIcon = "meetup-card-group-icon"
    const val GroupName = "meetup-card-group-name"
    const val QrCodes = "meetup-card-qr"
    /** 单个二维码；码会换行/换列，只量容器边界看不出有没有被挤掉。 */
    const val QrCode = "meetup-card-qr-item"
    const val Fields = "meetup-card-fields"
    const val SideBand = "meetup-card-side-band"
}

/** 模板内容使用的物理安全边距；聚光与侧签背景仍全出血，资料栏按图片区裁切。 */
@Immutable
private data class MeetupContentInsets(
    val left: Dp = 0.dp,
    val top: Dp = 0.dp,
    val right: Dp = 0.dp,
    val bottom: Dp = 0.dp,
)

private fun MeetupContentInsets.withMinimum(
    left: Dp,
    top: Dp,
    right: Dp,
    bottom: Dp,
): MeetupContentInsets = MeetupContentInsets(
    left = maxOf(this.left, left),
    top = maxOf(this.top, top),
    right = maxOf(this.right, right),
    bottom = maxOf(this.bottom, bottom),
)

private fun Modifier.meetupContentPadding(insets: MeetupContentInsets): Modifier = absolutePadding(
    left = insets.left,
    top = insets.top,
    right = insets.right,
    bottom = insets.bottom,
)

/** 用户选择的主题色；照片会盖住底层背景，因此模板前景也要用它才可见。 */
private val MeetupCardUiState.accentColor: Color get() = Color(config.accentArgb)

/** 主题色上的可读前景色。 */
private fun Color.contrastingContent(): Color =
    if (luminance() > 0.5f) Color.Black.copy(alpha = 0.86f) else Color.White

/** 深色遮罩混入主题色，让主题色在照片之上依然成立。 */
private fun Color.tintedScrim(alpha: Float): Color =
    lerp(Color.Black, this, 0.28f).copy(alpha = alpha)

/**
 * 卡片层已经解码好的装饰画面。装饰不在模板里播放：切换版式会重建整棵模板树，
 * 播放器跟着重建就要在主线程等解码器放锁，再从第 0 帧重放整段动画。
 */
@Immutable
internal data class MeetupDecorationVisuals(
    val iconFrame: DecorationVisual? = null,
    val nameplateEffect: DecorationVisual? = null,
)

/** 按模板与方向渲染身份信息层；三套模板共享同一批槽位组件。 */
@Composable
internal fun MeetupCardTemplateContent(
    state: MeetupCardUiState,
    orientation: MeetupOrientation,
    modifier: Modifier = Modifier,
    decorations: MeetupDecorationVisuals = MeetupDecorationVisuals(),
    contentPadding: PaddingValues = PaddingValues(),
    infoBarMedia: (@Composable BoxScope.() -> Unit)? = null,
) {
    val layoutDirection = LocalLayoutDirection.current
    val contentInsets = MeetupContentInsets(
        left = contentPadding.calculateLeftPadding(layoutDirection),
        top = contentPadding.calculateTopPadding(),
        right = contentPadding.calculateRightPadding(layoutDirection),
        bottom = contentPadding.calculateBottomPadding(),
    )
    when (state.config.templateFor(orientation)) {
        MeetupCardTemplate.InfoBar ->
            InfoBarTemplate(state, orientation, decorations, contentInsets, infoBarMedia, modifier)
        MeetupCardTemplate.Spotlight ->
            SpotlightTemplate(state, orientation, decorations, contentInsets, modifier)
        MeetupCardTemplate.SideTag ->
            SideTagTemplate(state, orientation, decorations, contentInsets, modifier)
    }
}

/** 资料栏：照片区与信息面板明确分区，可稳定容纳全部字段与二维码。 */
@Composable
private fun InfoBarTemplate(
    state: MeetupCardUiState,
    orientation: MeetupOrientation,
    decorations: MeetupDecorationVisuals,
    contentInsets: MeetupContentInsets,
    media: (@Composable BoxScope.() -> Unit)?,
    modifier: Modifier,
) {
    val bodyInsets = when (orientation) {
        // 面板位于底部，不需要为屏幕顶部留出空白。
        MeetupOrientation.Portrait -> contentInsets.copy(top = 0.dp)
        // 面板位于右侧，左侧切口不会覆盖到它。
        MeetupOrientation.Landscape -> contentInsets.copy(left = 0.dp)
    }.withMinimum(left = 16.dp, top = 16.dp, right = 16.dp, bottom = 16.dp)
    val panelShape = when (orientation) {
        MeetupOrientation.Portrait -> RoundedCornerShape(
            topStart = 8.dp,
            topEnd = 8.dp,
        )
        MeetupOrientation.Landscape -> RoundedCornerShape(
            topStart = 8.dp,
            bottomStart = 8.dp,
        )
    }
    val panel: @Composable (Modifier) -> Unit = { panelModifier ->
        Surface(
            modifier = panelModifier.testTag(MeetupCardTestTags.InfoBarPanel),
            shape = panelShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        ) {
            Box {
                // 细主题色边线保留模板识别，不再用大面积主题色染整块资料区。
                Box(
                    modifier = Modifier
                        .align(
                            if (orientation == MeetupOrientation.Portrait) {
                                Alignment.TopCenter
                            } else {
                                Alignment.CenterStart
                            },
                        )
                        .let { rail ->
                            if (orientation == MeetupOrientation.Portrait) {
                                rail.fillMaxWidth().height(3.dp)
                            } else {
                                rail.fillMaxHeight().width(3.dp)
                            }
                        }
                        .background(state.accentColor),
                )
                Column(
                    modifier = Modifier
                        .let { content ->
                            if (orientation == MeetupOrientation.Landscape) {
                                content.fillMaxHeight()
                            } else {
                                content
                            }
                        }
                        .meetupContentPadding(bodyInsets),
                    verticalArrangement = Arrangement.spacedBy(
                        space = 12.dp,
                        alignment = if (orientation == MeetupOrientation.Landscape) {
                            Alignment.CenterVertically
                        } else {
                            Alignment.Top
                        },
                    ),
                ) {
                    // 群组位于资料栏顶部，展示内容由用户设置决定。
                    MeetupRepresentedGroup(state, MeetupGroupStyle.Header)
                    // 名字独占整行，不与二维码抢横向空间。
                    MeetupNameplateBlock(state, decorations, onPhoto = false)
                    MeetupInfoAndQrRow(
                        state = state,
                        onPhoto = false,
                        shortTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    when (orientation) {
        MeetupOrientation.Portrait -> Column(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .testTag(MeetupCardTestTags.InfoBarMedia),
                content = { media?.invoke(this) },
            )
            panel(Modifier.fillMaxWidth())
        }
        MeetupOrientation.Landscape -> Row(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clipToBounds()
                    .testTag(MeetupCardTestTags.InfoBarMedia),
                content = { media?.invoke(this) },
            )
            // 360dp 能容纳两列二维码和可读字段，同时让横屏主体始终占据更多画面。
            panel(Modifier.fillMaxHeight().width(MeetupInfoBarLandscapeWidth))
        }
    }
}

/** 横屏资料栏的稳定宽度；小于视口约束时 Compose 会自动收窄。 */
private val MeetupInfoBarLandscapeWidth = 360.dp

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
    decorations: MeetupDecorationVisuals,
    contentInsets: MeetupContentInsets,
    modifier: Modifier,
) {
    val resolvedInsets = contentInsets.withMinimum(
        left = 24.dp,
        top = 28.dp,
        right = 24.dp,
        bottom = 28.dp,
    )
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
                modifier = Modifier.meetupContentPadding(resolvedInsets),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MeetupRepresentedGroup(state, MeetupGroupStyle.Inset)
                // 名字与状态等副行独占铭牌；语言与二维码在下方同一 Row 并列。
                MeetupNameplateBlock(
                    state = state,
                    decorations = decorations,
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
 * 铭牌为 Column(头像, Row(状态副行, 名字))，状态项共用一列，名字独占一列。
 * 竖屏把二维码压在信息下方；横屏侧签只有一屏高，竖着堆会把二维码挤没，
 * 因此横屏改为二维码单独占最左一列，信息列排在它右边。
 */
@Composable
private fun SideTagTemplate(
    state: MeetupCardUiState,
    orientation: MeetupOrientation,
    decorations: MeetupDecorationVisuals,
    contentInsets: MeetupContentInsets,
    modifier: Modifier,
) {
    val landscape = orientation == MeetupOrientation.Landscape
    val showQrCode = state.config.showQrCode
    val qrColumnWidth = MeetupQrSize + MeetupQrColumnGap
    val bandWidth = when {
        // 横屏多留出二维码那一列的宽度，信息列可用宽度与竖屏保持一致。
        landscape -> 200.dp + if (showQrCode) qrColumnWidth else 0.dp
        // 竖屏留够两列码的宽度：码放满时高度不够，得靠换列才能全部显示完整。
        else -> 176.dp
    }
    val bandInsets = contentInsets.copy(right = 0.dp).withMinimum(
        left = 16.dp,
        top = 16.dp,
        right = 16.dp,
        bottom = 16.dp,
    )
    val qrCodes: @Composable (Modifier) -> Unit = { qrModifier ->
        if (showQrCode) {
            MeetupCardQrCodes(
                userId = state.ownerUserId,
                linkTypes = state.config.resolvedQrLinkTypes(),
                size = MeetupQrSize,
                profileLinks = state.config.resolvedQrProfileLinks(),
                vertical = true,
                modifier = qrModifier.testTag(MeetupCardTestTags.QrCodes),
            )
        }
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
            val info: @Composable ColumnScope.() -> Unit = {
                MeetupRepresentedGroup(
                    state = state,
                    style = MeetupGroupStyle.Compact,
                )
                // 书签式竖向铭牌：头像在上，状态项共用一列，名字独占一列。
                MeetupNameplateBlock(
                    state = state,
                    decorations = decorations,
                    onPhoto = true,
                    vertical = true,
                    modifier = Modifier.heightIn(max = nameplateMaxHeight),
                )
                MeetupFieldsFlow(state, onPhoto = true)
                MeetupShortText(state, color = Color.White.copy(alpha = 0.92f))
            }
            if (landscape) {
                Row(
                    modifier = Modifier.fillMaxHeight().meetupContentPadding(bandInsets),
                    horizontalArrangement = Arrangement.spacedBy(MeetupQrColumnGap),
                ) {
                    qrCodes(Modifier)
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        content = info,
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxHeight().meetupContentPadding(bandInsets),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    info()
                    Spacer(modifier = Modifier.weight(1f))
                    qrCodes(Modifier)
                }
            }
        }
    }
}

/** 横屏侧签中二维码列与信息列之间的间距。 */
private val MeetupQrColumnGap = 12.dp

@Composable
private fun MeetupNameColor(onPhoto: Boolean): Color =
    if (onPhoto) Color.White else MaterialTheme.colorScheme.onSurface

/** 头像与可选的官方头像框；关闭头像时头像框也随之不显示。 */
@Composable
private fun MeetupAvatarBlock(state: MeetupCardUiState, decorations: MeetupDecorationVisuals) {
    if (!state.config.showAvatar) return
    val avatarUrl = state.config.profile.avatarUrl.takeIf(String::isNotBlank) ?: return
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
        DecorationVisualImage(
            visual = decorations.iconFrame,
            contentScale = ContentScale.Fit,
            modifier = Modifier.matchParentSize(),
        )
    }
}

/**
 * 铭牌块：官方铭牌特效与渐变包裹整块内容，与 VRChat 铭牌结构一致。
 * 横排为 Row(头像, Column(名字, 副信息组))；[vertical] 时版式为
 * Column(头像, Row(副信息组, 名字))，副信息组与名字各自顺时针旋转 90 度竖向排布；
 * 副信息组内的人称代词与状态共用一列。横版特效素材同样旋转铺满，头像保持正脸。
 * 副信息内容见 [MeetupNameplateMeta]。
 */
@Composable
private fun MeetupNameplateBlock(
    state: MeetupCardUiState,
    decorations: MeetupDecorationVisuals,
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
        nameplate?.let {
            DecorationVisualImage(
                visual = decorations.nameplateEffect,
                contentScale = ContentScale.Crop,
                // 官方素材是横版设计：竖向铭牌把它顺时针旋转 90 度后铺满。
                modifier = Modifier.matchParentSize()
                    .let { base -> if (vertical) base.rotateClockwise() else base },
            )
        }
        // 铭牌现在总有底色，内容始终留出内边距。
        val contentModifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        if (vertical) {
            // 副信息先横排再整体旋转，因此人称代词与状态会落在同一竖列；
            // 名字保留独立列，头像仍在最上方保持正脸。
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = contentModifier,
            ) {
                MeetupAvatarBlock(state, decorations)
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
                MeetupAvatarBlock(state, decorations)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
 * 铭牌副信息：人称代词与状态分别成组，避免全开时变成一串缺少层级的文本。
 * 横排先放稳定的身份信息，再用剩余宽度展示状态；竖向铭牌会把这一整行旋转，
 * 因而人称代词与状态仍然落在同一列，不会横向扩出额外列。
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
    val items: @Composable () -> Unit = {
        pronouns?.let { value -> MeetupNameplateMetaChip(value, color) }
        if (status != null || statusDescription != null) {
            MeetupNameplateStatus(
                status = status,
                description = statusDescription,
                color = color,
            )
        }
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = { items() },
    )
}

/** 人称代词作为独立身份标签，不再与临时状态依靠标点硬拼在一起。 */
@Composable
private fun MeetupNameplateMetaChip(
    text: String,
    color: Color,
) {
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = CircleShape,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

/** 状态类型与描述合成一个视觉单元；描述不足空间时由它先截断。 */
@Composable
private fun MeetupNameplateStatus(
    status: UserStatus?,
    description: String?,
    color: Color,
) {
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = CircleShape,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (description == null) 5.dp else 7.dp,
                vertical = 2.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            status?.let { value ->
                // 与好友列表同一套状态色标，颜色承担状态类型，不重复英文状态名。
                Box(
                    modifier = Modifier
                        .size(MeetupStatusDotSize)
                        .background(GameColor.Status.fromValue(value), CircleShape)
                        .border(1.dp, color.copy(alpha = 0.35f), CircleShape),
                )
            }
            description?.let { value ->
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 状态圆点直径；标签已经提供留白，圆点只需承担颜色识别。 */
private val MeetupStatusDotSize = 8.dp

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

/** 群组所在区域；只影响图标行的配色与密度，不覆盖用户选择的内容形式。 */
private enum class MeetupGroupStyle {
    Header,
    Inset,
    Compact,
}

/**
 * 主选群组按配置展示封面或图标名称行。旧配置仍按模板沿用原有样式；
 * 封面缺失时回退图标名称行，避免渲染空占位。
 */
@Composable
private fun MeetupRepresentedGroup(
    state: MeetupCardUiState,
    style: MeetupGroupStyle,
) {
    val config = state.config
    if (!config.showRepresentedGroup) return
    val group = config.profile.representedGroup ?: return
    if (group.name.isBlank() && group.bannerUrl.isBlank() && group.iconUrl.isBlank()) return
    val template = when (style) {
        MeetupGroupStyle.Header -> MeetupCardTemplate.InfoBar
        MeetupGroupStyle.Inset -> MeetupCardTemplate.Spotlight
        MeetupGroupStyle.Compact -> MeetupCardTemplate.SideTag
    }
    val showBanner = config.resolvedGroupDisplayStyle(template) == MeetupGroupDisplayStyle.Banner &&
        group.bannerUrl.isNotBlank()
    if (showBanner) {
        MeetupGroupCover(group)
        return
    }
    when (style) {
        MeetupGroupStyle.Header -> MeetupGroupHeaderFallback(group)
        MeetupGroupStyle.Inset -> MeetupGroupChip(group, prominent = true)
        MeetupGroupStyle.Compact -> MeetupGroupChip(group, prominent = false)
    }
}

/** 群组封面原样展示，不叠加遮罩或重复的图标与名称。 */
@Composable
private fun MeetupGroupCover(
    group: MeetupGroupSnapshot,
) {
    Box(
        modifier = Modifier
            .testTag(MeetupCardTestTags.GroupBanner)
            .height(MeetupGroupBannerHeight)
            .aspectRatio(MeetupGroupBannerAspectRatio),
    ) {
        AsyncImage(
            model = group.bannerUrl,
            contentDescription = group.name.takeIf(String::isNotBlank),
            imageLoader = koinInject<ImageLoader>(),
            contentScale = ContentScale.Fit,
            modifier = Modifier.matchParentSize().testTag(MeetupCardTestTags.GroupBannerImage),
        )
    }
}

/** 资料栏中的图标名称行，也作为封面缺失时的回退。 */
@Composable
private fun MeetupGroupHeaderFallback(group: MeetupGroupSnapshot) {
    Row(
        modifier = Modifier
            .testTag(MeetupCardTestTags.GroupBanner)
            .widthIn(max = MeetupGroupFallbackMaxWidth)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MeetupGroupIcon(group, size = MeetupGroupIconSize, onPhoto = false)
        MeetupGroupName(
            group = group,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** 照片与侧栏区域使用紧凑身份行。 */
@Composable
private fun MeetupGroupChip(group: MeetupGroupSnapshot, prominent: Boolean) {
    Surface(
        modifier = Modifier.testTag(MeetupCardTestTags.GroupBanner),
        color = Color.Black.copy(alpha = 0.24f),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (prominent) 10.dp else 8.dp,
                vertical = if (prominent) 6.dp else 4.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MeetupGroupIcon(
                group = group,
                size = if (prominent) MeetupGroupIconSize else MeetupGroupChipIconSize,
                onPhoto = true,
            )
            MeetupGroupName(group, Color.White, Modifier.weight(1f, fill = false))
        }
    }
}

@Composable
private fun MeetupGroupIcon(group: MeetupGroupSnapshot, size: Dp, onPhoto: Boolean) {
    val iconUrl = group.iconUrl.takeIf(String::isNotBlank) ?: return
    Surface(
        modifier = Modifier.size(size).testTag(MeetupCardTestTags.GroupIcon),
        color = if (onPhoto) {
            Color.White.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        shape = CircleShape,
    ) {
        AsyncImage(
            model = iconUrl,
            contentDescription = null,
            imageLoader = koinInject<ImageLoader>(),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(2.dp),
        )
    }
}

@Composable
private fun MeetupGroupName(
    group: MeetupGroupSnapshot,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (group.name.isBlank()) return
    Text(
        text = group.name,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.testTag(MeetupCardTestTags.GroupName),
    )
}

/**
 * 群组尺寸承担稳定布局契约：资料栏 Banner 固定高度并保持 21:9，
 * 缺图回退行可适当放宽，侧栏 chip 只占一行。
 */
private const val MeetupGroupBannerAspectRatio = 21f / 9f
private val MeetupGroupBannerHeight = 48.dp
private val MeetupGroupFallbackMaxWidth = 180.dp
private val MeetupGroupIconSize = 24.dp
private val MeetupGroupChipIconSize = 18.dp

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
