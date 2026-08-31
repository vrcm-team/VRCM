package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.core.extensions.toLocalDateTime
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.attributes.lastSeenAt
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.network.api.files.data.PlatformType.*
import io.github.vrcmteam.vrcm.network.api.groups.data.LimitedGroup
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.presentation.extensions.ignoredFormat
import io.github.vrcmteam.vrcm.presentation.navigation.rememberContainerTransformToken
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.service.platformPackages

/**
 * 用户列表渲染
 */
fun LazyListScope.renderUserItems(
    users: List<IUser>,
    onUserClick: (IUser, String) -> Unit
) {
    items(users, key = { it.id }) { user ->
        renderUserItem(user, onUserClick)
    }
}

/**
 * 单个用户项渲染
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LazyItemScope.renderUserItem(
    user: IUser,
    onUserClick: (IUser, String) -> Unit
) {
    val sharedSuffixKey = rememberContainerTransformToken("user:${user.id}")
        ?: LocalSharedSuffixKey.current
    SearchResultItem(
        item = user,
        onClick = { onUserClick(it, sharedSuffixKey) },
        modifier = Modifier.animateItem(),
        leadingContent = {
            UserStateIcon(
                modifier = Modifier.sharedBoundsBy(
                    key = "${user.id}UserIcon",
                    suffixKey = sharedSuffixKey,
                ).size(48.dp),
                iconUrl = user.iconUrl,
            )
        },
        headlineContent = {
            UserInfoRow(
                iconSize = 16.dp,
                style = MaterialTheme.typography.titleMedium,
                user = user,
                sharedSuffixKey = sharedSuffixKey,
                pronouns = user.pronouns,
            )
        },
        supportingContent = {
            UserStatusRow(
                iconSize = 8.dp,
                style = MaterialTheme.typography.bodyMedium,
                user = user,
                sharedSuffixKey = sharedSuffixKey,
            )
        },
        trailingContent = {
            // 离线用户显示最后活动时间
            val lastSeenAt = user.lastSeenAt()
            if (user.status != UserStatus.Offline || lastSeenAt == null) return@SearchResultItem
            Text(
                text = lastSeenAt.toLocalDateTime()?.ignoredFormat.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    )
}

/**
 * 判断世界是否被隐藏（API对不可见世界返回id为"???"）
 */
fun WorldData.isHiddenWorld(): Boolean = id == "???"

/**
 * 获取世界的安全图片URL，空字符串视为无图片
 */
fun WorldData.safeImageUrl(): String? = imageUrl.ifBlank { null }

/**
 * 获取隐藏世界的显示名称（使用favoriteId替代"???"）
 */
fun WorldData.hiddenWorldDisplayName(): String = favoriteId ?: name

/**
 * 世界列表渲染
 */
fun LazyListScope.renderWorldItems(
    worlds: List<WorldData>,
    onWorldClick: (WorldData, String) -> Unit
) {
    items(worlds, key = { it.favoriteId ?: it.id }) { world ->
        renderWorldItem(world, onWorldClick)
    }
}

/**
 * 单个世界项渲染
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LazyItemScope.renderWorldItem(
    world: WorldData,
    onWorldClick: (WorldData, String) -> Unit
) {
    val sharedSuffixKey = rememberContainerTransformToken("world:${world.favoriteId ?: world.id}")
        ?: LocalSharedSuffixKey.current
    SearchResultItem(
        item = world,
        onClick = { onWorldClick(it, sharedSuffixKey) },
        modifier = Modifier.animateItem(),
        leadingContent = {
            if (world.isHiddenWorld()) {
                Box(
                    modifier = Modifier.sharedBoundsBy(
                        key = "${world.id}WorldImage",
                        suffixKey = sharedSuffixKey,
                    ).size(48.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.VisibilityOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                AImage(
                    modifier = Modifier.sharedBoundsBy(
                        key = "${world.id}WorldImage",
                        suffixKey = sharedSuffixKey,
                    ).size(48.dp)
                        .clip(MaterialTheme.shapes.medium),
                    imageData = world.safeImageUrl(),
                )
            }
        },
        headlineContent = {
            Text(
                text = if (world.isHiddenWorld()) world.hiddenWorldDisplayName() else world.name,
                style = MaterialTheme.typography.titleMedium,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
        },
        supportingContent = {
            Text(
                text = if (world.isHiddenWorld()) strings.hiddenWorld else world.authorName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
        },
        trailingContent = {
            // 显示世界平台类型
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ){
               remember { world.unityPackages.platformPackages.keys.sortedBy { it.name } } .forEach {
                    val icon = when(it){
                        Android -> AppIcons.Android
                        Ios -> AppIcons.Apple
                        Windows -> AppIcons.Windows
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "PlatformIcon",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

        }
    )
}

/**
 * 模型列表渲染
 */
fun LazyListScope.renderAvatarItems(
    avatars: List<AvatarData>,
    onAvatarClick: (AvatarData, String) -> Unit
) {
    items(avatars, key = { it.id }) { avatar ->
        renderAvatarItem(avatar, onAvatarClick)
    }
}

/**
 * 单个模型项渲染
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LazyItemScope.renderAvatarItem(
    avatar: AvatarData,
    onAvatarClick: (AvatarData, String) -> Unit
) {
    val sharedSuffixKey = rememberContainerTransformToken("avatar:${avatar.id}")
        ?: LocalSharedSuffixKey.current
    SearchResultItem(
        item = avatar,
        onClick = { onAvatarClick(it, sharedSuffixKey) },
        modifier = Modifier.animateItem(),
        leadingContent = {
            if (avatar.releaseStatus == "hidden") {
                Box(
                    modifier = Modifier.sharedBoundsBy(
                        key = "${avatar.id}AvatarImage",
                        suffixKey = sharedSuffixKey,
                    ).size(48.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.VisibilityOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                AImage(
                    modifier = Modifier.sharedBoundsBy(
                        key = "${avatar.id}AvatarImage",
                        suffixKey = sharedSuffixKey,
                    ).size(48.dp)
                        .clip(MaterialTheme.shapes.medium),
                    imageData = avatar.thumbnailImageUrl,
                )
            }
        },
        headlineContent = {
            Text(
                text = if (avatar.releaseStatus == "hidden") avatar.id else avatar.name,
                style = MaterialTheme.typography.titleMedium,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
        },
        supportingContent = {
            Text(
                text = if (avatar.releaseStatus == "hidden") strings.hiddenModel else avatar.authorName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
        },
        trailingContent = {
            // 显示模型平台类型
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                remember(avatar.unityPackages) {
                    avatar.unityPackages.mapNotNull { pkg ->
                        when (pkg.platform?.lowercase()) {
                            "android" -> Android
                            "ios" -> Ios
                            "standalonewindows", "windows" -> Windows
                            else -> null
                        }
                    }.distinct().sortedBy { it.name }
                }.forEach {
                    val icon = when (it) {
                        Android -> AppIcons.Android
                        Ios -> AppIcons.Apple
                        Windows -> AppIcons.Windows
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "PlatformIcon",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    )
}

/**
 * 群组列表渲染
 */
fun LazyListScope.renderGroupItems(
    groups: List<LimitedGroup>,
    onGroupClick: (LimitedGroup, String) -> Unit
) {
    items(groups, key = { it.id }) { group ->
        renderGroupItem(group, onGroupClick)
    }
}

/**
 * 单个群组项渲染
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LazyItemScope.renderGroupItem(
    group: LimitedGroup,
    onGroupClick: (LimitedGroup, String) -> Unit
) {
    val sharedSuffixKey = rememberContainerTransformToken("group:${group.id}")
        ?: LocalSharedSuffixKey.current
    SearchResultItem(
        item = group,
        onClick = { onGroupClick(it, sharedSuffixKey) },
        modifier = Modifier.animateItem(),
        leadingContent = {
            GroupIcon(
                iconUrl = group.iconUrl,
                modifier = Modifier.sharedBoundsBy(
                    key = "${group.id}GroupIcon",
                    suffixKey = sharedSuffixKey,
                ),
                size = 48.dp
            )
        },
        headlineContent = {
            Text(
                modifier = Modifier.sharedBoundsBy(
                    key = groupNameSharedKey(group.id),
                    suffixKey = sharedSuffixKey,
                    resizeMode = SharedTextBoundsResizeMode,
                ),
                text = group.name,
                style = MaterialTheme.typography.titleMedium,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
        },
        supportingContent = {
            Text(
                text = group.description,
                style = MaterialTheme.typography.bodyMedium,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
        },
        trailingContent = {
            // 显示成员数量
            Text(
                text = "${group.memberCount}",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    )
}
