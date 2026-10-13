package com.tellyo.app.core.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BufferingWatchdogTest {
    @Test fun slowSegmentsCanFillTheRecoveryBufferBeyondTwentySeconds() {
        val watchdog = BufferingWatchdog(0)
        assertFalse(watchdog.shouldRetry(7_000, 4_000))
        assertFalse(watchdog.shouldRetry(14_000, 10_000))
        assertFalse(watchdog.shouldRetry(20_000, 10_000))
        assertFalse(watchdog.shouldRetry(21_000, 14_000))
    }

    @Test fun noProgressStillRetriesAfterTwentySeconds() {
        val watchdog = BufferingWatchdog(1_000)
        assertFalse(watchdog.shouldRetry(20_999, 0))
        assertTrue(watchdog.shouldRetry(21_000, 0))
    }

    @Test fun aStallIsMeasuredFromTheLastBufferIncrease() {
        val watchdog = BufferingWatchdog(0)
        assertFalse(watchdog.shouldRetry(14_000, 10_000))
        assertFalse(watchdog.shouldRetry(33_999, 10_000))
        assertTrue(watchdog.shouldRetry(34_000, 10_000))
    }

    @Test fun oscillatingBufferDoesNotMasqueradeAsDownloadProgress() {
        val watchdog = BufferingWatchdog(0)
        assertFalse(watchdog.shouldRetry(1_000, 5_000))
        assertFalse(watchdog.shouldRetry(10_000, 4_000))
        assertTrue(watchdog.shouldRetry(21_000, 5_000))
    }

    @Test fun continuousProgressCannotBufferIndefinitely() {
        val watchdog = BufferingWatchdog(0)
        for (time in 10_000L..80_000L step 10_000L) {
            assertFalse(watchdog.shouldRetry(time, time / 100))
        }
        assertTrue(watchdog.shouldRetry(90_000, 900))
    }
}
