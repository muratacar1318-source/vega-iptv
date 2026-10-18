# G-038 — TV canlı: OK duraklatmasın, saat gizlensin, tampon canlı güncellensin, "Canlıya geç" düğmesi, HEVC açılış ölçümü

- **Öncelik:** 🔴
- **Agent dağılımı:** Tek agent (bölme). Gemini 3.6 Flash için adım adım — **adımların dışına çıkma, kendi fikrini ekleme**.
- **Branch:** `feat/tv-canliya-gec` (main'den aç)
- **Commit mesajı:** `feat(tv): canlıya geç düğmesi, OK duraklatmıyor, saat bilgiyle gizleniyor, tampon canlı güncelleniyor`
- **Değişecek dosyalar (SADECE bunlar):**
  1. `app/src/main/java/com/tivimax/player/ui/tv/TvPlayerOverlay.kt`
  2. `app/src/main/java/com/tivimax/player/player/TiviPlayerManager.kt`
  3. Bu görev dosyası (Agent raporu) ve `YAPILACAKLAR.md` (durum satırı)

## Kullanıcının istekleri
1. OK tuşu yayını durdurmasın.
2. Sağ üstteki saat hep ekranda kalıyor; bilgi kartıyla birlikte gizlensin.
3. Bilgi kartındaki "Tampon" değeri sadece ilk açılışta yazılıyor ve yanlış; kart açıkken canlı güncellensin.
4. Menüye **"Canlıya geç"** düğmesi (YouTube/Kick'teki gibi): yayını en güncel noktaya alsın, **3 sn** tamponla
   başlasın. Sağlayıcıdan gelen veri yetişmezse (atlamadan sonraki 60 sn içinde takılma olursa) sonraki atlamalarda
   **8 sn** tampon kullanılsın.
5. HEVC kanallar geç açılıyor → önce **nerede beklediğini ölçeceğiz** (sadece geliştirici kurulumunda ekranda görünür).

---

## Bölüm A — TiviPlayerManager.kt

**A1. Yeni alanlar.** `private var pendingRetryJob: Job? = null` satırının **hemen altına**:
```kotlin
    // G-038: "Canlıya geç" — tampon hedefi (veri yetişmezse 8 sn'ye çıkar)
    private var liveJumpTargetMs = 3_000L
    private var liveJumpPlayingSinceMs = 0L
    private var liveJumpJob: Job? = null

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
```

**A2. `playWithFallbacks` başında sıfırla.** G-037'de eklenen `isReconnecting = false` satırının (playWithFallbacks
içindeki) **hemen altına**:
```kotlin
        // G-038: Canlıya geç bekleyişi ve açılış ölçümü sıfırla
        liveJumpJob?.cancel()
        liveJumpJob = null
        liveJumpPlayingSinceMs = 0L
        dbgFormatMs = -1L
        dbgCodec = ""
        dbgDecoder = ""
        dbgFirstFrameMs = -1L
        _startupDebugInfo.value = ""
```

**A3. "Canlıya geç" fonksiyonu.** `fun syncToLive(): Boolean {` fonksiyonunun **hemen ÜSTÜNE** ekle (syncToLive'a dokunma):
```kotlin
    /**
     * G-038: Canlı yayını en güncel noktaya alır (YouTube/Kick "Canlı" düğmesi gibi).
     * Bağlantıyı yeniler, [liveJumpTargetMs] kadar tampon dolunca oynatır (en fazla hedef + 4 sn bekler).
     */
    fun jumpToLive() {
        val player = exoPlayer ?: return
        if (!isLiveStream || currentUrl.isBlank()) return
        liveJumpJob?.cancel()
        liveJumpPlayingSinceMs = 0L
        player.playWhenReady = false
        player.stop()
        player.prepare()
        val target = liveJumpTargetMs
        liveJumpJob = scope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            while (isActive) {
                val p = exoPlayer ?: return@launch
                val waited = SystemClock.elapsedRealtime() - startedAt
                if (p.totalBufferedDuration >= target || waited >= target + 4_000L) break
                delay(200L)
            }
            exoPlayer?.playWhenReady = true
            liveJumpPlayingSinceMs = SystemClock.elapsedRealtime()
            updateDiagnostics()
        }
    }
```

**A4. Veri yetişmezse tamponu büyüt.** Ana `player.addListener(object : Player.Listener {` içindeki
`onPlaybackStateChanged` → `Player.STATE_BUFFERING -> {` dalında, `_errorMessage.value = null` satırının **hemen altına**:
```kotlin
                        // G-038: Canlıya geçtikten sonraki 60 sn içinde takılma olursa sonraki atlamalarda 8 sn tampon
                        if (isLiveStream && liveJumpPlayingSinceMs > 0L &&
                            SystemClock.elapsedRealtime() - liveJumpPlayingSinceMs < 60_000L
                        ) {
                            liveJumpTargetMs = 8_000L
                            liveJumpPlayingSinceMs = 0L
                        }
```
(Dikkat: dosyada iki `STATE_BUFFERING` var. Bu, **`player.addListener(object : Player.Listener`** içindeki,
`_isBuffering.value = true` yazan dal. `timingAnalyticsListener` içindekine dokunma.)

**A5. Açılış ölçümü.** `timingAnalyticsListener` (`object : AnalyticsListener {`) içine, `override fun onRenderedFirstFrame(`
fonksiyonunun **hemen ÜSTÜNE** iki yeni fonksiyon ekle:
```kotlin
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
```
Ve mevcut `onRenderedFirstFrame` içindeki `if (BuildConfig.DEBUG) {` bloğunun **en başına** ekle:
```kotlin
                    if (dbgFirstFrameMs < 0) {
                        dbgFirstFrameMs = timingOffsetMs()
                        publishStartupDebug()
                    }
```

## Bölüm B — TvPlayerOverlay.kt

**B1. OK duraklatmasın.** OK dalında:
```kotlin
                        if (!showInfoCard) {
                            showInfoCard = true
                        } else {
                            playerManager.togglePlayPause()
                        }
```
→
```kotlin
                        // G-038: OK yayını durdurmaz; bilgi kartını açar/kapatır
                        showInfoCard = !showInfoCard
```

**B2. Saat bilgi kartıyla gizlensin.** Yorum `// SAAT` altındaki `Surface(...) { Text(text = clockText, ...) }` bloğunun
**tamamını** şu koşula al (rakam tuşlama kısmına dokunma):
```kotlin
            // SAAT (G-038: sadece bilgi kartı/menü açıkken)
            if (showInfoCard || showActionTray) {
                Surface( ... mevcut saat Surface'ı aynen ... )
            }
```

**B3. Tampon kart açıkken her saniye güncellensin.** Mevcut `LaunchedEffect(showTrackSheet) { ... }` bloğunun (G-032)
**hemen altına**:
```kotlin
    // G-038: Bilgi kartı açıkken tampon/teknik bilgileri her saniye güncelle
    LaunchedEffect(showInfoCard) {
        while (showInfoCard) {
            playerManager.updateDiagnostics()
            delay(1000L)
        }
    }
```

**B4. Açılış ölçümünü göster (sadece geliştirici kurulumu).** Diğer `collectAsState` satırlarının yanına:
```kotlin
    val startupDebugInfo by playerManager.startupDebugInfo.collectAsState()
```
G-032'de eklenen ipucu bloğunun (`if (!showActionTray) { ... "▲▼ Kanal değiştir ..." }`) **hemen ÜSTÜNE**:
```kotlin
                    if (com.tivimax.player.BuildConfig.DEBUG && startupDebugInfo.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Açılış ölçümü: $startupDebugInfo",
                            color = VegaColors.TextMuted,
                            fontSize = VegaText.Caption,
                            maxLines = 2
                        )
                    }
```

**B5. "Canlıya geç" düğmesi.** Menüdeki `PlayerPillButton(text = "Ses ve altyazı", ...)` düğmesinin **hemen ALTINA**
(ikinci düğme olarak) ekle:
```kotlin
                                PlayerPillButton(
                                    text = "Canlıya geç",
                                    icon = Icons.Default.LiveTv,
                                    onClick = {
                                        playerManager.jumpToLive()
                                        showActionTray = false
                                        seekRippleMessage = "Canlı yayına geçiliyor…"
                                    },
                                    isPortrait = false
                                )
```
(`Icons.Default.LiveTv` — `material-icons-extended` projede var; import yoksa `androidx.compose.material.icons.filled.LiveTv`.)

---

## Kontrol ve gönderim
1. `./gradlew compileDebugKotlin` → hata varsa düzelt (isim uydurmadan).
2. `./gradlew testDebugUnitTest` → tüm testler geçmeli.
3. `git diff --stat`: **sadece** yukarıdaki dosyalar. Başka dosya değiştiyse geri al.
4. Commit → `feat/tv-canliya-gec` dalına push. **Main'e birleştirme. APK üretme. Sürüm numarasını değiştirme.**
5. `YAPILACAKLAR.md`'de G-038 durumunu `🔍 İncelemede (branch: feat/tv-canliya-gec)` yap.

## DOKUNMA
Kanal geçiş mantığı (G-037), `syncToLive`, tampon ayarları (`createLoadControl`), çözücü ayarları, telefon oynatıcısı
(`PlayerScreen.kt`), güncelleme sistemi (AutoUpdateManager), imza, CI, sürüm numarası.

## Test (TV varsa — yoksa "test edilemedi" yaz)
- OK: bilgi kartı açılır/kapanır, yayın durmaz. Saat sadece kart/menü açıkken görünür.
- Kart açıkken "Tampon" değeri her saniye değişir.
- ▶ → "Canlıya geç": yayın kısa siyah ekrandan sonra ~3 sn tamponla en güncel noktadan devam eder.
- HEVC bir kanal aç → bilgi kartında "Açılış ölçümü: biçim +… · çözücü … · ilk kare +…" görünür (sadece Android Studio kurulumunda).

---

## Agent raporu

- **Yapılan değişiklikler:**
  - **Bölüm A (TiviPlayerManager.kt):**
    - **A1:** `liveJumpTargetMs` (3s), `liveJumpPlayingSinceMs`, `liveJumpJob` ve `startupDebugInfo` (`dbgFormatMs`, `dbgCodec`, `dbgDecoder`, `dbgFirstFrameMs`) alanları ve `publishStartupDebug()` fonksiyonu eklendi.
    - **A2:** `playWithFallbacks` başında canlıya geç işi iptal edildi ve açılış ölçüm sayaçları sıfırlandı.
    - **A3:** `jumpToLive()` fonksiyonu eklendi; en güncel noktaya atlayıp belirlenen tampon dolana kadar bekleyip oynatıyor.
    - **A4:** Canlıya geçişten sonraki ilk 60 saniyede `STATE_BUFFERING` olursa `liveJumpTargetMs` otomatik olarak 8 saniyeye çıkarıldı.
    - **A5:** `timingAnalyticsListener` içine `onVideoInputFormatChanged` ve `onVideoDecoderInitialized` eklenerek biçim ve çözücü süreleri kaydedildi; `onRenderedFirstFrame` içine ilk kare süresi ölçümü eklendi.
  - **Bölüm B (TvPlayerOverlay.kt):**
    - **B1:** Kumanda OK (DPAD_CENTER / ENTER) basıldığında yayının duraklatılması engellendi, sadece bilgi kartı açılıp kapanır hale getirildi (`showInfoCard = !showInfoCard`).
    - **B2:** Saat `Surface`'ı `if (showInfoCard || showActionTray)` koşuluna alınarak sadece bilgi kartı veya menü açıkken ekranda görünmesi sağlandı.
    - **B3:** `LaunchedEffect(showInfoCard)` ile bilgi kartı açıkken her 1 saniyede bir `updateDiagnostics()` çağrılarak tampon bilgisinin canlı güncellenmesi sağlandı.
    - **B4:** `startupDebugInfo` dinlendi ve hata ayıklama (debug) kurulumlarında bilgi kartının altında açılış ölçümü metni gösterildi.
    - **B5:** Menü yatay butonlarına (Action Dock) ikinci düğme olarak `LiveTv` ikonlu "Canlıya geç" düğmesi eklendi.
- **Değiştirilen dosyalar:**
  - `app/src/main/java/com/tivimax/player/player/TiviPlayerManager.kt`
  - `app/src/main/java/com/tivimax/player/ui/tv/TvPlayerOverlay.kt`
  - `docs/gorevler/G-038-tv-canli-ok-saat-tampon-canliya-gec.md`
  - `YAPILACAKLAR.md`
- **Test sonuçları:**
  - `./gradlew compileDebugKotlin`: Başarılı (`BUILD SUCCESSFUL in 13s`).
  - `./gradlew testDebugUnitTest`: Başarılı (`BUILD SUCCESSFUL in 5s`, 28 testin tümü geçti).
  - Fiziksel TV / emülatör bağlı olmadığı için ekran üzerinde manuel TV testi yapılamadı (test edilemedi).
