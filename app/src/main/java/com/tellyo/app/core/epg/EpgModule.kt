package com.tellyo.app.core.epg

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EpgModule {
    @Binds
    @Singleton
    abstract fun bindEpgProvider(provider: StvrEpgProvider): EpgProvider

    @Binds
    @Singleton
    abstract fun bindEpgRepository(repository: RoomEpgRepository): EpgRepository
}
