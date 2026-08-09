package io.github.vrcmteam.vrcm.storage

import io.github.vrcmteam.vrcm.storage.data.FriendListCache
import io.github.vrcmteam.vrcm.storage.data.UserProfileCache

/** 内存版资料缓存，供不关心持久化细节的测试使用。 */
class InMemoryUserProfileCacheStore : UserProfileCacheStore {
    private val entries = mutableMapOf<Pair<String, String>, UserProfileCache>()

    override suspend fun load(ownerUserId: String, userId: String): UserProfileCache? =
        entries[ownerUserId to userId]

    override suspend fun save(ownerUserId: String, userId: String, cache: UserProfileCache) {
        entries[ownerUserId to userId] = cache
    }

    override suspend fun clearOwner(ownerUserId: String) {
        entries.keys.filter { it.first == ownerUserId }.forEach(entries::remove)
    }

    override suspend fun clearAll() = entries.clear()
}

/** 内存版好友列表缓存，供不关心持久化细节的测试使用。 */
class InMemoryFriendListCacheStore : FriendListCacheStore {
    private val entries = mutableMapOf<String, FriendListCache>()

    override suspend fun load(userId: String): FriendListCache? = entries[userId]

    override suspend fun save(userId: String, cache: FriendListCache) {
        entries[userId] = cache
    }

    override suspend fun clear(userId: String) {
        entries.remove(userId)
    }

    override suspend fun clearAll() = entries.clear()
}
