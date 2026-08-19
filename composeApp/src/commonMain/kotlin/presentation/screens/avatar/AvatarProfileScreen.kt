package io.github.vrcmteam.vrcm.presentation.screens.avatar

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import io.github.vrcmteam.vrcm.presentation.navigation.AppDetailRoute
import org.koin.compose.viewmodel.koinViewModel
import io.github.vrcmteam.vrcm.presentation.navigation.LocalNavigator
import io.github.vrcmteam.vrcm.core.extensions.toLocalDate
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.presentation.compoments.ATooltipBox
import io.github.vrcmteam.vrcm.presentation.compoments.LocalSharedSuffixKey
import io.github.vrcmteam.vrcm.presentation.compoments.OfficialUrlShareButton
import io.github.vrcmteam.vrcm.presentation.compoments.ProfileScaffold
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.compoments.sharedBoundsBy
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.simpleClickable
import io.github.vrcmteam.vrcm.presentation.extensions.simpleFormat
import io.github.vrcmteam.vrcm.presentation.favorites.FavoriteEntryState
import io.github.vrcmteam.vrcm.presentation.screens.avatar.data.AvatarPlatformInfo
import io.github.vrcmteam.vrcm.presentation.screens.avatar.data.AvatarProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.ImageEditorTarget
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PrintImageEditorScreen
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PrintImageEditorSessionStore
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PrintImageProcessor
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.handoffPreparedImageToEditor
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.world.components.FavoriteGroupBottomSheet
import io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStrings
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

internal fun AvatarProfileNotice.localizedToast(locale: LocaleStrings): ToastText = when (this) {
    AvatarProfileNotice.Banned -> ToastText.Error(locale.avatarProfileBanned)
    AvatarProfileNotice.Switched -> ToastText.Success(locale.avatarProfileSwitched)
    AvatarProfileNotice.Copied -> ToastText.Success(locale.avatarProfileCopied)
    is AvatarProfileNotice.SelectionFailed -> ToastText.Error(
        message ?: locale.avatarProfileSelectFailed
    )
    AvatarProfileNotice.InvalidName -> ToastText.Error(locale.avatarEditInvalidName)
    AvatarProfileNotice.NoMetadataChanges -> ToastText.Info(locale.avatarEditNoChanges)
    AvatarProfileNotice.MetadataSaved -> ToastText.Success(locale.avatarEditMetadataSaved)
    is AvatarProfileNotice.MetadataSaveFailed -> ToastText.Error(
        message ?: locale.avatarEditMetadataSaveFailed
    )
    AvatarProfileNotice.CoverSaved -> ToastText.Success(locale.avatarEditCoverSaved)
}

internal fun AvatarActionAvailability.localizedButtonText(locale: LocaleStrings): String = when (this) {
    AvatarActionAvailability.Checking -> locale.avatarProfileActionChecking
    AvatarActionAvailability.Current -> locale.avatarProfileActionCurrent
    AvatarActionAvailability.Banned -> locale.avatarProfileBanned
    AvatarActionAvailability.Own -> locale.avatarProfileActionSwitch
    AvatarActionAvailability.Copyable -> locale.avatarProfileActionSwitch
    AvatarActionAvailability.NotCopyable -> locale.avatarProfileActionNotCopyable
    AvatarActionAvailability.CheckFailed -> locale.avatarProfileActionCheckFailed
}

@Serializable
class AvatarProfileScreen(
    private val avatarProfileVo: AvatarProfileVo,
    private val sharedSuffixKey: String = "",
    private val sharedImageCacheKey: String? = null,
) : AppDetailRoute {

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    override fun Content() {
        val navigator = currentNavigator
        val screenModel: AvatarProfileScreenModel = koinViewModel()
        val imageProcessor: PrintImageProcessor = koinInject()
        val editorSessionStore: PrintImageEditorSessionStore = koinInject()
        val refreshedAvatar by screenModel.avatarProfileState.collectAsState()
        val actionState by screenModel.actionState.collectAsState()
        val editState by screenModel.editState.collectAsState()
        val avatarCoverUpdates by editorSessionStore.avatarCoverUpdates.collectAsState()
        val favoriteEntryState by screenModel.favoriteEntryState.collectAsState()
        val locale = strings
        var showEditSheet by remember { mutableStateOf(false) }
        var showFavoriteSheet by remember { mutableStateOf(false) }

        LaunchedEffect(screenModel, locale) {
            screenModel.notices.collect { notice ->
                SharedFlowCentre.toastText.emit(notice.localizedToast(locale))
            }
        }

        LaunchedEffect(avatarProfileVo.avatarId) {
            screenModel.refreshAvatarData(avatarProfileVo)
        }

        LaunchedEffect(editState.canEdit) {
            if (!editState.canEdit) showEditSheet = false
        }

        val displayedAvatar = refreshedAvatar ?: avatarProfileVo
        LaunchedEffect(displayedAvatar.avatarId, avatarCoverUpdates) {
            val updated = avatarCoverUpdates[displayedAvatar.avatarId]
                ?: return@LaunchedEffect
            if (screenModel.applyCoverUpdate(updated)) {
                editorSessionStore.consumeAvatarCoverUpdate(updated.id)
            }
        }

        CompositionLocalProvider(LocalSharedSuffixKey provides sharedSuffixKey) {
            ProfileScaffold(
                imageModifier = Modifier.sharedBoundsBy("${displayedAvatar.avatarId}AvatarImage"),
                profileImageUrl = displayedAvatar.avatarImageUrl,
                iconUrl = displayedAvatar.avatarImageUrl,
                sharedImageCacheKey = sharedImageCacheKey,
                onReturn = { navigator.pop() },
                topBarActions = { colors ->
                    OfficialUrlShareButton(
                        url = "https://vrchat.com/home/avatar/${displayedAvatar.avatarId}",
                        colors = colors,
                    )
                },
            ) { ratio, contentMinHeight ->
                AvatarProfileContent(
                    avatarProfileVo = displayedAvatar,
                    contentMinHeight = contentMinHeight,
                    actionState = actionState,
                    onSelectAvatar = screenModel::selectAvatar,
                    onFavorite = { showFavoriteSheet = true },
                    favoriteEntryState = favoriteEntryState,
                    onRetryFavorite = screenModel::retryFavoriteEntryLoad,
                    canEdit = editState.canEdit,
                    onEdit = { showEditSheet = true },
                )
            }
        }
        FavoriteGroupBottomSheet(
            isVisible = showFavoriteSheet,
            favoriteId = displayedAvatar.avatarId,
            favoriteType = FavoriteType.Avatar,
            onDismiss = { showFavoriteSheet = false },
        )
        if (showEditSheet && editState.canEdit) {
            AvatarEditSheet(
                avatar = displayedAvatar,
                state = editState,
                imageProcessor = imageProcessor,
                onDismiss = { showEditSheet = false },
                onSaveMetadata = screenModel::saveMetadata,
                onEditCover = { source, prepared ->
                    handoffPreparedImageToEditor(
                        source = source,
                        prepared = prepared,
                        sessionStore = editorSessionStore,
                        target = ImageEditorTarget.AvatarCover(displayedAvatar.avatarId),
                        push = { sessionId ->
                            navigator.push(PrintImageEditorScreen(sessionId))
                            showEditSheet = false
                        },
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AvatarProfileContent(
    avatarProfileVo: AvatarProfileVo,
    contentMinHeight: Dp,
    actionState: AvatarActionState,
    onSelectAvatar: () -> Unit,
    onFavorite: () -> Unit,
    favoriteEntryState: FavoriteEntryState,
    onRetryFavorite: () -> Unit,
    canEdit: Boolean,
    onEdit: () -> Unit,
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
                        userProfileVO = UserProfileVo(
                            id = avatarProfileVo.authorId,
                            displayName = avatarProfileVo.authorName,
                        )
                    )
                )
            }
        )
    }

    AvatarActionButton(
        state = actionState,
        onClick = onSelectAvatar,
    )

    OutlinedButton(
        onClick = {
            if (favoriteEntryState == FavoriteEntryState.LoadFailed) {
                onRetryFavorite()
            } else {
                onFavorite()
            }
        },
        enabled = favoriteEntryState != FavoriteEntryState.Loading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = AppIcons.Favorite,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            when (favoriteEntryState) {
                FavoriteEntryState.Loading -> strings.loading
                FavoriteEntryState.Favorited -> strings.editFavorite
                FavoriteEntryState.NotFavorited -> strings.favoriteAvatar
                FavoriteEntryState.LoadFailed -> strings.retry
            }
        )
    }

    if (canEdit) {
        FilledTonalButton(
            onClick = onEdit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = AppIcons.Settings,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(strings.avatarEditTitle)
        }
        Spacer(Modifier.height(12.dp))
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
private fun AvatarActionButton(
    state: AvatarActionState,
    onClick: () -> Unit,
) {
    val availability = state.availability
    val enabled = !state.isSelecting && (
        availability == AvatarActionAvailability.Own ||
            availability == AvatarActionAvailability.Copyable
        )
    val showProgress = state.isSelecting || availability == AvatarActionAvailability.Checking
    val icon = when (availability) {
        AvatarActionAvailability.Current -> AppIcons.CheckCircle
        AvatarActionAvailability.Own -> AppIcons.Update
        AvatarActionAvailability.Copyable -> AppIcons.Queue
        AvatarActionAvailability.Banned,
        AvatarActionAvailability.NotCopyable -> AppIcons.Block
        AvatarActionAvailability.Checking,
        AvatarActionAvailability.CheckFailed -> AppIcons.QuestionMark
    }

    Button(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        enabled = enabled,
        onClick = onClick,
    ) {
        if (showProgress) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = LocalContentColor.current,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = availability.localizedButtonText(strings),
            textAlign = TextAlign.Center,
        )
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
