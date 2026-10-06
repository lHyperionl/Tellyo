package com.tellyo.app.core.player

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackModule {
    @Binds
    @Singleton
    abstract fun bindPlaybackService(client: Media3PlaybackClient): PlaybackService

    @Binds
    @Singleton
    abstract fun bindPlaybackLogger(logger: AndroidPlaybackLogger): PlaybackLogger
}
