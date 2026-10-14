package com.tivimax.player.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tivimax.player.data.config.AppConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "tivimax_preferences")

data class WatchProgress(
    val streamId: Int,
    val positionMs: Long,
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis()
) {
    val isWatched: Boolean get() = durationMs > 0 && positionMs >= (durationMs * 0.88)
}

class UserPreferences(private val context: Context) {

    private val gson = Gson()

    companion object {
        val KEY_USERNAME = stringPreferencesKey("username")
        val KEY_PASSWORD = stringPreferencesKey("password")
        val KEY_SERVER_URL = stringPreferencesKey("server_url")
        val KEY_REMEMBER_ME = booleanPreferencesKey("remember_me")
        val KEY_IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val KEY_EXP_DATE = stringPreferencesKey("exp_date")
        val KEY_MAX_CONNECTIONS = stringPreferencesKey("max_connections")
        val KEY_ACTIVE_CONNECTIONS = stringPreferencesKey("active_connections")

        // Favoriler
        val KEY_FAVORITE_LIVE = stringSetPreferencesKey("fav_live")
        val KEY_FAVORITE_VOD = stringSetPreferencesKey("fav_vod")
        val KEY_FAVORITE_SERIES = stringSetPreferencesKey("fav_series")

        // İzleme Geçmişi ve Kaldığı Yer (JSON String)
        val KEY_WATCH_PROGRESS = stringPreferencesKey("watch_progress_json")

        // Ebeveyn Kilidi
        val KEY_PARENTAL_PIN = stringPreferencesKey("parental_pin")
        val KEY_LOCKED_CATEGORIES = stringSetPreferencesKey("locked_categories")

        // Oynatıcı Tercihleri
        val KEY_ASPECT_RATIO = stringPreferencesKey("aspect_ratio") // "FIT", "FILL", "ZOOM", "16:9"
        val KEY_STREAM_FORMAT = stringPreferencesKey("stream_format") // "TS", "HLS"
        val KEY_FAST_ZAPPING = booleanPreferencesKey("fast_zapping") // true/false

        // Sıralama Tercihleri
        val KEY_LIVE_SORT = stringPreferencesKey("live_sort")
        val KEY_VOD_SORT = stringPreferencesKey("vod_sort")
        val KEY_SERIES_SORT = stringPreferencesKey("series_sort")

        // Canlı Yayın Senkronizasyonu (Düşük Gecikme)
        val KEY_AUTO_SYNC_LIVE = booleanPreferencesKey("auto_sync_live")

        // Resim İçinde Resim (PiP - Arka planda küçük pencere)
        val KEY_AUTO_PIP = booleanPreferencesKey("auto_pip")

        // Film ve Dizi Sürekli Tampon Süresi (Saniye - Varsayılan: 60s)
        val KEY_VOD_BUFFER_SECONDS = intPreferencesKey("vod_buffer_seconds")

        // Canlı Yayın Arabellek Boyutu (Saniye - 2, 5, 10)
        val KEY_LIVE_BUFFER_SECONDS = intPreferencesKey("live_buffer_seconds")

        // Video Çözücü / Donanım Hızlandırma Modu ("HARDWARE", "SOFTWARE")
        val KEY_DECODER_MODE = stringPreferencesKey("decoder_mode")

        // Altyazı Tercihleri
        val KEY_SUBTITLE_SIZE = stringPreferencesKey("subtitle_size") // "KUCUK", "ORTA", "BUYUK", "COK_BUYUK"
        val KEY_SUBTITLE_COLOR = stringPreferencesKey("subtitle_color") // "BEYAZ", "SARI", "YESIL", "MAVI"
    }

    val usernameFlow: Flow<String> = context.dataStore.data.map { it[KEY_USERNAME] ?: "" }
    val passwordFlow: Flow<String> = context.dataStore.data.map { it[KEY_PASSWORD] ?: "" }
    val serverUrlFlow: Flow<String> = context.dataStore.data.map { 
        val saved = it[KEY_SERVER_URL]
        if (saved.isNullOrBlank() || saved.contains("my-iptv-server")) {
            AppConfig.DEFAULT_SERVER_URL
        } else {
            saved
        }
    }
    val isLoggedInFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_IS_LOGGED_IN] ?: false }
    val expDateFlow: Flow<String> = context.dataStore.data.map { it[KEY_EXP_DATE] ?: "" }
    val maxConnectionsFlow: Flow<String> = context.dataStore.data.map { it[KEY_MAX_CONNECTIONS] ?: "1" }
    val activeConnectionsFlow: Flow<String> = context.dataStore.data.map { it[KEY_ACTIVE_CONNECTIONS] ?: "1" }

    val favoriteLiveFlow: Flow<Set<String>> = context.dataStore.data.map { it[KEY_FAVORITE_LIVE] ?: emptySet() }
    val favoriteVodFlow: Flow<Set<String>> = context.dataStore.data.map { it[KEY_FAVORITE_VOD] ?: emptySet() }
    val favoriteSeriesFlow: Flow<Set<String>> = context.dataStore.data.map { it[KEY_FAVORITE_SERIES] ?: emptySet() }

    val parentalPinFlow: Flow<String> = context.dataStore.data.map { it[KEY_PARENTAL_PIN] ?: "0000" }
    val streamFormatFlow: Flow<String> = context.dataStore.data.map { it[KEY_STREAM_FORMAT] ?: "TS" }
    val fastZappingFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_FAST_ZAPPING] ?: true }
    val aspectRatioFlow: Flow<String> = context.dataStore.data.map { it[KEY_ASPECT_RATIO] ?: "FIT" }

    val liveSortFlow: Flow<String> = context.dataStore.data.map { it[KEY_LIVE_SORT] ?: "DEFAULT" }
    val vodSortFlow: Flow<String> = context.dataStore.data.map { it[KEY_VOD_SORT] ?: "ADDED_DESC" }
    val seriesSortFlow: Flow<String> = context.dataStore.data.map { it[KEY_SERIES_SORT] ?: "ADDED_DESC" }
    val autoSyncLiveFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_SYNC_LIVE] ?: true }
    val autoPipFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_PIP] ?: true }
    val vodBufferSecondsFlow: Flow<Int> = context.dataStore.data.map { it[KEY_VOD_BUFFER_SECONDS] ?: 60 }
    val liveBufferSecondsFlow: Flow<Int> = context.dataStore.data.map { it[KEY_LIVE_BUFFER_SECONDS] ?: 2 }
    val decoderModeFlow: Flow<String> = context.dataStore.data.map { it[KEY_DECODER_MODE] ?: "HARDWARE" }

    val watchProgressMapFlow: Flow<Map<String, WatchProgress>> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_WATCH_PROGRESS] ?: "{}"
        val type = object : TypeToken<Map<String, WatchProgress>>() {}.type
        try {
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun setLiveSort(sort: String) {
        context.dataStore.edit { it[KEY_LIVE_SORT] = sort }
    }

    suspend fun setVodSort(sort: String) {
        context.dataStore.edit { it[KEY_VOD_SORT] = sort }
    }

    suspend fun setSeriesSort(sort: String) {
        context.dataStore.edit { it[KEY_SERIES_SORT] = sort }
    }

    suspend fun setAutoSyncLive(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_SYNC_LIVE] = enabled }
    }

    suspend fun setAutoPip(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_PIP] = enabled }
    }

    suspend fun setVodBufferSeconds(seconds: Int) {
        context.dataStore.edit { it[KEY_VOD_BUFFER_SECONDS] = seconds }
    }

    suspend fun setLiveBufferSeconds(seconds: Int) {
        context.dataStore.edit { it[KEY_LIVE_BUFFER_SECONDS] = seconds }
    }

    suspend fun setDecoderMode(mode: String) {
        context.dataStore.edit { it[KEY_DECODER_MODE] = mode }
    }

    suspend fun setStreamFormat(format: String) {
        context.dataStore.edit { it[KEY_STREAM_FORMAT] = format }
    }

    suspend fun setFastZapping(enabled: Boolean) {
        context.dataStore.edit { it[KEY_FAST_ZAPPING] = enabled }
    }

    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { it[KEY_SERVER_URL] = url }
    }

    suspend fun saveAuth(
        username: String,
        password: String,
        serverUrl: String = AppConfig.DEFAULT_SERVER_URL,
        expDate: String = "",
        maxCons: String = "1",
        activeCons: String = "1"
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USERNAME] = username
            prefs[KEY_PASSWORD] = password
            prefs[KEY_SERVER_URL] = serverUrl
            prefs[KEY_IS_LOGGED_IN] = true
            prefs[KEY_EXP_DATE] = expDate
            prefs[KEY_MAX_CONNECTIONS] = maxCons
            prefs[KEY_ACTIVE_CONNECTIONS] = activeCons
        }
    }

    suspend fun logout() {
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_LOGGED_IN] = false
            prefs[KEY_PASSWORD] = ""
        }
    }

    suspend fun toggleFavoriteLive(streamId: String) {
        context.dataStore.edit { prefs ->
            val current = (prefs[KEY_FAVORITE_LIVE] ?: emptySet()).toMutableSet()
            if (current.contains(streamId)) {
                current.remove(streamId)
            } else {
                current.add(streamId)
            }
            prefs[KEY_FAVORITE_LIVE] = current
        }
    }

    suspend fun toggleFavoriteVod(streamId: String) {
        context.dataStore.edit { prefs ->
            val current = (prefs[KEY_FAVORITE_VOD] ?: emptySet()).toMutableSet()
            if (current.contains(streamId)) {
                current.remove(streamId)
            } else {
                current.add(streamId)
            }
            prefs[KEY_FAVORITE_VOD] = current
        }
    }

    suspend fun toggleFavoriteSeries(seriesId: String) {
        context.dataStore.edit { prefs ->
            val current = (prefs[KEY_FAVORITE_SERIES] ?: emptySet()).toMutableSet()
            if (current.contains(seriesId)) {
                current.remove(seriesId)
            } else {
                current.add(seriesId)
            }
            prefs[KEY_FAVORITE_SERIES] = current
        }
    }

    suspend fun saveWatchProgress(streamId: Int, positionMs: Long, durationMs: Long) {
        context.dataStore.edit { prefs ->
            val json = prefs[KEY_WATCH_PROGRESS] ?: "{}"
            val type = object : TypeToken<MutableMap<String, WatchProgress>>() {}.type
            val map: MutableMap<String, WatchProgress> = try {
                gson.fromJson(json, type) ?: mutableMapOf()
            } catch (e: Exception) {
                mutableMapOf()
            }
            map[streamId.toString()] = WatchProgress(streamId, positionMs, durationMs)
            prefs[KEY_WATCH_PROGRESS] = gson.toJson(map)
        }
    }

    suspend fun getWatchProgress(streamId: Int): WatchProgress? {
        val prefs = context.dataStore.data.first()
        val json = prefs[KEY_WATCH_PROGRESS] ?: return null
        val type = object : TypeToken<Map<String, WatchProgress>>() {}.type
        val map: Map<String, WatchProgress> = try {
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
        return map[streamId.toString()]
    }

    suspend fun setAspectRatio(ratio: String) {
        context.dataStore.edit { it[KEY_ASPECT_RATIO] = ratio }
    }

    suspend fun getAspectRatio(): String {
        return context.dataStore.data.first()[KEY_ASPECT_RATIO] ?: "FIT"
    }

    suspend fun setParentalPin(pin: String) {
        context.dataStore.edit { it[KEY_PARENTAL_PIN] = pin }
    }

    suspend fun toggleCategoryHidden(categoryId: String) {
        context.dataStore.edit { prefs ->
            val current = (prefs[KEY_LOCKED_CATEGORIES] ?: emptySet()).toMutableSet()
            if (current.contains(categoryId)) current.remove(categoryId) else current.add(categoryId)
            prefs[KEY_LOCKED_CATEGORIES] = current
        }
    }

    val lockedCategoriesFlow: Flow<Set<String>> = context.dataStore.data.map { it[KEY_LOCKED_CATEGORIES] ?: emptySet() }

    val subtitleSizeFlow: Flow<String> = context.dataStore.data.map { it[KEY_SUBTITLE_SIZE] ?: "ORTA" }
    val subtitleColorFlow: Flow<String> = context.dataStore.data.map { it[KEY_SUBTITLE_COLOR] ?: "BEYAZ" }

    suspend fun setSubtitleSize(size: String) {
        context.dataStore.edit { it[KEY_SUBTITLE_SIZE] = size }
    }

    suspend fun setSubtitleColor(color: String) {
        context.dataStore.edit { it[KEY_SUBTITLE_COLOR] = color }
    }
}
