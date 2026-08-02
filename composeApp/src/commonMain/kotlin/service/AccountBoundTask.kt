package io.github.vrcmteam.vrcm.service

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class AccountBoundTask<T>(
    private val scope: CoroutineScope,
    private val isCurrent: (T) -> Boolean,
    private val runTask: suspend (T) -> Unit,
) {
    private val lock = SynchronizedObject()
    private var token: T? = null
    private var job: Job? = null

    fun start(newToken: T) = synchronized(lock) {
        if (token == newToken && job?.isActive == true) return

        val previousJob = job
        previousJob?.cancel()
        token = newToken
        job = scope.launch(start = CoroutineStart.LAZY) {
            withContext(NonCancellable) { previousJob?.cancelAndJoin() }
            if (isCurrent(newToken)) runTask(newToken)
        }.also { it.start() }
    }

    fun cancel() {
        synchronized(lock) {
            token = null
            job?.cancel()
            job = null
        }
    }

    suspend fun cancelAndJoin() {
        val runningJob = synchronized(lock) {
            token = null
            job.also {
                job = null
                it?.cancel()
            }
        }
        runningJob?.cancelAndJoin()
    }
}
