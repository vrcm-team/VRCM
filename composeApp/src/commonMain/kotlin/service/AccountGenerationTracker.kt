package io.github.vrcmteam.vrcm.service

internal data class AccountGenerationToken(
    val userId: String,
    val generation: Long,
)

internal data class AccountActivation(
    val token: AccountGenerationToken,
    val changed: Boolean,
)

internal class AccountGenerationTracker(initialUserId: String? = null) {
    private val lock = Any()
    private var userId = initialUserId
    private var generation = if (initialUserId == null) 0L else 1L

    fun activate(newUserId: String): AccountActivation = synchronized(lock) {
        val changed = userId != newUserId
        if (changed) generation++
        userId = newUserId
        AccountActivation(AccountGenerationToken(newUserId, generation), changed)
    }

    fun clear(): Boolean = synchronized(lock) {
        if (userId == null) return@synchronized false
        userId = null
        generation++
        true
    }

    fun currentToken(): AccountGenerationToken? = synchronized(lock) {
        userId?.let { AccountGenerationToken(it, generation) }
    }

    fun isCurrent(token: AccountGenerationToken): Boolean = synchronized(lock) {
        userId == token.userId && generation == token.generation
    }
}
