package com.tellyo.app.core.model

import java.time.Instant

data class Program(
    val id: String,
    val channelId: String,
    val title: String,
    val description: String?,
    val start: Instant,
    val end: Instant,
    val category: String?,
    val artworkUrl: String? = null,
)
