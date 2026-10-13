package com.tellyo.app.core.update

import android.content.Context
import android.os.Build
import com.tellyo.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {
    @Provides
    @Singleton
    fun provideUpdateRepository(client: OkHttpClient): UpdateRepository = GitHubUpdateRepository(
        client, BuildConfig.TELLYO_UPDATE_REPOSITORY, BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT,
    )

    @Provides
    @Singleton
    fun provideUpdateDownloader(@ApplicationContext context: Context, client: OkHttpClient): UpdateDownloader =
        AndroidUpdateDownloader(context, client)
}
