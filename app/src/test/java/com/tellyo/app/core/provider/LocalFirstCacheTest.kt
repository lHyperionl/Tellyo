package com.tellyo.app.core.provider

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalFirstCacheTest {
    @Test
    fun slowRefreshDoesNotBlockLocalReadsAndConcurrentReadersShareOneRefresh() = runTest {
        val remote = CompletableDeferred<String>()
        var localLoads = 0
        var remoteLoads = 0
        val cache = LocalFirstCache(
            scope = backgroundScope,
            refreshIntervalNanos = 100L,
            loadLocal = { localLoads++; "bundled" },
            refresh = { remoteLoads++; remote.await() },
            nanoTime = { 0L },
        )

        assertEquals("bundled", cache.get())
        runCurrent()
        val readers = List(20) { async { cache.get() } }
        readers.forEach { assertEquals("bundled", it.await()) }
        assertEquals(1, localLoads)
        assertEquals(1, remoteLoads)

        remote.complete("updated")
        runCurrent()
        assertEquals("updated", cache.get())
        assertEquals(1, remoteLoads)
    }

    @Test
    fun failedRefreshRetainsSnapshotAndRetriesOnlyAfterInterval() = runTest {
        var now = 0L
        var attempts = 0
        val cache = LocalFirstCache(
            scope = backgroundScope,
            refreshIntervalNanos = 100L,
            loadLocal = { "saved" },
            refresh = {
                attempts++
                if (attempts == 1) error("Offline")
                "new"
            },
            nanoTime = { now },
        )

        assertEquals("saved", cache.get())
        runCurrent()
        now = 99L
        assertEquals("saved", cache.get())
        runCurrent()
        assertEquals(1, attempts)

        now = 100L
        assertEquals("saved", cache.get())
        runCurrent()
        assertEquals("new", cache.get())
        assertEquals(2, attempts)
    }
}
