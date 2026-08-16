package com.tellyo.app.core.provider

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ProviderRegistryModule {
    @Provides
    @Singleton
    fun provideProviderRegistry(providers: Set<@JvmSuppressWildcards TvProvider>): ProviderRegistry =
        ProviderRegistry(providers.associateBy { it.id })
}
