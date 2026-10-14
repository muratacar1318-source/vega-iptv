package com.tivimax.player.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

import com.tivimax.player.data.config.AppConfig
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
    FIT,      // Orijinal en-boy oranını korur
    FILL,     // Ekranı doldurur (kırpma yapabilir)
    ZOOM,     // Hafif yakınlaştırma
    RATIO_16_9 // 16:9 sabit oran
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
    private val trackSelector = DefaultTrackSelector(context).apply {
        parameters = buildUponParameters()
            .setExceedRendererCapabilitiesIfNecessary(true)
            .setExceedAudioConstraintsIfNecessary(true)
            .setTunnelingEnabled(false)
            .build()
    }
    private var bufferingRecoveryJob: Job? = null

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
    private var isLiveStream: Boolean = false
    private var positionJob: Job? = null
    private var liveRetryJob: Job? = null
    private var liveRetryCount = 0
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _bufferedPosition = MutableStateFlow(0L)
    val bufferedPosition: StateFlow<Long> = _bufferedPosition.asStateFlow()

    private var _vodBufferSeconds: Int = 60
    val vodBufferSecondsState = MutableStateFlow(60)

    private var _liveBufferSeconds: Int = 2
    val liveBufferSecondsState = MutableStateFlow(2)

    private var _decoderMode: String = "HARDWARE"
    val decoderModeState = MutableStateFlow("HARDWARE")

    private var lastInitializedIsLive: Boolean? = null

    private fun buildLoadControl(isLive: Boolean): DefaultLoadControl {
        return if (isLive) {
            // Canlı Yayınlar (HLS .m3u8 ve TS .ts):
            // Hızlı kanal geçişi (fast zapping) için 800ms başlatma tamponu ve 2-3s düşük gecikmeli canlı sınır
            val targetSecs = _liveBufferSeconds.coerceIn(2, 6)
            val minBufferMs = targetSecs * 1000
            val maxBufferMs = (targetSecs * 1000) + 1500 // Maksimum 3.5s (2s hedef için)
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    minBufferMs,
                    maxBufferMs,
                    800, // Hızlı kanal açılışı için 800ms başlatma tamponu korundu
                    1200 // Yeniden tamponlama
                )
                .setBackBuffer(0, false) // Canlıda geçmiş tampon gecikmeyi artırır, kapat
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        } else {
            // VOD (MP4/MKV Film ve Dizi):
            // Yüksek bit hızlı ve uzun filmlerde kesintisiz akış için geniş ve sürekli dolan tampon.
            // prioritizeTimeOverSizeThresholds = false sayesinde ExoPlayer 10. saniyede indirmeyi aniden kesmez,
            // TCP soketinin sunucu tarafından zaman aşımına uğramasını ve 5-6. saniyede videonun donmasını önler.
            val targetSecs = _vodBufferSeconds.coerceIn(30, 120)
            val minBufferMs = 20_000
            val maxBufferMs = (targetSecs * 1000).coerceAtLeast(minBufferMs + 10_000)
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    minBufferMs, // minBufferMs = 20000 (20 saniye)
                    maxBufferMs, // maxBufferMs = 60000+ (60-120 saniye kesintisiz akış)
                    2500,        // bufferForPlaybackMs = 2500 (Akıcı ilk açılış)
                    5000         // bufferForPlaybackAfterRebufferMs = 5000
                )
                .setTargetBufferBytes(64 * 1024 * 1024) // 64 MB bellek sınırı
                .setPrioritizeTimeOverSizeThresholds(false) // Kesintisiz indirme (Kritik: donmayı çözer)
                .setBackBuffer(20_000, true) // 20 saniye geri sarma tamponu
                .build()
        }
    }

    fun setLiveBufferSeconds(seconds: Int) {
        val validSecs = seconds.coerceIn(2, 10)
        _liveBufferSeconds = validSecs
        liveBufferSecondsState.value = validSecs
        if (isLiveStream && exoPlayer != null) {
            val current = currentUrl
            lastInitializedIsLive = null
            release()
            if (current.isNotBlank()) {
                playInternal(current, isLive = true)
            }
        }
    }

    fun setDecoderMode(mode: String) {
        val cleanMode = if (mode.equals("SOFTWARE", ignoreCase = true)) "SOFTWARE" else "HARDWARE"
        if (_decoderMode != cleanMode) {
            _decoderMode = cleanMode
            decoderModeState.value = cleanMode
            if (exoPlayer != null) {
                val current = currentUrl
                val isLive = isLiveStream
                val pos = exoPlayer?.currentPosition ?: 0L
                release()
                if (current.isNotBlank()) {
                    playInternal(current, isLive = isLive, startPositionMs = pos)
                }
            }
        }
    }

    fun setVodBufferSeconds(seconds: Int) {
        val validSecs = seconds.coerceIn(15, 300)
        _vodBufferSeconds = validSecs
        vodBufferSecondsState.value = validSecs
        if (!isLiveStream && exoPlayer != null) {
            val current = currentUrl
            val pos = exoPlayer?.currentPosition ?: 0L
            release()
            if (current.isNotBlank()) {
                playInternal(current, isLive = false, startPositionMs = pos)
            }
        }
    }

    fun initializePlayer(isLive: Boolean = isLiveStream): ExoPlayer {
        if (exoPlayer != null && lastInitializedIsLive == isLive) return exoPlayer!!

        if (exoPlayer != null) {
            release()
        }
        lastInitializedIsLive = isLive

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setEnableDecoderFallback(true) // Donanım başarısız olursa otomatik yazılıma geç
            setAllowedVideoJoiningTimeMs(10000) // Yavaş dekoder başlangıçlarında video senkron hatasını önle
            // AC3, EAC3 ve DTS ses kodeklerini FFmpeg eklentisiyle öncelikli çözmek için PREFER modu
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            if (_decoderMode == "SOFTWARE") {
                setMediaCodecSelector { mimeType, requiresSecure, requiresTunneling ->
                    try {
                        val decoders = androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT
                            .getDecoderInfos(mimeType, requiresSecure, requiresTunneling)
                        if (decoders.any { it.softwareOnly }) {
                            decoders.sortedByDescending { it.softwareOnly }
                        } else {
                            decoders
                        }
                    } catch (e: Throwable) {
                        androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT
                            .getDecoderInfos(mimeType, requiresSecure, requiresTunneling)
                    }
                }
            }
        }

        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory().apply {
            setConstantBitrateSeekingEnabled(true)
            // Matroska (.mkv) dosya sonundaki cues aramasını kapat: Xtream sunucuları HTTP Range
            // desteklemediğinde tüm dosyayı baştan indirmeye çalışıp videoyu kilitler.
            // FLAG_DISABLE_SEEK_FOR_CUES ile dosya başından akıcı olarak hemen oynatmaya başlar.
            setMatroskaExtractorFlags(androidx.media3.extractor.mkv.MatroskaExtractor.FLAG_DISABLE_SEEK_FOR_CUES)
            setMp4ExtractorFlags(androidx.media3.extractor.mp4.Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)
            setFragmentedMp4ExtractorFlags(androidx.media3.extractor.mp4.FragmentedMp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)
            setTsExtractorFlags(
                androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
            )
        }

        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent("IPTV Smarters Pro")
            .setDefaultRequestProperties(
                mapOf(
                    "User-Agent" to "IPTV Smarters Pro",
                    "Accept" to "*/*",
                    "Connection" to "keep-alive"
                )
            )
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(30000)
            .setKeepPostFor302Redirects(true)

        val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory, extractorsFactory)
            .setLoadErrorHandlingPolicy(
                androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(5)
            )

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(audioAttributes, true)
            .setLoadControl(buildLoadControl(isLive))
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        _isBuffering.value = true
                        _errorMessage.value = null
                        bufferingRecoveryJob?.cancel()
                        bufferingRecoveryJob = scope.launch {
                            delay(12000)
                            if (_isBuffering.value && exoPlayer != null) {
                                if (isLiveStream) {
                                    android.util.Log.w("VegaIPTV", "Buffering timeout detected, attempting live recovery")
                                    exoPlayer?.seekToDefaultPosition()
                                    exoPlayer?.prepare()
                                    exoPlayer?.play()
                                } else {
                                    // VOD / Dizi: Kesinlikle 0L'ye sarma! Mevcut izleme konumundan taze bağlantı kur
                                    android.util.Log.w("VegaIPTV", "Buffering timeout detected, attempting VOD recovery at current position")
                                    val currentPos = exoPlayer?.currentPosition ?: 0L
                                    if (fallbackUrls.size > 1 && fallbackAttempt + 1 < fallbackUrls.size) {
                                        fallbackAttempt++
                                        val nextUrl = fallbackUrls[fallbackAttempt]
                                        playInternal(nextUrl, isLive = false, startPositionMs = currentPos)
                                    } else {
                                        exoPlayer?.seekTo(currentPos)
                                        exoPlayer?.prepare()
                                        exoPlayer?.play()
                                    }
                                }
                            }
                        }
                    }
                    Player.STATE_READY -> {
                        bufferingRecoveryJob?.cancel()
                        liveRetryJob?.cancel()
                        liveRetryCount = 0
                        _isBuffering.value = false
                        _duration.value = if (isLiveStream) 0L else player.duration.coerceAtLeast(0L)
                        _errorMessage.value = null
                        updateDiagnostics()
                    }
                    Player.STATE_ENDED -> {
                        if (isLiveStream) {
                            // Canlı yayında 'oynatma bitti' durma hatası verme!
                            // 3 saniyelik tampon ile canlı sınırdan otomatik devam et.
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

                // Canlı yayın izlerken yayın anlık koparsa siyah ekranda kalmaması için
                // 2 saniye arayla maksimum 3 kez otomatik yeniden bağlanma (retry/prepare) mekanizması
                if (isLiveStream && liveRetryCount < 3) {
                    liveRetryCount++
                    _isBuffering.value = true
                    _errorMessage.value = "Yayın yeniden bağlanıyor ($liveRetryCount/3)..."
                    liveRetryJob?.cancel()
                    liveRetryJob = scope.launch {
                        delay(2000)
                        if (isLiveStream && exoPlayer != null) {
                            android.util.Log.i("VegaIPTV", "Canlı yayın otomatik yeniden bağlanma denemesi: $liveRetryCount/3")
                            player.seekToDefaultPosition()
                            player.prepare()
                            player.play()
                        }
                    }
                    return
                }

                // Otomatik Alternatif Format Deneme (Örn: .mp4 -> .mkv veya sunucu hatası)
                if (fallbackAttempt + 1 < fallbackUrls.size) {
                    val currentPos = exoPlayer?.currentPosition ?: 0L
                    liveRetryCount = 0
                    fallbackAttempt++
                    val nextUrl = fallbackUrls[fallbackAttempt]
                    android.util.Log.i("VegaIPTV", "Otomatik alternatif formata geçiliyor: $nextUrl (konum: $currentPos ms)")
                    playInternal(nextUrl, isLive = isLiveStream, startPositionMs = currentPos)
                    return
                }

                val errorDesc = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                        "Yayın akışı bulunamadı (404). Sunucu bu kanalı şu an iletmiyor."
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                        "Bağlantı hatası: Sunucuya ulaşılamıyor. Lütfen internetinizi kontrol edin."
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
                        "Desteklenmeyen akış formatı."
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
        return player
    }

    private var fallbackUrls = mutableListOf<String>()
    private var fallbackAttempt = 0

    fun playWithFallbacks(urls: List<String>, isLive: Boolean = false, startPositionMs: Long = 0L) {
        this.fallbackUrls = urls.toMutableList()
        this.fallbackAttempt = 0
        this.liveRetryCount = 0
        this.liveRetryJob?.cancel()
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
        this.currentUrl = url
        this.isLiveStream = isLive
        this.liveRetryCount = 0
        this.liveRetryJob?.cancel()
        _errorMessage.value = null

        try {
            val player = initializePlayer(isLive)
            val mediaItemBuilder = MediaItem.Builder().setUri(url)

            if (isLive) {
                val targetOffset = (_liveBufferSeconds * 1000L).coerceIn(2000L, 30000L)
                val liveConfig = MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(targetOffset)
                    .setMinOffsetMs((targetOffset - 1000L).coerceAtLeast(1000L))
                    .setMaxOffsetMs(targetOffset + 4000L)
                    .setMinPlaybackSpeed(0.95f)
                    .setMaxPlaybackSpeed(1.05f)
                    .build()
                mediaItemBuilder.setLiveConfiguration(liveConfig)
            }

            if (url.contains(".m3u8", ignoreCase = true)) {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            } else if (url.contains(".ts", ignoreCase = true)) {
                mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP2T)
            }

            player.setMediaItem(mediaItemBuilder.build())
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

    fun retryCurrent() {
        if (currentUrl.isNotBlank()) {
            _errorMessage.value = null
            playInternal(currentUrl, isLiveStream)
        }
    }

    fun retryNextFallback() {
        if (fallbackUrls.isNotEmpty()) {
            fallbackAttempt = (fallbackAttempt + 1) % fallbackUrls.size
            val nextUrl = fallbackUrls[fallbackAttempt]
            _errorMessage.value = null
            playInternal(nextUrl, isLive = isLiveStream)
        }
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

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    fun forward10Seconds() {
        exoPlayer?.let {
            val target = (it.currentPosition + 10_000L).coerceAtMost(it.duration)
            it.seekTo(target)
        }
    }

    fun rewind10Seconds() {
        exoPlayer?.let {
            val target = (it.currentPosition - 10_000L).coerceAtLeast(0L)
            it.seekTo(target)
        }
    }

    fun forward30Seconds() {
        exoPlayer?.let {
            val target = (it.currentPosition + 30_000L).coerceAtMost(it.duration)
            it.seekTo(target)
        }
    }

    fun rewind30Seconds() {
        exoPlayer?.let {
            val target = (it.currentPosition - 30_000L).coerceAtLeast(0L)
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
            AspectRatioMode.RATIO_16_9 -> AspectRatioMode.FIT
        }
        _aspectRatio.value = next
        return next
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        _aspectRatio.value = mode
    }

    // Canlı Yayın Gecikmesi (Milisaniye)
    private val _liveOffsetMs = MutableStateFlow(0L)
    val liveOffsetMs: StateFlow<Long> = _liveOffsetMs.asStateFlow()

    private var autoSyncLive: Boolean = true

    fun setAutoSyncLive(enabled: Boolean) {
        this.autoSyncLive = enabled
    }

    fun syncToLive(): Boolean {
        val player = exoPlayer ?: return false
        if (!isLiveStream || currentUrl.isBlank()) return false

        // Hedef tamponu 2 saniyeye sabitle
        _liveBufferSeconds = 2
        liveBufferSecondsState.value = 2

        val url = currentUrl
        val mediaItemBuilder = MediaItem.Builder().setUri(url)
        val liveConfig = MediaItem.LiveConfiguration.Builder()
            .setTargetOffsetMs(2000L)
            .setMinOffsetMs(1000L)
            .setMaxOffsetMs(3000L)
            .setMinPlaybackSpeed(0.98f)
            .setMaxPlaybackSpeed(1.10f)
            .build()
        mediaItemBuilder.setLiveConfiguration(liveConfig)

        if (url.contains(".m3u8", ignoreCase = true)) {
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
        } else if (url.contains(".ts", ignoreCase = true)) {
            mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP2T)
        }

        // resetPosition = true ile ExoPlayer HTTP soketini ve biriken 16s tamponu tamamen boşaltır
        player.setMediaItem(mediaItemBuilder.build(), /* resetPosition = */ true)
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
                exoPlayer?.let {
                    if (!isLiveStream) {
                        _currentPosition.value = it.currentPosition
                        _duration.value = it.duration.coerceAtLeast(0L)
                        _bufferedPosition.value = it.bufferedPosition
                    } else {
                        val offset = it.currentLiveOffset
                        if (offset > 0) {
                            _liveOffsetMs.value = offset
                            val syncThreshold = (_liveBufferSeconds * 1000L) + 2000L
                            if (autoSyncLive && offset > syncThreshold) {
                                it.seekToDefaultPosition()
                            }
                        }
                    }
                    // Yayın ve tampon sağlığını her saniye gerçek zamanlı güncelle
                    updateDiagnostics()
                }
                delay(1000)
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
            "az", "aze", "azerbaijani" -> "Azerbaycanca"
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
                } else if (trackType == C.TRACK_TYPE_TEXT) {
                    val base = format.label?.takeIf { it.isNotBlank() } ?: displayLang.takeIf { it.isNotBlank() } ?: "Altyazı #${formatIndex + 1}"
                    base
                } else {
                    format.label ?: "Parça #${formatIndex + 1}"
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
                    .clearOverridesOfType(trackType)
                    .setTrackTypeDisabled(trackType, false)
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

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        val loadControl = buildLoadControl(isLive = true)

        val player = ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setLoadControl(loadControl)
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
        bufferingRecoveryJob?.cancel()
        liveRetryJob?.cancel()
        liveRetryCount = 0
        cancelSleepTimer()
        stopPositionUpdates()
        exoPlayer?.release()
        exoPlayer = null
        lastInitializedIsLive = null
    }
}

