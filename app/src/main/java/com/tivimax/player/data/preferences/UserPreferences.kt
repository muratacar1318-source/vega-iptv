package com.tivimax.player.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tivimax.player.data.config.AppConfig
import com.tivimax.player.util.DeviceMode
import com.tivimax.player.util.OrientationManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

val Context.appDataStore by preferencesDataStore(name = "tivimax_preferences")

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
        fun keyFor(base: String, playlistId: String): String {
            return if (playlistId == "default" || playlistId.isBlank()) {
                base
            } else {
                "${base}__${playlistId}"
            }
        }

        val KEY_ACTIVE_PLAYLIST_ID = stringPreferencesKey("active_playlist_id")

        val KEY_USERNAME = stringPreferencesKey("username")
        val KEY_PASSWORD = stringPreferencesKey("password")
        val KEY_SERVER_URL = stringPreferencesKey("server_url")
        val KEY_REMEMBER_ME = booleanPreferencesKey("remember_me")
        val KEY_IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val KEY_EXP_DATE = stringPreferencesKey("exp_date")
        val KEY_MAX_CONNECTIONS = stringPreferencesKey("max_connections")
        val KEY_ACTIVE_CONNECTIONS = stringPreferencesKey("active_connections")
        val KEY_STATUS = stringPreferencesKey("status")
        val KEY_IS_TRIAL = stringPreferencesKey("is_trial")
        val KEY_CREATED_AT = stringPreferencesKey("created_at")

        // Favoriler
        val KEY_FAVORITE_LIVE = stringSetPreferencesKey("fav_live")
        val KEY_FAVORITE_VOD = stringSetPreferencesKey("fav_vod")
        val KEY_FAVORITE_SERIES = stringSetPreferencesKey("fav_series")

        // İzleme Geçmişi ve Kaldığı Yer (JSON String)
        val KEY_WATCH_PROGRESS = stringPreferencesKey("watch_progress_json")

        // Ebeveyn Kilidi & Gizlenen Kategoriler
        val KEY_PARENTAL_PIN = stringPreferencesKey("parental_pin")
        val KEY_LOCKED_CATEGORIES = stringSetPreferencesKey("locked_categories")
        val KEY_PINNED_CATEGORIES = stringSetPreferencesKey("pinned_categories")

        // Son İzlenen Canlı Kanallar (Son 20 Kanal - JSON StreamId Listesi)
        val KEY_RECENT_LIVE_CHANNELS = stringPreferencesKey("recent_live_channels_json")

        // Son Aramalar (En fazla 8 - JSON Listesi)
        val KEY_RECENT_SEARCHES = stringPreferencesKey("recent_searches_json")

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
        val KEY_SUBTITLE_DELAY_MS = longPreferencesKey("subtitle_delay_ms") // 0..5000 ms

        // Varsayılan Ses ve Altyazı Dili Tercihleri
        val KEY_DEFAULT_AUDIO_LANG = stringPreferencesKey("default_audio_lang") // "tr", "en", "de", "fr", "auto"
        val KEY_DEFAULT_SUBTITLE_LANG = stringPreferencesKey("default_subtitle_lang") // "tr", "en", "de", "fr", "none", "auto"

        // Oynatıcı Motoru Tercihi ("EXOPLAYER", "VLC", "EXTERNAL_VLC")
        val KEY_PLAYER_ENGINE = stringPreferencesKey("player_engine")

        // Uygulama Teması (Görünüm)
        val KEY_APP_THEME = stringPreferencesKey("app_theme") // "MIDNIGHT", "OCEAN", "FOREST", "VIOLET"

        // Otomatik Kare Hızı Eşleme (Auto Frame Rate - AFR)
        val KEY_AUTO_FRAME_RATE = booleanPreferencesKey("auto_frame_rate") // true/false

        // Açılışta Son İzlenen Kanalı Otomatik Başlatma
        val KEY_AUTO_START_LAST_CHANNEL = booleanPreferencesKey("auto_start_last_channel") // true/false
        val KEY_LAST_PLAYED_STREAM_ID = intPreferencesKey("last_played_stream_id")

        // Oynatma Listesi Senkronizasyon Durumu
        val KEY_IS_PLAYLIST_SYNCED = booleanPreferencesKey("is_playlist_synced")
        val KEY_LAST_SYNC_TIMESTAMP = stringPreferencesKey("last_sync_timestamp")

        // Cihaz Modu (PHONE, TABLET, TV)
        val KEY_DEVICE_MODE = stringPreferencesKey("device_mode")
        val KEY_DEVICE_MODE_SET = booleanPreferencesKey("device_mode_set")

        // Canlı TV Düzeni ("SHELF" = Raf Düzeni, "CLASSIC" = Klasik Düzen [Varsayılan])
        val KEY_LIVE_LAYOUT_MODE = stringPreferencesKey("live_layout_mode")
        const val LIVE_LAYOUT_SHELF = "SHELF"
        const val LIVE_LAYOUT_CLASSIC = "CLASSIC"

        // Canlı TV Liste Görünüm Modu ("COMPACT" = Kompakt Liste, "GRID" = Izgara, "DETAILED" = Detaylı [Varsayılan])
        val KEY_LIVE_VIEW_MODE = stringPreferencesKey("live_view_mode")
        const val LIVE_VIEW_COMPACT = "COMPACT"
        const val LIVE_VIEW_GRID = "GRID"
        const val LIVE_VIEW_DETAILED = "DETAILED"

        // Dikey Kategori Navigasyon Modu ("CHIPS" = Yatay Çipler & Tam Genişlik [Varsayılan], "SIDEBAR" = Klasik Sol Panel, "DROPDOWN" = Kompakt Başlık Menüsü)
        val KEY_PORTRAIT_CATEGORY_NAV_STYLE = stringPreferencesKey("portrait_category_nav_style")
        const val PORTRAIT_CAT_NAV_CHIPS = "CHIPS"
        const val PORTRAIT_CAT_NAV_SIDEBAR = "SIDEBAR"
        const val PORTRAIT_CAT_NAV_DROPDOWN = "DROPDOWN"

        // Güncellemeyi Erteleme (Snooze)
        val KEY_SNOOZED_UPDATE_TAG = stringPreferencesKey("snoozed_update_tag")
        val KEY_SNOOZE_UPDATE_UNTIL = longPreferencesKey("snooze_update_until")

        // VOD Ön Isıtma Kalıcı Devre Dışı (Circuit Breaker)
        val KEY_VOD_PREWARM_DISABLED = booleanPreferencesKey("vod_prewarm_disabled")
    }

    val activePlaylistIdFlow: Flow<String> = context.appDataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_PLAYLIST_ID] ?: "default"
    }

    suspend fun getActivePlaylistId(): String = activePlaylistIdFlow.first()

    val usernameFlow: Flow<String> = context.appDataStore.data.map { it[KEY_USERNAME] ?: "" }
    val passwordFlow: Flow<String> = context.appDataStore.data.map { it[KEY_PASSWORD] ?: "" }
    val serverUrlFlow: Flow<String> = context.appDataStore.data.map { 
        val saved = it[KEY_SERVER_URL]
        if (saved.isNullOrBlank() || saved.contains("my-iptv-server")) {
            AppConfig.DEFAULT_SERVER_URL
        } else {
            saved
        }
    }
    val isLoggedInFlow: Flow<Boolean> = context.appDataStore.data.map { it[KEY_IS_LOGGED_IN] ?: false }

    @OptIn(ExperimentalCoroutinesApi::class)
    val expDateFlow: Flow<String> = activePlaylistIdFlow.flatMapLatest { id ->
        context.appDataStore.data.map { it[stringPreferencesKey(keyFor("exp_date", id))] ?: "" }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val maxConnectionsFlow: Flow<String> = activePlaylistIdFlow.flatMapLatest { id ->
        context.appDataStore.data.map { it[stringPreferencesKey(keyFor("max_connections", id))] ?: "1" }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeConnectionsFlow: Flow<String> = activePlaylistIdFlow.flatMapLatest { id ->
        context.appDataStore.data.map { it[stringPreferencesKey(keyFor("active_connections", id))] ?: "1" }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val statusFlow: Flow<String> = activePlaylistIdFlow.flatMapLatest { id ->
        context.appDataStore.data.map { it[stringPreferencesKey(keyFor("status", id))] ?: "" }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val isTrialFlow: Flow<String> = activePlaylistIdFlow.flatMapLatest { id ->
        context.appDataStore.data.map { it[stringPreferencesKey(keyFor("is_trial", id))] ?: "" }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val createdAtFlow: Flow<String> = activePlaylistIdFlow.flatMapLatest { id ->
        context.appDataStore.data.map { it[stringPreferencesKey(keyFor("created_at", id))] ?: "" }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val favoriteLiveFlow: Flow<Set<String>> = activePlaylistIdFlow.flatMapLatest { id ->
        context.appDataStore.data.map { it[stringSetPreferencesKey(keyFor("fav_live", id))] ?: emptySet() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val favoriteVodFlow: Flow<Set<String>> = activePlaylistIdFlow.flatMapLatest { id ->
        context.appDataStore.data.map { it[stringSetPreferencesKey(keyFor("fav_vod", id))] ?: emptySet() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val favoriteSeriesFlow: Flow<Set<String>> = activePlaylistIdFlow.flatMapLatest { id ->
        context.appDataStore.data.map { it[stringSetPreferencesKey(keyFor("fav_series", id))] ?: emptySet() }
    }

    val parentalPinFlow: Flow<String> = context.appDataStore.data.map { it[KEY_PARENTAL_PIN] ?: "0000" }
    val streamFormatFlow: Flow<String> = context.appDataStore.data.map { it[KEY_STREAM_FORMAT] ?: "TS" }
    val fastZappingFlow: Flow<Boolean> = context.appDataStore.data.map { it[KEY_FAST_ZAPPING] ?: true }
    val aspectRatioFlow: Flow<String> = context.appDataStore.data.map { it[KEY_ASPECT_RATIO] ?: "FIT" }

    val liveSortFlow: Flow<String> = context.appDataStore.data.map { it[KEY_LIVE_SORT] ?: "DEFAULT" }
    val vodSortFlow: Flow<String> = context.appDataStore.data.map { it[KEY_VOD_SORT] ?: "DEFAULT" }
    val seriesSortFlow: Flow<String> = context.appDataStore.data.map { it[KEY_SERIES_SORT] ?: "DEFAULT" }
    val autoSyncLiveFlow: Flow<Boolean> = context.appDataStore.data.map { it[KEY_AUTO_SYNC_LIVE] ?: true }
    val autoPipFlow: Flow<Boolean> = context.appDataStore.data.map { it[KEY_AUTO_PIP] ?: true }
    val vodBufferSecondsFlow: Flow<Int> = context.appDataStore.data.map { it[KEY_VOD_BUFFER_SECONDS] ?: 180 }
    val liveBufferSecondsFlow: Flow<Int> = context.appDataStore.data.map { it[KEY_LIVE_BUFFER_SECONDS] ?: 2 }
    val decoderModeFlow: Flow<String> = context.appDataStore.data.map { it[KEY_DECODER_MODE] ?: "HARDWARE" }

    suspend fun setLiveSort(sort: String) {
        context.appDataStore.edit { it[KEY_LIVE_SORT] = sort }
    }

    suspend fun setVodSort(sort: String) {
        context.appDataStore.edit { it[KEY_VOD_SORT] = sort }
    }

    suspend fun setSeriesSort(sort: String) {
        context.appDataStore.edit { it[KEY_SERIES_SORT] = sort }
    }

    suspend fun setAutoSyncLive(enabled: Boolean) {
        context.appDataStore.edit { it[KEY_AUTO_SYNC_LIVE] = enabled }
    }

    suspend fun setAutoPip(enabled: Boolean) {
        context.appDataStore.edit { it[KEY_AUTO_PIP] = enabled }
    }

    suspend fun setVodBufferSeconds(seconds: Int) {
        context.appDataStore.edit { it[KEY_VOD_BUFFER_SECONDS] = seconds }
    }

    suspend fun setLiveBufferSeconds(seconds: Int) {
        context.appDataStore.edit { it[KEY_LIVE_BUFFER_SECONDS] = seconds }
    }

    suspend fun setDecoderMode(mode: String) {
        context.appDataStore.edit { it[KEY_DECODER_MODE] = mode }
    }

    suspend fun setStreamFormat(format: String) {
        context.appDataStore.edit { it[KEY_STREAM_FORMAT] = format }
    }

    suspend fun setFastZapping(enabled: Boolean) {
        context.appDataStore.edit { it[KEY_FAST_ZAPPING] = enabled }
    }

    suspend fun setServerUrl(url: String) {
        context.appDataStore.edit { it[KEY_SERVER_URL] = url }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val isPlaylistSyncedFlow: Flow<Boolean> = activePlaylistIdFlow.flatMapLatest { id ->
        context.appDataStore.data.map { it[booleanPreferencesKey(keyFor("is_playlist_synced", id))] ?: false }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val lastSyncTimestampFlow: Flow<String> = activePlaylistIdFlow.flatMapLatest { id ->
        context.appDataStore.data.map { it[stringPreferencesKey(keyFor("last_sync_timestamp", id))] ?: "" }
    }

    suspend fun setPlaylistSynced(synced: Boolean, timestamp: String = "", playlistId: String? = null) {
        val plId = playlistId ?: getActivePlaylistId()
        context.appDataStore.edit { prefs ->
            prefs[booleanPreferencesKey(keyFor("is_playlist_synced", plId))] = synced
            if (timestamp.isNotEmpty()) {
                prefs[stringPreferencesKey(keyFor("last_sync_timestamp", plId))] = timestamp
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val watchProgressMapFlow: Flow<Map<String, WatchProgress>> = activePlaylistIdFlow.flatMapLatest { id ->
        context.appDataStore.data.map { prefs ->
            val json = prefs[stringPreferencesKey(keyFor("watch_progress_json", id))] ?: "{}"
            val type = object : TypeToken<Map<String, WatchProgress>>() {}.type
            try {
                gson.fromJson(json, type) ?: emptyMap()
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }

    suspend fun saveCredentials(username: String, pass: String, serverUrl: String) {
        context.appDataStore.edit { prefs ->
            prefs[KEY_USERNAME] = username
            prefs[KEY_PASSWORD] = pass
            prefs[KEY_SERVER_URL] = serverUrl
            prefs[KEY_IS_LOGGED_IN] = true
        }
    }

    suspend fun saveAuth(
        username: String,
        password: String,
        serverUrl: String = AppConfig.DEFAULT_SERVER_URL,
        expDate: String = "",
        maxCons: String = "1",
        activeCons: String = "1",
        status: String = "",
        isTrial: String = "",
        createdAt: String = "",
        playlistId: String? = null
    ) {
        val plId = playlistId ?: getActivePlaylistId()
        context.appDataStore.edit { prefs ->
            prefs[KEY_USERNAME] = username
            prefs[KEY_PASSWORD] = password
            prefs[KEY_SERVER_URL] = serverUrl
            prefs[KEY_IS_LOGGED_IN] = true

            prefs[stringPreferencesKey(keyFor("exp_date", plId))] = expDate
            prefs[stringPreferencesKey(keyFor("max_connections", plId))] = maxCons
            prefs[stringPreferencesKey(keyFor("active_connections", plId))] = activeCons
            prefs[stringPreferencesKey(keyFor("status", plId))] = status
            prefs[stringPreferencesKey(keyFor("is_trial", plId))] = isTrial
            prefs[stringPreferencesKey(keyFor("created_at", plId))] = createdAt
        }
    }

    suspend fun logout() {
        context.appDataStore.edit { prefs ->
            prefs[KEY_IS_LOGGED_IN] = false
            prefs[KEY_PASSWORD] = ""
            prefs[KEY_IS_PLAYLIST_SYNCED] = false
        }
    }

    suspend fun toggleFavoriteLive(streamId: String, playlistId: String? = null) {
        val plId = playlistId ?: getActivePlaylistId()
        val key = stringSetPreferencesKey(keyFor("fav_live", plId))
        context.appDataStore.edit { prefs ->
            val current = (prefs[key] ?: emptySet()).toMutableSet()
            if (current.contains(streamId)) {
                current.remove(streamId)
            } else {
                current.add(streamId)
            }
            prefs[key] = current
        }
    }

    suspend fun toggleFavoriteVod(streamId: String, playlistId: String? = null) {
        val plId = playlistId ?: getActivePlaylistId()
        val key = stringSetPreferencesKey(keyFor("fav_vod", plId))
        context.appDataStore.edit { prefs ->
            val current = (prefs[key] ?: emptySet()).toMutableSet()
            if (current.contains(streamId)) {
                current.remove(streamId)
            } else {
                current.add(streamId)
            }
            prefs[key] = current
        }
    }

    suspend fun toggleFavoriteSeries(seriesId: String, playlistId: String? = null) {
        val plId = playlistId ?: getActivePlaylistId()
        val key = stringSetPreferencesKey(keyFor("fav_series", plId))
        context.appDataStore.edit { prefs ->
            val current = (prefs[key] ?: emptySet()).toMutableSet()
            if (current.contains(seriesId)) {
                current.remove(seriesId)
            } else {
                current.add(seriesId)
            }
            prefs[key] = current
        }
    }

    suspend fun saveWatchProgress(streamId: Int, positionMs: Long, durationMs: Long, playlistId: String? = null) {
        val plId = playlistId ?: getActivePlaylistId()
        val key = stringPreferencesKey(keyFor("watch_progress_json", plId))
        context.appDataStore.edit { prefs ->
            val json = prefs[key] ?: "{}"
            val type = object : TypeToken<MutableMap<String, WatchProgress>>() {}.type
            val map: MutableMap<String, WatchProgress> = try {
                gson.fromJson(json, type) ?: mutableMapOf()
            } catch (e: Exception) {
                mutableMapOf()
            }
            map[streamId.toString()] = WatchProgress(streamId, positionMs, durationMs)
            prefs[key] = gson.toJson(map)
        }
    }

    suspend fun getWatchProgress(streamId: Int, playlistId: String? = null): WatchProgress? {
        val plId = playlistId ?: getActivePlaylistId()
        val prefs = context.appDataStore.data.first()
        val key = stringPreferencesKey(keyFor("watch_progress_json", plId))
        val json = prefs[key] ?: return null
        val type = object : TypeToken<Map<String, WatchProgress>>() {}.type
        val map: Map<String, WatchProgress> = try {
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
        return map[streamId.toString()]
    }

    suspend fun removeWatchProgress(streamId: Int, playlistId: String? = null) {
        val plId = playlistId ?: getActivePlaylistId()
        val key = stringPreferencesKey(keyFor("watch_progress_json", plId))
        context.appDataStore.edit { prefs ->
            val json = prefs[key] ?: "{}"
            val type = object : TypeToken<MutableMap<String, WatchProgress>>() {}.type
            val map: MutableMap<String, WatchProgress> = try {
                gson.fromJson(json, type) ?: mutableMapOf()
            } catch (e: Exception) {
                mutableMapOf()
            }
            map.remove(streamId.toString())
            prefs[key] = gson.toJson(map)
        }
    }

    suspend fun clearAllWatchProgress(playlistId: String? = null) {
        val plId = playlistId ?: getActivePlaylistId()
        val key = stringPreferencesKey(keyFor("watch_progress_json", plId))
        context.appDataStore.edit { prefs ->
            prefs.remove(key)
        }
    }

    suspend fun setAspectRatio(ratio: String) {
        context.appDataStore.edit { it[KEY_ASPECT_RATIO] = ratio }
    }

    suspend fun getAspectRatio(): String {
        return context.appDataStore.data.first()[KEY_ASPECT_RATIO] ?: "FIT"
    }

    suspend fun setParentalPin(pin: String) {
        context.appDataStore.edit { it[KEY_PARENTAL_PIN] = pin }
    }

    suspend fun toggleCategoryHidden(categoryId: String) {
        context.appDataStore.edit { prefs ->
            val current = (prefs[KEY_LOCKED_CATEGORIES] ?: emptySet()).toMutableSet()
            if (current.contains(categoryId)) current.remove(categoryId) else current.add(categoryId)
            prefs[KEY_LOCKED_CATEGORIES] = current
        }
    }

    val lockedCategoriesFlow: Flow<Set<String>> = context.appDataStore.data.map { it[KEY_LOCKED_CATEGORIES] ?: emptySet() }

    @OptIn(ExperimentalCoroutinesApi::class)
    val pinnedCategoriesFlow: Flow<Set<String>> = activePlaylistIdFlow.flatMapLatest { id ->
        context.appDataStore.data.map { it[stringSetPreferencesKey(keyFor("pinned_categories", id))] ?: emptySet() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val recentLiveChannelsFlow: Flow<String> = activePlaylistIdFlow.flatMapLatest { id ->
        context.appDataStore.data.map { it[stringPreferencesKey(keyFor("recent_live_channels_json", id))] ?: "[]" }
    }

    suspend fun saveRecentLiveChannels(streamIds: List<Int>, playlistId: String? = null) {
        val plId = playlistId ?: getActivePlaylistId()
        val key = stringPreferencesKey(keyFor("recent_live_channels_json", plId))
        context.appDataStore.edit { prefs ->
            prefs[key] = gson.toJson(streamIds)
        }
    }

    suspend fun toggleCategoryPinned(categoryId: String, playlistId: String? = null) {
        val plId = playlistId ?: getActivePlaylistId()
        val key = stringSetPreferencesKey(keyFor("pinned_categories", plId))
        context.appDataStore.edit { prefs ->
            val current = (prefs[key] ?: emptySet()).toMutableSet()
            if (current.contains(categoryId)) {
                current.remove(categoryId)
            } else {
                current.add(categoryId)
            }
            prefs[key] = current
        }
    }

    suspend fun setCategoryHidden(categoryId: String, isHidden: Boolean) {
        context.appDataStore.edit { prefs ->
            val current = (prefs[KEY_LOCKED_CATEGORIES] ?: emptySet()).toMutableSet()
            if (isHidden) {
                current.add(categoryId)
            } else {
                current.remove(categoryId)
            }
            prefs[KEY_LOCKED_CATEGORIES] = current
        }
    }

    suspend fun setAllCategoriesHidden(categoryIds: Collection<String>, isHidden: Boolean) {
        context.appDataStore.edit { prefs ->
            val current = (prefs[KEY_LOCKED_CATEGORIES] ?: emptySet()).toMutableSet()
            if (isHidden) {
                current.addAll(categoryIds)
            } else {
                current.removeAll(categoryIds.toSet())
            }
            prefs[KEY_LOCKED_CATEGORIES] = current
        }
    }

    val subtitleSizeFlow: Flow<String> = context.appDataStore.data.map { it[KEY_SUBTITLE_SIZE] ?: "ORTA" }
    val subtitleColorFlow: Flow<String> = context.appDataStore.data.map { it[KEY_SUBTITLE_COLOR] ?: "BEYAZ" }
    val subtitleDelayMsFlow: Flow<Long> = context.appDataStore.data.map { it[KEY_SUBTITLE_DELAY_MS] ?: 0L }

    val defaultAudioLangFlow: Flow<String> = context.appDataStore.data.map { it[KEY_DEFAULT_AUDIO_LANG] ?: "tr" }
    val defaultSubtitleLangFlow: Flow<String> = context.appDataStore.data.map { it[KEY_DEFAULT_SUBTITLE_LANG] ?: "tr" }
    val playerEngineFlow: Flow<String> = context.appDataStore.data.map { it[KEY_PLAYER_ENGINE] ?: "EXOPLAYER" }

    val appThemeFlow: Flow<String> = context.appDataStore.data.map { it[KEY_APP_THEME] ?: "MIDNIGHT" }

    val autoFrameRateFlow: Flow<Boolean> = context.appDataStore.data.map { it[KEY_AUTO_FRAME_RATE] ?: true }
    val autoStartLastChannelFlow: Flow<Boolean> = context.appDataStore.data.map { it[KEY_AUTO_START_LAST_CHANNEL] ?: false }

    @OptIn(ExperimentalCoroutinesApi::class)
    val lastPlayedStreamIdFlow: Flow<Int> = activePlaylistIdFlow.flatMapLatest { id ->
        context.appDataStore.data.map { it[intPreferencesKey(keyFor("last_played_stream_id", id))] ?: 0 }
    }

    suspend fun setAppTheme(themeName: String) {
        context.appDataStore.edit { it[KEY_APP_THEME] = themeName }
    }

    suspend fun setSubtitleSize(size: String) {
        context.appDataStore.edit { it[KEY_SUBTITLE_SIZE] = size }
    }

    suspend fun setSubtitleColor(color: String) {
        context.appDataStore.edit { it[KEY_SUBTITLE_COLOR] = color }
    }

    suspend fun setSubtitleDelayMs(delayMs: Long) {
        context.appDataStore.edit { it[KEY_SUBTITLE_DELAY_MS] = delayMs.coerceIn(0L, 5000L) }
    }

    suspend fun setDefaultAudioLang(lang: String) {
        context.appDataStore.edit { it[KEY_DEFAULT_AUDIO_LANG] = lang }
    }

    suspend fun setDefaultSubtitleLang(lang: String) {
        context.appDataStore.edit { it[KEY_DEFAULT_SUBTITLE_LANG] = lang }
    }

    suspend fun setPlayerEngine(engine: String) {
        context.appDataStore.edit { it[KEY_PLAYER_ENGINE] = engine }
    }

    suspend fun setAutoFrameRate(enabled: Boolean) {
        context.appDataStore.edit { it[KEY_AUTO_FRAME_RATE] = enabled }
    }

    suspend fun setAutoStartLastChannel(enabled: Boolean) {
        context.appDataStore.edit { it[KEY_AUTO_START_LAST_CHANNEL] = enabled }
    }

    suspend fun setLastPlayedStreamId(streamId: Int, playlistId: String? = null) {
        val plId = playlistId ?: getActivePlaylistId()
        val key = intPreferencesKey(keyFor("last_played_stream_id", plId))
        context.appDataStore.edit { it[key] = streamId }
    }

    val deviceModeFlow: Flow<DeviceMode?> = context.appDataStore.data.map { prefs ->
        val modeStr = prefs[KEY_DEVICE_MODE]
        if (modeStr.isNullOrEmpty()) null
        else {
            try {
                DeviceMode.valueOf(modeStr)
            } catch (_: Exception) {
                null
            }
        }
    }

    val isDeviceModeSetFlow: Flow<Boolean> = context.appDataStore.data.map { prefs ->
        prefs[KEY_DEVICE_MODE_SET] ?: false
    }

    suspend fun setDeviceMode(mode: DeviceMode) {
        OrientationManager.saveFastCache(context, mode)
        context.appDataStore.edit { prefs ->
            prefs[KEY_DEVICE_MODE] = mode.name
            prefs[KEY_DEVICE_MODE_SET] = true
        }
    }

    suspend fun resetDeviceMode() {
        OrientationManager.clearFastCache(context)
        context.appDataStore.edit { prefs ->
            prefs.remove(KEY_DEVICE_MODE)
            prefs[KEY_DEVICE_MODE_SET] = false
        }
    }

    val liveLayoutModeFlow: Flow<String> = context.appDataStore.data.map { it[KEY_LIVE_LAYOUT_MODE] ?: LIVE_LAYOUT_CLASSIC }

    suspend fun setLiveLayoutMode(mode: String) {
        context.appDataStore.edit { it[KEY_LIVE_LAYOUT_MODE] = mode }
    }

    val liveViewModeFlow: Flow<String> = context.appDataStore.data.map { it[KEY_LIVE_VIEW_MODE] ?: LIVE_VIEW_DETAILED }

    suspend fun setLiveViewMode(mode: String) {
        context.appDataStore.edit { it[KEY_LIVE_VIEW_MODE] = mode }
    }

    val portraitCategoryNavStyleFlow: Flow<String> = context.appDataStore.data.map { it[KEY_PORTRAIT_CATEGORY_NAV_STYLE] ?: PORTRAIT_CAT_NAV_CHIPS }

    suspend fun setPortraitCategoryNavStyle(style: String) {
        context.appDataStore.edit { prefs ->
            prefs[KEY_PORTRAIT_CATEGORY_NAV_STYLE] = style
        }
    }

    suspend fun snoozeUpdate(tagName: String, durationHours: Int = 24) {
        val until = System.currentTimeMillis() + (durationHours * 60 * 60 * 1000L)
        context.appDataStore.edit { prefs ->
            prefs[KEY_SNOOZED_UPDATE_TAG] = tagName
            prefs[KEY_SNOOZE_UPDATE_UNTIL] = until
        }
    }

    suspend fun isUpdateSnoozed(tagName: String): Boolean {
        val prefs = context.appDataStore.data.first()
        val snoozedTag = prefs[KEY_SNOOZED_UPDATE_TAG]
        val until = prefs[KEY_SNOOZE_UPDATE_UNTIL] ?: 0L
        val now = System.currentTimeMillis()
        return (snoozedTag == tagName && now < until)
    }

    suspend fun clearUpdateSnooze() {
        context.appDataStore.edit { prefs ->
            prefs.remove(KEY_SNOOZED_UPDATE_TAG)
            prefs.remove(KEY_SNOOZE_UPDATE_UNTIL)
        }
    }

    val vodPrewarmDisabledFlow: Flow<Boolean> = context.appDataStore.data.map { prefs ->
        prefs[KEY_VOD_PREWARM_DISABLED] ?: false
    }

    suspend fun setVodPrewarmDisabled(disabled: Boolean) {
        context.appDataStore.edit { prefs ->
            prefs[KEY_VOD_PREWARM_DISABLED] = disabled
        }
    }

    // ============================================================================
    // EVRENSEL ARAMA: SON ARAMALAR (EN FAZLA 8)
    // ============================================================================

    @OptIn(ExperimentalCoroutinesApi::class)
    val recentSearchesFlow: Flow<List<String>> = activePlaylistIdFlow.flatMapLatest { id ->
        context.appDataStore.data.map { prefs ->
            val json = prefs[stringPreferencesKey(keyFor("recent_searches_json", id))] ?: return@map emptyList()
            try {
                val type = object : TypeToken<List<String>>() {}.type
                gson.fromJson<List<String>>(json, type) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    suspend fun addRecentSearch(query: String, playlistId: String? = null) {
        val clean = query.trim()
        if (clean.length < 2) return
        val plId = playlistId ?: getActivePlaylistId()
        val key = stringPreferencesKey(keyFor("recent_searches_json", plId))
        context.appDataStore.edit { prefs ->
            val currentJson = prefs[key]
            val currentList: MutableList<String> = try {
                if (currentJson != null) {
                    val type = object : TypeToken<List<String>>() {}.type
                    (gson.fromJson<List<String>>(currentJson, type) ?: emptyList()).toMutableList()
                } else mutableListOf()
            } catch (_: Exception) {
                mutableListOf()
            }
            currentList.removeAll { it.equals(clean, ignoreCase = true) }
            currentList.add(0, clean)
            val trimmed = currentList.take(8)
            prefs[key] = gson.toJson(trimmed)
        }
    }

    suspend fun removeRecentSearch(query: String, playlistId: String? = null) {
        val clean = query.trim()
        val plId = playlistId ?: getActivePlaylistId()
        val key = stringPreferencesKey(keyFor("recent_searches_json", plId))
        context.appDataStore.edit { prefs ->
            val currentJson = prefs[key] ?: return@edit
            val currentList: MutableList<String> = try {
                val type = object : TypeToken<List<String>>() {}.type
                (gson.fromJson<List<String>>(currentJson, type) ?: emptyList()).toMutableList()
            } catch (_: Exception) {
                return@edit
            }
            currentList.removeAll { it.equals(clean, ignoreCase = true) }
            prefs[key] = gson.toJson(currentList)
        }
    }

    suspend fun clearRecentSearches(playlistId: String? = null) {
        val plId = playlistId ?: getActivePlaylistId()
        val key = stringPreferencesKey(keyFor("recent_searches_json", plId))
        context.appDataStore.edit { prefs ->
            prefs.remove(key)
        }
    }

    /**
     * Bir oynatma listesi silindiğinde o listeye ait tüm özel tercihleri temizler.
     */
    suspend fun removePlaylistPreferences(playlistId: String) {
        val baseKeys = listOf(
            "fav_live", "fav_vod", "fav_series",
            "watch_progress_json",
            "pinned_categories",
            "recent_live_channels_json",
            "recent_searches_json",
            "last_played_stream_id",
            "is_playlist_synced", "last_sync_timestamp",
            "exp_date", "max_connections", "active_connections", "status", "is_trial", "created_at"
        )
        context.appDataStore.edit { prefs ->
            for (base in baseKeys) {
                val targetName = keyFor(base, playlistId)
                val matching = prefs.asMap().keys.filter { it.name == targetName }
                for (k in matching) {
                    prefs.remove(k)
                }
            }
        }
    }
}
