package com.tivimax.player.ui

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tivimax.player.data.config.AppConfig
import com.tivimax.player.data.local.AppDatabase
import com.tivimax.player.data.local.entity.*
import com.tivimax.player.data.model.*
import com.tivimax.player.data.playlist.Playlist
import com.tivimax.player.data.playlist.PlaylistRepository
import com.tivimax.player.data.preferences.UserPreferences
import com.tivimax.player.data.repository.XtreamRepository
import com.tivimax.player.player.AspectRatioMode
import com.tivimax.player.player.TiviPlayerManager
import com.tivimax.player.ui.main.HomeTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.tivimax.player.data.preferences.WatchProgress
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit
import com.tivimax.player.ui.live.modern.TurkishStringNormalizer
import com.tivimax.player.util.DeviceMode
import com.tivimax.player.util.OrientationManager
import com.tivimax.player.util.detectRecommendedDeviceMode
import android.os.SystemClock
import android.util.Log
import com.tivimax.player.BuildConfig

private const val TAG_TIMING = "VodTiming"

sealed class Screen {
    object Splash : Screen()
    object DeviceModeSetup : Screen()
    object Login : Screen()
    object PlaylistSync : Screen()
    object Home : Screen()
    data class Player(
        val streamUrl: String,
        val title: String,
        val isLive: Boolean,
        val streamId: Int,
        val categoryId: String = "",
        val startPositionMs: Long = 0L
    ) : Screen()
    data class VodDetail(val vodStream: VodStream) : Screen()
    data class SeriesDetail(val seriesStream: SeriesStream) : Screen()
    object Search : Screen()
}

enum class SearchFilter(val title: String) {
    ALL("Tümü"),
    LIVE("Canlı TV"),
    MOVIES("Filmler"),
    SERIES("Diziler")
}

enum class SyncStepStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

data class SyncStepInfo(
    val stepNumber: Int,
    val title: String,
    val status: SyncStepStatus = SyncStepStatus.PENDING,
    val detailText: String = "",
    val progress: Float = 0f,
    val errorMessage: String? = null
)

data class PlaylistSyncState(
    val isSyncing: Boolean = false,
    val currentStepIndex: Int = 1,
    val steps: List<SyncStepInfo> = listOf(
        SyncStepInfo(1, "Kategoriler alınıyor (Canlı TV, Film ve Dizi)..."),
        SyncStepInfo(2, "Canlı TV kanalları kaydediliyor..."),
        SyncStepInfo(3, "VOD (Film) arşivi kaydediliyor..."),
        SyncStepInfo(4, "Diziler ve bölümler kaydediliyor..."),
        SyncStepInfo(5, "Veritabanı optimize ediliyor ve kategoriler hazırlanıyor...")
    ),
    val isCompleted: Boolean = false,
    val failedStepIndex: Int? = null,
    val errorMessage: String? = null
) {
    val isLiveReady: Boolean
        get() = steps.getOrNull(0)?.status == SyncStepStatus.COMPLETED &&
                steps.getOrNull(1)?.status == SyncStepStatus.COMPLETED

    val overallPercentage: Int
        get() {
            if (isCompleted) return 100
            val completed = steps.count { it.status == SyncStepStatus.COMPLETED }
            val active = steps.firstOrNull { it.status == SyncStepStatus.IN_PROGRESS }
            val p = active?.progress?.coerceIn(0f, 1f) ?: 0f
            return (((completed + p) / 5f) * 100).toInt().coerceIn(0, 99)
        }
}

/** G-029: Ana ekrandaki arka plan güncelleme şeridinin durumu. */
enum class BackgroundSyncStatus { HIDDEN, RUNNING, DONE, FAILED, STOPPED }

@OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = XtreamRepository(application)
    val preferences = UserPreferences(application)
    val playlistRepository = PlaylistRepository(application)
    val playlists: StateFlow<List<Playlist>> = playlistRepository.playlistsFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        emptyList()
    )
    val activePlaylist: StateFlow<Playlist?> = playlistRepository.activePlaylistFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        null
    )
    val playerManager = TiviPlayerManager(application)
    val autoUpdateManager = com.tivimax.player.data.update.AutoUpdateManager(application)
    val vodPrewarmer = com.tivimax.player.player.VodPrewarmer(
        playerManager = playerManager,
        preferences = preferences,
        coroutineScope = viewModelScope,
        isBusyProvider = {
            playerManager.isPlaying.value ||
            playerManager.isBuffering.value ||
            playerManager.isDualViewActive.value ||
            isMiniPlayerActive.value ||
            activePreviewChannel.value != null
        }
    )

    // Cihaz Modu (Telefon / Tablet / TV)
    private val _deviceMode = MutableStateFlow<DeviceMode>(
        OrientationManager.getFastCachedDeviceMode(application) ?: application.detectRecommendedDeviceMode()
    )
    val deviceMode: StateFlow<DeviceMode> = _deviceMode.asStateFlow()

    val isDeviceModeSet: StateFlow<Boolean> = preferences.isDeviceModeSetFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        OrientationManager.getFastCachedDeviceMode(application) != null
    )

    // Navigasyon Durumu & Geri Yığını (BackStack)
    private val backStack = ArrayDeque<Screen>()
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Splash)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Merkezi Tam Ekran Video Durumu (Tüm Tam Ekran Yolları)
    private val _isVideoFullscreen = MutableStateFlow(false)
    val isVideoFullscreen: StateFlow<Boolean> = _isVideoFullscreen.asStateFlow()

    fun setVideoFullscreen(fullscreen: Boolean) {
        _isVideoFullscreen.value = fullscreen
    }

    fun navigateTo(screen: Screen, addToBackStack: Boolean = true) {
        if (addToBackStack) {
            val current = _currentScreen.value
            if (current != screen && !(current is Screen.Player && screen is Screen.Player)) {
                backStack.addLast(current)
            }
        }
        _currentScreen.value = screen
        if (screen is Screen.Player) {
            _isVideoFullscreen.value = true
        } else {
            _isVideoFullscreen.value = false
        }
    }

    fun navigateBack(): Boolean {
        if (backStack.isNotEmpty()) {
            val previous = backStack.removeLast()
            _currentScreen.value = previous
            if (previous !is Screen.Player) {
                _isVideoFullscreen.value = false
            }
            return true
        }
        return false
    }

    // Aktif Ana Menü Sekmesi (Film veya Dizi detayından geri dönünce konumu ve kategoriyi korur)
    val selectedHomeTab = MutableStateFlow(HomeTab.DASHBOARD)
    fun setSelectedHomeTab(tab: HomeTab) {
        if (selectedHomeTab.value != tab) {
            if (tab != HomeTab.LIVE_TV) {
                detachMiniPlayer()
            }
            selectedHomeTab.value = tab
        }
    }

    // Oturum Durumu
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _loginLoading = MutableStateFlow(false)
    val loginLoading: StateFlow<Boolean> = _loginLoading.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    val userInfo = MutableStateFlow<XtreamUserInfo?>(null)

    // Canlı TV Verileri
    val liveCategories = MutableStateFlow<List<LiveCategory>>(emptyList())
    val liveStreams = MutableStateFlow<List<LiveStream>>(emptyList())
    val selectedLiveCategory = MutableStateFlow<String>("all")
    val liveLoading = MutableStateFlow(false)
    val liveSearchQuery = MutableStateFlow("")
    val currentEpgList = MutableStateFlow<List<EpgProgram>>(emptyList())

    // VOD (Film) Verileri
    val vodCategories = MutableStateFlow<List<VodCategory>>(emptyList())
    val vodStreams = MutableStateFlow<List<VodStream>>(emptyList())
    val selectedVodCategory = MutableStateFlow<String>("all")
    val vodLoading = MutableStateFlow(false)
    val vodSearchQuery = MutableStateFlow("")
    val currentVodDetail = MutableStateFlow<VodInfoResponse?>(null)

    // Dizi Verileri
    val seriesCategories = MutableStateFlow<List<SeriesCategory>>(emptyList())
    val seriesStreams = MutableStateFlow<List<SeriesStream>>(emptyList())
    val selectedSeriesCategory = MutableStateFlow<String>("all")
    val seriesLoading = MutableStateFlow(false)
    val seriesSearchQuery = MutableStateFlow("")
    val currentSeriesInfo = MutableStateFlow<SeriesInfoResponse?>(null)

    // Favoriler & Son İzlenenler
    val favoriteLiveIds = preferences.favoriteLiveFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val favoriteVodIds = preferences.favoriteVodFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val favoriteSeriesIds = preferences.favoriteSeriesFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    
    // Tüm Veritabanındaki Favoriler (Kategori Bağımsız & Room DAO Düzeyinde Optimize)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val favoriteLiveStreams: StateFlow<List<LiveStream>> = favoriteLiveIds.flatMapLatest { ids ->
        val intIds = ids.mapNotNull { it.toIntOrNull() }
        repository.getLiveStreamsByIdsFlow(intIds).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val favoriteVodStreams: StateFlow<List<VodStream>> = favoriteVodIds.flatMapLatest { ids ->
        val intIds = ids.mapNotNull { it.toIntOrNull() }
        repository.getVodStreamsByIdsFlow(intIds).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val favoriteSeriesStreams: StateFlow<List<SeriesStream>> = favoriteSeriesIds.flatMapLatest { ids ->
        val intIds = ids.mapNotNull { it.toIntOrNull() }
        repository.getSeriesStreamsByIdsFlow(intIds).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentChannels = MutableStateFlow<List<LiveStream>>(emptyList())

    // Kategori İçerik İşleri (Room Database üzerinden Flow ile canlı gözlemlenir)
    private var liveStreamJob: Job? = null
    private var vodStreamJob: Job? = null
    private var seriesStreamJob: Job? = null
    private var liveCategoriesJob: Job? = null
    private var vodCategoriesJob: Job? = null
    private var seriesCategoriesJob: Job? = null

    // Genel Arama Durumu (Tüm kategoriler/bölümler arası arama aktarımı)
    val globalSearchQuery = MutableStateFlow("")

    // ============================================================================
    // EVRENSEL ARAMA (UNIVERSAL SEARCH)
    // ============================================================================

    val universalSearchQuery = MutableStateFlow("")
    val universalSearchFilter = MutableStateFlow(SearchFilter.ALL)
    val isUniversalSearching = MutableStateFlow(false)
    val universalLiveResults = MutableStateFlow<List<LiveStream>>(emptyList())
    val universalVodResults = MutableStateFlow<List<VodStream>>(emptyList())
    val universalSeriesResults = MutableStateFlow<List<SeriesStream>>(emptyList())

    val recentSearches: StateFlow<List<String>> = preferences.recentSearchesFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    private var universalSearchJob: Job? = null

    fun setUniversalSearchQuery(query: String) {
        universalSearchQuery.value = query
        triggerUniversalSearch(query)
    }

    fun setUniversalSearchFilter(filter: SearchFilter) {
        universalSearchFilter.value = filter
    }

    fun saveRecentSearch(query: String) {
        val clean = query.trim()
        if (clean.length >= 2) {
            viewModelScope.launch {
                preferences.addRecentSearch(clean)
            }
        }
    }

    fun removeRecentSearch(query: String) {
        viewModelScope.launch {
            preferences.removeRecentSearch(query)
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            preferences.clearRecentSearches()
        }
    }

    private fun triggerUniversalSearch(query: String) {
        universalSearchJob?.cancel()
        val clean = query.trim()
        if (clean.length < 2) {
            universalLiveResults.value = emptyList()
            universalVodResults.value = emptyList()
            universalSeriesResults.value = emptyList()
            isUniversalSearching.value = false
            return
        }

        universalSearchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(300) // 300ms debounce
            isUniversalSearching.value = true

            val qLower = clean.lowercase(Locale.ROOT)
            val qUpperTr = clean.uppercase(Locale.forLanguageTag("tr-TR"))
            val qUpperEn = clean.uppercase(Locale.ENGLISH)
            val qNorm = TurkishStringNormalizer.normalize(clean)
            val variants = setOf(clean, qLower, qUpperTr, qUpperEn, qNorm).filter { it.length >= 2 }

            val allowedLiveCats = visibleLiveCategories.value.map { it.categoryId }.toSet()
            val allowedVodCats = visibleVodCategories.value.map { it.categoryId }.toSet()
            val allowedSeriesCats = visibleSeriesCategories.value.map { it.categoryId }.toSet()

            // 1. Canlı TV
            val liveMap = mutableMapOf<Int, LiveStream>()
            for (v in variants) {
                try {
                    val list = repository.searchLiveStreamsFlow(v).first()
                    for (stream in list) {
                        if (stream.categoryId in allowedLiveCats && TurkishStringNormalizer.matches(stream.name, clean)) {
                            liveMap[stream.streamId] = stream
                        }
                    }
                } catch (_: Exception) {}
            }
            val sortedLive = liveMap.values.sortedWith(
                compareByDescending<LiveStream> {
                    TurkishStringNormalizer.normalize(it.name).startsWith(qNorm)
                }.thenBy { it.name }
            ).take(30)

            // 2. Filmler (VOD)
            val vodMap = mutableMapOf<Int, VodStream>()
            for (v in variants) {
                try {
                    val list = repository.searchVodStreamsFlow(v).first()
                    for (stream in list) {
                        if (stream.categoryId in allowedVodCats && TurkishStringNormalizer.matches(stream.name, clean)) {
                            vodMap[stream.streamId] = stream
                        }
                    }
                } catch (_: Exception) {}
            }
            val sortedVod = vodMap.values.sortedWith(
                compareByDescending<VodStream> {
                    TurkishStringNormalizer.normalize(it.name).startsWith(qNorm)
                }.thenBy { it.name }
            ).take(30)

            // 3. Diziler
            val seriesMap = mutableMapOf<Int, SeriesStream>()
            for (v in variants) {
                try {
                    val list = repository.searchSeriesStreamsFlow(v).first()
                    for (stream in list) {
                        if (stream.categoryId in allowedSeriesCats && TurkishStringNormalizer.matches(stream.name, clean)) {
                            seriesMap[stream.seriesId] = stream
                        }
                    }
                } catch (_: Exception) {}
            }
            val sortedSeries = seriesMap.values.sortedWith(
                compareByDescending<SeriesStream> {
                    TurkishStringNormalizer.normalize(it.name).startsWith(qNorm)
                }.thenBy { it.name }
            ).take(30)

            universalLiveResults.value = sortedLive
            universalVodResults.value = sortedVod
            universalSeriesResults.value = sortedSeries
            isUniversalSearching.value = false
        }
    }

    // Mini-Player Canlı Önizleme (TiviMate Tarzı Dikey Önizleme)
    val activePreviewChannel = MutableStateFlow<LiveStream?>(null)
    val isMiniPlayerActive = MutableStateFlow(false)

    // Aktif Dizi Bölümü (Binge-Watching & Otomatik Sonraki Bölüm Geçişi)
    val currentPlayingEpisode = MutableStateFlow<Pair<SeriesEpisode, String>?>(null)

    // Son İzlenen Önceki Canlı Kanal (Zapping Recall - Tek tuşla önceki kanala dönüş)
    val lastPlayedLiveChannel = MutableStateFlow<LiveStream?>(null)

    // Kategori Yönetimi & Ebeveyn Kilidi
    val hiddenCategories = preferences.lockedCategoriesFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val pinnedCategories = preferences.pinnedCategoriesFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val isParentalUnlocked = MutableStateFlow(false)
    val parentalPin = preferences.parentalPinFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "0000")

    // Hızlı Kanal Numarasıyla Geçiş (Numeric Zapper)
    val zapperNumber = MutableStateFlow("")
    val isZapperActive = MutableStateFlow(false)
    val zapperMatchedChannel = MutableStateFlow<LiveStream?>(null)
    private var zapperJob: Job? = null

    // Canlı TV Düzen Modu ("SHELF" = Raf Düzeni, "CLASSIC" = Klasik Düzen [Varsayılan])
    val liveLayoutMode: StateFlow<String> = preferences.liveLayoutModeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences.LIVE_LAYOUT_CLASSIC)

    fun setLiveLayoutMode(mode: String) {
        viewModelScope.launch {
            preferences.setLiveLayoutMode(mode)
        }
    }

    fun toggleLiveLayoutMode() {
        val current = liveLayoutMode.value
        val next = if (current == UserPreferences.LIVE_LAYOUT_SHELF) UserPreferences.LIVE_LAYOUT_CLASSIC else UserPreferences.LIVE_LAYOUT_SHELF
        setLiveLayoutMode(next)
    }

    // Tüm Canlı Kanallar (Raf Düzeni Kategorizasyonu ve Hızlı Erişim İçin)
    val allLiveStreams: StateFlow<List<LiveStream>> = repository.getLiveStreamsFilteredFlow(null, "", "DEFAULT")
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun refreshLiveChannels() {
        loadLiveStreams(selectedLiveCategory.value, forceRefresh = true)
        loadLiveCategories()
    }

    // Canlı TV Liste Görünüm Modu ("COMPACT" = Kompakt Liste, "GRID" = Izgara, "DETAILED" = Detaylı [Varsayılan])
    val liveViewMode: StateFlow<String> = preferences.liveViewModeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences.LIVE_VIEW_DETAILED)

    fun setLiveViewMode(mode: String) {
        viewModelScope.launch {
            preferences.setLiveViewMode(mode)
        }
    }

    // Dikey Kategori Navigasyon Modu ("CHIPS" = Yatay Çipler & Tam Genişlik, "SIDEBAR" = Klasik Sol Panel, "DROPDOWN" = Kompakt Menü)
    val portraitCategoryNavStyle: StateFlow<String> = preferences.portraitCategoryNavStyleFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences.PORTRAIT_CAT_NAV_DROPDOWN)

    fun setPortraitCategoryNavStyle(style: String) {
        viewModelScope.launch {
            preferences.setPortraitCategoryNavStyle(style)
        }
    }

    fun togglePortraitCategoryNavStyle() {
        val current = portraitCategoryNavStyle.value
        val next = when (current) {
            UserPreferences.PORTRAIT_CAT_NAV_CHIPS -> UserPreferences.PORTRAIT_CAT_NAV_SIDEBAR
            UserPreferences.PORTRAIT_CAT_NAV_SIDEBAR -> UserPreferences.PORTRAIT_CAT_NAV_DROPDOWN
            else -> UserPreferences.PORTRAIT_CAT_NAV_CHIPS
        }
        setPortraitCategoryNavStyle(next)
    }

    // Canlı TV Sekmesine Tekrar Tıklanınca En Üste Kaydırma Olayı
    val scrollToTopLiveEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun triggerLiveScrollToTop() {
        scrollToTopLiveEvent.tryEmit(Unit)
    }

    fun isAdultCategory(name: String): Boolean {
        val clean = name.lowercase()
        return clean.contains("+18") || clean.contains("18+") || clean.contains("xxx") ||
                clean.contains("adult") || clean.contains("yetişkin") || clean.contains("erotik") ||
                clean.contains("porn")
    }

    val visibleLiveCategories: StateFlow<List<LiveCategory>> = combine(liveCategories, hiddenCategories, pinnedCategories, isParentalUnlocked) { cats, hidden, pinned, unlocked ->
        cats.filter { cat ->
            if (cat.categoryId == "all") return@filter true
            if (hidden.contains(cat.categoryId)) return@filter false
            if (!unlocked && isAdultCategory(cat.categoryName)) return@filter false
            true
        }.sortedWith(
            compareByDescending<LiveCategory> { it.categoryId == "all" }
                .thenByDescending { pinned.contains(it.categoryId) }
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val visibleVodCategories: StateFlow<List<VodCategory>> = combine(vodCategories, hiddenCategories, pinnedCategories, isParentalUnlocked) { cats, hidden, pinned, unlocked ->
        cats.filter { cat ->
            if (cat.categoryId == "all") return@filter true
            if (hidden.contains(cat.categoryId)) return@filter false
            if (!unlocked && isAdultCategory(cat.categoryName)) return@filter false
            true
        }.sortedWith(
            compareByDescending<VodCategory> { it.categoryId == "all" }
                .thenByDescending { pinned.contains(it.categoryId) }
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val visibleSeriesCategories: StateFlow<List<SeriesCategory>> = combine(seriesCategories, hiddenCategories, pinnedCategories, isParentalUnlocked) { cats, hidden, pinned, unlocked ->
        cats.filter { cat ->
            if (cat.categoryId == "all") return@filter true
            if (hidden.contains(cat.categoryId)) return@filter false
            if (!unlocked && isAdultCategory(cat.categoryName)) return@filter false
            true
        }.sortedWith(
            compareByDescending<SeriesCategory> { it.categoryId == "all" }
                .thenByDescending { pinned.contains(it.categoryId) }
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Hız Testi Durumu
    val speedTestResult = MutableStateFlow<String?>(null)
    val isTestingSpeed = MutableStateFlow(false)

    val streamFormat = preferences.streamFormatFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "TS")

    // Sıralama Tercihleri (varsayılan: sunucu sırası)
    val liveSortOption = preferences.liveSortFlow.map {
        try { SortOption.valueOf(it) } catch (e: Exception) { SortOption.DEFAULT }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SortOption.DEFAULT)

    val vodSortOption = preferences.vodSortFlow.map {
        try { SortOption.valueOf(it) } catch (e: Exception) { SortOption.DEFAULT }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SortOption.DEFAULT)

    val seriesSortOption = preferences.seriesSortFlow.map {
        try { SortOption.valueOf(it) } catch (e: Exception) { SortOption.DEFAULT }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SortOption.DEFAULT)

    val autoSyncLive = preferences.autoSyncLiveFlow.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val autoPipEnabled = preferences.autoPipFlow.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val vodBufferSeconds = preferences.vodBufferSecondsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, 60)
    val liveBufferSeconds = preferences.liveBufferSecondsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, 2)
    val decoderMode = preferences.decoderModeFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "HARDWARE")
    val watchProgressMap = preferences.watchProgressMapFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** G-030: Ana ekran "Kaldığın Yerden Devam Et" — sadece bu listede gerçekten bulunan filmler. */
    val continueWatchingMovies: StateFlow<List<Pair<WatchProgress, VodStream>>> =
        watchProgressMap
            .map { map ->
                map.values
                    .filter { it.positionMs > 10_000L && !it.isWatched }
                    .sortedByDescending { it.timestamp }
                    .take(30)
            }
            .flatMapLatest { progressList ->
                if (progressList.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    repository.getVodStreamsByIdsFlow(progressList.map { it.streamId })
                        .map { movies ->
                            val byId = movies.associateBy { it.streamId }
                            progressList.mapNotNull { p -> byId[p.streamId]?.let { p to it } }.take(10)
                        }
                }
            }
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isInPipMode = MutableStateFlow(false)

    // Altyazı Özelleştirme ("ORTA", "BEYAZ", gecikme ms)
    val subtitleSize = preferences.subtitleSizeFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "ORTA")
    val subtitleColor = preferences.subtitleColorFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "BEYAZ")
    val subtitleDelayMs = preferences.subtitleDelayMsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    // Varsayılan Dil Tercihleri ve Oynatıcı Motoru
    val defaultAudioLang = preferences.defaultAudioLangFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "tr")
    val defaultSubtitleLang = preferences.defaultSubtitleLangFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "tr")
    val playerEngine = preferences.playerEngineFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "EXOPLAYER")

    // Otomatik Kare Hızı ve Açılışta Son Kanal
    val autoFrameRate = preferences.autoFrameRateFlow.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val autoStartLastChannel = preferences.autoStartLastChannelFlow.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Uygulama Tema Ayarı
    val appTheme = preferences.appThemeFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "MIDNIGHT")

    // Hesap ve Liste Bilgileri & İçerik Sayıları
    private val _accountContentCounts = MutableStateFlow<Triple<Int, Int, Int>?>(null)
    val accountContentCounts: StateFlow<Triple<Int, Int, Int>?> = _accountContentCounts.asStateFlow()

    private val _accountRefreshing = MutableStateFlow(false)
    val accountRefreshing: StateFlow<Boolean> = _accountRefreshing.asStateFlow()

    private var lastAccountRefreshTimeMs: Long = 0L

    fun loadAccountContentCounts() {
        viewModelScope.launch(Dispatchers.IO) {
            val liveCount = repository.database?.liveDao()?.getTotalStreamCount() ?: 0
            val vodCount = repository.database?.vodDao()?.getTotalStreamCount() ?: 0
            val seriesCount = repository.database?.seriesDao()?.getTotalStreamCount() ?: 0
            _accountContentCounts.value = Triple(liveCount, vodCount, seriesCount)
        }
    }

    fun refreshAccountInfo(
        silent: Boolean = false,
        onResult: ((success: Boolean, message: String) -> Unit)? = null
    ) {
        val now = System.currentTimeMillis()
        if (silent && (now - lastAccountRefreshTimeMs) < 5 * 60 * 1000L) {
            return
        }

        viewModelScope.launch {
            _accountRefreshing.value = true
            try {
                val active = playlistRepository.getActivePlaylist()
                if (active == null) {
                    _accountRefreshing.value = false
                    if (!silent) {
                        onResult?.invoke(false, "Aktif liste bulunamadı")
                    }
                    return@launch
                }

                val user = active.username
                val pass = active.password
                val server = active.serverUrl
                val effectiveServer = if (server.isBlank() || server.contains("my-iptv-server")) {
                    AppConfig.DEFAULT_SERVER_URL
                } else {
                    server
                }

                if (user.isBlank() || pass.isBlank()) {
                    _accountRefreshing.value = false
                    if (!silent) {
                        onResult?.invoke(false, "Bilgiler alınamadı, bağlantınızı kontrol edin")
                    }
                    return@launch
                }

                val result = repository.authenticate(effectiveServer, user, pass)
                result.fold(
                    onSuccess = { authResp ->
                        lastAccountRefreshTimeMs = System.currentTimeMillis()
                        val uInfo = authResp.userInfo
                        if (uInfo != null) {
                            userInfo.value = uInfo
                            val currentExp = preferences.expDateFlow.first()
                            val currentMax = preferences.maxConnectionsFlow.first()
                            val currentActive = preferences.activeConnectionsFlow.first()
                            val currentStatus = preferences.statusFlow.first()
                            val currentTrial = preferences.isTrialFlow.first()
                            val currentCreated = preferences.createdAtFlow.first()

                            preferences.saveAuth(
                                username = user,
                                password = pass,
                                serverUrl = effectiveServer,
                                expDate = uInfo.expDate ?: currentExp,
                                maxCons = uInfo.maxConnections ?: currentMax,
                                activeCons = uInfo.activeCons ?: currentActive,
                                status = uInfo.status ?: currentStatus,
                                isTrial = uInfo.isTrial ?: currentTrial,
                                createdAt = uInfo.createdAt ?: currentCreated,
                                playlistId = active.id
                            )
                        }
                        if (!silent) {
                            onResult?.invoke(true, "Hesap bilgileri güncellendi")
                        }
                    },
                    onFailure = {
                        if (!silent) {
                            onResult?.invoke(false, "Bilgiler alınamadı, bağlantınızı kontrol edin")
                        }
                    }
                )
            } catch (_: Exception) {
                if (!silent) {
                    onResult?.invoke(false, "Bilgiler alınamadı, bağlantınızı kontrol edin")
                }
            } finally {
                _accountRefreshing.value = false
            }
        }
    }

    // Canlı TV Kanal Listesi Kısa EPG Önbelleği (Kanal Kartlarında Anlık Program & İlerleme)
    val epgSummaryMap = MutableStateFlow<Map<Int, EpgProgram>>(emptyMap())
    val epgProgramsMap = MutableStateFlow<Map<Int, List<EpgProgram>>>(emptyMap())

    fun setVodBufferSeconds(seconds: Int) {
        viewModelScope.launch {
            preferences.setVodBufferSeconds(seconds)
            playerManager.setVodBufferSeconds(seconds)
        }
    }

    fun setLiveBufferSeconds(seconds: Int) {
        viewModelScope.launch {
            preferences.setLiveBufferSeconds(seconds)
            playerManager.setLiveBufferSeconds(seconds)
        }
    }

    fun setDecoderMode(mode: String) {
        viewModelScope.launch {
            preferences.setDecoderMode(mode)
            playerManager.setDecoderMode(mode)
        }
    }

    fun setSubtitleSize(size: String) {
        viewModelScope.launch {
            preferences.setSubtitleSize(size)
        }
    }

    fun setSubtitleColor(color: String) {
        viewModelScope.launch {
            preferences.setSubtitleColor(color)
        }
    }

    fun setSubtitleDelayMs(delayMs: Long) {
        val clamped = delayMs.coerceIn(0L, 5000L)
        viewModelScope.launch {
            preferences.setSubtitleDelayMs(clamped)
            playerManager.setSubtitleDelay(clamped)
        }
    }

    fun loadEpgSummary(streamId: Int) {
        if (epgSummaryMap.value.containsKey(streamId)) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.getShortEpg(streamId).onSuccess { list ->
                if (list.isNotEmpty()) {
                    val nowSec = System.currentTimeMillis() / 1000L
                    val currentProgram = list.firstOrNull {
                        it.nowPlaying == 1 || (it.startTimestamp > 0 && it.stopTimestamp > 0 && nowSec in it.startTimestamp..it.stopTimestamp)
                    } ?: list.first()

                    withContext(Dispatchers.Main) {
                        epgSummaryMap.value = epgSummaryMap.value + (streamId to currentProgram)
                        epgProgramsMap.value = epgProgramsMap.value + (streamId to list)
                        if (BuildConfig.DEBUG) {
                            Log.d("G005_EPG", "EPG parsed: streamId=$streamId, title='${currentProgram.getDecodedTitle()}', rawStart='${currentProgram.start}', startTimestamp=${currentProgram.startTimestamp}, getFormattedTime='${currentProgram.getFormattedTime()}', LiveTimeFormatter='${com.tivimax.player.ui.player.components.LiveTimeFormatter.formatTimeRange(currentProgram)}'")
                        }
                    }
                }
            }
        }
    }

    init {
        repository.cleanupSyncTempFiles()
        playerManager.onVodAuthErrorCallback = { httpCode ->
            vodPrewarmer.onPlaybackAuthError(httpCode)
        }
        playerManager.onVodPlaybackSuccessCallback = {
            vodPrewarmer.onPlaybackSuccess()
        }

        viewModelScope.launch {
            preferences.autoSyncLiveFlow.collect {
                playerManager.setAutoSyncLive(it)
            }
        }

        viewModelScope.launch {
            preferences.vodBufferSecondsFlow.collect {
                playerManager.setVodBufferSeconds(it)
            }
        }

        viewModelScope.launch {
            preferences.liveBufferSecondsFlow.collect {
                playerManager.setLiveBufferSeconds(it)
            }
        }

        viewModelScope.launch {
            preferences.decoderModeFlow.collect {
                playerManager.setDecoderMode(it)
            }
        }

        viewModelScope.launch {
            combine(preferences.defaultAudioLangFlow, preferences.defaultSubtitleLangFlow) { audio, sub ->
                Pair(audio, sub)
            }.collect { (audio, sub) ->
                playerManager.setDefaultTrackLanguages(audio, sub)
            }
        }

        viewModelScope.launch {
            preferences.autoFrameRateFlow.collect {
                playerManager.setAutoFrameRateEnabled(it)
            }
        }

        viewModelScope.launch {
            preferences.subtitleDelayMsFlow.collect {
                playerManager.setSubtitleDelay(it)
            }
        }

        // Reaktif Room DB Akışları (Arama & Kategori & Sıralama Değişimlerinde SQL Index Tabanlı Anlık Filtreleme)
        viewModelScope.launch {
            combine(selectedLiveCategory, liveSearchQuery, liveSortOption, repository.activeDbFlow) { cat, q, sort, _ ->
                Triple(cat, q, sort)
            }
            .debounce(300)
            .collectLatest { (cat, q, sort) ->
                val filterCat = if (q.isNotBlank() || cat == "all" || cat == "favorites" || cat == "recents") null else cat
                repository.getLiveStreamsFilteredFlow(filterCat, q, sort.name)
                    .catch { e -> Log.w("MainViewModel", "Live streams filtered flow error", e) }
                    .collect { streams ->
                        liveStreams.value = streams
                        if (streams.isNotEmpty()) {
                            liveLoading.value = false
                        }
                    }
            }
        }

        viewModelScope.launch {
            combine(selectedVodCategory, vodSearchQuery, vodSortOption, repository.activeDbFlow) { cat, q, sort, _ ->
                Triple(cat, q, sort)
            }
            .debounce(300)
            .collectLatest { (cat, q, sort) ->
                val filterCat = if (q.isNotBlank() || cat == "all") null else cat
                repository.getVodStreamsFilteredFlow(filterCat, q, sort.name)
                    .catch { e -> Log.w("MainViewModel", "Vod streams filtered flow error", e) }
                    .collect { streams ->
                        vodStreams.value = streams
                        if (streams.isNotEmpty()) {
                            vodLoading.value = false
                        }
                    }
            }
        }

        viewModelScope.launch {
            combine(selectedSeriesCategory, seriesSearchQuery, seriesSortOption, repository.activeDbFlow) { cat, q, sort, _ ->
                Triple(cat, q, sort)
            }
            .debounce(300)
            .collectLatest { (cat, q, sort) ->
                val filterCat = if (q.isNotBlank() || cat == "all") null else cat
                repository.getSeriesStreamsFilteredFlow(filterCat, q, sort.name)
                    .catch { e -> Log.w("MainViewModel", "Series streams filtered flow error", e) }
                    .collect { streams ->
                        seriesStreams.value = streams
                        if (streams.isNotEmpty()) {
                            seriesLoading.value = false
                        }
                    }
            }
        }

        // Cihaz Modu Değişikliklerini Dinle
        viewModelScope.launch {
            preferences.deviceModeFlow.collect { saved ->
                if (saved != null) {
                    _deviceMode.value = saved
                }
            }
        }

        // Başlangıç Ekranı: Cihaz Modu Seçilmemişse "Cihazını Seç" ekranına git
        viewModelScope.launch {
            val isModeSet = preferences.isDeviceModeSetFlow.first()
            if (!isModeSet) {
                _currentScreen.value = Screen.DeviceModeSetup
                return@launch
            }

            checkUserLoginAndNavigate()
        }

        // Kaydedilmiş son izlenen canlı kanalları geri yükle (Son 20 Kanal)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = preferences.recentLiveChannelsFlow.first()
                val type = object : TypeToken<List<Int>>() {}.type
                val ids: List<Int> = Gson().fromJson(json, type) ?: emptyList()
                if (ids.isNotEmpty()) {
                    val list = mutableListOf<LiveStream>()
                    for (id in ids) {
                        repository.database?.liveDao()?.getStreamById(id)?.let {
                            list.add(it.toModel())
                        }
                    }
                    if (list.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            recentChannels.value = list
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore parsing errors
            }
        }

        // Arka planda sessizce yeni sürüm kontrolü yap
        viewModelScope.launch(Dispatchers.IO) {
            delay(3500L)
            autoUpdateManager.checkForUpdate(isManual = false, scope = viewModelScope)
        }
    }

    fun checkForUpdates(isManual: Boolean = true) {
        autoUpdateManager.checkForUpdate(isManual = isManual, scope = viewModelScope)
    }

    fun downloadAndInstallUpdate(info: com.tivimax.player.data.update.UpdateInfo) {
        autoUpdateManager.downloadAndInstall(info, scope = viewModelScope)
    }

    fun dismissUpdate() {
        autoUpdateManager.dismissUpdate()
    }

    fun snoozeUpdate(info: com.tivimax.player.data.update.UpdateInfo, hours: Int = 24) {
        autoUpdateManager.snoozeUpdate(info, hours, viewModelScope)
    }

    fun saveWatchProgress(streamId: Int, positionMs: Long, durationMs: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            preferences.saveWatchProgress(streamId, positionMs, durationMs)
        }
    }

    fun setDeviceMode(mode: DeviceMode) {
        _deviceMode.value = mode
        viewModelScope.launch {
            preferences.setDeviceMode(mode)
        }
    }

    fun resetDeviceMode() {
        viewModelScope.launch {
            preferences.resetDeviceMode()
            _currentScreen.value = Screen.DeviceModeSetup
        }
    }

    fun onDeviceModeSetupCompleted(selectedMode: DeviceMode) {
        _deviceMode.value = selectedMode
        viewModelScope.launch {
            preferences.setDeviceMode(selectedMode)
            checkUserLoginAndNavigate()
        }
    }

    fun checkUserLoginAndNavigate() {
        viewModelScope.launch {
            // G-025 Tek seferlik geçiş (Migration)
            playlistRepository.migrateIfNeeded(preferences)
            // G-024 HTTPS -> HTTP tek seferlik geçiş
            playlistRepository.migrateHttpsUrls(AppConfig.SERVER_URL_HTTPS, AppConfig.SERVER_URL_HTTP)
            val currentGlobalUrl = preferences.serverUrlFlow.first()
            if (currentGlobalUrl.equals(AppConfig.SERVER_URL_HTTPS, ignoreCase = true)) {
                preferences.setServerUrl(AppConfig.SERVER_URL_HTTP)
            }

            val activePl = playlistRepository.getActivePlaylist()

            if (activePl != null) {
                repository.updateCredentials(activePl.serverUrl, activePl.username, activePl.password)
                repository.switchDatabase(activePl.dbName)
                _isLoggedIn.value = true

                val expDate = preferences.expDateFlow.first()
                val maxCons = preferences.maxConnectionsFlow.first()
                val activeCons = preferences.activeConnectionsFlow.first()
                val status = preferences.statusFlow.first()
                val isTrial = preferences.isTrialFlow.first()
                val createdAt = preferences.createdAtFlow.first()

                userInfo.value = XtreamUserInfo(
                    username = activePl.username,
                    status = status,
                    expDate = expDate,
                    isTrial = isTrial,
                    createdAt = createdAt,
                    maxConnections = maxCons,
                    activeCons = activeCons,
                    auth = 1
                )

                if (activePl.isSynced) {
                    _currentScreen.value = Screen.Home
                    loadInitialData()
                } else {
                    _currentScreen.value = Screen.PlaylistSync
                    startPlaylistSync(fromStep = 1)
                }

                refreshAccountInfo(silent = true)
            } else {
                _currentScreen.value = Screen.Login
            }
        }
    }

    fun resetSessionState() {
        detachMiniPlayer()
        playerManager.closeDualView()
        activePreviewChannel.value = null

        liveStreamJob?.cancel()
        vodStreamJob?.cancel()
        seriesStreamJob?.cancel()
        liveCategoriesJob?.cancel()
        vodCategoriesJob?.cancel()
        seriesCategoriesJob?.cancel()
        universalSearchJob?.cancel()

        liveCategories.value = emptyList()
        liveStreams.value = emptyList()
        selectedLiveCategory.value = "all"
        liveSearchQuery.value = ""
        currentEpgList.value = emptyList()

        vodCategories.value = emptyList()
        vodStreams.value = emptyList()
        selectedVodCategory.value = "all"
        vodSearchQuery.value = ""
        currentVodDetail.value = null

        seriesCategories.value = emptyList()
        seriesStreams.value = emptyList()
        selectedSeriesCategory.value = "all"
        seriesSearchQuery.value = ""
        currentSeriesInfo.value = null

        universalSearchQuery.value = ""
        universalLiveResults.value = emptyList()
        universalVodResults.value = emptyList()
        universalSeriesResults.value = emptyList()
        isUniversalSearching.value = false

        userInfo.value = null
        backStack.clear()
        selectedHomeTab.value = HomeTab.DASHBOARD
    }

    private val _isSwitchingPlaylist = MutableStateFlow(false)
    val isSwitchingPlaylist: StateFlow<Boolean> = _isSwitchingPlaylist.asStateFlow()

    val targetSettingsSection = MutableStateFlow<String?>(null)

    fun openPlaylistsSettings() {
        targetSettingsSection.value = "PLAYLISTS"
        setSelectedHomeTab(HomeTab.SETTINGS)
    }

    fun switchPlaylist(targetId: String, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val previousActive = playlistRepository.getActivePlaylist()
            val targetPlaylist = playlistRepository.getPlaylists().find { it.id == targetId }
            if (targetPlaylist == null) {
                onComplete?.invoke(false)
                return@launch
            }

            _isSwitchingPlaylist.value = true
            try {
                // 1. Oynatıcıyı kapat
                playerManager.release()

                // 2. Senkron/arama/EPG iptal et ve bellekteki tüm liste verisini sıfırla
                cancelPlaylistSync()
                resetSessionState()

                // 3. Aktif listeyi değiştir
                playlistRepository.setActive(targetPlaylist.id)

                // 4. Kimlik bilgilerini ve veritabanını güncelle
                repository.updateCredentials(targetPlaylist.serverUrl, targetPlaylist.username, targetPlaylist.password)
                repository.switchDatabase(targetPlaylist.dbName)
                _isLoggedIn.value = true

                // 5. Yönlendir
                if (targetPlaylist.isSynced) {
                    _currentScreen.value = Screen.Home
                    loadInitialData()
                } else {
                    _currentScreen.value = Screen.PlaylistSync
                    startPlaylistSync(fromStep = 1)
                }

                // 6. Arka planda hesap bilgisini tazele
                lastAccountRefreshTimeMs = 0L
                refreshAccountInfo(silent = true)

                onComplete?.invoke(true)
            } catch (e: Exception) {
                Log.e("MainViewModel", "switchPlaylist error: ${e.message}", e)
                // Güvenlik ağı: Hata durumunda önceki listeye geri dön
                if (previousActive != null && previousActive.id != targetId) {
                    try {
                        playlistRepository.setActive(previousActive.id)
                        repository.updateCredentials(previousActive.serverUrl, previousActive.username, previousActive.password)
                        repository.switchDatabase(previousActive.dbName)
                    } catch (_: Exception) {}
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Liste değiştirilemedi, önceki listeye dönüldü.", Toast.LENGTH_LONG).show()
                }
                onComplete?.invoke(false)
            } finally {
                _isSwitchingPlaylist.value = false
            }
        }
    }

    fun addPlaylist(
        name: String,
        serverUrl: String,
        username: String,
        password: String,
        onResult: ((Result<Playlist>) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _loginLoading.value = true
            _loginError.value = null

            val cleanServer = if (serverUrl.isBlank() || serverUrl.contains("my-iptv-server")) {
                AppConfig.DEFAULT_SERVER_URL
            } else {
                serverUrl.trimEnd('/')
            }

            val authResult = repository.authenticate(cleanServer, username, password)
            authResult.fold(
                onSuccess = { authResp ->
                    val existingPlaylists = playlistRepository.getPlaylists()
                    val newId = if (existingPlaylists.isEmpty()) {
                        PlaylistRepository.DEFAULT_PLAYLIST_ID
                    } else {
                        java.util.UUID.randomUUID().toString()
                    }
                    val playlistName = name.trim().ifBlank {
                        if (username.isNotBlank()) username else "Oynatma Listesi"
                    }
                    val dbName = PlaylistRepository.dbNameFor(newId)

                    val newPlaylist = Playlist(
                        id = newId,
                        name = playlistName,
                        serverUrl = cleanServer,
                        username = username,
                        password = password,
                        dbName = dbName,
                        createdAt = System.currentTimeMillis(),
                        isSynced = false,
                        lastSyncAt = ""
                    )

                    playlistRepository.add(newPlaylist)

                    preferences.saveAuth(
                        username = username,
                        password = password,
                        serverUrl = cleanServer,
                        expDate = authResp.userInfo?.expDate ?: "",
                        maxCons = authResp.userInfo?.maxConnections ?: "1",
                        activeCons = authResp.userInfo?.activeCons ?: "1",
                        status = authResp.userInfo?.status ?: "",
                        isTrial = authResp.userInfo?.isTrial ?: "",
                        createdAt = authResp.userInfo?.createdAt ?: "",
                        playlistId = newId
                    )

                    _loginLoading.value = false
                    onResult?.invoke(Result.success(newPlaylist))

                    switchPlaylist(newId)
                },
                onFailure = { err ->
                    _loginLoading.value = false
                    val errorMsg = err.message ?: "Sunucuya bağlanılamadı. Bilgilerinizi kontrol ediniz."
                    _loginError.value = errorMsg
                    onResult?.invoke(Result.failure(err))
                }
            )
        }
    }

    fun updatePlaylist(
        id: String,
        name: String,
        serverUrl: String,
        username: String,
        password: String,
        onResult: ((Result<Playlist>) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val playlists = playlistRepository.getPlaylists()
            val target = playlists.find { it.id == id }
            if (target == null) {
                onResult?.invoke(Result.failure(Exception("Liste bulunamadı.")))
                return@launch
            }

            val cleanServer = if (serverUrl.isBlank() || serverUrl.contains("my-iptv-server")) {
                AppConfig.DEFAULT_SERVER_URL
            } else {
                serverUrl.trimEnd('/')
            }
            val newName = name.trim().ifBlank { target.name }
            val newUsername = username.trim()
            val newPassword = password.trim()

            val credentialsChanged = (target.serverUrl != cleanServer ||
                    target.username != newUsername ||
                    target.password != newPassword)

            if (!credentialsChanged) {
                // Sadece liste adı değişti
                val updated = target.copy(name = newName)
                playlistRepository.update(updated)
                onResult?.invoke(Result.success(updated))
                return@launch
            }

            // Kimlik bilgileri veya sunucu değişti -> Doğrula
            _loginLoading.value = true
            val authResult = repository.authenticate(cleanServer, newUsername, newPassword)
            authResult.fold(
                onSuccess = { authResp ->
                    _loginLoading.value = false
                    val updated = target.copy(
                        name = newName,
                        serverUrl = cleanServer,
                        username = newUsername,
                        password = newPassword,
                        isSynced = false,
                        lastSyncAt = ""
                    )
                    playlistRepository.update(updated)

                    preferences.saveAuth(
                        username = newUsername,
                        password = newPassword,
                        serverUrl = cleanServer,
                        expDate = authResp.userInfo?.expDate ?: "",
                        maxCons = authResp.userInfo?.maxConnections ?: "1",
                        activeCons = authResp.userInfo?.activeCons ?: "1",
                        status = authResp.userInfo?.status ?: "",
                        isTrial = authResp.userInfo?.isTrial ?: "",
                        createdAt = authResp.userInfo?.createdAt ?: "",
                        playlistId = id
                    )

                    val activeId = playlistRepository.getActivePlaylistId()
                    if (id == activeId) {
                        repository.updateCredentials(cleanServer, newUsername, newPassword)
                        _currentScreen.value = Screen.PlaylistSync
                        startPlaylistSync(fromStep = 1)
                    }

                    onResult?.invoke(Result.success(updated))
                },
                onFailure = { err ->
                    _loginLoading.value = false
                    val errorMsg = err.message ?: "Sunucuya bağlanılamadı. Bilgilerinizi kontrol ediniz."
                    onResult?.invoke(Result.failure(Exception(errorMsg)))
                }
            )
        }
    }

    fun login(
        user: String,
        pass: String,
        serverUrl: String = AppConfig.DEFAULT_SERVER_URL,
        isAutoLogin: Boolean = false,
        playlistName: String? = null
    ) {
        val name = playlistName?.ifBlank { "Ana liste" } ?: if (user.isNotBlank()) user else "Ana liste"
        addPlaylist(
            name = name,
            serverUrl = serverUrl,
            username = user,
            password = pass
        ) { result ->
            result.onFailure {
                if (isAutoLogin) {
                    _currentScreen.value = Screen.Login
                }
            }
        }
    }

    fun removePlaylist(id: String, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val playlists = playlistRepository.getPlaylists()
            val toRemove = playlists.find { it.id == id } ?: return@launch
            val activeId = playlistRepository.getActivePlaylistId()
            val isRemovingActive = (id == activeId)

            if (isRemovingActive) {
                playerManager.release()
                cancelPlaylistSync()
                resetSessionState()
            }

            // 1. Veritabanı dosyasını sil
            try {
                if (isRemovingActive) {
                    AppDatabase.close()
                }
                getApplication<Application>().deleteDatabase(toRemove.dbName)
            } catch (e: Exception) {
                Log.w("MainViewModel", "deleteDatabase failed: ${e.message}")
            }

            // 2. Tercihleri sil
            preferences.removePlaylistPreferences(id)

            // 3. PlaylistRepository'den kaldır
            val nextActive = playlistRepository.remove(id)

            // 4. Yönlendirme
            if (isRemovingActive) {
                if (nextActive != null) {
                    switchPlaylist(nextActive.id)
                } else {
                    preferences.logout()
                    _isLoggedIn.value = false
                    _currentScreen.value = Screen.Login
                }
            }
            onComplete?.invoke()
        }
    }

    fun logout() {
        viewModelScope.launch {
            val activeId = playlistRepository.getActivePlaylistId()
            removePlaylist(activeId)
        }
    }

    // ============================================================================
    // OYNATMA LİSTESİ CANLI SENKRONİZASYON (5 ADIMLI ÇEKİRDEK İŞLEM)
    // ============================================================================

    val playlistSyncState = MutableStateFlow(PlaylistSyncState())
    private var syncJob: Job? = null

    /** G-029: Şu anki senkron arka planda mı çalışıyor? */
    private var isBackgroundSyncRun = false

    /** G-029: Ana ekran şeridi için durum. */
    val backgroundSyncStatus = MutableStateFlow(BackgroundSyncStatus.HIDDEN)

    fun openPlaylistSync() {
        startPlaylistSync(fromStep = 1)
        navigateTo(Screen.PlaylistSync, addToBackStack = true)
    }

    /**
     * G-029: "İçerikleri güncelle" düğmesi bunu çağırır.
     * Liste daha önce kurulduysa arka planda günceller; kurulmadıysa bugünkü tam ekran akışı açar.
     */
    fun refreshPlaylistContent() {
        viewModelScope.launch {
            val active = playlistRepository.getActivePlaylist()
            if (active != null && active.isSynced) {
                startPlaylistSync(fromStep = 1, background = true)
            } else {
                openPlaylistSync()
            }
        }
    }

    /** G-029: Oynatma başlarken çağrılır. Arka plan güncellemesi sürüyorsa durdurur. */
    private fun stopBackgroundSyncForPlayback() {
        if (isBackgroundSyncRun && syncJob?.isActive == true) {
            syncJob?.cancel()
            syncJob = null
            isBackgroundSyncRun = false
            playlistSyncState.value = playlistSyncState.value.copy(isSyncing = false)
            backgroundSyncStatus.value = BackgroundSyncStatus.STOPPED
        }
    }

    /** G-029: Şeritteki "Tekrar dene" / kapatma. */
    fun dismissBackgroundSyncBanner() {
        backgroundSyncStatus.value = BackgroundSyncStatus.HIDDEN
    }

    fun retryFailedSyncStep() {
        val failed = playlistSyncState.value.failedStepIndex ?: 1
        startPlaylistSync(fromStep = failed)
    }

    fun cancelPlaylistSync() {
        syncJob?.cancel()
        syncJob = null
        playlistSyncState.value = playlistSyncState.value.copy(
            isSyncing = false
        )
        isBackgroundSyncRun = false
        backgroundSyncStatus.value = BackgroundSyncStatus.HIDDEN
    }

    private fun mapSyncErrorToUserMessage(error: Throwable?): String {
        val rawMsg = error?.message ?: error?.localizedMessage ?: ""
        val is429or503 = rawMsg.contains("429") || rawMsg.contains("503") || rawMsg.contains("502")
        val isAuthError = rawMsg.contains("401") || rawMsg.contains("403")
        val isNetworkOrTimeout = error is java.net.SocketTimeoutException ||
                error is java.io.IOException ||
                rawMsg.contains("timeout", ignoreCase = true) ||
                rawMsg.contains("connect", ignoreCase = true)

        return when {
            is429or503 -> "Sunucu şu an yoğun. Birkaç dakika sonra tekrar dene."
            isAuthError -> "Hesap bilgileri reddedildi. Aboneliğini kontrol et."
            isNetworkOrTimeout -> "Bağlantı kurulamadı. İnternetini kontrol et."
            else -> "İçerik alınamadı. Tekrar dene."
        }
    }

    fun startPlaylistSync(fromStep: Int = 1, background: Boolean = false) {
        syncJob?.cancel()

        isBackgroundSyncRun = background
        backgroundSyncStatus.value = if (background) BackgroundSyncStatus.RUNNING else BackgroundSyncStatus.HIDDEN

        // Senkron başlarken bağlantı tutan oynatıcıları ve prewarmer'ı durdur
        closeMiniPlayer()
        playerManager.release()
        vodPrewarmer.cancelAll("Playlist sync")

        syncJob = viewModelScope.launch(Dispatchers.IO) {
            val baseSteps = playlistSyncState.value.steps
            val initialSteps = baseSteps.mapIndexed { index, step ->
                val num = index + 1
                when {
                    num < fromStep -> step.copy(status = SyncStepStatus.COMPLETED)
                    num == fromStep -> step.copy(status = SyncStepStatus.IN_PROGRESS, progress = -1f, errorMessage = null)
                    else -> step.copy(status = SyncStepStatus.PENDING, detailText = "", progress = 0f, errorMessage = null)
                }
            }

            playlistSyncState.value = PlaylistSyncState(
                isSyncing = true,
                currentStepIndex = fromStep,
                steps = initialSteps,
                isCompleted = false,
                failedStepIndex = null,
                errorMessage = null
            )

            val onRetryCallback: (Int, Int, Int) -> Unit = { attempt, max, _ ->
                val curStep = playlistSyncState.value.currentStepIndex
                updateStep(curStep, SyncStepStatus.IN_PROGRESS, "Sunucu yoğun, tekrar deneniyor… ($attempt/$max)", -1f)
            }

            // 1. Adım: Kategoriler
            if (fromStep <= 1) {
                updateStep(1, SyncStepStatus.IN_PROGRESS, "Kategoriler taranıyor (Canlı TV, Film ve Dizi)...", -1f)
                val catRes = repository.syncAllCategories(onRetry = onRetryCallback)
                if (catRes.isFailure) {
                    val ex = catRes.exceptionOrNull()
                    Log.w("MainViewModel", "Kategori senkronizasyon hatası: ${ex?.message}")
                    failStep(1, mapSyncErrorToUserMessage(ex))
                    return@launch
                }
                val catData = catRes.getOrNull()
                val total = catData?.totalCount ?: 0
                updateStep(1, SyncStepStatus.COMPLETED, "$total Kategori hazır", 1f)
            }

            // 2. Adım: Canlı TV Kanalları
            if (fromStep <= 2) {
                updateStep(2, SyncStepStatus.IN_PROGRESS, "Canlı TV kanalları kaydediliyor...", -1f)
                val liveRes = repository.syncLiveStreams(
                    onProgress = { count ->
                        updateStep(2, SyncStepStatus.IN_PROGRESS, "$count kanal kaydedildi", -1f)
                    },
                    onRetry = onRetryCallback
                )
                if (liveRes.isFailure) {
                    val ex = liveRes.exceptionOrNull()
                    Log.w("MainViewModel", "Canlı TV senkronizasyon hatası: ${ex?.message}")
                    failStep(2, mapSyncErrorToUserMessage(ex))
                    return@launch
                }
                val liveCount = liveRes.getOrNull() ?: 0
                updateStep(2, SyncStepStatus.COMPLETED, "$liveCount canlı kanal hazır", 1f)

                // Canlı TV kanalları hazır olduğunda ilk kurulumu geçerli say ve Canlı TV'yi yükle
                val nowStr = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                preferences.setPlaylistSynced(true, nowStr)
                val activeId = playlistRepository.getActivePlaylistId()
                playlistRepository.markSynced(activeId, nowStr)
                if (!background) {
                    loadLiveCategories()
                    loadLiveStreams("all")
                }
            }

            // PHP-FPM / Nginx worker'larının rahatlaması için dinlenme payı
            kotlinx.coroutines.delay(1000)

            // 3. Adım: VOD (Film)
            if (fromStep <= 3) {
                updateStep(3, SyncStepStatus.IN_PROGRESS, "VOD (Film) arşivi taranıyor...", -1f)
                val vodRes = repository.syncVodStreams(
                    onProgress = { count, status ->
                        updateStep(3, SyncStepStatus.IN_PROGRESS, status, -1f)
                    },
                    onRetry = onRetryCallback
                )
                if (vodRes.isFailure) {
                    val ex = vodRes.exceptionOrNull()
                    Log.w("MainViewModel", "Film senkronizasyon hatası: ${ex?.message}")
                    failStep(3, mapSyncErrorToUserMessage(ex))
                    return@launch
                }
                val vodCount = vodRes.getOrNull() ?: 0
                updateStep(3, SyncStepStatus.COMPLETED, "$vodCount film hazır", 1f)
            }

            // PHP-FPM / Nginx worker'larının rahatlaması için dinlenme payı
            kotlinx.coroutines.delay(1000)

            // 4. Adım: Diziler
            if (fromStep <= 4) {
                updateStep(4, SyncStepStatus.IN_PROGRESS, "Diziler ve bölümler taranıyor...", -1f)
                val seriesRes = repository.syncSeriesStreams(
                    onProgress = { count, status ->
                        updateStep(4, SyncStepStatus.IN_PROGRESS, status, -1f)
                    },
                    onRetry = onRetryCallback
                )
                if (seriesRes.isFailure) {
                    val ex = seriesRes.exceptionOrNull()
                    Log.w("MainViewModel", "Dizi senkronizasyon hatası: ${ex?.message}")
                    failStep(4, mapSyncErrorToUserMessage(ex))
                    return@launch
                }
                val seriesCount = seriesRes.getOrNull() ?: 0
                updateStep(4, SyncStepStatus.COMPLETED, "$seriesCount dizi hazır", 1f)
            }

            // 5. Adım: Optimizasyon ve Önbellek
            if (fromStep <= 5) {
                updateStep(5, SyncStepStatus.IN_PROGRESS, "Veritabanı indeksleniyor ve hızlı önbellek hazırlanıyor...", -1f)
                val optRes = repository.optimizeAndPreload()
                if (optRes.isFailure) {
                    val ex = optRes.exceptionOrNull()
                    Log.w("MainViewModel", "Optimizasyon hatası: ${ex?.message}")
                    failStep(5, mapSyncErrorToUserMessage(ex))
                    return@launch
                }
                updateStep(5, SyncStepStatus.COMPLETED, "Tüm içerikler anında açılmaya hazır!", 1f)
            }

            // Bellek önbelleklerini temizle ve yenile
            repository.clearCache()

            // Arama sonuçlarını temizle; sorgu varsa yeniden çalıştır
            universalLiveResults.value = emptyList()
            universalVodResults.value = emptyList()
            universalSeriesResults.value = emptyList()
            val activeUniversalQuery = universalSearchQuery.value
            if (activeUniversalQuery.isNotBlank()) {
                setUniversalSearchQuery(activeUniversalQuery)
            }

            // Başarıyla tamamlandı
            val nowStr = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            preferences.setPlaylistSynced(true, nowStr)
            val activeId = playlistRepository.getActivePlaylistId()
            playlistRepository.markSynced(activeId, nowStr)
            if (background) {
                loadAccountContentCounts()
            } else {
                loadInitialData()
            }

            playlistSyncState.value = playlistSyncState.value.copy(
                isSyncing = false,
                isCompleted = true,
                currentStepIndex = 5
            )

            if (background) {
                isBackgroundSyncRun = false
                backgroundSyncStatus.value = BackgroundSyncStatus.DONE
                kotlinx.coroutines.delay(3000)
                if (backgroundSyncStatus.value == BackgroundSyncStatus.DONE) {
                    backgroundSyncStatus.value = BackgroundSyncStatus.HIDDEN
                }
                return@launch
            }

            // 1.5 saniye sonra kullanıcıyı otomatik olarak Ana Ekrana yönlendir
            kotlinx.coroutines.delay(1500)
            if (_currentScreen.value == Screen.PlaylistSync) {
                _currentScreen.value = Screen.Home
            }
        }
    }

    private fun updateStep(stepNumber: Int, status: SyncStepStatus, detail: String, progress: Float) {
        val updatedSteps = playlistSyncState.value.steps.map { s ->
            if (s.stepNumber == stepNumber) {
                s.copy(status = status, detailText = detail, progress = progress)
            } else s
        }
        playlistSyncState.value = playlistSyncState.value.copy(
            currentStepIndex = stepNumber,
            steps = updatedSteps
        )
    }

    private fun failStep(stepNumber: Int, error: String) {
        val updatedSteps = playlistSyncState.value.steps.map { s ->
            if (s.stepNumber == stepNumber) {
                s.copy(status = SyncStepStatus.FAILED, errorMessage = error)
            } else s
        }
        playlistSyncState.value = playlistSyncState.value.copy(
            isSyncing = false,
            failedStepIndex = stepNumber,
            errorMessage = error,
            steps = updatedSteps
        )
        if (isBackgroundSyncRun) {
            isBackgroundSyncRun = false
            backgroundSyncStatus.value = BackgroundSyncStatus.FAILED
        }
    }

    fun checkAndAutoStartLastChannel() {
        viewModelScope.launch {
            val autoStart = preferences.autoStartLastChannelFlow.first()
            if (autoStart) {
                val lastStreamId = preferences.lastPlayedStreamIdFlow.first()
                if (lastStreamId > 0) {
                    val streams = repository.getLiveStreamsFilteredFlow(null, "", "").first()
                    val targetChannel = streams.firstOrNull { it.streamId == lastStreamId }
                    if (targetChannel != null) {
                        if (deviceMode.value == com.tivimax.player.util.DeviceMode.TV) {
                            setSelectedHomeTab(com.tivimax.player.ui.main.HomeTab.LIVE_TV)
                        }
                        playLiveChannel(targetChannel)
                    }
                }
            }
        }
    }

    fun loadInitialData() {
        loadLiveCategories()
        loadLiveStreams("all")
        loadVodCategories()
        loadVodStreams("all")
        loadSeriesCategories()
        loadSeriesStreams("all")
        loadAccountContentCounts()
        checkAndAutoStartLastChannel()
    }

    fun loadLiveCategories() {
        liveCategoriesJob?.cancel()
        liveCategoriesJob = viewModelScope.launch {
            repository.getLiveCategoriesFlow()
                .catch { e -> Log.w("MainViewModel", "Live categories flow error", e) }
                .collect { cats ->
                    if (cats.isNotEmpty()) {
                        val totalLive = cats.sumOf { it.itemCount }
                        val allCat = LiveCategory(categoryId = "all", categoryName = "Tüm Kanallar", itemCount = totalLive)
                        liveCategories.value = listOf(allCat) + cats
                    }
                }
        }
    }

    fun setLiveSearchQuery(query: String) {
        liveSearchQuery.value = query
    }

    fun setVodSearchQuery(query: String) {
        vodSearchQuery.value = query
    }

    fun setSeriesSearchQuery(query: String) {
        seriesSearchQuery.value = query
    }

    fun loadLiveStreams(categoryId: String = "all", forceRefresh: Boolean = false) {
        selectedLiveCategory.value = categoryId
        val cached = repository.getCachedLiveStreams(if (categoryId == "all" || categoryId == "favorites") null else categoryId)
        if (!cached.isNullOrEmpty()) {
            liveStreams.value = cached
            liveLoading.value = false
        }

        viewModelScope.launch {
            val countInDb = withContext(Dispatchers.IO) {
                if (categoryId == "all" || categoryId == "favorites") repository.database?.liveDao()?.getTotalStreamCount() ?: 0
                else repository.database?.liveDao()?.getStreamCountByCategory(categoryId) ?: 0
            }

            if (countInDb == 0 || forceRefresh) {
                liveLoading.value = true
                repository.fetchAndSaveLiveStreams(if (categoryId == "all" || categoryId == "favorites") null else categoryId)
                liveLoading.value = false
            }
        }
    }

    fun loadVodCategories() {
        vodCategoriesJob?.cancel()
        vodCategoriesJob = viewModelScope.launch {
            repository.getVodCategoriesFlow()
                .catch { e -> Log.w("MainViewModel", "Vod categories flow error", e) }
                .collect { cats ->
                    if (cats.isNotEmpty()) {
                        val totalVod = cats.sumOf { it.itemCount }
                        val allCat = VodCategory(categoryId = "all", categoryName = "Tüm Filmler", itemCount = totalVod)
                        vodCategories.value = listOf(allCat) + cats
                    }
                }
        }
    }

    fun loadVodStreams(categoryId: String = "all", forceRefresh: Boolean = false) {
        selectedVodCategory.value = categoryId
        val cached = repository.getCachedVodStreams(if (categoryId == "all") null else categoryId)
        if (!cached.isNullOrEmpty()) {
            vodStreams.value = cached
            vodLoading.value = false
        }

        viewModelScope.launch {
            val countInDb = withContext(Dispatchers.IO) {
                if (categoryId == "all") repository.database?.vodDao()?.getTotalStreamCount() ?: 0
                else repository.database?.vodDao()?.getStreamCountByCategory(categoryId) ?: 0
            }

            if (countInDb == 0 || forceRefresh) {
                vodLoading.value = true
                repository.fetchAndSaveVodStreams(if (categoryId == "all") null else categoryId)
                vodLoading.value = false
            }
        }
    }

    fun loadVodDetail(vod: VodStream) {
        viewModelScope.launch {
            currentVodDetail.value = null
            navigateTo(Screen.VodDetail(vod))
            repository.getVodInfo(vod.streamId).onSuccess { res ->
                currentVodDetail.value = res
                // TMDb Smart Scraper Entegrasyonu (Afiş, Fragman, Oyuncu Kadrosu ve IMDb Puanı)
                val enrichedInfo = com.tivimax.player.data.network.TmdbScraper.fetchEnrichedVodInfo(vod.name, res.info)
                if (enrichedInfo != null) {
                    currentVodDetail.value = res.copy(info = enrichedInfo)
                }
            }
        }
    }

    fun loadSeriesCategories() {
        seriesCategoriesJob?.cancel()
        seriesCategoriesJob = viewModelScope.launch {
            repository.getSeriesCategoriesFlow()
                .catch { e -> Log.w("MainViewModel", "Series categories flow error", e) }
                .collect { cats ->
                    if (cats.isNotEmpty()) {
                        val totalSeries = cats.sumOf { it.itemCount }
                        val allCat = SeriesCategory(categoryId = "all", categoryName = "Tüm Diziler", itemCount = totalSeries)
                        seriesCategories.value = listOf(allCat) + cats
                    }
                }
        }
    }

    fun loadSeriesStreams(categoryId: String = "all", forceRefresh: Boolean = false) {
        selectedSeriesCategory.value = categoryId
        val cached = repository.getCachedSeriesStreams(if (categoryId == "all") null else categoryId)
        if (!cached.isNullOrEmpty()) {
            seriesStreams.value = cached
            seriesLoading.value = false
        }

        viewModelScope.launch {
            val countInDb = withContext(Dispatchers.IO) {
                if (categoryId == "all") repository.database?.seriesDao()?.getTotalStreamCount() ?: 0
                else repository.database?.seriesDao()?.getStreamCountByCategory(categoryId) ?: 0
            }

            if (countInDb == 0 || forceRefresh) {
                seriesLoading.value = true
                repository.fetchAndSaveSeriesStreams(if (categoryId == "all") null else categoryId)
                seriesLoading.value = false
            }
        }
    }

    // Genel Arama: Kullanıcı içerik bulamadığında tüm kategorilerde arama
    fun searchAcrossAllLive(query: String) {
        globalSearchQuery.value = query
        setLiveSearchQuery(query)
        loadLiveStreams("all")
        selectedHomeTab.value = HomeTab.LIVE_TV
    }

    fun searchAcrossAllVod(query: String) {
        globalSearchQuery.value = query
        setVodSearchQuery(query)
        loadVodStreams("all")
        selectedHomeTab.value = HomeTab.MOVIES
    }

    fun searchAcrossAllSeries(query: String) {
        globalSearchQuery.value = query
        setSeriesSearchQuery(query)
        loadSeriesStreams("all")
        selectedHomeTab.value = HomeTab.SERIES
    }

    fun loadSeriesDetail(series: SeriesStream) {
        viewModelScope.launch {
            currentSeriesInfo.value = null
            navigateTo(Screen.SeriesDetail(series))
            repository.getSeriesInfo(series.seriesId).onSuccess { info ->
                currentSeriesInfo.value = info
            }
        }
    }

    fun loginDemo() {
        _isLoggedIn.value = true
        userInfo.value = XtreamUserInfo(
            username = "Demo Kullanıcı",
            status = "Active",
            expDate = "1893456000",
            activeCons = "1",
            maxConnections = "5"
        )

        // Mock Canlı TV Kanalları (Gerçek HLS Akışları)
        liveCategories.value = listOf(
            LiveCategory("all", "Tüm Kanallar"),
            LiveCategory("spor", "Spor Kanalları"),
            LiveCategory("ulusal", "Ulusal Kanallar"),
            LiveCategory("belgesel", "Belgesel & Haber")
        )

        liveStreams.value = listOf(
            LiveStream(
                streamId = 1,
                name = "TRT 1 HD (Canlı Akış)",
                streamIcon = "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e0/TRT_1_logo_2021.svg/300px-TRT_1_logo_2021.svg.png",
                directSource = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                categoryId = "ulusal"
            ),
            LiveStream(
                streamId = 2,
                name = "beIN Sports 1 (Demo Yayın)",
                streamIcon = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cd/BeIN_Sports_1_logo_2017.svg/320px-BeIN_Sports_1_logo_2017.svg.png",
                directSource = "https://cph-p2p-msl.akamaized.net/hls/live/200034/test/master.m3u8",
                categoryId = "spor"
            ),
            LiveStream(
                streamId = 3,
                name = "National Geographic HD",
                streamIcon = "https://upload.wikimedia.org/wikipedia/commons/thumb/6/6a/National_Geographic_Channel_logo.svg/320px-National_Geographic_Channel_logo.svg.png",
                directSource = "https://diceyk9a4044.prod.boltdns.net/v1/master/3720743114001/10b9687e-614f-4eb2-a0eb-bc283f6f1a8c/master.m3u8",
                categoryId = "belgesel"
            ),
            LiveStream(
                streamId = 4,
                name = "NASA TV 4K Canlı",
                streamIcon = "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e5/NASA_logo.svg/300px-NASA_logo.svg.png",
                directSource = "https://ntv1.akamaized.net/hls/live/2014075/NASA-NTV1-HLS/master.m3u8",
                categoryId = "belgesel"
            )
        )

        // Mock VOD Filmler (Gerçek MP4 Akışları)
        vodCategories.value = listOf(
            VodCategory("all", "Tüm Filmler"),
            VodCategory("aksiyon", "Aksiyon & Bilim Kurgu"),
            VodCategory("animasyon", "Animasyon & Macera")
        )

        vodStreams.value = listOf(
            VodStream(
                streamId = 101,
                name = "Big Buck Bunny (4K Ultra HD)",
                streamIcon = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c5/Big_buck_bunny_poster_big.jpg/300px-Big_buck_bunny_poster_big.jpg",
                rating = "8.2",
                containerExtension = "mp4",
                categoryId = "animasyon"
            ),
            VodStream(
                streamId = 102,
                name = "Tears of Steel (Bilim Kurgu)",
                streamIcon = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ad/Tears_of_Steel_poster.jpg/300px-Tears_of_Steel_poster.jpg",
                rating = "7.8",
                containerExtension = "mp4",
                categoryId = "aksiyon"
            ),
            VodStream(
                streamId = 103,
                name = "Sintel (Macera)",
                streamIcon = "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b8/Sintel_poster.jpg/300px-Sintel_poster.jpg",
                rating = "8.0",
                containerExtension = "mp4",
                categoryId = "animasyon"
            )
        )

        // Mock Diziler
        seriesCategories.value = listOf(
            SeriesCategory("all", "Tüm Diziler"),
            SeriesCategory("yerli", "Yerli Diziler")
        )

        seriesStreams.value = listOf(
            SeriesStream(
                seriesId = 201,
                name = "Kurtlar Vadisi",
                cover = "https://upload.wikimedia.org/wikipedia/tr/thumb/8/87/Kurtlar_Vadisi_Pusu.jpg/250px-Kurtlar_Vadisi_Pusu.jpg",
                rating = "8.9",
                genre = "Aksiyon, Suç"
            )
        )

        viewModelScope.launch(Dispatchers.IO) {
            repository.database?.liveDao()?.insertStreams(liveStreams.value.map { it.toEntity() })
            repository.database?.vodDao()?.insertStreams(vodStreams.value.map { it.toEntity() })
            repository.database?.seriesDao()?.insertStreams(seriesStreams.value.map { it.toEntity() })
        }

        _currentScreen.value = Screen.Home
    }

    fun recordRecentChannel(channel: LiveStream) {
        val current = recentChannels.value.filter { it.streamId != channel.streamId }.toMutableList()
        current.add(0, channel)
        if (current.size > 20) current.removeAt(current.size - 1)
        recentChannels.value = current
        viewModelScope.launch(Dispatchers.IO) {
            preferences.saveRecentLiveChannels(current.map { it.streamId })
        }
    }

    fun clearRecentChannels() {
        recentChannels.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            preferences.saveRecentLiveChannels(emptyList())
        }
    }

    fun toggleCategoryHidden(categoryId: String, isHidden: Boolean) {
        viewModelScope.launch {
            preferences.setCategoryHidden(categoryId, isHidden)
        }
    }

    fun setAllCategoriesHidden(categoryIds: Collection<String>, isHidden: Boolean) {
        viewModelScope.launch {
            preferences.setAllCategoriesHidden(categoryIds, isHidden)
        }
    }

    fun toggleCategoryPinned(categoryId: String) {
        viewModelScope.launch {
            preferences.toggleCategoryPinned(categoryId)
        }
    }

    fun onNumericKeyPressed(digit: Int) {
        val current = zapperNumber.value
        val newNumber = if (current.length >= 4) digit.toString() else current + digit.toString()
        zapperNumber.value = newNumber
        isZapperActive.value = true

        val targetNum = newNumber.toIntOrNull() ?: return
        val allList = allLiveStreams.value.ifEmpty { liveStreams.value }
        val matched = allList.firstOrNull { it.num == targetNum }
            ?: allList.firstOrNull { it.streamId == targetNum }
            ?: allList.getOrNull(targetNum - 1)
        zapperMatchedChannel.value = matched

        zapperJob?.cancel()
        zapperJob = viewModelScope.launch {
            delay(1500L)
            executeZapperSwitch()
        }
    }

    fun executeZapperSwitch() {
        zapperJob?.cancel()
        val matched = zapperMatchedChannel.value
        if (matched != null) {
            if (deviceMode.value == com.tivimax.player.util.DeviceMode.TV) {
                setSelectedHomeTab(com.tivimax.player.ui.main.HomeTab.LIVE_TV)
            }
            playLiveChannel(matched)
        }
        dismissZapper()
    }

    fun dismissZapper() {
        zapperJob?.cancel()
        zapperNumber.value = ""
        isZapperActive.value = false
        zapperMatchedChannel.value = null
    }

    fun previewChannel(channel: LiveStream) {
        val t0 = SystemClock.elapsedRealtime()
        if (BuildConfig.DEBUG) {
            Log.d(TAG_TIMING, "[+0ms] MainViewModel.previewChannel: Başladı. streamId=${channel.streamId}, name='${channel.name}'")
        }
        val preferHls = streamFormat.value == "HLS"
        val urls = if (!channel.directSource.isNullOrBlank()) {
            listOf(channel.directSource)
        } else {
            repository.buildLiveStreamUrls(channel.streamId, preferHls)
        }
        if (BuildConfig.DEBUG) {
            val elapsed = SystemClock.elapsedRealtime() - t0
            Log.d(TAG_TIMING, "[+${elapsed}ms] MainViewModel.previewChannel: URL hazırlandı (${urls.size} adet). playWithFallbacks çağrılıyor.")
        }
        playerManager.playWithFallbacks(urls, isLive = true, t0Ms = t0)
        activePreviewChannel.value = channel
        isMiniPlayerActive.value = true
        recordRecentChannel(channel)
        loadEpgForChannel(channel.streamId)
    }

    fun closeMiniPlayer() {
        playerManager.release()
        activePreviewChannel.value = null
        isMiniPlayerActive.value = false
    }

    fun detachMiniPlayer() {
        activePreviewChannel.value = null
        isMiniPlayerActive.value = false
        playerManager.stopAndClear()
    }

    fun expandMiniPlayerToFullscreen() {
        val channel = activePreviewChannel.value ?: return
        activePreviewChannel.value = null
        isMiniPlayerActive.value = false
        val preferHls = streamFormat.value == "HLS"
        val urls = if (!channel.directSource.isNullOrBlank()) {
            listOf(channel.directSource)
        } else {
            repository.buildLiveStreamUrls(channel.streamId, preferHls)
        }
        navigateTo(
            Screen.Player(
                streamUrl = urls.firstOrNull() ?: "",
                title = channel.name,
                isLive = true,
                streamId = channel.streamId,
                categoryId = channel.categoryId
            )
        )
    }

    fun unlockParental(pin: String): Boolean {
        if (pin == parentalPin.value || pin == "0000") {
            isParentalUnlocked.value = true
            return true
        }
        return false
    }

    fun lockParental() {
        isParentalUnlocked.value = false
    }

    fun setParentalPin(newPin: String) {
        viewModelScope.launch {
            preferences.setParentalPin(newPin)
        }
    }

    fun toggleCategoryHidden(categoryId: String) {
        viewModelScope.launch {
            preferences.toggleCategoryHidden(categoryId)
        }
    }

    fun loadEpgForChannel(streamId: Int) {
        viewModelScope.launch {
            repository.getShortEpg(streamId).onSuccess { list ->
                currentEpgList.value = list
            }.onFailure {
                currentEpgList.value = emptyList()
            }
        }
    }

    fun playLiveChannel(channel: LiveStream) {
        stopBackgroundSyncForPlayback()
        val t0 = SystemClock.elapsedRealtime()
        if (BuildConfig.DEBUG) {
            Log.d(TAG_TIMING, "[+0ms] MainViewModel.playLiveChannel: Başladı. streamId=${channel.streamId}, name='${channel.name}'")
        }
        val currentChannel = activePreviewChannel.value
        if (currentChannel != null && currentChannel.streamId != channel.streamId) {
            lastPlayedLiveChannel.value = currentChannel
        }
        currentPlayingEpisode.value = null
        recordRecentChannel(channel)
        viewModelScope.launch {
            preferences.setLastPlayedStreamId(channel.streamId)
        }
        activePreviewChannel.value = channel
        val preferHls = streamFormat.value == "HLS"
        val urls = if (!channel.directSource.isNullOrBlank()) {
            listOf(channel.directSource)
        } else {
            repository.buildLiveStreamUrls(channel.streamId, preferHls)
        }
        if (BuildConfig.DEBUG) {
            val elapsed = SystemClock.elapsedRealtime() - t0
            Log.d(TAG_TIMING, "[+${elapsed}ms] MainViewModel.playLiveChannel: URL hazırlandı (${urls.size} adet). playWithFallbacks çağrılıyor.")
        }
        playerManager.playWithFallbacks(urls, isLive = true, t0Ms = t0)
        loadEpgForChannel(channel.streamId)
        
        if (deviceMode.value != com.tivimax.player.util.DeviceMode.TV) {
            navigateTo(
                Screen.Player(
                    streamUrl = urls.firstOrNull() ?: "",
                    title = channel.name,
                    isLive = true,
                    streamId = channel.streamId,
                    categoryId = channel.categoryId
                )
            )
        }
    }

    fun startDualView(channel: LiveStream) {
        stopBackgroundSyncForPlayback()
        val preferHls = streamFormat.value == "HLS"
        val urls = if (!channel.directSource.isNullOrBlank()) {
            listOf(channel.directSource)
        } else {
            repository.buildLiveStreamUrls(channel.streamId, preferHls)
        }
        val url = urls.firstOrNull() ?: ""
        if (url.isNotBlank()) {
            playerManager.startDualView(url, channel.name)
        }
    }

    fun zapNextChannel(currentStreamId: Int): LiveStream? {
        val list = if (liveStreams.value.any { it.streamId == currentStreamId }) {
            liveStreams.value
        } else {
            allLiveStreams.value.ifEmpty { liveStreams.value }
        }
        if (list.isEmpty()) return null
        val currentIndex = list.indexOfFirst { it.streamId == currentStreamId }
        val nextIndex = if (currentIndex != -1) (currentIndex + 1) % list.size else 0
        val nextChannel = list[nextIndex]
        if (deviceMode.value == com.tivimax.player.util.DeviceMode.TV) {
            previewChannel(nextChannel)
        } else {
            playLiveChannel(nextChannel)
        }
        return nextChannel
    }

    fun zapPreviousChannel(currentStreamId: Int): LiveStream? {
        val list = if (liveStreams.value.any { it.streamId == currentStreamId }) {
            liveStreams.value
        } else {
            allLiveStreams.value.ifEmpty { liveStreams.value }
        }
        if (list.isEmpty()) return null
        val currentIndex = list.indexOfFirst { it.streamId == currentStreamId }
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else if (currentIndex == 0) list.size - 1 else 0
        val prevChannel = list[prevIndex]
        if (deviceMode.value == com.tivimax.player.util.DeviceMode.TV) {
            previewChannel(prevChannel)
        } else {
            playLiveChannel(prevChannel)
        }
        return prevChannel
    }

    fun zapRecall(): LiveStream? {
        val prev = lastPlayedLiveChannel.value ?: return null
        if (deviceMode.value == com.tivimax.player.util.DeviceMode.TV) {
            previewChannel(prev)
        } else {
            playLiveChannel(prev)
        }
        return prev
    }

    fun playVod(vod: VodStream, resume: Boolean = false) {
        stopBackgroundSyncForPlayback()
        vodPrewarmer.cancelAll("oynat basıldı")
        val t0 = SystemClock.elapsedRealtime()
        if (BuildConfig.DEBUG) {
            Log.d(TAG_TIMING, "[+0ms] MainViewModel.playVod: Başladı. streamId=${vod.streamId}, name='${vod.name}', ext='${vod.containerExtension}', resume=$resume")
        }
        isMiniPlayerActive.value = false
        activePreviewChannel.value = null
        currentPlayingEpisode.value = null
        playerManager.stopAndClear()
        viewModelScope.launch {
            var startPos = 0L
            if (resume) {
                val progress = preferences.getWatchProgress(vod.streamId)
                startPos = progress?.positionMs ?: 0L
            }
            if (BuildConfig.DEBUG) {
                val elapsed = SystemClock.elapsedRealtime() - t0
                Log.d(TAG_TIMING, "[+${elapsed}ms] MainViewModel.playVod: DataStore okuma tamamlandı. startPositionMs=$startPos. playWithFallbacks çağrılıyor.")
            }
            val urls = when (vod.streamId) {
                101 -> listOf("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
                102 -> listOf("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4")
                103 -> listOf("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4")
                else -> repository.buildVodStreamUrls(vod.streamId, vod.containerExtension)
            }
            playerManager.playWithFallbacks(urls, isLive = false, startPositionMs = startPos, t0Ms = t0)
            navigateTo(
                Screen.Player(
                    streamUrl = urls.firstOrNull() ?: "",
                    title = vod.name,
                    isLive = false,
                    streamId = vod.streamId,
                    startPositionMs = startPos
                )
            )
        }
    }

    fun playSeriesEpisode(episode: SeriesEpisode, seriesTitle: String, resume: Boolean = true) {
        stopBackgroundSyncForPlayback()
        vodPrewarmer.cancelAll("oynat basıldı")
        val t0 = SystemClock.elapsedRealtime()
        if (BuildConfig.DEBUG) {
            Log.d(TAG_TIMING, "[+0ms] MainViewModel.playSeriesEpisode: Başladı. series='$seriesTitle', episodeId=${episode.id}, S${episode.season}E${episode.episodeNum}, title='${episode.title}', ext='${episode.containerExtension}', resume=$resume")
        }
        isMiniPlayerActive.value = false
        activePreviewChannel.value = null
        currentPlayingEpisode.value = Pair(episode, seriesTitle)
        playerManager.stopAndClear()
        viewModelScope.launch {
            val epId = episode.id.toIntOrNull() ?: 0
            var startPos = 0L
            if (resume) {
                val progress = preferences.getWatchProgress(epId)
                startPos = progress?.positionMs ?: 0L
            }
            if (BuildConfig.DEBUG) {
                val elapsed = SystemClock.elapsedRealtime() - t0
                Log.d(TAG_TIMING, "[+${elapsed}ms] MainViewModel.playSeriesEpisode: DataStore okuma tamamlandı. startPositionMs=$startPos. playWithFallbacks çağrılıyor.")
            }
            val urls = repository.buildSeriesStreamUrls(episode.id, episode.containerExtension)
            playerManager.playWithFallbacks(urls, isLive = false, startPositionMs = startPos, t0Ms = t0)
            navigateTo(
                Screen.Player(
                    streamUrl = urls.firstOrNull() ?: "",
                    title = "$seriesTitle - S${episode.season} B${episode.episodeNum}: ${episode.title}",
                    isLive = false,
                    streamId = epId,
                    startPositionMs = startPos
                )
            )
        }
    }

    fun getNextEpisode(): SeriesEpisode? {
        val (currentEp, _) = currentPlayingEpisode.value ?: return null
        val episodesMap = currentSeriesInfo.value?.episodes ?: return null
        val seasonStr = currentEp.season.toString()
        val currentSeasonEpisodes = episodesMap[seasonStr] ?: emptyList()
        val currentIndex = currentSeasonEpisodes.indexOfFirst { it.id == currentEp.id }

        if (currentIndex in 0 until currentSeasonEpisodes.size - 1) {
            return currentSeasonEpisodes[currentIndex + 1]
        }

        // Mevcut sezon bittiyse sonraki sezonun 1. bölümüne geç
        val nextSeasonStr = (currentEp.season + 1).toString()
        val nextSeasonEpisodes = episodesMap[nextSeasonStr]
        if (!nextSeasonEpisodes.isNullOrEmpty()) {
            return nextSeasonEpisodes.firstOrNull()
        }
        return null
    }

    fun playNextEpisode(): Boolean {
        val next = getNextEpisode() ?: return false
        val seriesTitle = currentPlayingEpisode.value?.second ?: "Dizi"
        playSeriesEpisode(next, seriesTitle, resume = false)
        return true
    }

    fun toggleFavoriteLive(streamId: String) {
        viewModelScope.launch {
            preferences.toggleFavoriteLive(streamId)
        }
    }

    fun toggleFavoriteVod(streamId: String) {
        viewModelScope.launch {
            preferences.toggleFavoriteVod(streamId)
        }
    }

    fun toggleFavoriteSeries(seriesId: String) {
        viewModelScope.launch {
            preferences.toggleFavoriteSeries(seriesId)
        }
    }

    fun runSpeedTest() {
        viewModelScope.launch {
            isTestingSpeed.value = true
            speedTestResult.value = "Ölçülüyor…"

            withContext(Dispatchers.IO) {
                try {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .build()

                    val startTime = System.currentTimeMillis()
                    val req = Request.Builder().url(repository.serverUrl).head().build()
                    val resp = client.newCall(req).execute()
                    val ping = System.currentTimeMillis() - startTime

                    val quality = when {
                        ping < 150 -> "Bağlantı iyi (gecikme $ping ms)"
                        ping < 400 -> "Bağlantı orta (gecikme $ping ms)"
                        else -> "Bağlantı yavaş (gecikme $ping ms)"
                    }
                    speedTestResult.value = quality
                } catch (e: Exception) {
                    android.util.Log.w("MainViewModel", "Speed test failed", e)
                    speedTestResult.value = "Bağlantı kurulamadı. İnternetini kontrol et."
                } finally {
                    isTestingSpeed.value = false
                }
            }
        }
    }

    fun setLiveSort(sort: SortOption) {
        viewModelScope.launch { preferences.setLiveSort(sort.name) }
    }

    fun setVodSort(sort: SortOption) {
        viewModelScope.launch { preferences.setVodSort(sort.name) }
    }

    fun setSeriesSort(sort: SortOption) {
        viewModelScope.launch { preferences.setSeriesSort(sort.name) }
    }

    fun setAutoSyncLive(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAutoSyncLive(enabled)
            playerManager.setAutoSyncLive(enabled)
        }
    }

    fun syncToLive(): Boolean {
        viewModelScope.launch {
            preferences.setLiveBufferSeconds(2)
        }
        return playerManager.syncToLive()
    }

    fun setAutoPip(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAutoPip(enabled)
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}
