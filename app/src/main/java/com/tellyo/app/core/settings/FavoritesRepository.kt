package com.tellyo.app.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tellyo.app.core.model.ChannelLineup
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

data class FavoriteSettings(
    val configured: Boolean = false,
    val channelIds: Set<String> = emptySet(),
    val orderedChannelIds: List<String> = emptyList(),
)

interface FavoritesRepository {
    val settings: Flow<FavoriteSettings>

    suspend fun initialize(channelIds: List<String>)

    suspend fun toggle(channelId: String)

    suspend fun move(channelId: String, direction: Int)
}

private val Context.tellyoFavoritesDataStore by preferencesDataStore(name = "tellyo_favorites")

@Singleton
class DataStoreFavoritesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : FavoritesRepository {
    override val settings: Flow<FavoriteSettings> = context.tellyoFavoritesDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            FavoriteSettings(
                configured = preferences[Keys.configured] ?: false,
                channelIds = preferences[Keys.channelIds].orEmpty(),
                orderedChannelIds = decodeOrder(preferences[Keys.order]),
            )
        }

    override suspend fun initialize(channelIds: List<String>) {
        if (channelIds.isEmpty()) return
        context.tellyoFavoritesDataStore.edit { preferences ->
            if (preferences[Keys.configured] != true) {
                preferences[Keys.configured] = true
                preferences[Keys.channelIds] = channelIds.toSet()
                preferences[Keys.order] = encodeOrder(channelIds)
            }
            if (preferences[Keys.childrenAfterSport2] != true &&
                ChannelLineup.CHILDREN_ANCHOR_ID in channelIds &&
                channelIds.containsAll(ChannelLineup.CHILDREN_IDS)
            ) {
                val updated = FavoriteSettings(
                    configured = true,
                    channelIds = preferences[Keys.channelIds].orEmpty(),
                    orderedChannelIds = decodeOrder(preferences[Keys.order]),
                ).withChildrenAfterSport2(channelIds)
                preferences[Keys.channelIds] = updated.channelIds
                preferences[Keys.order] = encodeOrder(updated.orderedChannelIds)
                preferences[Keys.childrenAfterSport2] = true
            }
        }
    }

    override suspend fun toggle(channelId: String) {
        context.tellyoFavoritesDataStore.edit { preferences ->
            val ids = preferences[Keys.channelIds].orEmpty().toMutableSet()
            if (!ids.add(channelId)) ids.remove(channelId)
            preferences[Keys.configured] = true
            preferences[Keys.channelIds] = ids
            preferences[Keys.order] = encodeOrder(
                decodeOrder(preferences[Keys.order]).filter { it in ids },
            )
        }
    }

    override suspend fun move(channelId: String, direction: Int) {
        if (direction == 0) return
        context.tellyoFavoritesDataStore.edit { preferences ->
            val ids = preferences[Keys.channelIds].orEmpty()
            if (channelId !in ids) return@edit
            val order = decodeOrder(preferences[Keys.order])
                .filter { it in ids }
                .toMutableList()
            ids.forEach { id -> if (id !in order) order += id }
            val currentIndex = order.indexOf(channelId)
            val targetIndex = (currentIndex + direction).coerceIn(0, order.lastIndex)
            if (currentIndex != targetIndex) {
                val moved = order.removeAt(currentIndex)
                order.add(targetIndex, moved)
            }
            preferences[Keys.order] = encodeOrder(order)
        }
    }

    private object Keys {
        val configured = booleanPreferencesKey("configured")
        val channelIds = stringSetPreferencesKey("channel_ids")
        val order = stringPreferencesKey("channel_order")
        val childrenAfterSport2 = booleanPreferencesKey("children_after_sport2_applied")
    }

    private companion object {
        const val ORDER_SEPARATOR = "|"

        fun encodeOrder(ids: List<String>): String = ids.joinToString(ORDER_SEPARATOR)

        fun decodeOrder(value: String?): List<String> = value
            ?.split(ORDER_SEPARATOR)
            ?.filter(String::isNotBlank)
            .orEmpty()
    }
}

/** Match Sport2's favorite group so the requested block stays together on existing installs. */
internal fun FavoriteSettings.withChildrenAfterSport2(availableIds: List<String>): FavoriteSettings {
    val children = ChannelLineup.CHILDREN_IDS.filter { it in availableIds }
    val favorites = channelIds - children.toSet()
    if (ChannelLineup.CHILDREN_ANCHOR_ID !in favorites) {
        return copy(channelIds = favorites, orderedChannelIds = orderedChannelIds.filterNot { it in children })
    }
    val order = (orderedChannelIds + availableIds).distinct()
        .filter { it in favorites && it in availableIds }.toMutableList()
    order.addAll(order.indexOf(ChannelLineup.CHILDREN_ANCHOR_ID) + 1, children)
    return copy(channelIds = favorites + children, orderedChannelIds = order)
}
