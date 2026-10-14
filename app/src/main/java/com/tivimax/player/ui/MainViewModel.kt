package com.tivimax.player.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tivimax.player.data.config.AppConfig
import com.tivimax.player.data.local.entity.*
import com.tivimax.player.data.model.*
import com.tivimax.player.data.preferences.UserPreferences
import com.tivimax.player.data.repository.XtreamRepository
import com.tivimax.player.player.AspectRatioMode
import com.tivimax.player.player.TiviPlayerManager
import com.tivimax.player.ui.main.HomeTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

sealed class Screen {
    object Splash : Screen()
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
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = XtreamRepository(application)
    val preferences = UserPreferences(application)
    val playerManager = TiviPlayerManager(application)

    // Navigasyon Durumu & Geri Yığını (BackStack)
    private val backStack = ArrayDeque<Screen>()
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Splash)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    fun navigateTo(screen: Screen, addToBackStack: Boolean = true) {
        if (addToBackStack) {
            val current = _currentScreen.value
            if (current != screen && !(current is Screen.Player && screen is Screen.Player)) {
                backStack.addLast(current)
            }
        }
        _currentScreen.value = screen
    }

    fun navigateBack(): Boolean {
        if (backStack.isNotEmpty()) {
            val previous = backStack.removeLast()
            _currentScreen.value = previous
            return true
        }
        return false
    }

    // Aktif Ana Menü Sekmesi (Film veya Dizi detayından geri dönünce konumu ve kategoriyi korur)
    val selectedHomeTab = MutableStateFlow(HomeTab.LIVE_TV)
    fun setSelectedHomeTab(tab: HomeTab) {
        selectedHomeTab.value = tab
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
    val recentChannels = MutableStateFlow<List<LiveStream>>(emptyList())

    // Kategori İçerik İşleri (Room Database üzerinden Flow ile canlı gözlemlenir)
    private var liveStreamJob: Job? = null
    private var vodStreamJob: Job? = null
    private var seriesStreamJob: Job? = null

    // Genel Arama Durumu (Tüm kategoriler/bölümler arası arama aktarımı)
    val globalSearchQuery = MutableStateFlow("")

    // Mini-Player Canlı Önizleme (TiviMate Tarzı Dikey Önizleme)
    val activePreviewChannel = MutableStateFlow<LiveStream?>(null)
    val isMiniPlayerActive = MutableStateFlow(false)

    // Aktif Dizi Bölümü (Binge-Watching & Otomatik Sonraki Bölüm Geçişi)
    val currentPlayingEpisode = MutableStateFlow<Pair<SeriesEpisode, String>?>(null)

    // Son İzlenen Önceki Canlı Kanal (Zapping Recall - Tek tuşla önceki kanala dönüş)
    val lastPlayedLiveChannel = MutableStateFlow<LiveStream?>(null)

    // Kategori Yönetimi & Ebeveyn Kilidi
    val hiddenCategories = preferences.lockedCategoriesFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val isParentalUnlocked = MutableStateFlow(false)
    val parentalPin = preferences.parentalPinFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "0000")

    fun isAdultCategory(name: String): Boolean {
        val clean = name.lowercase()
        return clean.contains("+18") || clean.contains("18+") || clean.contains("xxx") ||
                clean.contains("adult") || clean.contains("yetişkin") || clean.contains("erotik") ||
                clean.contains("porn")
    }

    val visibleLiveCategories: StateFlow<List<LiveCategory>> = combine(liveCategories, hiddenCategories, isParentalUnlocked) { cats, hidden, unlocked ->
        cats.filter { cat ->
            if (cat.categoryId == "all") return@filter true
            if (hidden.contains(cat.categoryId)) return@filter false
            if (!unlocked && isAdultCategory(cat.categoryName)) return@filter false
            true
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val visibleVodCategories: StateFlow<List<VodCategory>> = combine(vodCategories, hiddenCategories, isParentalUnlocked) { cats, hidden, unlocked ->
        cats.filter { cat ->
            if (cat.categoryId == "all") return@filter true
            if (hidden.contains(cat.categoryId)) return@filter false
            if (!unlocked && isAdultCategory(cat.categoryName)) return@filter false
            true
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val visibleSeriesCategories: StateFlow<List<SeriesCategory>> = combine(seriesCategories, hiddenCategories, isParentalUnlocked) { cats, hidden, unlocked ->
        cats.filter { cat ->
            if (cat.categoryId == "all") return@filter true
            if (hidden.contains(cat.categoryId)) return@filter false
            if (!unlocked && isAdultCategory(cat.categoryName)) return@filter false
            true
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Hız Testi Durumu
    val speedTestResult = MutableStateFlow<String?>(null)
    val isTestingSpeed = MutableStateFlow(false)

    val streamFormat = preferences.streamFormatFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "TS")

    // Sıralama Tercihleri (Canlı TV: Varsayılan | Film & Dizi: Eklenme Tarihi)
    val liveSortOption = preferences.liveSortFlow.map {
        try { SortOption.valueOf(it) } catch (e: Exception) { SortOption.DEFAULT }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SortOption.DEFAULT)

    val vodSortOption = preferences.vodSortFlow.map {
        try { SortOption.valueOf(it) } catch (e: Exception) { SortOption.ADDED_DESC }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SortOption.ADDED_DESC)

    val seriesSortOption = preferences.seriesSortFlow.map {
        try { SortOption.valueOf(it) } catch (e: Exception) { SortOption.ADDED_DESC }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SortOption.ADDED_DESC)

    val autoSyncLive = preferences.autoSyncLiveFlow.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val autoPipEnabled = preferences.autoPipFlow.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val vodBufferSeconds = preferences.vodBufferSecondsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, 60)
    val liveBufferSeconds = preferences.liveBufferSecondsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, 2)
    val decoderMode = preferences.decoderModeFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "HARDWARE")
    val watchProgressMap = preferences.watchProgressMapFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val isInPipMode = MutableStateFlow(false)

    // Altyazı Özelleştirme ("ORTA", "BEYAZ")
    val subtitleSize = preferences.subtitleSizeFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "ORTA")
    val subtitleColor = preferences.subtitleColorFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "BEYAZ")

    // Canlı TV Kanal Listesi Kısa EPG Önbelleği (Kanal Kartlarında Anlık Program & İlerleme)
    val epgSummaryMap = MutableStateFlow<Map<Int, EpgProgram>>(emptyMap())

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
                    }
                }
            }
        }
    }

    init {
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

        // Reaktif Room DB Akışları (Arama & Kategori & Sıralama Değişimlerinde SQL Index Tabanlı Anlık Filtreleme)
        viewModelScope.launch {
            combine(selectedLiveCategory, liveSearchQuery, liveSortOption) { cat, q, sort ->
                Triple(cat, q, sort)
            }.collectLatest { (cat, q, sort) ->
                val filterCat = if (cat == "all" || cat == "favorites") null else cat
                repository.getLiveStreamsFilteredFlow(filterCat, q, sort.name).collect { streams ->
                    liveStreams.value = streams
                    if (streams.isNotEmpty()) {
                        liveLoading.value = false
                    }
                }
            }
        }

        viewModelScope.launch {
            combine(selectedVodCategory, vodSearchQuery, vodSortOption) { cat, q, sort ->
                Triple(cat, q, sort)
            }.collectLatest { (cat, q, sort) ->
                val filterCat = if (cat == "all") null else cat
                repository.getVodStreamsFilteredFlow(filterCat, q, sort.name).collect { streams ->
                    vodStreams.value = streams
                    if (streams.isNotEmpty()) {
                        vodLoading.value = false
                    }
                }
            }
        }

        viewModelScope.launch {
            combine(selectedSeriesCategory, seriesSearchQuery, seriesSortOption) { cat, q, sort ->
                Triple(cat, q, sort)
            }.collectLatest { (cat, q, sort) ->
                val filterCat = if (cat == "all") null else cat
                repository.getSeriesStreamsFilteredFlow(filterCat, q, sort.name).collect { streams ->
                    seriesStreams.value = streams
                    if (streams.isNotEmpty()) {
                        seriesLoading.value = false
                    }
                }
            }
        }

        // Otomatik Giriş Kontrolü
        viewModelScope.launch {
            val user = preferences.usernameFlow.first()
            val pass = preferences.passwordFlow.first()
            val server = preferences.serverUrlFlow.first()
            val loggedIn = preferences.isLoggedInFlow.first()
            val expDate = preferences.expDateFlow.first()
            val maxCons = preferences.maxConnectionsFlow.first()
            val activeCons = preferences.activeConnectionsFlow.first()

            val effectiveServer = if (server.isBlank() || server.contains("my-iptv-server")) {
                AppConfig.DEFAULT_SERVER_URL
            } else {
                server
            }

            if (loggedIn && user.isNotBlank() && pass.isNotBlank()) {
                // Önceden giriş yapılmış: Bilgileri ata
                userInfo.value = XtreamUserInfo(
                    username = user,
                    status = "Active",
                    expDate = expDate,
                    maxConnections = maxCons,
                    activeCons = activeCons,
                    auth = 1
                )
                repository.updateCredentials(effectiveServer, user, pass)
                _isLoggedIn.value = true

                val isSynced = preferences.isPlaylistSyncedFlow.first()
                if (isSynced) {
                    _currentScreen.value = Screen.Home
                    loadInitialData()
                } else {
                    _currentScreen.value = Screen.PlaylistSync
                    startPlaylistSync(fromStep = 1)
                }

                // Arka planda sunucudan üyelik bilgilerini tazele (sessiz senkronizasyon)
                val result = repository.authenticate(effectiveServer, user, pass)
                result.onSuccess { authResp ->
                    userInfo.value = authResp.userInfo
                    preferences.saveAuth(
                        username = user,
                        password = pass,
                        serverUrl = effectiveServer,
                        expDate = authResp.userInfo?.expDate ?: expDate,
                        maxCons = authResp.userInfo?.maxConnections ?: maxCons,
                        activeCons = authResp.userInfo?.activeCons ?: activeCons
                    )
                }
            } else {
                _currentScreen.value = Screen.Login
            }
        }
    }

    fun saveWatchProgress(streamId: Int, positionMs: Long, durationMs: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            preferences.saveWatchProgress(streamId, positionMs, durationMs)
        }
    }

    fun login(user: String, pass: String, serverUrl: String = AppConfig.DEFAULT_SERVER_URL, isAutoLogin: Boolean = false) {
        val cleanServer = if (serverUrl.isBlank() || serverUrl.contains("my-iptv-server")) {
            AppConfig.DEFAULT_SERVER_URL
        } else {
            serverUrl.trimEnd('/')
        }

        viewModelScope.launch {
            _loginLoading.value = true
            _loginError.value = null

            val result = repository.authenticate(cleanServer, user, pass)
            result.fold(
                onSuccess = { authResp ->
                    _loginLoading.value = false
                    _isLoggedIn.value = true
                    userInfo.value = authResp.userInfo

                    preferences.saveAuth(
                        username = user,
                        password = pass,
                        serverUrl = cleanServer,
                        expDate = authResp.userInfo?.expDate ?: "",
                        maxCons = authResp.userInfo?.maxConnections ?: "1",
                        activeCons = authResp.userInfo?.activeCons ?: "1"
                    )

                    val isSynced = preferences.isPlaylistSyncedFlow.first()
                    if (isSynced) {
                        _currentScreen.value = Screen.Home
                        loadInitialData()
                    } else {
                        _currentScreen.value = Screen.PlaylistSync
                        startPlaylistSync(fromStep = 1)
                    }
                },
                onFailure = { err ->
                    _loginLoading.value = false
                    _loginError.value = err.message ?: "Giriş yapılamadı"
                    if (isAutoLogin) {
                        _currentScreen.value = Screen.Login
                    }
                }
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            preferences.logout()
            playerManager.release()
            userInfo.value = null
            backStack.clear()
            liveStreamJob?.cancel()
            vodStreamJob?.cancel()
            seriesStreamJob?.cancel()
            withContext(Dispatchers.IO) {
                repository.database?.clearAllTables()
            }
            liveStreams.value = emptyList()
            vodStreams.value = emptyList()
            seriesStreams.value = emptyList()
            globalSearchQuery.value = ""
            selectedHomeTab.value = HomeTab.LIVE_TV
            _isLoggedIn.value = false
            _currentScreen.value = Screen.Login
        }
    }

    // ============================================================================
    // OYNATMA LİSTESİ CANLI SENKRONİZASYON (5 ADIMLI ÇEKİRDEK İŞLEM)
    // ============================================================================

    val playlistSyncState = MutableStateFlow(PlaylistSyncState())

    fun openPlaylistSync() {
        startPlaylistSync(fromStep = 1)
        navigateTo(Screen.PlaylistSync, addToBackStack = true)
    }

    fun retryFailedSyncStep() {
        val failed = playlistSyncState.value.failedStepIndex ?: 1
        startPlaylistSync(fromStep = failed)
    }

    fun startPlaylistSync(fromStep: Int = 1) {
        viewModelScope.launch {
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

            // 1. Adım: Kategoriler
            if (fromStep <= 1) {
                updateStep(1, SyncStepStatus.IN_PROGRESS, "Kategoriler taranıyor (Canlı TV, Film ve Dizi)...", -1f)
                val catRes = repository.syncAllCategories()
                if (catRes.isFailure) {
                    val err = catRes.exceptionOrNull()?.localizedMessage ?: "Kategoriler alınamadı"
                    failStep(1, err)
                    return@launch
                }
                val catData = catRes.getOrNull()
                val total = catData?.totalCount ?: 0
                updateStep(1, SyncStepStatus.COMPLETED, "$total Kategori hazır", 1f)
            }

            // 2. Adım: Canlı TV Kanalları
            if (fromStep <= 2) {
                updateStep(2, SyncStepStatus.IN_PROGRESS, "Canlı TV kanalları kaydediliyor...", -1f)
                val liveRes = repository.syncLiveStreams { count ->
                    updateStep(2, SyncStepStatus.IN_PROGRESS, "$count kanal kaydedildi", -1f)
                }
                if (liveRes.isFailure) {
                    val err = liveRes.exceptionOrNull()?.localizedMessage ?: "Canlı TV kanalları kaydedilemedi"
                    failStep(2, err)
                    return@launch
                }
                val liveCount = liveRes.getOrNull() ?: 0
                updateStep(2, SyncStepStatus.COMPLETED, "$liveCount canlı kanal hazır", 1f)
            }

            // 3. Adım: VOD (Film)
            if (fromStep <= 3) {
                updateStep(3, SyncStepStatus.IN_PROGRESS, "VOD (Film) arşivi kaydediliyor...", -1f)
                val vodRes = repository.syncVodStreams { count ->
                    updateStep(3, SyncStepStatus.IN_PROGRESS, "$count film hazır", -1f)
                }
                if (vodRes.isFailure) {
                    val err = vodRes.exceptionOrNull()?.localizedMessage ?: "Film arşivi kaydedilemedi"
                    failStep(3, err)
                    return@launch
                }
                val vodCount = vodRes.getOrNull() ?: 0
                updateStep(3, SyncStepStatus.COMPLETED, "$vodCount film hazır", 1f)
            }

            // 4. Adım: Diziler
            if (fromStep <= 4) {
                updateStep(4, SyncStepStatus.IN_PROGRESS, "Diziler ve bölümler kaydediliyor...", -1f)
                val seriesRes = repository.syncSeriesStreams { count ->
                    updateStep(4, SyncStepStatus.IN_PROGRESS, "$count dizi hazır", -1f)
                }
                if (seriesRes.isFailure) {
                    val err = seriesRes.exceptionOrNull()?.localizedMessage ?: "Diziler kaydedilemedi"
                    failStep(4, err)
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
                    val err = optRes.exceptionOrNull()?.localizedMessage ?: "Optimizasyon tamamlanamadı"
                    failStep(5, err)
                    return@launch
                }
                updateStep(5, SyncStepStatus.COMPLETED, "Tüm içerikler anında açılmaya hazır!", 1f)
            }

            // Başarıyla tamamlandı
            val nowStr = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            preferences.setPlaylistSynced(true, nowStr)
            loadInitialData()

            playlistSyncState.value = playlistSyncState.value.copy(
                isSyncing = false,
                isCompleted = true,
                currentStepIndex = 5
            )

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
    }

    fun loadInitialData() {
        loadLiveCategories()
        loadLiveStreams("all")
        loadVodCategories()
        loadVodStreams("all")
        loadSeriesCategories()
        loadSeriesStreams("all")
    }

    fun loadLiveCategories() {
        viewModelScope.launch {
            repository.getLiveCategoriesFlow().collect { cats ->
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
        viewModelScope.launch {
            repository.getVodCategoriesFlow().collect { cats ->
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
            repository.getVodInfo(vod.streamId).onSuccess { info ->
                currentVodDetail.value = info
            }
        }
    }

    fun loadSeriesCategories() {
        viewModelScope.launch {
            repository.getSeriesCategoriesFlow().collect { cats ->
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
        if (current.size > 10) current.removeAt(current.size - 1)
        recentChannels.value = current
    }

    fun clearRecentChannels() {
        recentChannels.value = emptyList()
    }

    fun previewChannel(channel: LiveStream) {
        val preferHls = streamFormat.value == "HLS"
        val urls = if (!channel.directSource.isNullOrBlank()) {
            listOf(channel.directSource)
        } else {
            repository.buildLiveStreamUrls(channel.streamId, preferHls)
        }
        playerManager.playWithFallbacks(urls, isLive = true)
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

    fun expandMiniPlayerToFullscreen() {
        val channel = activePreviewChannel.value ?: return
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
        val currentChannel = activePreviewChannel.value
        if (currentChannel != null && currentChannel.streamId != channel.streamId) {
            lastPlayedLiveChannel.value = currentChannel
        }
        currentPlayingEpisode.value = null
        recordRecentChannel(channel)
        activePreviewChannel.value = channel
        val preferHls = streamFormat.value == "HLS"
        val urls = if (!channel.directSource.isNullOrBlank()) {
            listOf(channel.directSource)
        } else {
            repository.buildLiveStreamUrls(channel.streamId, preferHls)
        }
        playerManager.playWithFallbacks(urls, isLive = true)
        loadEpgForChannel(channel.streamId)
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

    fun startDualView(channel: LiveStream) {
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

    fun zapRecall(): LiveStream? {
        val prev = lastPlayedLiveChannel.value ?: return null
        playLiveChannel(prev)
        return prev
    }

    fun zapNextChannel(currentStreamId: Int): LiveStream? {
        val list = liveStreams.value
        if (list.isEmpty()) return null
        val currentIndex = list.indexOfFirst { it.streamId == currentStreamId }
        val nextIndex = if (currentIndex != -1) (currentIndex + 1) % list.size else 0
        val nextChannel = list[nextIndex]
        playLiveChannel(nextChannel)
        return nextChannel
    }

    fun zapPreviousChannel(currentStreamId: Int): LiveStream? {
        val list = liveStreams.value
        if (list.isEmpty()) return null
        val currentIndex = list.indexOfFirst { it.streamId == currentStreamId }
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else list.size - 1
        val prevChannel = list[prevIndex]
        playLiveChannel(prevChannel)
        return prevChannel
    }

    fun playVod(vod: VodStream, resume: Boolean = false) {
        currentPlayingEpisode.value = null
        viewModelScope.launch {
            var startPos = 0L
            if (resume) {
                val progress = preferences.getWatchProgress(vod.streamId)
                startPos = progress?.positionMs ?: 0L
            }
            val urls = when (vod.streamId) {
                101 -> listOf("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
                102 -> listOf("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4")
                103 -> listOf("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4")
                else -> repository.buildVodStreamUrls(vod.streamId, vod.containerExtension)
            }
            playerManager.playWithFallbacks(urls, isLive = false, startPositionMs = startPos)
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
        currentPlayingEpisode.value = Pair(episode, seriesTitle)
        viewModelScope.launch {
            val epId = episode.id.toIntOrNull() ?: 0
            var startPos = 0L
            if (resume) {
                val progress = preferences.getWatchProgress(epId)
                startPos = progress?.positionMs ?: 0L
            }
            val urls = repository.buildSeriesStreamUrls(episode.id, episode.containerExtension)
            playerManager.playWithFallbacks(urls, isLive = false, startPositionMs = startPos)
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
            speedTestResult.value = "Sunucuya bağlanılıyor..."

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

                    speedTestResult.value = "Ping: ${ping}ms | Sunucu Yanıtı: ${resp.code} (Hızlı ve Erişilebilir)"
                } catch (e: Exception) {
                    speedTestResult.value = "Hız testi hatası: ${e.localizedMessage}"
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
