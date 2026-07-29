package io.github.vrcmteam.vrcm.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

internal class ConflatedAccountCacheWriter<T>(
    scope: CoroutineScope,
    private val save: suspend (accountUserId: String, value: T) -> Unit,
) {
    private val lock = Any()
    private val pendingByAccount = mutableMapOf<String, T>()
    private val signal = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (ignored in signal) drainPending()
        }
    }

    fun submit(accountUserId: String, value: T) {
        synchronized(lock) { pendingByAccount[accountUserId] = value }
        signal.trySend(Unit)
    }

    fun close() {
        signal.close()
    }

    private suspend fun drainPending() {
        while (true) {
            val writes = synchronized(lock) {
                if (pendingByAccount.isEmpty()) null else pendingByAccount.toMap().also {
                    pendingByAccount.clear()
                }
            } ?: return
            writes.forEach { (accountUserId, value) -> save(accountUserId, value) }
        }
    }
}
