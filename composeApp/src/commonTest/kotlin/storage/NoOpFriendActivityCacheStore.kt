package io.github.vrcmteam.vrcm.storage

object NoOpFriendActivityCacheStore : FriendActivityCacheStore {
    override suspend fun clearAccount(ownerUserId: String) = Unit

    override suspend fun clearAll() = Unit
}
