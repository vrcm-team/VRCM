package io.github.vrcmteam.vrcm.service

import kotlinx.serialization.Serializable

/** 名单的两种工作方式：白名单只提醒选中的，黑名单提醒选中之外的。 */
@Serializable
enum class PresenceFilterMode { Blacklist, Whitelist }

/**
 * 决定某位好友的上下线要不要提醒。
 *
 * 名单可以按收藏分组整组选，也可以精确到某个人。两者同时生效，冲突时以针对个人的选择为准：
 * 例如白名单里选了某个分组，又把其中一位单独取消，那位就不再提醒。
 */
@Serializable
data class FriendPresenceFilter(
    val mode: PresenceFilterMode = PresenceFilterMode.Blacklist,
    val groupIds: Set<String> = emptySet(),
    /** 针对单个好友的显式取舍，true 为提醒、false 为不提醒。 */
    val userOverrides: Map<String, Boolean> = emptyMap(),
) {
    fun allows(userId: String, userGroupIds: Set<String>): Boolean {
        userOverrides[userId]?.let { return it }
        val matchesGroup = userGroupIds.any { it in groupIds }
        return when (mode) {
            PresenceFilterMode.Whitelist -> matchesGroup
            PresenceFilterMode.Blacklist -> !matchesGroup
        }
    }

    companion object {
        /** 默认是空黑名单，等于谁都提醒。 */
        val Default = FriendPresenceFilter()
    }
}
