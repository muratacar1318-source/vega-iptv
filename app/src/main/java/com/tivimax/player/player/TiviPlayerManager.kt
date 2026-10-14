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

private class TiviDynamicLoadControl(
    private val getActiveControl: () -> DefaultLoadControl
) : LoadControl {
    override fun getAllocator(): androidx.media3.exoplayer.upstream.Allocator {
        return getActiveControl().allocator
    }

    override fun onPrepared() {
        getActiveControl().onPrepared()
    }

    override fun onTracksSelected(
        timeline: androidx.media3.common.Timeline,
        mediaPeriodId: androidx.media3.exoplayer.source.MediaSource.MediaPeriodId,
        renderers: Array<androidx.media3.exoplayer.Renderer>,
        trackGroups: androidx.media3.exoplayer.source.TrackGroupArray,
        trackSelections: Array<androidx.media3.exoplayer.trackselection.ExoTrackSelection>
    ) {
        getActiveControl().onTracksSelected(timeline, mediaPeriodId, renderers, trackGroups, trackSelections)
    }

    override fun onStopped() {
        getActiveControl().onStopped()
    }

    override fun onReleased() {
        getActiveControl().onReleased()
    }

    override fun getBackBufferDurationUs(): Long {
        return getActiveControl().backBufferDurationUs
    }

    override fun retainBackBufferFromKeyframe(): Boolean {
        return getActiveControl().retainBackBufferFromKeyframe()
    }

    override fun shouldContinueLoading(
        playbackPositionUs: Long,
        bufferedDurationUs: Long,
        playbackSpeed: Float
    ): Boolean {
        return getActiveControl().shouldContinueLoading(playbackPositionUs, bufferedDurationUs, playbackSpeed)
    }

    override fun shouldStartPlayback(
        timeline: androidx.media3.common.Timeline,
        mediaPeriodId: androidx.media3.exoplayer.source.MediaSource.MediaPeriodId,
        bufferedDurationUs: Long,
        playbackSpeed: Float,
        rebuffering: Boolean,
        targetLiveOffsetUs: Long
    ): Boolean {
        return getActiveControl().shouldStartPlayback(
            timeline, mediaPeriodId, bufferedDurationUs, playbackSpeed, rebuffering, targetLiveOffsetUs
        )
    }
}

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
    val bufferHealthSecs: String = "6.4s"
)

@OptIn(UnstableApi::class)
class TiviPlayerManager(private val context: Context) {

    private var exoPlayer: ExoPlayer? = null
    private val trackSelector = DefaultTrackSelector(context)

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

    private var currentUrl: String = ""
    private var isLiveStream: Boolean = false
    private var positionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _bufferedPosition = MutableStateFlow(0L)
    val bufferedPosition: StateFlow<Long> = _bufferedPosition.asStateFlow()

    private val sharedAllocator = androidx.media3.exoplayer.upstream.DefaultAllocator(
        true,
        androidx.media3.common.C.DEFAULT_BUFFER_SEGMENT_SIZE
    )

    private var _vodBufferSeconds: Int = 60
    val vodBufferSecondsState = MutableStateFlow(60)

    private var _liveBufferSeconds: Int = 5
    val liveBufferSecondsState = MutableStateFlow(5)

    private var _decoderMode: String = "HARDWARE"
    val decoderModeState = MutableStateFlow("HARDWARE")

    private fun buildLiveLoadControl(seconds: Int): DefaultLoadControl {
        val validSecs = seconds.coerceIn(2, 30)
        val bufferMs = validSecs * 1000
        val maxBufferMs = (validSecs * 3 * 1000).coerceAtLeast(15_000)
        return DefaultLoadControl.Builder()
            .setAllocator(sharedAllocator)
            .setBufferDurationsMs(
                bufferMs,          // Min buffer: e.g. 5000ms
                maxBufferMs,       // Max buffer: e.g. 15000ms
                1500,              // Playback start buffer: 1500ms
                bufferMs           // Re-buffer: 5000ms
            )
            .setBackBuffer(
                bufferMs,          // Canlı yayın geri tampon
                true
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

    private var liveLoadControl: DefaultLoadControl = buildLiveLoadControl(5)

    fun setLiveBufferSeconds(seconds: Int) {
        val validSecs = seconds.coerceIn(2, 30)
        _liveBufferSeconds = validSecs
        liveBufferSecondsState.value = validSecs
        liveLoadControl = buildLiveLoadControl(validSecs)
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
                exoPlayer?.release()
                exoPlayer = null
                if (current.isNotBlank()) {
                    playInternal(current, isLive = isLive, startPositionMs = pos)
                }
            }
        }
    }

    private var vodLoadControl: DefaultLoadControl = buildVodLoadControl(60)

    private fun buildVodLoadControl(seconds: Int): DefaultLoadControl {
        val validSecs = seconds.coerceIn(15, 300)
        val bufferMs = validSecs * 1000
        val maxBufferMs = (validSecs * 2 * 1000).coerceAtLeast(120_000)
        return DefaultLoadControl.Builder()
            .setAllocator(sharedAllocator)
            .setBufferDurationsMs(
                bufferMs,          // minBufferMs: e.g. 60,000ms (Sürekli 60 saniye kesintisiz tampon!)
                maxBufferMs,       // maxBufferMs: e.g. 120,000ms
                2500,              // bufferForPlaybackMs: 2500ms (Hızlı oynatma başlangıcı)
                5000               // bufferForPlaybackAfterRebufferMs: 5000ms
            )
            .setBackBuffer(
                bufferMs,          // backBufferDurationMs: 60,000ms (60 saniye geri sarma RAM önbelleği!)
                true               // retainBackBufferFromKeyframe: Geri sararken tekrar indirme yapmadan anında keyframe'den oynat
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

    fun setVodBufferSeconds(seconds: Int) {
        val validSecs = seconds.coerceIn(15, 300)
        _vodBufferSeconds = validSecs
        vodBufferSecondsState.value = validSecs
        vodLoadControl = buildVodLoadControl(validSecs)
    }

    private val dynamicLoadControl = TiviDynamicLoadControl {
        if (isLiveStream) liveLoadControl else vodLoadControl
    }

    fun initializePlayer(): ExoPlayer {
        if (exoPlayer != null) return exoPlayer!!

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setEnableDecoderFallback(true) // Donanım başarısız olursa otomatik yazılıma geç
            if (_decoderMode == "SOFTWARE") {
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                setMediaCodecSelector { mimeType, requiresSecure, requiresTunneling ->
                    val decoders = androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT
                        .getDecoderInfos(mimeType, requiresSecure, requiresTunneling)
                    decoders.sortedByDescending { it.softwareOnly }
                }
            } else {
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                setMediaCodecSelector(androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT)
            }
        }

        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent("IPTVSmartersPro/3.1.5 (Linux; Android 14; Mobile; ExoPlayer)")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
            .setKeepPostFor302Redirects(true)

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(dynamicLoadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory))
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

                // Canlı yayında geride kalındığında veya ufak sunucu gecikmesinde anında 3s canlı sınıra zıpla
                if (isLiveStream && (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)) {
                    _isBuffering.value = true
                    scope.launch {
                        delay(600)
                        player.seekToDefaultPosition()
                        player.prepare()
                        player.play()
                    }
                    return
                }

                // Otomatik Alternatif Format Deneme (404 ERROR_CODE_IO_FILE_NOT_FOUND Çözümü)
                if (fallbackAttempt + 1 < fallbackUrls.size) {
                    fallbackAttempt++
                    val nextUrl = fallbackUrls[fallbackAttempt]
                    playInternal(nextUrl, isLive = isLiveStream)
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
        _errorMessage.value = null

        val player = initializePlayer()
        val mediaItemBuilder = MediaItem.Builder().setUri(url)

        if (isLive) {
            val targetOffset = (_liveBufferSeconds * 1000L).coerceAtLeast(2000L)
            val liveConfig = MediaItem.LiveConfiguration.Builder()
                .setTargetOffsetMs(targetOffset)
                .setMinOffsetMs((targetOffset - 1000L).coerceAtLeast(1000L))
                .setMaxOffsetMs(targetOffset + 3000L)
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
        if (!isLiveStream) return false
        player.seekToDefaultPosition()
        player.prepare()
        player.play()
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
                }
                delay(500)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
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
                val lang = format.language ?: "und"
                val label = format.label ?: if (lang != "und") lang.uppercase() else "Ses/Altyazı #${formatIndex + 1}"

                val info = TrackInfo(
                    id = format.id ?: "$groupIndex-$formatIndex",
                    name = label,
                    language = lang,
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
                    .setOverrideForType(
                        TrackSelectionOverride(group.mediaTrackGroup, listOf(trackInfo.formatIndex))
                    )
            )
        }
    }

    fun disableSubtitles() {
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        )
    }

    fun updateDiagnostics() {
        val player = exoPlayer ?: return
        val vf = player.videoFormat
        val af = player.audioFormat

        val res = if (vf != null && vf.width > 0) "${vf.width}x${vf.height}" else "1080p (FHD)"
        val fps = if (vf != null && vf.frameRate > 0) "${vf.frameRate.toInt()} fps" else "50 fps"
        val vCodec = vf?.sampleMimeType?.substringAfterLast('/')?.uppercase() ?: "H.264 (AVC)"
        val aCodec = af?.sampleMimeType?.substringAfterLast('/')?.uppercase() ?: "AAC Stereo"
        val bitrate = if (vf != null && vf.bitrate > 0) String.format("%.1f Mbps", vf.bitrate / 1_000_000f) else "4.8 Mbps"
        val bufferSecs = String.format("%.1fs", (player.totalBufferedDuration / 1000f).coerceAtLeast(0f))

        _streamDiagnostics.value = StreamDiagnostics(
            resolution = res,
            frameRate = fps,
            videoCodec = vCodec,
            audioCodec = aCodec,
            bitrate = bitrate,
            bufferHealthSecs = bufferSecs
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

    fun release() {
        cancelSleepTimer()
        stopPositionUpdates()
        exoPlayer?.release()
        exoPlayer = null
    }
}
