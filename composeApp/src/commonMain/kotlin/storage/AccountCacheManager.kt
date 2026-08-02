package io.github.vrcmteam.vrcm.storage

import io.github.vrcmteam.vrcm.storage.data.FriendListCache
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

internal data class AccountCacheWriteToken(
    val userId: String,
    val globalGeneration: Long,
    val accountGeneration: Long,
)

class AccountCacheManager(
    private val friendListCacheDao: FriendListCacheDao,
    private val userProfileCacheDao: UserProfileCacheDao,
) {
    private val lock = SynchronizedObject()
    private var globalGeneration = 0L
    private val accountGenerations = mutableMapOf<String, Long>()

    internal fun captureWriteToken(userId: String): AccountCacheWriteToken = synchronized(lock) {
        AccountCacheWriteToken(
            userId = userId,
            globalGeneration = globalGeneration,
            accountGeneration = accountGenerations[userId] ?: 0L,
        )
    }

    internal fun saveFriendListIfCurrent(
        token: AccountCacheWriteToken,
        cache: FriendListCache,
    ): Boolean = synchronized(lock) {
        if (token.globalGeneration != globalGeneration ||
            token.accountGeneration != (accountGenerations[token.userId] ?: 0L)
        ) {
            return@synchronized false
        }
        friendListCacheDao.save(token.userId, cache)
        true
    }

    fun clearAccount(userId: String) = synchronized(lock) {
        accountGenerations[userId] = (accountGenerations[userId] ?: 0L) + 1L
        friendListCacheDao.clear(userId)
        userProfileCacheDao.clearOwner(userId)
    }

    fun clearAll() = synchronized(lock) {
        globalGeneration++
        accountGenerations.clear()
        friendListCacheDao.clearAll()
        userProfileCacheDao.clearAll()
    }
}
