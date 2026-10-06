package com.tellyo.app.core.config

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tellyo.app.core.provider.AuthorizedProviderConfigRepository
import com.tellyo.app.core.provider.ChannelCatalogRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RemoteConfigWorkerEntryPoint {
    fun channelCatalogRepository(): ChannelCatalogRepository
    fun authorizedProviderConfigRepository(): AuthorizedProviderConfigRepository
    fun remoteConfigEndpoint(): RemoteConfigEndpoint
}

/** Keeps authorized channel/provider metadata current while retaining local fallbacks. */
class RemoteConfigRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val dependencies = EntryPointAccessors.fromApplication(
            applicationContext,
            RemoteConfigWorkerEntryPoint::class.java,
        )
        if (!dependencies.remoteConfigEndpoint().isConfigured) return Result.success()

        val channelsUpdated = dependencies.channelCatalogRepository().refreshRemoteConfig()
        val providersUpdated = dependencies.authorizedProviderConfigRepository().refreshRemoteConfig()
        return if (channelsUpdated && providersUpdated) {
            Result.success()
        } else if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
            Result.retry()
        } else {
            Result.failure()
        }
    }

    private companion object {
        const val MAX_RETRY_ATTEMPTS = 3
    }
}
