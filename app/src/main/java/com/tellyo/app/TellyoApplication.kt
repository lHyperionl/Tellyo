package com.tellyo.app

import android.app.Application
import androidx.work.Constraints
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.tellyo.app.core.config.RemoteConfigRefreshWorker
import com.tellyo.app.core.epg.EpgRefreshWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class TellyoApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    private val refreshWorkScheduled = AtomicBoolean(false)
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Called after the first Activity frame; also survives Activity recreation. */
    fun scheduleBackgroundRefresh() {
        if (!refreshWorkScheduled.compareAndSet(false, true)) return
        backgroundScope.launch { enqueueRefreshWork() }
    }

    private fun enqueueRefreshWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<EpgRefreshWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        val workManager = WorkManager.getInstance(this)
        workManager.enqueueUniquePeriodicWork(
            EPG_REFRESH_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )

        val configRequest = PeriodicWorkRequestBuilder<RemoteConfigRefreshWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniquePeriodicWork(
            REMOTE_CONFIG_REFRESH_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            configRequest,
        )
        workManager.enqueueUniqueWork(
            REMOTE_CONFIG_INITIAL_WORK_NAME,
            androidx.work.ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<RemoteConfigRefreshWorker>()
                .setConstraints(constraints)
                .build(),
        )
    }

    private companion object {
        const val EPG_REFRESH_WORK_NAME = "tellyo-epg-refresh"
        const val REMOTE_CONFIG_REFRESH_WORK_NAME = "tellyo-remote-config-refresh"
        const val REMOTE_CONFIG_INITIAL_WORK_NAME = "tellyo-remote-config-initial"
    }
}
