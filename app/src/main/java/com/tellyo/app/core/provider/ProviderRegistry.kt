package com.tellyo.app.core.provider

class ProviderRegistry(
    private val providers: Map<String, TvProvider> = emptyMap(),
) {
    fun providerFor(providerId: String): TvProvider? = providers[providerId]

    fun all(): List<TvProvider> = providers.values.toList()
}
