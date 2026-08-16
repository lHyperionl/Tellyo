package com.tellyo.app.core.model

enum class ChannelCategory {
    GENERAL,
    NEWS,
    SPORTS,
    CULTURE,
}

data class Channel(
    val id: String,
    val number: Int,
    val name: String,
    val shortName: String,
    val logoUrl: String?,
    val providerId: String,
    val category: ChannelCategory,
    val enabled: Boolean,
)
