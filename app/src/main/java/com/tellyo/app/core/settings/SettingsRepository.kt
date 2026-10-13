package com.tellyo.app.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.emptyPreferences
import com.tellyo.app.core.model.VideoQualityPreference
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

data class TellyoSettings(
    val lastChannelId: String? = null,
    val reduceMotion: Boolean = false,
    val videoQuality: VideoQualityPreference = VideoQualityPreference.AUTO,
    val blockEmbedAds: Boolean = true,
)

interface SettingsRepository {
    val settings: Flow<TellyoSettings>

    suspend fun setLastChannel(id: String)

    suspend fun setReduceMotion(enabled: Boolean)

    suspend fun setVideoQuality(preference: VideoQualityPreference)

    suspend fun setBlockEmbedAds(enabled: Boolean)
}

private val Context.tellyoSettingsDataStore by preferencesDataStore(name = "tellyo_settings")

class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {
    override val settings: Flow<TellyoSettings> = context.tellyoSettingsDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            TellyoSettings(
                lastChannelId = preferences[Keys.lastChannelId],
                reduceMotion = preferences[Keys.reduceMotion] ?: false,
                blockEmbedAds = preferences[Keys.blockEmbedAds] ?: true,
                videoQuality = preferences[Keys.videoQuality]
                    ?.let { raw -> runCatching { VideoQualityPreference.valueOf(raw) }.getOrNull() }
                    ?: VideoQualityPreference.AUTO,
            )
        }

    override suspend fun setLastChannel(id: String) {
        context.tellyoSettingsDataStore.edit { it[Keys.lastChannelId] = id }
    }

    override suspend fun setReduceMotion(enabled: Boolean) {
        context.tellyoSettingsDataStore.edit { it[Keys.reduceMotion] = enabled }
    }

    override suspend fun setVideoQuality(preference: VideoQualityPreference) {
        context.tellyoSettingsDataStore.edit { it[Keys.videoQuality] = preference.name }
    }

    override suspend fun setBlockEmbedAds(enabled: Boolean) {
        context.tellyoSettingsDataStore.edit { it[Keys.blockEmbedAds] = enabled }
    }

    private object Keys {
        val lastChannelId = stringPreferencesKey("last_channel_id")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val blockEmbedAds = booleanPreferencesKey("block_embed_ads")
        val videoQuality = stringPreferencesKey("video_quality")
    }
}
