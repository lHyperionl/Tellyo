package com.tellyo.app.core.epg

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EpgModule {
    @Binds
    @IntoSet
    @Singleton
    abstract fun bindConfiguredEpgProvider(provider: ConfiguredXmlTvEpgProvider): EpgProvider

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindJojEpgProvider(provider: JojEpgProvider): EpgProvider

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindMarkizaEpgProvider(provider: MarkizaEpgProvider): EpgProvider

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindTa3EpgProvider(provider: Ta3EpgProvider): EpgProvider

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindPrimaSportEpgProvider(provider: PrimaSportEpgProvider): EpgProvider

    @Binds
    @Singleton
    abstract fun bindEpgRepository(repository: RoomEpgRepository): EpgRepository
}
