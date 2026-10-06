package com.tellyo.app.core.epg

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface EpgWorkerEntryPoint {
    fun epgRepository(): EpgRepository
}

/** Periodically refreshes the local EPG without coupling it to playback. */
class EpgRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = runCatching {
        EntryPointAccessors.fromApplication(
            applicationContext,
            EpgWorkerEntryPoint::class.java,
        ).epgRepository().refresh()
        Result.success()
    }.getOrElse {
        if (it is kotlinx.coroutines.CancellationException) throw it
        if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.failure()
    }

    private companion object {
        const val MAX_RETRY_ATTEMPTS = 3
    }
}
