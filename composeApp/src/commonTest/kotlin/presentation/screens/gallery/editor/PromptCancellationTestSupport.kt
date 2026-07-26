package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.startCoroutine

internal class QueuedTestDispatcher : CoroutineDispatcher() {
    private val tasks = ArrayDeque<Runnable>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        tasks.addLast(block)
    }

    fun runNext() {
        tasks.removeFirst().run()
    }

    fun runAll() {
        while (tasks.isNotEmpty()) runNext()
    }
}

internal fun <T> cancelAfterWorkerCompletion(
    workerDispatcher: QueuedTestDispatcher,
    cancellation: CancellationException,
    block: suspend () -> T,
): Result<T> {
    val callerDispatcher = QueuedTestDispatcher()
    val callerJob = Job()
    var completion: Result<T>? = null
    block.startCoroutine(
        object : Continuation<T> {
            override val context: CoroutineContext = callerJob + callerDispatcher

            override fun resumeWith(result: Result<T>) {
                completion = result
            }
        },
    )

    callerDispatcher.runNext()
    workerDispatcher.runNext()
    callerJob.cancel(cancellation)
    callerDispatcher.runAll()
    return requireNotNull(completion)
}

internal fun <T> runWorkerFailure(
    workerDispatcher: QueuedTestDispatcher,
    block: suspend () -> T,
): Result<T> {
    val callerDispatcher = QueuedTestDispatcher()
    var completion: Result<T>? = null
    block.startCoroutine(
        object : Continuation<T> {
            override val context: CoroutineContext = callerDispatcher

            override fun resumeWith(result: Result<T>) {
                completion = result
            }
        },
    )

    callerDispatcher.runNext()
    workerDispatcher.runNext()
    callerDispatcher.runAll()
    return requireNotNull(completion)
}
