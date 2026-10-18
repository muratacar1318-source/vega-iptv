package com.tivimax.player.data.playlist

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tivimax.player.data.preferences.UserPreferences
import com.tivimax.player.data.preferences.appDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class PlaylistRepository(private val context: Context) {

    private val gson = Gson()

    companion object {
        val KEY_PLAYLISTS = stringPreferencesKey("playlists_json")
        val KEY_ACTIVE_PLAYLIST_ID = stringPreferencesKey("active_playlist_id")

        const val DEFAULT_PLAYLIST_ID = "default"
        const val DEFAULT_DB_NAME = "vega_iptv.db"

        fun dbNameFor(playlistId: String): String {
            return if (playlistId == DEFAULT_PLAYLIST_ID) {
                DEFAULT_DB_NAME
            } else {
                "vega_pl_${playlistId}.db"
            }
        }

        /**
         * Bir liste silindiğinde bir sonraki aktif listenin kimliğini belirler.
         * Aktif liste silinmediyse mevcut aktif kimlik korunur.
         * Aktif liste silindiyse listede kalan ilk eleman seçilir, liste boşsa null döner.
         */
        fun selectNextActive(
            remainingPlaylists: List<Playlist>,
            removedId: String,
            currentActiveId: String
        ): String? {
            if (removedId != currentActiveId) {
                return currentActiveId
            }
            return remainingPlaylists.firstOrNull()?.id
        }

        /**
         * Verilen durumlar için migration gerekip gerekmediğini ve oluşturulacak
         * playlist nesnesini belirler.
         */
        fun computeMigration(
            hasExistingPlaylists: Boolean,
            isLoggedIn: Boolean,
            serverUrl: String,
            username: String,
            password: String,
            isSynced: Boolean,
            lastSyncAt: String
        ): Playlist? {
            if (hasExistingPlaylists) return null
            if (!isLoggedIn) return null

            return Playlist(
                id = DEFAULT_PLAYLIST_ID,
                name = "Ana liste",
                serverUrl = serverUrl,
                username = username,
                password = password,
                dbName = DEFAULT_DB_NAME,
                createdAt = System.currentTimeMillis(),
                isSynced = isSynced,
                lastSyncAt = lastSyncAt
            )
        }
    }

    val playlistsFlow: Flow<List<Playlist>> = context.appDataStore.data.map { prefs ->
        val json = prefs[KEY_PLAYLISTS]
        parsePlaylists(json)
    }

    val activePlaylistIdFlow: Flow<String> = context.appDataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_PLAYLIST_ID] ?: DEFAULT_PLAYLIST_ID
    }

    val activePlaylistFlow: Flow<Playlist?> = combine(playlistsFlow, activePlaylistIdFlow) { list, activeId ->
        list.find { it.id == activeId } ?: list.firstOrNull()
    }

    private fun parsePlaylists(json: String?): List<Playlist> {
        if (json.isNullOrBlank()) return emptyList()
        val type = object : TypeToken<List<Playlist>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getPlaylists(): List<Playlist> {
        return playlistsFlow.first()
    }

    suspend fun getActivePlaylistId(): String {
        return activePlaylistIdFlow.first()
    }

    suspend fun getActivePlaylist(): Playlist? {
        return activePlaylistFlow.first()
    }

    suspend fun add(playlist: Playlist) {
        context.appDataStore.edit { prefs ->
            val current = parsePlaylists(prefs[KEY_PLAYLISTS]).toMutableList()
            val existingIndex = current.indexOfFirst { it.id == playlist.id }
            if (existingIndex >= 0) {
                current[existingIndex] = playlist
            } else {
                current.add(playlist)
            }
            prefs[KEY_PLAYLISTS] = gson.toJson(current)
            if (current.size == 1) {
                prefs[KEY_ACTIVE_PLAYLIST_ID] = playlist.id
            }
        }
    }

    suspend fun update(playlist: Playlist) {
        add(playlist)
    }

    suspend fun remove(id: String): Playlist? {
        var nextActive: Playlist? = null
        context.appDataStore.edit { prefs ->
            val current = parsePlaylists(prefs[KEY_PLAYLISTS]).toMutableList()
            current.removeAll { it.id == id }
            prefs[KEY_PLAYLISTS] = gson.toJson(current)

            val currentActive = prefs[KEY_ACTIVE_PLAYLIST_ID] ?: DEFAULT_PLAYLIST_ID
            val nextActiveId = selectNextActive(current, id, currentActive)
            if (nextActiveId != null) {
                prefs[KEY_ACTIVE_PLAYLIST_ID] = nextActiveId
                nextActive = current.find { it.id == nextActiveId }
            } else {
                prefs.remove(KEY_ACTIVE_PLAYLIST_ID)
                nextActive = null
            }
        }
        return nextActive
    }

    suspend fun setActive(id: String) {
        context.appDataStore.edit { prefs ->
            prefs[KEY_ACTIVE_PLAYLIST_ID] = id
        }
    }

    suspend fun markSynced(id: String, timestamp: String) {
        context.appDataStore.edit { prefs ->
            val current = parsePlaylists(prefs[KEY_PLAYLISTS]).toMutableList()
            val index = current.indexOfFirst { it.id == id }
            if (index >= 0) {
                current[index] = current[index].copy(isSynced = true, lastSyncAt = timestamp)
                prefs[KEY_PLAYLISTS] = gson.toJson(current)
            }
        }
    }

    /**
     * Tek seferlik geçiş (Migration):
     * Liste kaydı yoksa ve kullanıcı giriş yapmışsa mevcut kimlik bilgilerinden
     * "default" (Ana liste) kaydı oluşturulur ve aktif yapılır.
     * Giriş yapılmamışsa liste boş bırakılır.
     */
    suspend fun migrateIfNeeded(userPrefs: UserPreferences): Playlist? {
        val existing = getPlaylists()
        if (existing.isNotEmpty()) {
            return getActivePlaylist()
        }

        val isLoggedIn = userPrefs.isLoggedInFlow.first()
        val defaultPl = computeMigration(
            hasExistingPlaylists = existing.isNotEmpty(),
            isLoggedIn = isLoggedIn,
            serverUrl = userPrefs.serverUrlFlow.first(),
            username = userPrefs.usernameFlow.first(),
            password = userPrefs.passwordFlow.first(),
            isSynced = userPrefs.isPlaylistSyncedFlow.first(),
            lastSyncAt = userPrefs.lastSyncTimestampFlow.first()
        ) ?: return null

        add(defaultPl)
        setActive(DEFAULT_PLAYLIST_ID)
        return defaultPl
    }
}
