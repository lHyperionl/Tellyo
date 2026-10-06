package com.tellyo.app.core.provider

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProviderModule {
    @Binds
    @Singleton
    abstract fun bindCzechPlaylistRepository(repository: CachedCzechPlaylistRepository): CzechPlaylistRepository

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindCzechIptvProvider(provider: CzechIptvProvider): TvProvider

    @Binds
    @Singleton
    abstract fun bindSlovakPlaylistRepository(repository: CachedSlovakPlaylistRepository): SlovakPlaylistRepository

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindSlovakIptvProvider(provider: SlovakIptvProvider): TvProvider

    @Binds
    @Singleton
    abstract fun bindStvrOfficialApi(api: HttpStvrOfficialApi): StvrOfficialApi

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindStvrTvProvider(provider: StvrTvProvider): TvProvider

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindJojTvProvider(provider: JojTvProvider): TvProvider

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindMarkizaTvProvider(provider: MarkizaTvProvider): TvProvider

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindTa3TvProvider(provider: Ta3TvProvider): TvProvider
}
