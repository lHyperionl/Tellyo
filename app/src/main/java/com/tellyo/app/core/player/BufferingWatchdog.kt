package com.tellyo.app.core.player

/** Monotonic wall time measures stalls; media duration measures download progress. */
internal class BufferingWatchdog(private val startedAtMillis: Long) {
    private var lastProgressAtMillis = startedAtMillis
    private var largestBufferMillis = 0L

    fun shouldRetry(nowMillis: Long, bufferedDurationMillis: Long): Boolean {
        if (bufferedDurationMillis > largestBufferMillis) {
            largestBufferMillis = bufferedDurationMillis
            lastProgressAtMillis = nowMillis
        }
        return nowMillis - lastProgressAtMillis >= STALL_TIMEOUT_MILLIS ||
            nowMillis - startedAtMillis >= MAX_BUFFERING_MILLIS
    }

    private companion object {
        const val STALL_TIMEOUT_MILLIS = 20_000L
        const val MAX_BUFFERING_MILLIS = 90_000L
    }
}
