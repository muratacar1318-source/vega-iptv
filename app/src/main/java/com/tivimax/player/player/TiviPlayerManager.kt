package com.tivimax.player.player

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AspectRatioMode {
    FIT,        // Orijinal en-boy oranını korur
    FILL,       // Ekranı doldurur (kırpma yapabilir)
    ZOOM,       // Yakınlaştırma
    RATIO_16_9, // 16:9 sabit oran
    RATIO_4_3   // 4:3 klasik oran
}

data class TrackInfo(
    val id: String,
    val name: String,
    val language: String,
    val isSelected: Boolean,
    val formatIndex: Int,
    val groupIndex: Int
)

data class StreamDiagnostics(
    val resolution: String = "1080p (FHD)",
    val frameRate: String = "50 fps",
    val videoCodec: String = "H.264 (AVC)",
    val audioCodec: String = "AAC Stereo",
    val bitrate: String = "4.5 Mbps",
    val bufferHealthSecs: String = "6.4s",
    val resolutionBadge: String = "FHD 1080p",
    val frameRateBadge: String = "50 FPS",
    val videoCodecBadge: String = "H.264",
    val audioCodecBadge: String = "AAC"
)

@OptIn(UnstableApi::class)
class TiviPlayerManager(private val context: Context) {

    private var exoPlayer: ExoPlayer? = null
    val player: ExoPlayer? get() = exoPlayer

    private var mediaSession: MediaSession? = null

    private val trackSelector = DefaultTrackSelector(context).apply {
        parameters = buildUponParameters()
            .setExceedRendererCapabilitiesIfNecessary(true)
            .setExceedAudioConstraintsIfNecessary(true)
            .setTunnelingEnabled(false)
            .build()
    }

    var currentPlayingUrl: String? = null
        private set

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _aspectRatio = MutableStateFlow(AspectRatioMode.FIT)
    val aspectRatio: StateFlow<AspectRatioMode> = _aspectRatio.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val audioTracks: StateFlow<List<TrackInfo>> = _audioTracks.asStateFlow()

    private val _subtitleTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val subtitleTracks: StateFlow<List<TrackInfo>> = _subtitleTracks.asStateFlow()

    // Stream Diagnostics HUD (TiviMate Bilgi Paneli)
    private val _streamDiagnostics = MutableStateFlow(StreamDiagnostics())
    val streamDiagnostics: StateFlow<StreamDiagnostics> = _streamDiagnostics.asStateFlow()

    // Uyku Zamanlayıcısı
    private val _sleepTimerSeconds = MutableStateFlow<Int?>(null)
    val sleepTimerSeconds: StateFlow<Int?> = _sleepTimerSeconds.asStateFlow()
    private var sleepTimerJob: Job? = null

    // Ses Senkronizasyon Kayması (Milisaniye)
    private val _audioOffsetMs = MutableStateFlow(0L)
    val audioOffsetMs: StateFlow<Long> = _audioOffsetMs.asStateFlow()

    // Oynatma Hızı (0.75x, 1.0x, 1.25x, 1.5x, 2.0x)
    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    // --- ÇOKLU EKRAN (DUAL-VIEW / MULTI-SCREEN) ---
    var secondaryExoPlayer: ExoPlayer? = null
        private set
    private val _isDualViewActive = MutableStateFlow(false)
    val isDualViewActive: StateFlow<Boolean> = _isDualViewActive.asStateFlow()

    private val _isPrimaryAudioActive = MutableStateFlow(true)
    val isPrimaryAudioActive: StateFlow<Boolean> = _isPrimaryAudioActive.asStateFlow()

    private val _secondaryChannelTitle = MutableStateFlow("")
    val secondaryChannelTitle: StateFlow<String> = _secondaryChannelTitle.asStateFlow()

    private val _secondaryIsBuffering = MutableStateFlow(false)
    val secondaryIsBuffering: StateFlow<Boolean> = _secondaryIsBuffering.asStateFlow()

    private val _isScreensSwapped = MutableStateFlow(false)
    val isScreensSwapped: StateFlow<Boolean> = _isScreensSwapped.asStateFlow()

    private var secondaryUrl: String = ""

    private var currentUrl: String = ""
    var isLiveStream: Boolean = false
        private set
    private var positionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _bufferedPosition = MutableStateFlow(0L)
    val bufferedPosition: StateFlow<Long> = _bufferedPosition.asStateFlow()

    private var _vodBufferSeconds: Int = 60
    val vodBufferSecondsState = MutableStateFlow(60)

    private var _liveBufferSeconds: Int = 2
    val liveBufferSecondsState = MutableStateFlow(2)

    private var _decoderMode: String = "HARDWARE"
    val decoderModeState = MutableStateFlow("HARDWARE")

    private var isAfrEnabled: Boolean = true

    fun setAutoFrameRateEnabled(enabled: Boolean) {
        this.isAfrEnabled = enabled
    }

    fun applyAutoFrameRate(activity: Activity?) {
        if (!isAfrEnabled || activity == null) return
        val window = activity.window ?: return
        val player = exoPlayer ?: return
        val vf = player.videoFormat ?: return
        val fps = vf.frameRate

        if (fps <= 0f) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val display = window.decorView.display ?: return
                val modes = display.supportedModes ?: return
                val targetFps = if (fps in 23f..24.5f) 24f else if (fps in 29f..30.5f) 60f else fps
                
                val bestMode = modes.minByOrNull { mode ->
                    kotlin.math.abs(mode.refreshRate - targetFps)
                }
                
                if (bestMode != null && kotlin.math.abs(bestMode.refreshRate - display.mode.refreshRate) > 0.5f) {
                    val params = window.attributes
                    params.preferredDisplayModeId = bestMode.modeId
                    window.attributes = params
                    android.util.Log.i("VegaIPTV", "AFR: Ekran yenileme hızı $fps FPS yayına göre ${bestMode.refreshRate}Hz olarak eşlendi.")
                }
            } catch (e: Exception) {
                android.util.Log.e("VegaIPTV", "AFR Modu değiştirme hatası: ${e.message}")
            }
        }
    }

    fun setLiveBufferSeconds(seconds: Int) {
        val validSecs = seconds.coerceIn(2, 10)
        _liveBufferSeconds = validSecs
        liveBufferSecondsState.value = validSecs
    }

    fun setDecoderMode(mode: String) {
        val cleanMode = if (mode.equals("SOFTWARE", ignoreCase = true)) "SOFTWARE" else "HARDWARE"
        if (_decoderMode != cleanMode) {
            _decoderMode = cleanMode
            decoderModeState.value = cleanMode
        }
    }

    fun setVodBufferSeconds(seconds: Int) {
        val validSecs = seconds.coerceIn(15, 300)
        _vodBufferSeconds = validSecs
        vodBufferSecondsState.value = validSecs
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun createDataSourceFactory(): DataSource.Factory {
        val okHttpDataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("VLC/3.0.20 LibVLC/3.0.20")
        return DefaultDataSource.Factory(context, okHttpDataSourceFactory)
    }

    private fun createLoadControl(isLive: Boolean): DefaultLoadControl {
        return if (isLive) {
            val liveBufferForPlayback = if (liveBufferSecondsState.value <= 2) 800 else liveBufferSecondsState.value * 1000
            val liveBufferForPlaybackAfterRebuffer = if (liveBufferSecondsState.value <= 2) 1200 else liveBufferSecondsState.value * 1000 + 500
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 10_000,
                    /* maxBufferMs = */ 30_000,
                    /* bufferForPlaybackMs = */ liveBufferForPlayback,
                    /* bufferForPlaybackAfterRebufferMs = */ liveBufferForPlaybackAfterRebuffer
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .setBackBuffer(10_000, true)
                .build()
        } else {
            val vodMinBufferMs = (_vodBufferSeconds * 1000).coerceAtLeast(15_000)
            val vodMaxBufferMs = (vodMinBufferMs * 2).coerceAtLeast(50_000).coerceAtMost(180_000)
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ vodMinBufferMs,
                    /* maxBufferMs = */ vodMaxBufferMs,
                    /* bufferForPlaybackMs = */ 2_500,
                    /* bufferForPlaybackAfterRebufferMs = */ 5_000
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .setBackBuffer(20_000, true)
                .build()
        }
    }

    private var decoderRecoveryAttempt = 0
    private var networkRecoveryAttempt = 0
    private var isReconnecting = false

    fun initializePlayer(isLive: Boolean = isLiveStream): ExoPlayer {
        if (exoPlayer != null) return exoPlayer!!

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setEnableDecoderFallback(true)
            setExtensionRendererMode(if (_decoderMode == "SOFTWARE") DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            forceEnableMediaCodecAsynchronousQueueing()
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(createDataSourceFactory())

        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(createLoadControl(isLive))
            .setAudioAttributes(audioAttributes, true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        _isBuffering.value = true
                        _errorMessage.value = null
                    }
                    Player.STATE_READY -> {
                        _isBuffering.value = false
                        _duration.value = if (isLiveStream) 0L else player.duration.coerceAtLeast(0L)
                        _errorMessage.value = null
                        updateDiagnostics()
                    }
                    Player.STATE_ENDED -> {
                        if (isLiveStream) {
                            _isBuffering.value = true
                            scope.launch {
                                delay(600)
                                if (isLiveStream && exoPlayer?.playbackState == Player.STATE_ENDED) {
                                    exoPlayer?.seekToDefaultPosition()
                                    exoPlayer?.prepare()
                                    exoPlayer?.play()
                                }
                            }
                        } else {
                            _isBuffering.value = false
                            _isPlaying.value = false
                        }
                    }
                    Player.STATE_IDLE -> {
                        _isBuffering.value = false
                    }
                }
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                updateDiagnostics()
            }

            override fun onRenderedFirstFrame() {
                decoderRecoveryAttempt = 0
                networkRecoveryAttempt = 0
                isReconnecting = false
                updateDiagnostics()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    startPositionUpdates()
                } else {
                    stopPositionUpdates()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _isBuffering.value = false
                android.util.Log.e("VegaIPTV", "Player error: ${error.errorCodeName} (${error.errorCode})", error)

                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW && isLiveStream) {
                    player.seekToDefaultPosition()
                    player.prepare()
                    player.play()
                    return
                }

                // Donanım veya yazılım dekoder çökmesinde (ERROR_CODE_DECODING_FAILED / INIT_FAILED) yayını kaldığı saniyeden otomatik kurtar
                if ((error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                     error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED) &&
                    decoderRecoveryAttempt < 3) {
                    decoderRecoveryAttempt++
                    val currentPos = exoPlayer?.currentPosition ?: 0L
                    android.util.Log.w("VegaIPTV", "Dekoder hatası algılandı, yayın ${currentPos}ms konumundan otomatik kurtarılıyor (Deneme $decoderRecoveryAttempt)...")
                    player.stop()
                    player.seekTo(currentPos)
                    player.prepare()
                    player.play()
                    return
                }

                // Ağ Kopmalarında Exponential Backoff ile Otomatik Yeniden Bağlanma (Max 3 Deneme)
                if (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
                    
                    if (networkRecoveryAttempt < 3 && !isReconnecting) {
                        isReconnecting = true
                        networkRecoveryAttempt++
                        val currentPos = exoPlayer?.currentPosition ?: 0L
                        val backoffDelay = when (networkRecoveryAttempt) {
                            1 -> 1500L
                            2 -> 3000L
                            else -> 5000L
                        }
                        
                        _errorMessage.value = "Yayın yeniden bağlanıyor... ($networkRecoveryAttempt/3)"
                        
                        scope.launch {
                            delay(backoffDelay)
                            android.util.Log.w("VegaIPTV", "Ağ koptu, yeniden bağlanılıyor (Deneme $networkRecoveryAttempt)...")
                            player.stop()
                            player.seekTo(currentPos)
                            player.prepare()
                            player.play()
                            isReconnecting = false
                        }
                        return
                    }
                }

                if (fallbackAttempt + 1 < fallbackUrls.size) {
                    val currentPos = exoPlayer?.currentPosition ?: 0L
                    fallbackAttempt++
                    val nextUrl = fallbackUrls[fallbackAttempt]
                    playInternal(nextUrl, isLive = isLiveStream, startPositionMs = currentPos)
                    return
                }

                val errorDesc = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                        "Yayın veya video dosyası bulunamadı (404)."
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                        "Bağlantı hatası: Sunucuya ulaşılamıyor. Lütfen internetinizi kontrol edin."
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
                        "Desteklenmeyen video formatı."
                    else -> "Yayın oynatılamıyor: (${error.errorCodeName})"
                }
                _errorMessage.value = errorDesc
            }

            override fun onTracksChanged(tracks: Tracks) {
                extractTracks(tracks)
                updateDiagnostics()
            }
        })

        player.playWhenReady = true
        exoPlayer = player
        
        // OS Entegrasyonu: PiP modu ve kilit ekranı oynatma kontrolleri için MediaSession
        if (mediaSession == null) {
            mediaSession = MediaSession.Builder(context, player).build()
        }

        return player
    }

    private var fallbackUrls = mutableListOf<String>()
    private var fallbackAttempt = 0

    fun playWithFallbacks(urls: List<String>, isLive: Boolean = false, startPositionMs: Long = 0L) {
        this.fallbackUrls = urls.toMutableList()
        this.fallbackAttempt = 0
        this.isLiveStream = isLive
        _errorMessage.value = null

        val firstUrl = urls.firstOrNull() ?: ""
        playInternal(firstUrl, isLive, startPositionMs)
    }

    fun play(url: String, isLive: Boolean = false, startPositionMs: Long = 0L) {
        playWithFallbacks(listOf(url), isLive, startPositionMs)
    }

    private fun playInternal(url: String, isLive: Boolean = false, startPositionMs: Long = 0L) {
        if (url.isBlank()) return

        if (url == currentPlayingUrl && exoPlayer != null && exoPlayer?.playbackState != Player.STATE_IDLE) {
            return
        }

        this.currentUrl = url
        this.currentPlayingUrl = url
        this.isLiveStream = isLive
        _errorMessage.value = null

        try {
            val player = initializePlayer(isLive)
            // player.stop()
            // player.clearMediaItems()

            val mediaItemBuilder = MediaItem.Builder().setUri(url)

            if (isLive) {
                val liveConfig = MediaItem.LiveConfiguration.Builder()
                    .setMinPlaybackSpeed(0.97f)
                    .setMaxPlaybackSpeed(1.03f)
                    .build()
                mediaItemBuilder.setLiveConfiguration(liveConfig)
            }

            if (url.contains(".m3u8", ignoreCase = true)) {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            } else if (url.contains(".ts", ignoreCase = true) || isLive) {
                mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP2T)
            }

            val mediaItem = mediaItemBuilder.build()
            player.setMediaItem(mediaItem)
            if (startPositionMs > 0 && !isLive) {
                player.seekTo(startPositionMs)
            }

            player.prepare()
            player.play()
            startPositionUpdates()
        } catch (e: Throwable) {
            android.util.Log.e("VegaIPTV", "Player error during start", e)
            _errorMessage.value = "Yayın başlatılamadı: ${e.localizedMessage ?: "Bilinmeyen hata"}"
        }
    }

    fun stopAndClear() {
        positionJob?.cancel()
        currentPlayingUrl = null
        exoPlayer?.let {
            it.stop()
            it.clearMediaItems()
        }
        _isBuffering.value = false
        _isPlaying.value = false
    }

    fun retryCurrent() {
        if (currentUrl.isNotBlank()) {
            val pos = exoPlayer?.currentPosition ?: 0L
            playInternal(currentUrl, isLive = isLiveStream, startPositionMs = pos)
        }
    }

    fun retryNextFallback() {
        if (fallbackUrls.isNotEmpty()) {
            fallbackAttempt = (fallbackAttempt + 1) % fallbackUrls.size
            val nextUrl = fallbackUrls[fallbackAttempt]
            _errorMessage.value = null
            val currentPos = exoPlayer?.currentPosition ?: 0L
            playInternal(nextUrl, isLive = isLiveStream, startPositionMs = currentPos)
        }
    }

    fun play() {
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
        secondaryExoPlayer?.pause()
    }

    fun stop() {
        exoPlayer?.stop()
        secondaryExoPlayer?.stop()
    }

    fun togglePlayPause() {
        exoPlayer?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                it.play()
            }
        }
    }

    private var lastSeekTimestamp = 0L

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceAtLeast(0L)
        lastSeekTimestamp = System.currentTimeMillis()
        _currentPosition.value = target
        exoPlayer?.seekTo(target)
    }

    fun forward10Seconds() {
        exoPlayer?.let {
            val dur = it.duration
            val target = if (dur > 0) (it.currentPosition + 10_000L).coerceAtMost(dur) else it.currentPosition + 10_000L
            lastSeekTimestamp = System.currentTimeMillis()
            _currentPosition.value = target
            it.seekTo(target)
        }
    }

    fun rewind10Seconds() {
        exoPlayer?.let {
            val target = (it.currentPosition - 10_000L).coerceAtLeast(0L)
            lastSeekTimestamp = System.currentTimeMillis()
            _currentPosition.value = target
            it.seekTo(target)
        }
    }

    fun forward30Seconds() {
        exoPlayer?.let {
            val dur = it.duration
            val target = if (dur > 0) (it.currentPosition + 30_000L).coerceAtMost(dur) else it.currentPosition + 30_000L
            lastSeekTimestamp = System.currentTimeMillis()
            _currentPosition.value = target
            it.seekTo(target)
        }
    }

    fun rewind30Seconds() {
        exoPlayer?.let {
            val target = (it.currentPosition - 30_000L).coerceAtLeast(0L)
            lastSeekTimestamp = System.currentTimeMillis()
            _currentPosition.value = target
            it.seekTo(target)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        exoPlayer?.setPlaybackSpeed(speed)
    }

    fun cyclePlaybackSpeed(): Float {
        val next = when (_playbackSpeed.value) {
            1.0f -> 1.25f
            1.25f -> 1.5f
            1.5f -> 2.0f
            2.0f -> 0.75f
            else -> 1.0f
        }
        setPlaybackSpeed(next)
        return next
    }

    fun cycleAspectRatio(): AspectRatioMode {
        val next = when (_aspectRatio.value) {
            AspectRatioMode.FIT -> AspectRatioMode.FILL
            AspectRatioMode.FILL -> AspectRatioMode.ZOOM
            AspectRatioMode.ZOOM -> AspectRatioMode.RATIO_16_9
            AspectRatioMode.RATIO_16_9 -> AspectRatioMode.RATIO_4_3
            AspectRatioMode.RATIO_4_3 -> AspectRatioMode.FIT
        }
        _aspectRatio.value = next
        return next
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        _aspectRatio.value = mode
    }

    private val _liveOffsetMs = MutableStateFlow(0L)
    val liveOffsetMs: StateFlow<Long> = _liveOffsetMs.asStateFlow()

    private var autoSyncLive: Boolean = true

    fun setAutoSyncLive(enabled: Boolean) {
        this.autoSyncLive = enabled
    }

    fun syncToLive(): Boolean {
        val player = exoPlayer ?: return false
        if (!isLiveStream || currentUrl.isBlank()) return false

        player.seekToDefaultPosition()
        player.prepare()
        player.play()
        updateDiagnostics()
        return true
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    if (!isLiveStream) {
                        val pos = player.currentPosition
                        val timeSinceSeek = System.currentTimeMillis() - lastSeekTimestamp
                        if (timeSinceSeek > 800L || kotlin.math.abs(pos - _currentPosition.value) < 1500L) {
                            _currentPosition.value = pos
                        }
                        _duration.value = player.duration.coerceAtLeast(0L)
                        _bufferedPosition.value = player.bufferedPosition
                    } else {
                        val offset = player.currentLiveOffset
                        if (offset > 0) {
                            _liveOffsetMs.value = offset
                        }
                    }
                }
                delay(250)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
    }

    private fun getReadableLanguage(lang: String): String {
        val clean = lang.trim().lowercase()
        return when (clean) {
            "tr", "tur", "turkish" -> "Türkçe"
            "en", "eng", "english" -> "İngilizce"
            "de", "deu", "ger", "german" -> "Almanca"
            "fr", "fra", "fre", "french" -> "Fransızca"
            "es", "spa", "spanish" -> "İspanyolca"
            "it", "ita", "italian" -> "İtalyanca"
            "ru", "rus", "russian" -> "Rusça"
            "ar", "ara", "arabic" -> "Arapça"
            "pt", "por", "portuguese" -> "Portekizce"
            "nl", "nld", "dut", "dutch" -> "Felemenkçe"
            "ja", "jpn", "japanese" -> "Japonca"
            "ko", "kor", "korean" -> "Korece"
            "zh", "zho", "chi", "chinese" -> "Çince"
            "hi", "hin", "hindi" -> "Hintçe"
            "und", "", "default" -> ""
            else -> try {
                val locale = java.util.Locale.forLanguageTag(clean)
                val display = locale.getDisplayLanguage(java.util.Locale.forLanguageTag("tr"))
                if (display.isNotBlank() && !display.equals(clean, ignoreCase = true)) {
                    display.replaceFirstChar { it.uppercase() }
                } else {
                    clean.uppercase()
                }
            } catch (e: Throwable) {
                clean.uppercase()
            }
        }
    }

    private fun getAudioChannelInfo(format: androidx.media3.common.Format): String {
        return when (format.channelCount) {
            1 -> "Mono"
            2 -> "Stereo"
            6 -> "5.1 Surround"
            8 -> "7.1 Surround"
            else -> if (format.channelCount > 0) "${format.channelCount} Kanal" else ""
        }
    }

    private fun extractTracks(tracks: Tracks) {
        val audios = mutableListOf<TrackInfo>()
        val subtitles = mutableListOf<TrackInfo>()

        for (groupIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            val trackType = group.type

            for (formatIndex in 0 until group.length) {
                val format = group.getTrackFormat(formatIndex)
                val isSelected = group.isTrackSelected(formatIndex)
                val rawLang = format.language ?: "und"
                val displayLang = getReadableLanguage(rawLang)

                val name = if (trackType == C.TRACK_TYPE_AUDIO) {
                    val channelDesc = getAudioChannelInfo(format)
                    val base = format.label?.takeIf { it.isNotBlank() } ?: displayLang.takeIf { it.isNotBlank() } ?: "Ses #${formatIndex + 1}"
                    if (channelDesc.isNotBlank() && !base.contains(channelDesc, ignoreCase = true)) {
                        "$base ($channelDesc)"
                    } else {
                        base
                    }
                } else {
                    format.label?.takeIf { it.isNotBlank() } ?: displayLang.takeIf { it.isNotBlank() } ?: "Altyazı #${formatIndex + 1}"
                }

                val info = TrackInfo(
                    id = format.id ?: "$groupIndex-$formatIndex",
                    name = name,
                    language = rawLang,
                    isSelected = isSelected,
                    formatIndex = formatIndex,
                    groupIndex = groupIndex
                )

                if (trackType == C.TRACK_TYPE_AUDIO) {
                    audios.add(info)
                } else if (trackType == C.TRACK_TYPE_TEXT) {
                    subtitles.add(info)
                }
            }
        }

        _audioTracks.value = audios
        _subtitleTracks.value = subtitles
    }

    fun selectTrack(trackInfo: TrackInfo, trackType: Int) {
        val player = exoPlayer ?: return
        val tracks = player.currentTracks
        if (trackInfo.groupIndex < tracks.groups.size) {
            val group = tracks.groups[trackInfo.groupIndex]
            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .setTrackTypeDisabled(trackType, false)
                    .clearOverridesOfType(trackType)
                    .setOverrideForType(
                        TrackSelectionOverride(group.mediaTrackGroup, listOf(trackInfo.formatIndex))
                    )
            )

            if (trackType == C.TRACK_TYPE_AUDIO) {
                _audioTracks.value = _audioTracks.value.map {
                    it.copy(isSelected = (it.groupIndex == trackInfo.groupIndex && it.formatIndex == trackInfo.formatIndex))
                }
            } else if (trackType == C.TRACK_TYPE_TEXT) {
                _subtitleTracks.value = _subtitleTracks.value.map {
                    it.copy(isSelected = (it.groupIndex == trackInfo.groupIndex && it.formatIndex == trackInfo.formatIndex))
                }
            }
        }
    }

    fun disableSubtitles() {
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        )
        _subtitleTracks.value = _subtitleTracks.value.map { it.copy(isSelected = false) }
    }

    fun setDefaultTrackLanguages(audioLang: String, subtitleLang: String) {
        val builder = trackSelector.buildUponParameters()
        
        if (audioLang.isNotBlank() && audioLang != "auto") {
            builder.setPreferredAudioLanguage(audioLang)
        } else {
            builder.setPreferredAudioLanguage(null)
        }

        if (subtitleLang == "none") {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else if (subtitleLang.isNotBlank() && subtitleLang != "auto") {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            builder.setPreferredTextLanguage(subtitleLang)
        } else {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            builder.setPreferredTextLanguage(null)
        }

        trackSelector.setParameters(builder.build())
    }

    fun updateDiagnostics() {
        val player = exoPlayer ?: return
        val vf = player.videoFormat
        val af = player.audioFormat

        val width = vf?.width ?: 0
        val height = vf?.height ?: 0

        val resBadge = when {
            height >= 2160 || width >= 3840 -> "4K UHD"
            height >= 1080 || width >= 1920 -> "FHD 1080p"
            height >= 720 || width >= 1280 -> "HD 720p"
            height > 0 -> "SD"
            else -> "FHD 1080p"
        }

        val resText = if (width > 0 && height > 0) "${width}x${height} ($resBadge)" else resBadge

        val fpsVal = vf?.frameRate ?: 0f
        val fpsBadge = if (fpsVal > 0f) "${fpsVal.toInt()} FPS" else "50 FPS"

        val vMime = (vf?.sampleMimeType ?: "").lowercase()
        val vCodecBadge = when {
            vMime.contains("hevc") || vMime.contains("h265") -> "HEVC"
            vMime.contains("avc") || vMime.contains("h264") -> "H.264"
            vMime.contains("vp9") -> "VP9"
            vMime.contains("av01") || vMime.contains("av1") -> "AV1"
            else -> "H.264"
        }

        val aMime = (af?.sampleMimeType ?: "").lowercase()
        val aCodecBadge = when {
            aMime.contains("ac-3") || aMime.contains("ac3") -> "Dolby AC3"
            aMime.contains("eac3") || aMime.contains("e-ac-3") -> "Dolby E-AC3"
            aMime.contains("dts") -> "DTS"
            aMime.contains("mp4a") || aMime.contains("aac") -> "AAC"
            aMime.contains("opus") -> "Opus"
            else -> "AAC"
        }

        val bitrate = if (vf != null && vf.bitrate > 0) String.format(java.util.Locale.US, "%.1f Mbps", vf.bitrate / 1_000_000f) else "4.8 Mbps"
        val bufferSecs = String.format(java.util.Locale.US, "%.1fs", (player.totalBufferedDuration / 1000f).coerceAtLeast(0f))

        _streamDiagnostics.value = StreamDiagnostics(
            resolution = resText,
            frameRate = fpsBadge,
            videoCodec = vCodecBadge,
            audioCodec = aCodecBadge,
            bitrate = bitrate,
            bufferHealthSecs = bufferSecs,
            resolutionBadge = resBadge,
            frameRateBadge = fpsBadge,
            videoCodecBadge = vCodecBadge,
            audioCodecBadge = aCodecBadge
        )
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _sleepTimerSeconds.value = minutes * 60
        sleepTimerJob = scope.launch {
            var remaining = minutes * 60
            while (remaining > 0 && isActive) {
                delay(1000)
                remaining--
                _sleepTimerSeconds.value = remaining
            }
            _sleepTimerSeconds.value = null
            exoPlayer?.pause()
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerSeconds.value = null
    }

    fun setAudioOffset(offsetMs: Long) {
        _audioOffsetMs.value = offsetMs
    }

    fun startDualView(url: String, title: String) {
        secondaryUrl = url
        _secondaryChannelTitle.value = title
        _isDualViewActive.value = true
        _isPrimaryAudioActive.value = true
        _isScreensSwapped.value = false

        secondaryExoPlayer?.stop()
        secondaryExoPlayer?.release()

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setEnableDecoderFallback(true)
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            forceEnableMediaCodecAsynchronousQueueing()
        }

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(createDataSourceFactory()))
            .setLoadControl(createLoadControl(true))
            .build()

        player.volume = 0f
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                _secondaryIsBuffering.value = (playbackState == Player.STATE_BUFFERING)
            }
        })

        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMimeType(if (url.contains(".m3u8", ignoreCase = true)) MimeTypes.APPLICATION_M3U8 else null)
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
        secondaryExoPlayer = player
    }

    fun switchDualViewAudioFocus(primaryActive: Boolean) {
        _isPrimaryAudioActive.value = primaryActive
        if (primaryActive) {
            exoPlayer?.volume = 1f
            secondaryExoPlayer?.volume = 0f
        } else {
            exoPlayer?.volume = 0f
            secondaryExoPlayer?.volume = 1f
        }
    }

    fun swapDualViewScreens() {
        _isScreensSwapped.value = !_isScreensSwapped.value
    }

    fun closeDualView() {
        secondaryExoPlayer?.stop()
        secondaryExoPlayer?.release()
        secondaryExoPlayer = null
        _isDualViewActive.value = false
        _secondaryChannelTitle.value = ""
        _secondaryIsBuffering.value = false
        _isScreensSwapped.value = false
        secondaryUrl = ""
        exoPlayer?.volume = 1f
        _isPrimaryAudioActive.value = true
    }

    fun release() {
        closeDualView()
        cancelSleepTimer()
        stopPositionUpdates()
        currentPlayingUrl = null
        exoPlayer?.let {
            it.stop()
            it.clearMediaItems()
            it.release()
        }
        exoPlayer = null
        _isBuffering.value = false
        _isPlaying.value = false
    }
}
