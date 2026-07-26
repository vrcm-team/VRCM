package io.github.vrcmteam.vrcm.presentation.screens.avatar

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import io.github.vrcmteam.vrcm.core.extensions.toLocalDate
import io.github.vrcmteam.vrcm.presentation.compoments.ATooltipBox
import io.github.vrcmteam.vrcm.presentation.compoments.ProfileScaffold
import io.github.vrcmteam.vrcm.presentation.compoments.sharedBoundsBy
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.simpleClickable
import io.github.vrcmteam.vrcm.presentation.extensions.simpleFormat
import io.github.vrcmteam.vrcm.presentation.screens.avatar.data.AvatarPlatformInfo
import io.github.vrcmteam.vrcm.presentation.screens.avatar.data.AvatarProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons

class AvatarProfileScreen(
    private val avatarProfileVo: AvatarProfileVo,
    private val sharedSuffixKey: String = "",
) : Screen {

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    override fun Content() {
        val navigator = currentNavigator
        val screenModel: AvatarProfileScreenModel = koinScreenModel()
        val refreshedAvatar by screenModel.avatarProfileState.collectAsState()

        LaunchedEffect(avatarProfileVo.avatarId) {
            screenModel.refreshAvatarData(avatarProfileVo)
        }

        val displayedAvatar = refreshedAvatar ?: avatarProfileVo

        ProfileScaffold(
            imageModifier = Modifier.sharedBoundsBy("${displayedAvatar.avatarId}AvatarImage"),
            profileImageUrl = displayedAvatar.avatarImageUrl,
            iconUrl = null,
            onReturn = { navigator.pop() },
        ) { ratio, contentMinHeight ->
            AvatarProfileContent(
                avatarProfileVo = displayedAvatar,
                contentMinHeight = contentMinHeight,
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AvatarProfileContent(
    avatarProfileVo: AvatarProfileVo,
    contentMinHeight: Dp,
) {
    val navigator = currentNavigator

    // 名称
    SelectionContainer {
        Text(
            text = avatarProfileVo.avatarName,
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }

    // 作者
    if (avatarProfileVo.authorName.isNotBlank()) {
        Text(
            text = avatarProfileVo.authorName,
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.simpleClickable {
                navigator.push(
                    UserProfileScreen(
                        userProfileVO = UserProfileVo(id = avatarProfileVo.authorId)
                    )
                )
            }
        )
    }

    // 描述
    if (avatarProfileVo.avatarDescription.isNotBlank()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = MaterialTheme.shapes.extraLarge
        ) {
            SelectionContainer {
                Text(
                    modifier = Modifier.padding(12.dp),
                    text = avatarProfileVo.avatarDescription,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // 属性信息卡片
    AvatarInfoCards(avatarProfileVo)

    Spacer(modifier = Modifier.height(12.dp))

    // 平台信息（过滤没有适配的平台）
    val knownPlatforms = avatarProfileVo.platformInfos.filter {
        !it.performanceRating.isNullOrEmpty()
    }
    if (knownPlatforms.isNotEmpty()) {
        AvatarPlatformSection(knownPlatforms)
    }
}

@Composable
private fun AvatarInfoCards(avatarProfileVo: AvatarProfileVo) {
    val infoCards = mutableListOf<Triple<ImageVector, String, String>>()

    // 版本
    avatarProfileVo.version?.let {
        infoCards.add(Triple(AppIcons.Update, "v$it", strings.avatarProfileVersion))
    }

    // 发布状态
    infoCards.add(Triple(
        AppIcons.Visibility,
        avatarProfileVo.releaseStatus.replaceFirstChar { it.uppercase() },
        strings.avatarProfileStatus
    ))

    // 创建时间
    avatarProfileVo.createdAt?.takeIf { it.isNotEmpty() }?.toLocalDate()?.simpleFormat?.let {
        infoCards.add(Triple(AppIcons.Publish, it, strings.avatarProfileCreated))
    }

    // 更新时间
    avatarProfileVo.updatedAt?.takeIf { it.isNotEmpty() }?.toLocalDate()?.simpleFormat?.let {
        infoCards.add(Triple(AppIcons.DateRange, it, strings.avatarProfileUpdated))
    }

    val cardHeight = 68.dp
    val cardsPerRow = 4
    val rows = (infoCards.size + cardsPerRow - 1) / cardsPerRow
    val spacing = 8.dp

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cardWidth = (maxWidth - spacing * (cardsPerRow - 1)) / cardsPerRow
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            for (rowIndex in 0 until rows) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    for (colIndex in 0 until cardsPerRow) {
                        val cardIndex = rowIndex * cardsPerRow + colIndex
                        if (cardIndex < infoCards.size) {
                            val (icon, label, description) = infoCards[cardIndex]
                            AvatarInfoItemBlock(
                                modifier = Modifier.width(cardWidth).height(cardHeight),
                                icon = icon,
                                label = label,
                                description = description
                            )
                        } else {
                            Spacer(modifier = Modifier.width(cardWidth).height(cardHeight))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AvatarInfoItemBlock(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    description: String,
) {
    ATooltipBox(
        tooltip = {
            Text(text = description, style = MaterialTheme.typography.labelSmall)
        }
    ) {
        val bgColor = MaterialTheme.colorScheme.tertiary
        Column(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                tint = MaterialTheme.colorScheme.onPrimary,
                contentDescription = description,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AvatarPlatformSection(platformInfos: List<AvatarPlatformInfo>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = strings.avatarProfilePlatforms,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                platformInfos.forEach { info ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = info.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = info.ratingDisplay,
                            style = MaterialTheme.typography.labelMedium,
                            color = ratingColor(info.performanceRating)
                        )
                    }
                    if (info != platformInfos.last()) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 评级颜色
 */
@Composable
private fun ratingColor(rating: String?): androidx.compose.ui.graphics.Color {
    return when (rating?.lowercase()) {
        "excellent" -> androidx.compose.ui.graphics.Color(0xFF51E57E)
        "good" -> androidx.compose.ui.graphics.Color(0xFF51E57E)
        "medium" -> androidx.compose.ui.graphics.Color(0xFFFFD24C)
        "poor" -> MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
        "verypoor" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
}
