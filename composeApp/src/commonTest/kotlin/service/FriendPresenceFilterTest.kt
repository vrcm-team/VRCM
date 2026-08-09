package io.github.vrcmteam.vrcm.service

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FriendPresenceFilterTest {
    @Test
    fun defaultFilterAlertsAboutEveryone() {
        assertTrue(FriendPresenceFilter.Default.allows("usr_a", setOf("grp_1")))
        assertTrue(FriendPresenceFilter.Default.allows("usr_b", emptySet()))
    }

    @Test
    fun whitelistOnlyAlertsAboutSelectedGroups() {
        val filter = FriendPresenceFilter(
            mode = PresenceFilterMode.Whitelist,
            groupIds = setOf("grp_close"),
        )

        assertTrue(filter.allows("usr_in", setOf("grp_close")))
        assertFalse(filter.allows("usr_out", setOf("grp_other")))
        assertFalse(filter.allows("usr_none", emptySet()))
    }

    @Test
    fun blacklistAlertsAboutEveryoneOutsideSelectedGroups() {
        val filter = FriendPresenceFilter(
            mode = PresenceFilterMode.Blacklist,
            groupIds = setOf("grp_muted"),
        )

        assertFalse(filter.allows("usr_muted", setOf("grp_muted")))
        assertTrue(filter.allows("usr_other", setOf("grp_other")))
    }

    /** 用户明确要求：单个好友的选择优先于分组，两者同时生效。 */
    @Test
    fun individualChoiceOverridesTheGroupRule() {
        val whitelist = FriendPresenceFilter(
            mode = PresenceFilterMode.Whitelist,
            groupIds = setOf("grp_close"),
            userOverrides = mapOf("usr_excluded" to false, "usr_extra" to true),
        )

        // 在白名单分组里，但被单独排除
        assertFalse(whitelist.allows("usr_excluded", setOf("grp_close")))
        // 不在任何选中分组，但被单独加入
        assertTrue(whitelist.allows("usr_extra", emptySet()))

        val blacklist = FriendPresenceFilter(
            mode = PresenceFilterMode.Blacklist,
            groupIds = setOf("grp_muted"),
            userOverrides = mapOf("usr_kept" to true, "usr_dropped" to false),
        )

        // 在黑名单分组里，但被单独保留
        assertTrue(blacklist.allows("usr_kept", setOf("grp_muted")))
        // 不在黑名单分组，但被单独屏蔽
        assertFalse(blacklist.allows("usr_dropped", setOf("grp_other")))
    }
}
