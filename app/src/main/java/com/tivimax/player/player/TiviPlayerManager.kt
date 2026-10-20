package com.tivimax.player.player

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import com.tivimax.player.BuildConfig
import androidx.annotation.OptIn
import androidx.core.view.doOnAttach
import androidx.media3.ui.PlayerView
import java.lang.ref.WeakReference
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
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.session.MediaSession
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import android.os.SystemClock
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
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
    val resolution: String = "",
    val frameRate: String = "",
    val videoCodec: String = "",
    val audioCodec: String = "",
    val bitrate: String = "",
    val bufferHealthSecs: String = "",
    val resolutionBadge: String = "",
    val frameRateBadge: String = "",
    val videoCodecBadge: String = "",
    val audioCodecBadge: String = ""
)

@OptIn(UnstableApi::class)
class TiviPlayerManager(private val context: Context) {

    companion object {
        private const val TAG_TIMING = "VodTiming"
        const val USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"

        fun extractHttpStatusCode(error: PlaybackException): Int? {
            var cause: Throwable? = error.cause
            while (cause != null) {
                if (cause is HttpDataSource.InvalidResponseCodeException) {
                    return cause.responseCode
                }
                cause = cause.cause
            }
            return null
        }

        fun maskUrl(url: String): String {
            if (url.isBlank()) return url
            var masked = url
            // Basic auth in authority: http://user:pass@host:port/
            masked = masked.replace(Regex("""(https?://)[^:/@]+:[^@]+@"""), "$1***:***@")
            // Xtream path format: http://host:port/(live|movie|series)/username/password/...
            masked = masked.replace(Regex("""(https?://[^/]+/(?:live|movie|series)/)[^/]+/[^/]+(/.*)?"""), "$1***/***$2")
            // Query parameters: user, username, pass, password, token
            masked = masked.replace(Regex("""(?i)(username|password|user|pass|token)=([^&]+)"""), "$1=***")
            return masked
        }

        fun getDataTypeName(dataType: Int): String = when (dataType) {
            C.DATA_TYPE_UNKNOWN -> "UNKNOWN"
            C.DATA_TYPE_MEDIA -> "MEDIA"
            C.DATA_TYPE_MEDIA_INITIALIZATION -> "INIT"
            C.DATA_TYPE_DRM -> "DRM"
            C.DATA_TYPE_MANIFEST -> "MANIFEST"
            C.DATA_TYPE_TIME_SYNCHRONIZATION -> "TIME_SYNC"
            C.DATA_TYPE_AD -> "AD"
            else -> "DATA_TYPE_$dataType"
        }
    }

    private var playerLoadControlIsLive: Boolean? = null
    @Volatile
    private var playStartElapsedMs: Long = 0L

    private var rebufferCount: Int = 0
    private var hasRenderedFirstFrameOrReady: Boolean = false
    private var firstUrlAuthRetried: Boolean = false
    private var seekStartMs: Long = 0L
    private var seekTargetMs: Long = 0L

    var onVodAuthErrorCallback: ((httpCode: Int) -> Unit)? = null
    var onVodPlaybackSuccessCallback: (() -> Unit)? = null

    private fun timingOffsetMs(): Long {
        val start = playStartElapsedMs
        return if (start == 0L) 0L else (SystemClock.elapsedRealtime() - start)
    }

    private val timingAnalyticsListener by lazy {
        object : AnalyticsListener {
            override fun onLoadStarted(
                eventTime: AnalyticsListener.EventTime,
                loadEventInfo: LoadEventInfo,
                mediaLoadData: MediaLoadData
            ) {
                if (BuildConfig.DEBUG) {
                    val reqHost = loadEventInfo.dataSpec.uri.host ?: loadEventInfo.dataSpec.uri.authority ?: "unknown"
                    val path = loadEventInfo.uri.path ?: loadEventInfo.dataSpec.uri.path ?: ""
                    val ext = path.substringBefore('?').substringAfterLast('.', "")
                    val extStr = if (ext.isNotEmpty()) ".$ext" else ""
                    Log.d(TAG_TIMING, "[+${timingOffsetMs()}ms] onLoadStarted: dataType=${getDataTypeName(mediaLoadData.dataType)}, host=$reqHost$extStr")
                }
            }

            override fun onLoadCompleted(
                eventTime: AnalyticsListener.EventTime,
                loadEventInfo: LoadEventInfo,
                mediaLoadData: MediaLoadData
            ) {
                if (BuildConfig.DEBUG) {
                    val reqHost = loadEventInfo.dataSpec.uri.host ?: loadEventInfo.dataSpec.uri.authority ?: "unknown"
                    val loadedHost = loadEventInfo.uri.host ?: loadEventInfo.uri.authority ?: reqHost
                    val path = loadEventInfo.uri.path ?: loadEventInfo.dataSpec.uri.path ?: ""
                    val ext = path.substringBefore('?').substringAfterLast('.', "")
                    val extStr = if (ext.isNotEmpty()) ".$ext" else ""
                    val redirectInfo = if (loadedHost != reqHost) " [REDIRECT: $reqHost -> $loadedHost]" else ""
                    Log.d(TAG_TIMING, "[+${timingOffsetMs()}ms] onLoadCompleted: dataType=${getDataTypeName(mediaLoadData.dataType)}, duration=${loadEventInfo.loadDurationMs}ms, bytes=${loadEventInfo.bytesLoaded}, host=$loadedHost$extStr$redirectInfo")
                }
            }

            override fun onLoadError(
                eventTime: AnalyticsListener.EventTime,
                loadEventInfo: LoadEventInfo,
                mediaLoadData: MediaLoadData,
                error: java.io.IOException,
                wasCanceled: Boolean
            ) {
                if (BuildConfig.DEBUG) {
                    val host = loadEventInfo.uri.host ?: "unknown"
                    val path = loadEventInfo.uri.path ?: ""
                    val ext = path.substringBefore('?').substringAfterLast('.', "")
                    val extStr = if (ext.isNotEmpty()) ".$ext" else ""
                    Log.d(TAG_TIMING, "[+${timingOffsetMs()}ms] onLoadError: dataType=${getDataTypeName(mediaLoadData.dataType)}, host=$host$extStr, error=${error.message}, canceled=$wasCanceled")
                }
            }

            override fun onPlaybackStateChanged(
                eventTime: AnalyticsListener.EventTime,
                state: Int
            ) {
                if (BuildConfig.DEBUG) {
                    when (state) {
                        Player.STATE_BUFFERING -> {
                            val offset = timingOffsetMs()
                            if (hasRenderedFirstFrameOrReady && offset <= 60_000L) {
                                rebufferCount++
                                Log.d(TAG_TIMING, "[+${offset}ms] rebuffer#$rebufferCount at +${offset}ms")
                            } else {
                                Log.d(TAG_TIMING, "[+${offset}ms] onPlaybackStateChanged: BUFFERING")
                            }
                        }
                        Player.STATE_READY -> Log.d(TAG_TIMING, "[+${timingOffsetMs()}ms] onPlaybackStateChanged: READY")
                        Player.STATE_ENDED -> Log.d(TAG_TIMING, "[+${timingOffsetMs()}ms] onPlaybackStateChanged: ENDED")
                        Player.STATE_IDLE -> Log.d(TAG_TIMING, "[+${timingOffsetMs()}ms] onPlaybackStateChanged: IDLE")
                    }
                }
            }

            override fun onVideoInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: androidx.media3.common.Format,
                decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?
            ) {
                if (BuildConfig.DEBUG && dbgFormatMs < 0) {
                    dbgFormatMs = timingOffsetMs()
                    dbgCodec = format.sampleMimeType ?: ""
                    publishStartupDebug()
                }
            }

            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
            ) {
                if (BuildConfig.DEBUG) {
                    dbgDecoder = "$decoderName (${initializationDurationMs}ms)"
                    publishStartupDebug()
                }
            }

            override fun onRenderedFirstFrame(
                eventTime: AnalyticsListener.EventTime,
                output: Any,
                renderTimeMs: Long
            ) {
                if (BuildConfig.DEBUG) {
                    if (dbgFirstFrameMs < 0) {
                        dbgFirstFrameMs = timingOffsetMs()
                        publishStartupDebug()
                    }
                    val totalMs = timingOffsetMs()
                    Log.d(TAG_TIMING, "[+${totalMs}ms] ★★★ onRenderedFirstFrame: İLK KARE ÇİZİLDİ! (Toplam açılış süresi: ${totalMs}ms, renderTime: ${renderTimeMs}ms) ★★★")
                }
            }

            override fun onPlayerError(
                eventTime: AnalyticsListener.EventTime,
                error: PlaybackException
            ) {
                if (BuildConfig.DEBUG) {
                    val nextFallback = if (fallbackAttempt + 1 < fallbackUrls.size) {
                        "Fallback denenecek: index=${fallbackAttempt + 1}/${fallbackUrls.size}"
                    } else {
                        "Başka fallback kalmadı (${fallbackAttempt + 1}/${fallbackUrls.size})"
                    }
                    Log.d(TAG_TIMING, "[+${timingOffsetMs()}ms] onPlayerError: [${error.errorCodeName}] (${error.errorCode}) ${error.message}. $nextFallback")
                }
            }
        }
    }

    private var exoPlayer: ExoPlayer? = null
    val player: ExoPlayer? get() = exoPlayer

    private var activePlayerView: WeakReference<PlayerView>? = null
    private var isCurrentOwnerPrimary: Boolean = false
    private var pendingSecondaryView: WeakReference<PlayerView>? = null

    /**
     * Video yüzeyini tek sahipli olarak yönetir.
     * Tam ekran oynatıcılar (isPrimary = true), önizleme görünümlerine göre önceliklidir.
     */
    fun attachView(view: PlayerView, isPrimary: Boolean = false, tag: String = "") {
        val oldView = activePlayerView?.get()
        if (oldView == null) {
            isCurrentOwnerPrimary = false
        }

        // Öncelik kuralı: tam ekran oynatıcı açıkken önizleme görünümleri yüzeyi ALAMAZ
        if (!isPrimary && isCurrentOwnerPrimary && oldView != null && oldView != view) {
            pendingSecondaryView = WeakReference(view)
            if (BuildConfig.DEBUG) {
                Log.d("VideoTarget", "[+${timingOffsetMs()}ms] attachView REDDEDİLDİ: tag='$tag' isPrimary=false ancak aktif sahip zaten tam ekran (oldView=$oldView). Pending olarak kaydedildi.")
            }
            return
        }

        if (pendingSecondaryView?.get() == view || isPrimary) {
            pendingSecondaryView = null
        }

        val currentPlayer = exoPlayer ?: initializePlayer()

        if (BuildConfig.DEBUG) {
            Log.d("VideoTarget", "[+${timingOffsetMs()}ms] attachView: tag='$tag', isPrimary=$isPrimary, oldView=$oldView, newView=$view, player=$currentPlayer")
        }

        if (oldView != null && oldView != view) {
            PlayerView.switchTargetView(currentPlayer, oldView, view)
        } else {
            if (view.player != currentPlayer) {
                view.player = currentPlayer
            }
        }

        activePlayerView = WeakReference(view)
        isCurrentOwnerPrimary = isPrimary
    }

    fun detachView(view: PlayerView, tag: String = "") {
        val currentOwner = activePlayerView?.get()
        if (BuildConfig.DEBUG) {
            Log.d("VideoTarget", "[+${timingOffsetMs()}ms] detachView: tag='$tag', view=$view, currentOwner=$currentOwner, isOwner=${currentOwner == view}")
        }

        // Bir önizleme görünümü detachView ile ayrılırken pending ise, pending'i temizle
        if (pendingSecondaryView?.get() == view) {
            pendingSecondaryView = null
        }

        // SADECE görünüm o anki sahipse view.player = null ve sahibi temizle
        if (currentOwner == view || currentOwner == null) {
            val wasPrimary = isCurrentOwnerPrimary
            if (currentOwner == view) {
                view.player = null
            }
            activePlayerView = null
            isCurrentOwnerPrimary = false

            // Ayrılan görünüm primary sahip ise ve pendingSecondaryView hâlâ geçerliyse yüzeyi hemen ona devret
            if (wasPrimary) {
                val pending = pendingSecondaryView?.get()
                pendingSecondaryView = null
                if (pending != null) {
                    if (pending.isAttachedToWindow) {
                        if (BuildConfig.DEBUG) {
                            Log.d("VideoTarget", "[+${timingOffsetMs()}ms] detachView: pending önizlemeye devredildi (pending=$pending)")
                        }
                        attachView(pending, isPrimary = false, tag = "pending-handover")
                    } else {
                        pending.doOnAttach {
                            if (isCurrentOwnerPrimary && activePlayerView?.get() != null) {
                                if (BuildConfig.DEBUG) {
                                    Log.d("VideoTarget", "[+${timingOffsetMs()}ms] detachView: pending pencereye eklendi ancak aktif sahip primary, devredilmedi (pending=$it)")
                                }
                                return@doOnAttach
                            }
                            if (BuildConfig.DEBUG) {
                                Log.d("VideoTarget", "[+${timingOffsetMs()}ms] detachView: pending pencereye eklenince devredildi (pending=$it)")
                            }
                            attachView(it as PlayerView, isPrimary = false, tag = "pending-handover-onAttach")
                        }
                    }
                }
            }
        } else {
            if (BuildConfig.DEBUG) {
                Log.d("VideoTarget", "[+${timingOffsetMs()}ms] detachView YOK SAYILDI: tag='$tag', bu view artık sahip değil (sahip=$currentOwner)")
            }
        }
    }

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

    private var smartSubtitleAppliedForUrl: String? = null
    private var preferredSubtitleLang: String = "auto"

    // Stream Diagnostics HUD (TiviMate Bilgi Paneli)
    private val _streamDiagnostics = MutableStateFlow(StreamDiagnostics())
    val streamDiagnostics: StateFlow<StreamDiagnostics> = _streamDiagnostics.asStateFlow()

    // Uyku Zamanlayıcısı
    private val _sleepTimerSeconds = MutableStateFlow<Int?>(null)
    val sleepTimerSeconds: StateFlow<Int?> = _sleepTimerSeconds.asStateFlow()
    private var sleepTimerJob: Job? = null

    // Ses Senkronizasyon Kayması (Milisaniye) - Uygulanmıyor: arayüzden kaldırıldı
    // Uygulanmıyor: arayüzden kaldırıldı
    private val _audioOffsetMs = MutableStateFlow(0L)
    val audioOffsetMs: StateFlow<Long> = _audioOffsetMs.asStateFlow()

    // Altyazı Gecikmesi (Milisaniye, 0..5000 ms, 100 ms adımlı)
    private val _subtitleDelayMs = MutableStateFlow(0L)
    val subtitleDelayMs: StateFlow<Long> = _subtitleDelayMs.asStateFlow()

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

    private var _vodBufferSeconds: Int = 180
    val vodBufferSecondsState = MutableStateFlow(180)

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

    val baseOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }
    private val okHttpClient: OkHttpClient get() = baseOkHttpClient

    fun createDataSourceFactory(): DataSource.Factory {
        val okHttpDataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(baseOkHttpClient)
            .setUserAgent(USER_AGENT)
        return DefaultDataSource.Factory(context, okHttpDataSourceFactory)
    }

    private class VodLoadErrorHandlingPolicy(
        private val minRetryCount: Int = 4
    ) : DefaultLoadErrorHandlingPolicy(minRetryCount) {

        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            val code = findResponseCode(loadErrorInfo.exception)
            if (code != null) {
                if (code == 429 || code == 503 || code == 509 || code in 500..599) {
                    return when (loadErrorInfo.errorCount) {
                        1 -> 400L
                        2 -> 800L
                        3 -> 1500L
                        else -> 2500L
                    }
                }
                if (code == 403 || code == 404) {
                    return C.TIME_UNSET
                }
            }
            return super.getRetryDelayMsFor(loadErrorInfo)
        }

        override fun getMinimumLoadableRetryCount(dataType: Int): Int = minRetryCount

        private fun findResponseCode(throwable: Throwable?): Int? {
            var current = throwable
            while (current != null) {
                if (current is HttpDataSource.InvalidResponseCodeException) {
                    return current.responseCode
                }
                current = current.cause
            }
            return null
        }
    }

    private fun createLoadControl(isLive: Boolean): DefaultLoadControl {
        return if (isLive) {
            val liveBufferForPlayback = if (liveBufferSecondsState.value <= 2) 800 else liveBufferSecondsState.value * 1000
            // G-042: Takılmadan sonra en az 2,5 sn tamponla devam et (art arda takılmayı önler)
            val liveBufferForPlaybackAfterRebuffer = maxOf(2_500, if (liveBufferSecondsState.value <= 2) 1200 else liveBufferSecondsState.value * 1000 + 500)
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
            // G-045: Uzun ön yükleme cihaz belleğini aşmasın — bellek sınırına gelince süre dolmasa da durur
            val vodTargetBufferBytes = (Runtime.getRuntime().maxMemory() / 4)
                .coerceIn(32L * 1024 * 1024, 200L * 1024 * 1024)
                .toInt()
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ vodMinBufferMs,
                    /* maxBufferMs = */ vodMaxBufferMs,
                    /* bufferForPlaybackMs = */ 1_500,
                    /* bufferForPlaybackAfterRebufferMs = */ 2_500
                )
                .setTargetBufferBytes(vodTargetBufferBytes)
                .setPrioritizeTimeOverSizeThresholds(false)
                .setBackBuffer(20_000, true)
                .build()
        }
    }

    private var decoderRecoveryAttempt = 0
    private var networkRecoveryAttempt = 0
    private var isReconnecting = false
    // G-037: Planlanmış yeniden deneme işi (yeni kanal başlarken iptal edilir)
    private var pendingRetryJob: Job? = null

    // G-044: Canlı yayın takılma bekçisi — "yükleniyor"da uzun kalırsa aynı kanala yeniden bağlanır
    private var liveStallJob: Job? = null
    private var liveStallRecoveries = 0

    private fun cancelLiveStallWatchdog() {
        liveStallJob?.cancel()
        liveStallJob = null
    }

    private fun armLiveStallWatchdog() {
        cancelLiveStallWatchdog()
        if (!isLiveStream) return
        val urlAtArm = currentPlayingUrl ?: return
        val timeoutMs = if (hasRenderedFirstFrameOrReady) 10_000L else 20_000L
        liveStallJob = scope.launch {
            delay(timeoutMs)
            val p = exoPlayer ?: return@launch
            if (!isLiveStream || currentPlayingUrl != urlAtArm) return@launch
            if (p.playbackState != Player.STATE_BUFFERING || !p.playWhenReady) return@launch
            if (isReconnecting) return@launch
            if (liveStallRecoveries >= 3) {
                _errorMessage.value = "Kanal şu an yanıt vermiyor. Biraz sonra tekrar dene ya da başka bir kanal aç."
                return@launch
            }
            liveStallRecoveries++
            android.util.Log.w("VegaIPTV", "Canlı yayın ${timeoutMs}ms takıldı, yeniden bağlanılıyor (Deneme $liveStallRecoveries)")
            _errorMessage.value = "Yayın yeniden bağlanıyor... ($liveStallRecoveries/3)"
            p.stop()
            p.seekToDefaultPosition()
            p.prepare()
            p.play()
        }
    }

    // G-038: Açılış ölçümü (sadece geliştirici kurulumunda gösterilir)
    private var dbgFormatMs = -1L
    private var dbgCodec = ""
    private var dbgDecoder = ""
    private var dbgFirstFrameMs = -1L
    private val _startupDebugInfo = MutableStateFlow("")
    val startupDebugInfo: StateFlow<String> = _startupDebugInfo.asStateFlow()

    private fun publishStartupDebug() {
        val parts = mutableListOf<String>()
        if (dbgFormatMs >= 0) parts.add("biçim +${dbgFormatMs}ms ${dbgCodec.substringAfter('/')}")
        if (dbgDecoder.isNotBlank()) parts.add("çözücü $dbgDecoder")
        if (dbgFirstFrameMs >= 0) parts.add("ilk kare +${dbgFirstFrameMs}ms")
        _startupDebugInfo.value = parts.joinToString(" · ")
    }

    fun initializePlayer(isLive: Boolean = isLiveStream): ExoPlayer {
        if (exoPlayer != null) {
            if (BuildConfig.DEBUG) {
                val createdWithLive = playerLoadControlIsLive
                val warning = if (createdWithLive != null && createdWithLive != isLive) {
                    " -> DİKKAT: Player createdWithLive=$createdWithLive ile oluşturulmuş, şu an istenen isLive=$isLive! (LoadControl: ${if (createdWithLive) "CANLI" else "VOD"})"
                } else {
                    " (LoadControl: ${if (createdWithLive == true) "CANLI" else "VOD"})"
                }
                Log.d(TAG_TIMING, "[+${timingOffsetMs()}ms] initializePlayer: Mevcut player tekrar kullanılıyor (reused=true, createdWithLive=$createdWithLive, requestedIsLive=$isLive)$warning")
            }
            return exoPlayer!!
        }

        playerLoadControlIsLive = isLive
        if (BuildConfig.DEBUG) {
            Log.d(TAG_TIMING, "[+${timingOffsetMs()}ms] initializePlayer: Yeni player oluşturuldu (createdWithLive=$isLive, requestedIsLive=$isLive, LoadControl=${if (isLive) "CANLI" else "VOD"})")
        }

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setEnableDecoderFallback(true)
            setExtensionRendererMode(if (_decoderMode == "SOFTWARE") DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        }

        // G-042: TS yayınlarda IDR olmayan anahtar karelerden de başlayabilsin (kanal açılışı hızlanır)
        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
            .setTsExtractorFlags(androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES)
        val mediaSourceFactory = DefaultMediaSourceFactory(createDataSourceFactory(), extractorsFactory)
        if (!isLive) {
            mediaSourceFactory.setLoadErrorHandlingPolicy(VodLoadErrorHandlingPolicy(4))
        }

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

        player.addAnalyticsListener(timingAnalyticsListener)

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        _isBuffering.value = true
                        _errorMessage.value = null
                        armLiveStallWatchdog() // G-044
                    }
                    Player.STATE_READY -> {
                        cancelLiveStallWatchdog() // G-044
                        liveStallRecoveries = 0
                        if (isLiveStream) {
                            player.setSeekParameters(SeekParameters.DEFAULT)
                        }
                        hasRenderedFirstFrameOrReady = true
                        _isBuffering.value = false
                        _duration.value = if (isLiveStream) 0L else player.duration.coerceAtLeast(0L)
                        _errorMessage.value = null
                        onVodPlaybackSuccessCallback?.invoke()
                        updateDiagnostics()
                        if (BuildConfig.DEBUG && !isLiveStream && seekStartMs > 0L) {
                            val duration = System.currentTimeMillis() - seekStartMs
                            Log.d(TAG_TIMING, "[VodTiming] Sarma tamamlandı: Hedef=${seekTargetMs}ms, Oynatma başlangıcı=${duration}ms")
                            seekStartMs = 0L
                        }
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
                        cancelLiveStallWatchdog() // G-044
                    }
                }
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                updateDiagnostics()
            }

            override fun onRenderedFirstFrame() {
                hasRenderedFirstFrameOrReady = true
                decoderRecoveryAttempt = 0
                networkRecoveryAttempt = 0
                isReconnecting = false
                onVodPlaybackSuccessCallback?.invoke()
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
                cancelLiveStallWatchdog() // G-044: hata akışı kendi yeniden denemesini yapar
                android.util.Log.e("VegaIPTV", "Player error: ${error.errorCodeName} (${error.errorCode})", error)

                val httpStatusCode = extractHttpStatusCode(error)

                // SADECE isLive == false iken: İlk URL HTTP 403, 429 veya 509 ile başarısız olursa,
                // sonraki fallback uzantısına geçmeden ÖNCE aynı URL'yi 1.500 ms bekleyip bir kez daha dene.
                if (!isLiveStream && httpStatusCode != null && (httpStatusCode in listOf(403, 429, 509))) {
                    onVodAuthErrorCallback?.invoke(httpStatusCode)
                    if (fallbackAttempt == 0 && !firstUrlAuthRetried && fallbackUrls.isNotEmpty()) {
                        firstUrlAuthRetried = true
                        val currentPos = exoPlayer?.currentPosition ?: 0L
                        val sameUrl = fallbackUrls[0]
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG_TIMING, "[+${timingOffsetMs()}ms] İlk VOD URL'si HTTP $httpStatusCode aldı (tek bağlantı emniyeti). 1500ms beklenip aynı URL tekrar deneniyor...")
                        }
                        pendingRetryJob = scope.launch {
                            delay(1500L)
                            playInternal(sameUrl, isLive = false, startPositionMs = currentPos)
                        }
                        return
                    }
                }

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
                if ((error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) &&
                    (isLiveStream || (fallbackUrls.isEmpty() && httpStatusCode !in listOf(403, 429, 509)))) {
                    
                    if (networkRecoveryAttempt < 3 && !isReconnecting) {
                        isReconnecting = true
                        networkRecoveryAttempt++
                        val currentPos = exoPlayer?.currentPosition ?: 0L
                        val backoffDelay = when (networkRecoveryAttempt) {
                            1 -> 1500L
                            2 -> 3000L
                            else -> 5000L
                        }
                        
                        _errorMessage.value = if (isLiveStream && httpStatusCode != null && httpStatusCode in listOf(403, 429, 456, 458, 509)) {
                            "Sağlayıcı meşgul, kanal açılıyor... ($networkRecoveryAttempt/3)"
                        } else {
                            "Yayın yeniden bağlanıyor... ($networkRecoveryAttempt/3)"
                        }
                        val urlAtSchedule = currentPlayingUrl

                        pendingRetryJob = scope.launch {
                            delay(backoffDelay)
                            // G-037: Bu arada başka kanala geçildiyse eski denemeyi yapma
                            if (currentPlayingUrl != urlAtSchedule) {
                                isReconnecting = false
                                return@launch
                            }
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
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG_TIMING, "[+${timingOffsetMs()}ms] Fallback geçişi yapılıyor -> deneme=$fallbackAttempt/${fallbackUrls.size}")
                    }
                    playInternal(nextUrl, isLive = isLiveStream, startPositionMs = currentPos)
                    return
                }

                // G-046: Kullanıcıya teknik kod gösterme; sade Türkçe mesaj
                val errorDesc = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                        "Bu yayın şu an açılamıyor. Biraz sonra tekrar dene ya da başka bir içerik seç."
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                        "Sunucuya ulaşılamıyor. İnternetini kontrol edip tekrar dene."
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
                        "Bu yayının biçimi cihazında desteklenmiyor."
                    else -> "Yayın oynatılamıyor. Biraz sonra tekrar dene."
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

        // Eğer önceden kayıtlı bir view varsa yeni player'ı ona bağla
        activePlayerView?.get()?.let { currentView ->
            if (currentView.player != player) {
                currentView.player = player
            }
        }
        
        // OS Entegrasyonu: PiP modu ve kilit ekranı oynatma kontrolleri için MediaSession
        val session = mediaSession
        if (session == null) {
            try {
                mediaSession = MediaSession.Builder(context, player)
                    .setId("vega_${System.identityHashCode(this)}_${SystemClock.elapsedRealtime()}")
                    .build()
            } catch (e: Exception) {
                Log.w("VegaIPTV", "MediaSession kurulamadı; oynatma devam ediyor", e)
            }
        } else if (session.player !== player) {
            session.player = player   // yeni ExoPlayer'a bağla (kapatılmış oynatıcıda kalmasın)
        }

        return player
    }

    private var fallbackUrls = mutableListOf<String>()
    private var fallbackAttempt = 0

    fun playWithFallbacks(urls: List<String>, isLive: Boolean = false, startPositionMs: Long = 0L, t0Ms: Long = 0L) {
        playStartElapsedMs = if (t0Ms > 0L) t0Ms else SystemClock.elapsedRealtime()
        rebufferCount = 0
        hasRenderedFirstFrameOrReady = false
        firstUrlAuthRetried = false
        // G-037: Önceki kanaldan kalan yeniden deneme işlerini iptal et, sayaçları sıfırla
        pendingRetryJob?.cancel()
        pendingRetryJob = null
        cancelLiveStallWatchdog() // G-044
        liveStallRecoveries = 0
        networkRecoveryAttempt = 0
        decoderRecoveryAttempt = 0
        isReconnecting = false
        // G-038: Açılış ölçümü sıfırla
        dbgFormatMs = -1L
        dbgCodec = ""
        dbgDecoder = ""
        dbgFirstFrameMs = -1L
        _startupDebugInfo.value = ""
        if (BuildConfig.DEBUG) {
            val masked = urls.map { maskUrl(it) }
            Log.d(TAG_TIMING, "[+${timingOffsetMs()}ms] playWithFallbacks: isLive=$isLive, urlCount=${urls.size}, startPositionMs=$startPositionMs, urls=$masked")
        }
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
            if (BuildConfig.DEBUG) {
                Log.d(TAG_TIMING, "[+${timingOffsetMs()}ms] playInternal: Aynı URL zaten oynatılıyor, çağrı atlandı.")
            }
            return
        }

        if (BuildConfig.DEBUG) {
            val ext = url.substringBefore('?').substringAfterLast('.', "")
            val extStr = if (ext.isNotEmpty()) ".$ext" else "no_ext"
            Log.d(TAG_TIMING, "[+${timingOffsetMs()}ms] playInternal: Fallback[$fallbackAttempt/${fallbackUrls.size}], uzantı=$extStr, url=${maskUrl(url)}")
        }

        this.currentUrl = url
        this.currentPlayingUrl = url
        this.isLiveStream = isLive
        this.smartSubtitleAppliedForUrl = null
        _errorMessage.value = null
        _streamDiagnostics.value = StreamDiagnostics()

        try {
            val player = initializePlayer(isLive)
            player.stop()
            player.clearMediaItems()

            player.setSeekParameters(SeekParameters.DEFAULT)
            if (!isLive && startPositionMs > 0) {
                player.setSeekParameters(SeekParameters.PREVIOUS_SYNC)
            }

            val mediaItemBuilder = MediaItem.Builder().setUri(url)

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
            _errorMessage.value = "Yayın başlatılamadı. Lütfen tekrar dene."
        }
    }

    fun stopAndClear() {
        cancelLiveStallWatchdog() // G-044
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
            if (BuildConfig.DEBUG) {
                Log.d(TAG_TIMING, "[+${timingOffsetMs()}ms] retryNextFallback: fallbackAttempt=$fallbackAttempt/${fallbackUrls.size}")
            }
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
        if (!isLiveStream) {
            exoPlayer?.setSeekParameters(SeekParameters.CLOSEST_SYNC)
        }
        if (BuildConfig.DEBUG && !isLiveStream) {
            seekStartMs = System.currentTimeMillis()
            seekTargetMs = target
        }
        _currentPosition.value = target
        exoPlayer?.seekTo(target)
    }

    fun forward10Seconds() {
        exoPlayer?.let {
            if (!isLiveStream) {
                it.setSeekParameters(SeekParameters.PREVIOUS_SYNC)
            }
            val dur = it.duration
            val target = if (dur > 0) (it.currentPosition + 10_000L).coerceAtMost(dur) else it.currentPosition + 10_000L
            lastSeekTimestamp = System.currentTimeMillis()
            if (BuildConfig.DEBUG && !isLiveStream) {
                seekStartMs = System.currentTimeMillis()
                seekTargetMs = target
            }
            _currentPosition.value = target
            it.seekTo(target)
        }
    }

    fun rewind10Seconds() {
        exoPlayer?.let {
            if (!isLiveStream) {
                it.setSeekParameters(SeekParameters.PREVIOUS_SYNC)
            }
            val target = (it.currentPosition - 10_000L).coerceAtLeast(0L)
            lastSeekTimestamp = System.currentTimeMillis()
            if (BuildConfig.DEBUG && !isLiveStream) {
                seekStartMs = System.currentTimeMillis()
                seekTargetMs = target
            }
            _currentPosition.value = target
            it.seekTo(target)
        }
    }

    fun forward30Seconds() {
        exoPlayer?.let {
            if (!isLiveStream) {
                it.setSeekParameters(SeekParameters.CLOSEST_SYNC)
            }
            val dur = it.duration
            val target = if (dur > 0) (it.currentPosition + 30_000L).coerceAtMost(dur) else it.currentPosition + 30_000L
            lastSeekTimestamp = System.currentTimeMillis()
            if (BuildConfig.DEBUG && !isLiveStream) {
                seekStartMs = System.currentTimeMillis()
                seekTargetMs = target
            }
            _currentPosition.value = target
            it.seekTo(target)
        }
    }

    fun rewind30Seconds() {
        exoPlayer?.let {
            if (!isLiveStream) {
                it.setSeekParameters(SeekParameters.CLOSEST_SYNC)
            }
            val target = (it.currentPosition - 30_000L).coerceAtLeast(0L)
            lastSeekTimestamp = System.currentTimeMillis()
            if (BuildConfig.DEBUG && !isLiveStream) {
                seekStartMs = System.currentTimeMillis()
                seekTargetMs = target
            }
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
                    if (isSelected && BuildConfig.DEBUG) {
                        Log.d("SubtitleInfo", "Mevcut seçili altyazı: sampleMimeType=${format.sampleMimeType}, language=${format.language}, label=${format.label}")
                    }
                }
            }
        }

        _audioTracks.value = audios
        _subtitleTracks.value = subtitles

        // Akıllı Altyazı Eşleme Mantığı:
        // İçerik başına sadece BİR KEZ uygulanır (currentPlayingUrl != null && smartSubtitleAppliedForUrl != currentPlayingUrl).
        // Böylece kullanıcının sonradan yaptığı altyazı açma/kapama/değiştirme seçimleri asla ezilmez.
        val currentUrl = currentPlayingUrl
        if (currentUrl != null && smartSubtitleAppliedForUrl != currentUrl && audios.isNotEmpty()) {
            smartSubtitleAppliedForUrl = currentUrl
            applySmartSubtitleLogic(audios, subtitles)
        }
    }

    private fun normalizeLanguageCode(lang: String?, label: String?): String {
        val cleanLang = lang?.trim()?.lowercase() ?: ""
        val cleanLabel = label?.trim()?.lowercase() ?: ""
        return when {
            cleanLang in listOf("tr", "tur", "turkish") || cleanLabel.contains("türk") || cleanLabel.contains("turk") ||
                cleanLabel.contains("dublaj") || cleanLabel == "tr" || cleanLabel.startsWith("tr ") -> "tr"
            cleanLang in listOf("en", "eng", "english") || cleanLabel.contains("ing") || cleanLabel.contains("eng") ||
                cleanLabel.contains("ngiliz") -> "en" // G-046: "İngilizce" küçük harfe çevrilince "ing" aranamıyordu
            cleanLang in listOf("de", "deu", "ger", "german") || cleanLabel.contains("alm") || cleanLabel.contains("ger") -> "de"
            cleanLang in listOf("fr", "fra", "fre", "french") || cleanLabel.contains("fran") || cleanLabel.contains("fren") -> "fr"
            cleanLang in listOf("es", "spa", "spanish") || cleanLabel.contains("ispan") || cleanLabel.contains("span") -> "es"
            cleanLang in listOf("it", "ita", "italian") || cleanLabel.contains("ital") -> "it"
            cleanLang in listOf("ru", "rus", "russian") || cleanLabel.contains("rus") -> "ru"
            cleanLang in listOf("ar", "ara", "arabic") || cleanLabel.contains("arap") || cleanLabel.contains("arab") -> "ar"
            cleanLang.isNotBlank() && cleanLang !in listOf("und", "default") -> cleanLang
            else -> ""
        }
    }

    private fun applySmartSubtitleLogic(audios: List<TrackInfo>, subtitles: List<TrackInfo>) {
        if (subtitles.isEmpty()) return

        // Tercih "none" (Kapalı) ise akıllı kural altyazıyı AÇMASIN (sadece kapatma yönündeki kurallar çalışabilir)
        val prefNone = (preferredSubtitleLang == "none")

        val selectedAudio = audios.firstOrNull { it.isSelected } ?: audios.firstOrNull() ?: return
        val audioNorm = normalizeLanguageCode(selectedAudio.language, selectedAudio.name)

        // 1 & 2. Ses dili Türkçe ise veya seçili altyazı ile aynı dilde ise altyazıyı kapat
        if (audioNorm == "tr") {
            // Türkçe dublaj veya Türkçe ses: Altyazı kapalı gelsin
            val anySubSelected = subtitles.any { it.isSelected }
            if (anySubSelected) {
                disableSubtitles()
            }
            return
        }

        // 3. İngilizce ses seçiliyse:
        // Eğer kullanıcı ayarlarında altyazı "none" (kapalı) değilse Türkçe altyazıyı otomatik seç
        if (audioNorm == "en" && !prefNone) {
            val prefNorm = if (preferredSubtitleLang.isNotBlank() && preferredSubtitleLang != "auto") {
                normalizeLanguageCode(preferredSubtitleLang, null)
            } else {
                null
            }

            // Eğer ayarda özel bir dil istenmişse (ör. Almanca vb.) onu ara, yoksa varsayılan Türkçe
            val targetLang = if (!prefNorm.isNullOrBlank()) prefNorm else "tr"
            val selectedSub = subtitles.firstOrNull { it.isSelected }
            val subNorm = selectedSub?.let { normalizeLanguageCode(it.language, it.name) }
            if (subNorm != targetLang) {
                val targetSubtitle = subtitles.firstOrNull { normalizeLanguageCode(it.language, it.name) == targetLang }
                if (targetSubtitle != null) {
                    selectTrackInternal(targetSubtitle, C.TRACK_TYPE_TEXT)
                    return
                }
            }
        }

        // Genel kural: Ses dili ve altyazı dili aynı ise altyazıyı kapat
        val selectedSub = subtitles.firstOrNull { it.isSelected }
        if (selectedSub != null) {
            val subNorm = normalizeLanguageCode(selectedSub.language, selectedSub.name)
            if (audioNorm.isNotBlank() && audioNorm == subNorm) {
                disableSubtitles()
            }
        }
    }

    private fun selectTrackInternal(trackInfo: TrackInfo, trackType: Int) {
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

            if (trackType == C.TRACK_TYPE_TEXT) {
                _subtitleTracks.value = _subtitleTracks.value.map {
                    it.copy(isSelected = (it.groupIndex == trackInfo.groupIndex && it.formatIndex == trackInfo.formatIndex))
                }
            } else if (trackType == C.TRACK_TYPE_AUDIO) {
                _audioTracks.value = _audioTracks.value.map {
                    it.copy(isSelected = (it.groupIndex == trackInfo.groupIndex && it.formatIndex == trackInfo.formatIndex))
                }
            }
        }
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
                // Kullanıcı ses dilini değiştirdiğinde akıllı altyazı kuralını hemen uygula
                applySmartSubtitleLogic(_audioTracks.value, _subtitleTracks.value)
            } else if (trackType == C.TRACK_TYPE_TEXT) {
                if (BuildConfig.DEBUG) {
                    val format = group.mediaTrackGroup.getFormat(trackInfo.formatIndex)
                    Log.d("SubtitleInfo", "Altyazı seçildi: sampleMimeType=${format.sampleMimeType}, language=${format.language}, label=${format.label}")
                }
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
        this.preferredSubtitleLang = subtitleLang
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
            else -> ""
        }

        val resText = if (height > 0) {
            if (width > 0 && resBadge.isNotBlank()) "${width}x${height} ($resBadge)"
            else if (width > 0) "${width}x${height}"
            else resBadge
        } else {
            ""
        }

        val fpsVal = vf?.frameRate ?: 0f
        val fpsBadge = if (fpsVal > 0f) "${fpsVal.toInt()} FPS" else ""

        val vMime = (vf?.sampleMimeType ?: "").lowercase()
        val vCodecBadge = when {
            vMime.isBlank() -> ""
            vMime.contains("hevc") || vMime.contains("h265") -> "HEVC"
            vMime.contains("avc") || vMime.contains("h264") -> "H.264"
            vMime.contains("vp9") -> "VP9"
            vMime.contains("av01") || vMime.contains("av1") -> "AV1"
            else -> vMime.substringAfterLast('/').uppercase()
        }

        val aMime = (af?.sampleMimeType ?: "").lowercase()
        val aCodecBadge = when {
            aMime.isBlank() -> ""
            aMime.contains("ac-3") || aMime.contains("ac3") -> "Dolby AC3"
            aMime.contains("eac3") || aMime.contains("e-ac-3") -> "Dolby E-AC3"
            aMime.contains("dts") -> "DTS"
            aMime.contains("mp4a") || aMime.contains("aac") -> "AAC"
            aMime.contains("opus") -> "Opus"
            else -> aMime.substringAfterLast('/').uppercase()
        }

        val bitrate = if (vf != null && vf.bitrate > 0) String.format(java.util.Locale.US, "%.1f Mbps", vf.bitrate / 1_000_000f) else ""
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

    // Uygulanmıyor: arayüzden kaldırıldı
    fun setAudioOffset(offsetMs: Long) {
        _audioOffsetMs.value = offsetMs
    }

    fun setSubtitleDelay(delayMs: Long) {
        _subtitleDelayMs.value = delayMs.coerceIn(0L, 5000L)
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
        cancelLiveStallWatchdog() // G-044
        closeDualView()
        cancelSleepTimer()
        stopPositionUpdates()
        currentPlayingUrl = null
        playerLoadControlIsLive = null
        mediaSession?.release()
        mediaSession = null
        exoPlayer?.let {
            it.stop()
            it.clearMediaItems()
            it.release()
        }
        exoPlayer = null
        activePlayerView = null
        isCurrentOwnerPrimary = false
        pendingSecondaryView = null
        _isBuffering.value = false
        _isPlaying.value = false
    }
}
