package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.presentation.extensions.ignoredFormat
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * 用户列表渲染
 */
fun LazyListScope.renderUserItems(
    users: List<IUser>,
    onUserClick: (IUser) -> Unit
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
    onUserClick: (IUser) -> Unit
) {
    SearchResultItem(
        item = user,
        onClick = onUserClick,
        leadingContent = {
            UserStateIcon(
                modifier = Modifier.sharedBoundsBy("${user.id}UserIcon"),
                iconUrl = user.iconUrl,
            )
        },
        headlineContent = {
            UserInfoRow(
                iconSize = 16.dp,
                style = MaterialTheme.typography.titleMedium,
                user = user
            )
        },
        supportingContent = {
            UserStatusRow(
                iconSize = 8.dp,
                style = MaterialTheme.typography.bodyMedium,
                user = user
            )
        },
        trailingContent = {
            // 离线用户显示最后登录时间
            val lastLoginStr = user.lastLogin
            if (user.status != io.github.vrcmteam.vrcm.network.api.attributes.UserStatus.Offline || lastLoginStr.isNullOrEmpty()) return@SearchResultItem
            val lastLogin = Instant.parse(lastLoginStr).toLocalDateTime(TimeZone.currentSystemDefault())
            Text(
                text = lastLogin.ignoredFormat, 
                style = MaterialTheme.typography.labelSmall, 
                maxLines = 1
            )
        }
    )
}

/**
 * 世界列表渲染
 */
fun LazyListScope.renderWorldItems(
    worlds: List<WorldData>,
    onWorldClick: (WorldData) -> Unit
) {
    items(worlds, key = { it.id }) { world ->
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
    onWorldClick: (WorldData) -> Unit
) {
    SearchResultItem(
        item = world,
        onClick = onWorldClick,
        leadingContent = {
            WorldThumbnail(
                modifier = Modifier.sharedBoundsBy("${world.id}WorldThumbnail"),
                thumbnailUrl = world.thumbnailImageUrl,
            )
        },
        headlineContent = {
            Text(
                text = world.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1
            )
        },
        supportingContent = {
            Text(
                text = world.authorName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
        },
        trailingContent = {
            // 显示世界访问等级
            Text(
                text = world.releaseStatus,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    )
} 