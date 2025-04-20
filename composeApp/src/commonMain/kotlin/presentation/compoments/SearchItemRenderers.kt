package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Arrangement
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
import io.github.vrcmteam.vrcm.network.api.files.data.PlatformType.*
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.presentation.extensions.ignoredFormat
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.service.platformPackages

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
        modifier = Modifier.animateItem(),
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
            if (user.status != UserStatus.Offline || lastLoginStr.isNullOrEmpty()) return@SearchResultItem
            Text(
                text = lastLoginStr.toLocalDateTime()?.ignoredFormat.orEmpty(),
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
        modifier = Modifier.animateItem(),
        leadingContent = {
            AImage(
                modifier = Modifier.sharedBoundsBy("${world.id}WorldImage").size(48.dp)
                    .clip(MaterialTheme.shapes.medium),
                imageData = world.imageUrl,
            )
        },
        headlineContent = {
            Text(
                text = world.name,
                style = MaterialTheme.typography.titleMedium,
                overflow = TextOverflow.Ellipsis,
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