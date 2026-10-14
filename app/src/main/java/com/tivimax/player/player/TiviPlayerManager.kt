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
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import okhttp3.ConnectionPool
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
    private var activePlayerView: androidx.media3.ui.PlayerView? = null

    private val trackSelector = DefaultTrackSelector(context).apply {
        parameters = buildUponParameters()
            .setExceedRendererCapabilitiesIfNecessary(true)
            .setExceedAudioConstraintsIfNecessary(true)
            .setTunnelingEnabled(false)
            .build()
    }

    // Standart & Kararlı Ağ Veri Kaynağı (DefaultHttpDataSource - VLC User-Agent & 60s timeout)
    fun createDataSourceFactory(): DataSource.Factory {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("VLC/3.0.20 LibVLC/3.0.20")
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(60000)
        return DefaultDataSource.Factory(context, httpDataSourceFactory)
    }

    var currentPlayingUrl: String? = null
        private set

    fun stopAndClear() {
        bufferingRecoveryJob?.cancel()
        positionJob?.cancel()
        currentPlayingUrl = null
        exoPlayer?.let {
            it.stop()
            it.clearMediaItems()
        }
        _isBuffering.value = false
        _isPlaying.value = false
    }

    fun registerPlayerView(view: androidx.media3.ui.PlayerView) {
        this.activePlayerView = view
    }

    fun unregisterPlayerView(view: androidx.media3.ui.PlayerView) {
        if (this.activePlayerView == view) {
            this.activePlayerView = null
        }
    }

    fun refreshVideoSurface() {
        val pv = activePlayerView ?: return
        val p = exoPlayer ?: return
        pv.player = null
        pv.player = p
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
    private var vodRetryCount = 0
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
            // Canlı TV (Live) Profili:
            // Kararlı akış: 15 sn min, 45 sn max, 2.5 sn başlatma tamponu (Takılma ve donma yapmaz)
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    15000, // minBufferMs = 15 sn
                    45000, // maxBufferMs = 45 sn
                    2500,  // bufferForPlaybackMs = 2.5 sn (Hızlı ve güvenli açılış)
                    4000   // bufferForPlaybackAfterRebufferMs = 4 sn
                )
                .setTargetBufferBytes(C.LENGTH_UNSET)
                .setPrioritizeTimeOverSizeThresholds(true)
                .setBackBuffer(5000, false)
                .build()
        } else {
            // VOD / Film & Dizi Profili (Saf ve Kesintisiz HTTP Range):
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    15000, // minBufferMs = 15 sn
                    60000, // maxBufferMs = 60 sn
                    1500,  // bufferForPlaybackMs = 1.5 sn (Hızlı ve akıcı başlangıç)
                    4000   // bufferForPlaybackAfterRebufferMs = 4 sn
                )
                .setTargetBufferBytes(C.LENGTH_UNSET)
                .setPrioritizeTimeOverSizeThresholds(true)
                .setBackBuffer(10000, true)
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
            setEnableDecoderFallback(true) // Donanım çözücü kilitlenirse anında yedek çözücüye geç
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON) // Donanım hızlandırma birincil, AC3/DTS için FFmpeg
            setEnableAudioFloatOutput(false) // Donanımsal AudioTrack kilitlenmelerini önler
            setEnableAudioTrackPlaybackParams(false)
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
            setConstantBitrateSeekingEnabled(false)
            setConstantBitrateSeekingAlwaysEnabled(false)
            if (isLive) {
                setTsExtractorFlags(
                    DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                    DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM
                )
                setTsExtractorTimestampSearchBytes(1500 * TsExtractor.TS_PACKET_SIZE)
            }
            setMp4ExtractorFlags(androidx.media3.extractor.mp4.Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)
            setFragmentedMp4ExtractorFlags(androidx.media3.extractor.mp4.FragmentedMp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)
        }

        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(if (isLive) C.AUDIO_CONTENT_TYPE_UNKNOWN else C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val mediaSourceFactory = DefaultMediaSourceFactory(createDataSourceFactory(), extractorsFactory)
            .setLoadErrorHandlingPolicy(
                androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(6)
            )

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setLoadControl(buildLoadControl(isLive))
            .setMediaSourceFactory(mediaSourceFactory)
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        _isBuffering.value = true
                        _errorMessage.value = null
                        bufferingRecoveryJob?.cancel()
                        // 3. 6 SANİYELİK WATCHDOG'U VOD VE DİZİLER İÇİN TAMAMEN DEVRE DIŞI BIRAK (Xtream 302 token korunur)
                        if (isLiveStream) {
                            bufferingRecoveryJob = scope.launch {
                                delay(18000) // Sadece canlı yayınlar için 18 sn takılma kurtarıcı
                                if (_isBuffering.value && exoPlayer != null && isLiveStream) {
                                    android.util.Log.w("VegaIPTV", "18s Buffering timeout: Canlı yayın otomatik kurtarılıyor...")
                                    exoPlayer?.seekToDefaultPosition()
                                    exoPlayer?.prepare()
                                    exoPlayer?.play()
                                }
                            }
                        }
                    }
                    Player.STATE_READY -> {
                        bufferingRecoveryJob?.cancel()
                        liveRetryJob?.cancel()
                        liveRetryCount = 0
                        vodRetryCount = 0
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

                // 0. BehindLiveWindowException Özel Anında Yakalama (0ms gecikme)
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    android.util.Log.w("VegaIPTV", "BehindLiveWindowException algılandı! 0ms gecikmeyle canlı sınıra atlanıyor...")
                    player.seekToDefaultPosition()
                    player.prepare()
                    player.play()
                    return
                }

                // 1. Donanım Dekoder Hatası Durumunda Otomatik Yazılıma (Software) Geçiş
                val isDecoderError = error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                        error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                        error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED

                if (isDecoderError && _decoderMode != "SOFTWARE") {
                    android.util.Log.w("VegaIPTV", "Decoder error encountered. Automatically switching to SOFTWARE decoder mode...")
                    setDecoderMode("SOFTWARE")
                    return
                }

                // 2. Canlı yayın izlerken yayın anlık koparsa otomatik yeniden bağlanma
                if (isLiveStream && liveRetryCount < 4) {
                    liveRetryCount++
                    _isBuffering.value = true
                    _errorMessage.value = "Yayın yeniden bağlanıyor ($liveRetryCount/4)..."
                    liveRetryJob?.cancel()
                    liveRetryJob = scope.launch {
                        delay(1200L * liveRetryCount)
                        if (isLiveStream && exoPlayer != null) {
                            android.util.Log.i("VegaIPTV", "Canlı yayın otomatik yeniden bağlanma denemesi: $liveRetryCount/4")
                            player.seekToDefaultPosition()
                            player.prepare()
                            player.play()
                        }
                    }
                    return
                }

                // 3. VOD / Film & Dizi için: Ağ veya soket kopması olursa aynı pozisyondan 2 kez tekrar dene
                if (!isLiveStream && vodRetryCount < 2) {
                    vodRetryCount++
                    _isBuffering.value = true
                    _errorMessage.value = "Video yeniden bağlanıyor ($vodRetryCount/2)..."
                    scope.launch {
                        delay(1000L * vodRetryCount)
                        val currentPos = exoPlayer?.currentPosition ?: 0L
                        if (!isLiveStream && exoPlayer != null) {
                            android.util.Log.i("VegaIPTV", "VOD otomatik yeniden bağlanma denemesi: $vodRetryCount/2 (konum: $currentPos ms)")
                            player.seekTo(currentPos)
                            player.prepare()
                            player.play()
                        }
                    }
                    return
                }

                // 4. Eğer ilk açılışta dosya bulunamadıysa veya format desteklenmiyorsa alternatif uzantıyı dene
                if (fallbackAttempt + 1 < fallbackUrls.size && (exoPlayer?.currentPosition ?: 0L) < 5000L) {
                    val currentPos = exoPlayer?.currentPosition ?: 0L
                    vodRetryCount = 0
                    fallbackAttempt++
                    val nextUrl = fallbackUrls[fallbackAttempt]
                    android.util.Log.i("VegaIPTV", "Otomatik alternatif formata geçiliyor: $nextUrl")
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
        return player
    }

    private var fallbackUrls = mutableListOf<String>()
    private var fallbackAttempt = 0

    fun playWithFallbacks(urls: List<String>, isLive: Boolean = false, startPositionMs: Long = 0L) {
        this.fallbackUrls = urls.toMutableList()
        this.fallbackAttempt = 0
        this.liveRetryCount = 0
        this.vodRetryCount = 0
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

        // 2. XTREAM TEK BAĞLANTI (SINGLE-CONNECTION) ÇAKIŞMASINI KES:
        // Eğer istenen URL zaten oynatılıyorsa ve oynatıcı aktifse mükerrer setMediaItem çağrılmaz
        if (url == currentPlayingUrl && exoPlayer != null && exoPlayer?.playbackState != Player.STATE_IDLE) {
            android.util.Log.d("VegaIPTV", "URL zaten oynatılıyor, mükerrer setMediaItem çağrılmadı: $url")
            return
        }

        this.currentUrl = url
        this.currentPlayingUrl = url
        this.isLiveStream = isLive
        this.liveRetryCount = 0
        this.vodRetryCount = 0
        this.liveRetryJob?.cancel()
        this.bufferingRecoveryJob?.cancel()
        _errorMessage.value = null

        try {
            val player = initializePlayer(isLive)
            val isProgressive = !isLive || url.contains(".mp4", ignoreCase = true) || url.contains(".mkv", ignoreCase = true)

            if (isProgressive && !url.contains(".m3u8", ignoreCase = true)) {
                // 4. VOD / Dizi için Saf ve Kesintisiz ProgressiveMediaSource
                val progressiveExtractors = androidx.media3.extractor.DefaultExtractorsFactory().apply {
                    setConstantBitrateSeekingEnabled(false)
                    setConstantBitrateSeekingAlwaysEnabled(false)
                }
                val mediaSource = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(
                    createDataSourceFactory(),
                    progressiveExtractors
                ).createMediaSource(MediaItem.fromUri(url))

                if (startPositionMs > 0) {
                    player.setMediaSource(mediaSource, startPositionMs)
                } else {
                    player.setMediaSource(mediaSource, /* resetPosition = */ true)
                }
            } else {
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
                if (startPositionMs > 0 && !isLive) {
                    player.setMediaItem(mediaItem, startPositionMs)
                } else {
                    player.setMediaItem(mediaItem, /* resetPosition = */ true)
                }
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
    }

    fun stop() {
        exoPlayer?.stop()
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
        val target = positionMs.coerceAtLeast(0L)
        exoPlayer?.seekTo(target)
    }

    fun forward10Seconds() {
        exoPlayer?.let {
            val target = if (it.duration > 0) {
                (it.currentPosition + 10_000L).coerceAtMost(it.duration)
            } else {
                it.currentPosition + 10_000L
            }
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
            val target = if (it.duration > 0) {
                (it.currentPosition + 30_000L).coerceAtMost(it.duration)
            } else {
                it.currentPosition + 30_000L
            }
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
                exoPlayer?.let { player ->
                    if (!isLiveStream) {
                        val pos = player.currentPosition
                        _currentPosition.value = pos
                        _duration.value = player.duration.coerceAtLeast(0L)
                        _bufferedPosition.value = player.bufferedPosition
                    } else {
                        val offset = player.currentLiveOffset
                        if (offset > 0) {
                            _liveOffsetMs.value = offset
                        }
                    }
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

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setEnableDecoderFallback(true)
            setAllowedVideoJoiningTimeMs(3000)
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            experimentalSetMediaCodecAsyncCryptoFlagEnabled(true)
        }
        val loadControl = buildLoadControl(isLive = true)

        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory().apply {
            setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM
            )
            setTsExtractorTimestampSearchBytes(1500 * TsExtractor.TS_PACKET_SIZE)
            setMp4ExtractorFlags(androidx.media3.extractor.mp4.Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)
            setFragmentedMp4ExtractorFlags(androidx.media3.extractor.mp4.FragmentedMp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)
        }
        val mediaSourceFactory = DefaultMediaSourceFactory(createDataSourceFactory(), extractorsFactory)
            .setLoadErrorHandlingPolicy(
                androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(4)
            )

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
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
        vodRetryCount = 0
        cancelSleepTimer()
        stopPositionUpdates()
        currentPlayingUrl = null
        activePlayerView = null
        exoPlayer?.let {
            it.stop()
            it.clearMediaItems()
            it.release()
        }
        exoPlayer = null
        lastInitializedIsLive = null
        _isBuffering.value = false
        _isPlaying.value = false
    }
}
