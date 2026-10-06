package com.tellyo.app

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.tellyo.app.core.config.RemoteConfigRefreshWorker
import com.tellyo.app.core.epg.EpgRefreshWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class TellyoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<EpgRefreshWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            EPG_REFRESH_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )

        val configRequest = PeriodicWorkRequestBuilder<RemoteConfigRefreshWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        val workManager = WorkManager.getInstance(this)
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
