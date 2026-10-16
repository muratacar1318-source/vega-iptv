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
import com.tivimax.player.util.DeviceMode
import com.tivimax.player.util.OrientationManager
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

        // Ebeveyn Kilidi & Gizlenen Kategoriler
        val KEY_PARENTAL_PIN = stringPreferencesKey("parental_pin")
        val KEY_LOCKED_CATEGORIES = stringSetPreferencesKey("locked_categories")
        val KEY_PINNED_CATEGORIES = stringSetPreferencesKey("pinned_categories")

        // Son İzlenen Canlı Kanallar (Son 20 Kanal - JSON StreamId Listesi)
        val KEY_RECENT_LIVE_CHANNELS = stringPreferencesKey("recent_live_channels_json")

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

        // Canlı TV Düzeni ("SHELF" = Raf Düzeni [Varsayılan], "CLASSIC" = Klasik Düzen)
        val KEY_LIVE_LAYOUT_MODE = stringPreferencesKey("live_layout_mode")
        const val LIVE_LAYOUT_SHELF = "SHELF"
        const val LIVE_LAYOUT_CLASSIC = "CLASSIC"

        // Canlı TV Liste Görünüm Modu ("COMPACT" = Kompakt Liste [Varsayılan], "GRID" = Izgara, "DETAILED" = Detaylı)
        val KEY_LIVE_VIEW_MODE = stringPreferencesKey("live_view_mode")
        const val LIVE_VIEW_COMPACT = "COMPACT"
        const val LIVE_VIEW_GRID = "GRID"
        const val LIVE_VIEW_DETAILED = "DETAILED"
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

    val isPlaylistSyncedFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_IS_PLAYLIST_SYNCED] ?: false }
    val lastSyncTimestampFlow: Flow<String> = context.dataStore.data.map { it[KEY_LAST_SYNC_TIMESTAMP] ?: "" }

    suspend fun setPlaylistSynced(synced: Boolean, timestamp: String = "") {
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_PLAYLIST_SYNCED] = synced
            if (timestamp.isNotEmpty()) {
                prefs[KEY_LAST_SYNC_TIMESTAMP] = timestamp
            }
        }
    }

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
            prefs[KEY_IS_PLAYLIST_SYNCED] = false
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
    val pinnedCategoriesFlow: Flow<Set<String>> = context.dataStore.data.map { it[KEY_PINNED_CATEGORIES] ?: emptySet() }
    val recentLiveChannelsFlow: Flow<String> = context.dataStore.data.map { it[KEY_RECENT_LIVE_CHANNELS] ?: "[]" }

    suspend fun saveRecentLiveChannels(streamIds: List<Int>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_RECENT_LIVE_CHANNELS] = gson.toJson(streamIds)
        }
    }

    suspend fun toggleCategoryPinned(categoryId: String) {
        context.dataStore.edit { prefs ->
            val current = (prefs[KEY_PINNED_CATEGORIES] ?: emptySet()).toMutableSet()
            if (current.contains(categoryId)) {
                current.remove(categoryId)
            } else {
                current.add(categoryId)
            }
            prefs[KEY_PINNED_CATEGORIES] = current
        }
    }

    suspend fun setCategoryHidden(categoryId: String, isHidden: Boolean) {
        context.dataStore.edit { prefs ->
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
        context.dataStore.edit { prefs ->
            val current = (prefs[KEY_LOCKED_CATEGORIES] ?: emptySet()).toMutableSet()
            if (isHidden) {
                current.addAll(categoryIds)
            } else {
                current.removeAll(categoryIds.toSet())
            }
            prefs[KEY_LOCKED_CATEGORIES] = current
        }
    }

    val subtitleSizeFlow: Flow<String> = context.dataStore.data.map { it[KEY_SUBTITLE_SIZE] ?: "ORTA" }
    val subtitleColorFlow: Flow<String> = context.dataStore.data.map { it[KEY_SUBTITLE_COLOR] ?: "BEYAZ" }

    val defaultAudioLangFlow: Flow<String> = context.dataStore.data.map { it[KEY_DEFAULT_AUDIO_LANG] ?: "tr" }
    val defaultSubtitleLangFlow: Flow<String> = context.dataStore.data.map { it[KEY_DEFAULT_SUBTITLE_LANG] ?: "tr" }
    val playerEngineFlow: Flow<String> = context.dataStore.data.map { it[KEY_PLAYER_ENGINE] ?: "EXOPLAYER" }

    val appThemeFlow: Flow<String> = context.dataStore.data.map { it[KEY_APP_THEME] ?: "MIDNIGHT" }

    val autoFrameRateFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_FRAME_RATE] ?: true }
    val autoStartLastChannelFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_START_LAST_CHANNEL] ?: false }
    val lastPlayedStreamIdFlow: Flow<Int> = context.dataStore.data.map { it[KEY_LAST_PLAYED_STREAM_ID] ?: 0 }

    suspend fun setAppTheme(themeName: String) {
        context.dataStore.edit { it[KEY_APP_THEME] = themeName }
    }

    suspend fun setSubtitleSize(size: String) {
        context.dataStore.edit { it[KEY_SUBTITLE_SIZE] = size }
    }

    suspend fun setSubtitleColor(color: String) {
        context.dataStore.edit { it[KEY_SUBTITLE_COLOR] = color }
    }

    suspend fun setDefaultAudioLang(lang: String) {
        context.dataStore.edit { it[KEY_DEFAULT_AUDIO_LANG] = lang }
    }

    suspend fun setDefaultSubtitleLang(lang: String) {
        context.dataStore.edit { it[KEY_DEFAULT_SUBTITLE_LANG] = lang }
    }

    suspend fun setPlayerEngine(engine: String) {
        context.dataStore.edit { it[KEY_PLAYER_ENGINE] = engine }
    }

    suspend fun setAutoFrameRate(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_FRAME_RATE] = enabled }
    }

    suspend fun setAutoStartLastChannel(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_START_LAST_CHANNEL] = enabled }
    }

    suspend fun setLastPlayedStreamId(streamId: Int) {
        context.dataStore.edit { it[KEY_LAST_PLAYED_STREAM_ID] = streamId }
    }

    val deviceModeFlow: Flow<DeviceMode?> = context.dataStore.data.map { prefs ->
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

    val isDeviceModeSetFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEVICE_MODE_SET] ?: false
    }

    suspend fun setDeviceMode(mode: DeviceMode) {
        OrientationManager.saveFastCache(context, mode)
        context.dataStore.edit { prefs ->
            prefs[KEY_DEVICE_MODE] = mode.name
            prefs[KEY_DEVICE_MODE_SET] = true
        }
    }

    suspend fun resetDeviceMode() {
        OrientationManager.clearFastCache(context)
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_DEVICE_MODE)
            prefs[KEY_DEVICE_MODE_SET] = false
        }
    }

    val liveLayoutModeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LIVE_LAYOUT_MODE] ?: LIVE_LAYOUT_SHELF
    }

    suspend fun setLiveLayoutMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LIVE_LAYOUT_MODE] = mode
        }
    }

    val liveViewModeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LIVE_VIEW_MODE] ?: LIVE_VIEW_COMPACT
    }

    suspend fun setLiveViewMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LIVE_VIEW_MODE] = mode
        }
    }
}
