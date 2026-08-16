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
}
