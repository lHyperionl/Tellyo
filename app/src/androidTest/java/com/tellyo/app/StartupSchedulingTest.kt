package com.tellyo.app

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupSchedulingTest {
    @Test fun refreshWorkIsScheduledAfterLaunchAndKeptAcrossActivityRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val workManager = WorkManager.getInstance(context)
            // Wait for the post-frame background enqueue without depending on
            // provider networking or the initial worker's completion.
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (workManager.getWorkInfosForUniqueWork("tellyo-remote-config-initial")
                    .get(5, TimeUnit.SECONDS).isEmpty() && System.nanoTime() < deadline) {
                Thread.sleep(50)
            }
            fun periodicIds(name: String) = workManager.getWorkInfosForUniqueWork(name)
                .get(5, TimeUnit.SECONDS).map { it.id }
            val epgIds = periodicIds("tellyo-epg-refresh")
            val configIds = periodicIds("tellyo-remote-config-refresh")
            assertEquals(1, epgIds.size)
            assertEquals(1, configIds.size)
            assertTrue(workManager.getWorkInfosForUniqueWork("tellyo-remote-config-initial")
                .get(5, TimeUnit.SECONDS).isNotEmpty())

            scenario.recreate()
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(epgIds, periodicIds("tellyo-epg-refresh"))
            assertEquals(configIds, periodicIds("tellyo-remote-config-refresh"))
        }
    }
}
