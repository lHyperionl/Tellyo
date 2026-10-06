package com.tellyo.app.core.player

import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/** Give each media source its own HTTP factory so channel headers cannot leak to the next channel. */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class StreamMediaSourceFactory(private val context: Context) : MediaSource.Factory {
    private var drmProvider: DrmSessionManagerProvider? = null
    private var errorPolicy: LoadErrorHandlingPolicy? = null

    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory = apply {
        drmProvider = provider
    }

    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory = apply {
        errorPolicy = policy
    }

    override fun getSupportedTypes(): IntArray = DefaultMediaSourceFactory(context).supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val bundle = mediaItem.requestMetadata.extras?.getBundle(HTTP_HEADERS_KEY)
        val headers = bundle?.keySet()?.mapNotNull { key ->
            bundle.getString(key)?.let { key to it }
        }?.toMap().orEmpty()
        val httpFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers)
        val factory = DefaultMediaSourceFactory(DefaultDataSource.Factory(context, httpFactory))
        drmProvider?.let(factory::setDrmSessionManagerProvider)
        errorPolicy?.let(factory::setLoadErrorHandlingPolicy)
        return factory.createMediaSource(mediaItem)
    }

    companion object {
        const val HTTP_HEADERS_KEY = "com.tellyo.app.HTTP_HEADERS"

        fun requestMetadata(headers: Map<String, String>): MediaItem.RequestMetadata =
            MediaItem.RequestMetadata.Builder().setExtras(
                Bundle().apply {
                    putBundle(HTTP_HEADERS_KEY, Bundle().apply {
                        headers.forEach { (key, value) -> putString(key, value) }
                    })
                },
            ).build()
    }
}
