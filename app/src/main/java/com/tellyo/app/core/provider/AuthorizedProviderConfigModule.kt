package com.tellyo.app.core.provider

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthorizedProviderConfigModule {
    @Binds
    @Singleton
    abstract fun bindAuthorizedProviderConfigRepository(
        repository: DataStoreAuthorizedProviderConfigRepository,
    ): AuthorizedProviderConfigRepository
}
