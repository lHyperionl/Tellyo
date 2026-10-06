package com.tellyo.app.core.provider

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serves local data immediately while one throttled refresh runs outside the read lock. */
internal class LocalFirstCache<T : Any>(
    private val scope: CoroutineScope,
    private val refreshIntervalNanos: Long,
    private val loadLocal: suspend () -> T,
    private val refresh: suspend () -> T,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val mutex = Mutex()
    private var value: T? = null
    private var lastAttemptNanos: Long? = null
    private var refreshing = false

    suspend fun get(): T = mutex.withLock {
        val current = value ?: loadLocal().also { value = it }
        val now = nanoTime()
        if (!refreshing && lastAttemptNanos?.let { now - it < refreshIntervalNanos } != true) {
            refreshing = true
            lastAttemptNanos = now
            scope.launch {
                try {
                    val updated = refresh()
                    mutex.withLock { value = updated }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A failed refresh must preserve the usable local snapshot.
                } finally {
                    withContext(NonCancellable) {
                        mutex.withLock { refreshing = false }
                    }
                }
            }
        }
        current
    }
}
