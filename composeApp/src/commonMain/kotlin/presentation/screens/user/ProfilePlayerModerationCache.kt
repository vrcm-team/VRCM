package io.github.vrcmteam.vrcm.presentation.screens.user

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.network.api.playermoderation.PlayerModerationApi
import io.github.vrcmteam.vrcm.network.api.playermoderation.PlayerModerationData
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shares one authoritative moderation snapshot across the controls on a single profile. */
internal class ProfilePlayerModerationCache(
    private val loadTarget: suspend (String) -> List<PlayerModerationData>,
) {
    constructor(api: PlayerModerationApi) : this(api::getForTarget)

    private val mutex = Mutex()
    private var cachedEntry: Entry? = null

    suspend fun get(
        sessionToken: AccountSessionToken,
        targetUserId: String,
    ): List<PlayerModerationData> = mutex.withLock {
        val key = Key(sessionToken, targetUserId)
        cachedEntry?.takeIf { it.key == key }?.moderations ?: loadTarget(targetUserId).also {
            cachedEntry = Entry(key, it)
        }
    }

    suspend fun invalidate(
        sessionToken: AccountSessionToken,
        targetUserId: String,
    ) = mutex.withLock {
        val key = Key(sessionToken, targetUserId)
        if (cachedEntry?.key == key) cachedEntry = null
    }

    private data class Key(
        val sessionToken: AccountSessionToken,
        val targetUserId: String,
    )

    private data class Entry(
        val key: Key,
        val moderations: List<PlayerModerationData>,
    )
}
