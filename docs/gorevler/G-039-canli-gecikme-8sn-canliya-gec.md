# G-039 — Canlı yayın 8 sn gecikmeyle izlensin; "Canlıya geç" gerçekten canlıya getirsin

- **Öncelik:** 🔴
- **Agent dağılımı:** Tek agent (bölme). Gemini 3.6 Flash için adım adım — **adımların dışına çıkma, kendi fikrini ekleme**.
- **Branch:** `fix/canli-gecikme` (main'den aç)
- **Commit mesajı:** `fix(live): canlı gecikme hedefi 8 sn (hızlandırarak yetişme), canlıya geç düğmesi düzeltildi`
- **Değişecek dosyalar (SADECE bunlar):**
  1. `app/src/main/java/com/tivimax/player/player/TiviPlayerManager.kt`
  2. `app/src/main/java/com/tivimax/player/ui/tv/TvPlayerOverlay.kt`
  3. Bu görev dosyası (Agent raporu) ve `YAPILACAKLAR.md` (durum satırı)

## Neden G-038'deki "Canlıya geç" işe yaramadı (Claude analizi)
Kullanıcının ekranında **Tampon: 35.7 s**. Canlı yayında "ileriye doğru tamponlanmış süre", **canlıdan ne kadar
geride olduğumuzu** gösterir (gelecekteki yayın indirilemez). IPTV sunucusu her bağlantıda son ~30 sn'yi **bir anda
gönderiyor**; oynatıcı bunun en başından oynatıyor → ~30 sn geride. G-038'deki düğme bağlantıyı yeniliyordu; sunucu
yine aynı 30 sn'yi gönderdiği için hiçbir şey değişmedi. Bu tür (.ts) canlı yayında ileri **sarmak mümkün değil**.

**Çözüm (YouTube/Kick'in düşük gecikme yöntemi):** fazla tamponu **hafifçe hızlandırarak eritmek**. Ses perdesi
değişmez (oynatıcı sesi bozmadan hızlandırır).
- Her canlı kanal açılışında: tampon **8 sn'den fazlaysa 1,5×** hızla oynat, 8 sn'ye inince **normal hıza** dön.
- "Canlıya geç": **2×** hızla 8 sn'ye kadar eritir (daha hızlı yetişir).
- Güvenlik: aynı kanalda 2 kez takılma olursa o kanalda otomatik hızlandırma durur (bağlantı zayıf demektir);
  "Canlıya geç" ile kullanıcı yine elle başlatabilir.

---

## Bölüm A — TiviPlayerManager.kt

**A1.** G-038'in şu alanlarını:
```kotlin
    // G-038: "Canlıya geç" — tampon hedefi (veri yetişmezse 8 sn'ye çıkar)
    private var liveJumpTargetMs = 3_000L
    private var liveJumpPlayingSinceMs = 0L
    private var liveJumpJob: Job? = null
```
şununla **değiştir**:
```kotlin
    // G-039: Canlı gecikme hedefi — fazla tampon hızlandırılarak eritilir
    private val liveTargetAheadMs = 8_000L
    private var liveCatchUpSpeed = 1.0f
    private var liveCatchUpStalls = 0
    private var liveCatchUpBoost = false
    private val _liveBehindSecs = MutableStateFlow(0f)
    val liveBehindSecs: StateFlow<Float> = _liveBehindSecs.asStateFlow()
    private val _liveCatchingUp = MutableStateFlow(false)
    val liveCatchingUp: StateFlow<Boolean> = _liveCatchingUp.asStateFlow()

    private fun setCatchUpSpeed(player: ExoPlayer, speed: Float) {
        liveCatchUpSpeed = speed
        player.setPlaybackSpeed(speed)
        _liveCatchingUp.value = speed > 1.0f
    }

    /** G-039: Konum döngüsünde (250 ms) canlı yayın için çağrılır. */
    private fun applyLiveCatchUp(player: ExoPlayer) {
        val ahead = player.totalBufferedDuration.coerceAtLeast(0L)
        _liveBehindSecs.value = ahead / 1000f
        val autoAllowed = liveCatchUpStalls < 2
        if (!player.isPlaying || (!autoAllowed && !liveCatchUpBoost)) {
            if (liveCatchUpSpeed != 1.0f) setCatchUpSpeed(player, 1.0f)
            return
        }
        val desired = when {
            ahead <= liveTargetAheadMs -> {
                liveCatchUpBoost = false
                1.0f
            }
            liveCatchUpBoost -> 2.0f
            ahead > liveTargetAheadMs + 1_500L -> 1.5f
            liveCatchUpSpeed > 1.0f -> liveCatchUpSpeed
            else -> 1.0f
        }
        if (desired != liveCatchUpSpeed) setCatchUpSpeed(player, desired)
    }
```

**A2.** `STATE_BUFFERING` dalındaki G-038 bloğunu:
```kotlin
                        // G-038: Canlıya geçtikten sonraki 60 sn içinde takılma olursa sonraki atlamalarda 8 sn tampon
                        if (isLiveStream && liveJumpPlayingSinceMs > 0L &&
                            SystemClock.elapsedRealtime() - liveJumpPlayingSinceMs < 60_000L
                        ) {
                            liveJumpTargetMs = 8_000L
                            liveJumpPlayingSinceMs = 0L
                        }
```
şununla **değiştir**:
```kotlin
                        // G-039: Görüntü geldikten sonra takılma → hızlandırmayı say; 2 takılmada otomatik hızlandırma durur
                        if (isLiveStream && hasRenderedFirstFrameOrReady) {
                            liveCatchUpStalls++
                            liveCatchUpBoost = false
                            exoPlayer?.let { if (liveCatchUpSpeed != 1.0f) setCatchUpSpeed(it, 1.0f) }
                        }
```

**A3.** `playWithFallbacks` içindeki G-038 sıfırlama satırlarından şu üçünü:
```kotlin
        liveJumpJob?.cancel()
        liveJumpJob = null
        liveJumpPlayingSinceMs = 0L
```
şununla **değiştir** (ölçüm `dbg...` satırları aynen kalır):
```kotlin
        // G-039: Yeni yayında hızlandırma durumu sıfırla; canlıda normal hız
        if (liveCatchUpSpeed != 1.0f || isLive) {
            exoPlayer?.setPlaybackSpeed(if (isLive) 1.0f else _playbackSpeed.value)
        }
        liveCatchUpSpeed = 1.0f
        liveCatchUpStalls = 0
        liveCatchUpBoost = false
        _liveCatchingUp.value = false
        _liveBehindSecs.value = 0f
```
Yorum satırını `// G-038/G-039: Canlı durum ve açılış ölçümü sıfırla` yap.

**A4.** `fun jumpToLive()` fonksiyonunun **tamamını** (KDoc yorumu dahil) şununla değiştir:
```kotlin
    /**
     * G-039: "Canlıya geç" — fazla tamponu 2× hızla eritip 8 sn gecikmeye getirir.
     * (.ts canlı yayında ileri sarılamaz; bağlantıyı yenilemek de işe yaramaz — sunucu yine aynı tamponu gönderir.)
     */
    fun jumpToLive() {
        if (!isLiveStream) return
        liveCatchUpStalls = 0
        liveCatchUpBoost = true
    }
```

**A5.** `startPositionUpdates()` içindeki canlı dalı:
```kotlin
                    } else {
                        val offset = player.currentLiveOffset
                        if (offset > 0) {
                            _liveOffsetMs.value = offset
                        }
                    }
```
→
```kotlin
                    } else {
                        val offset = player.currentLiveOffset
                        if (offset > 0) {
                            _liveOffsetMs.value = offset
                        }
                        applyLiveCatchUp(player)
                    }
```

`syncToLive`, `setPlaybackSpeed`, `cyclePlaybackSpeed`, `createLoadControl`'e **dokunma**.

## Bölüm B — TvPlayerOverlay.kt

**B1.** Diğer `collectAsState` satırlarının yanına:
```kotlin
    val liveBehindSecs by playerManager.liveBehindSecs.collectAsState()
    val liveCatchingUp by playerManager.liveCatchingUp.collectAsState()
```

**B2. Canlı durumu rozeti.** Bilgi kartındaki rozet satırında
`if (streamDiagnostics.bufferHealthSecs.isNotBlank()) {` satırının **hemen ÜSTÜNE**:
```kotlin
                                    // G-039: Canlı durumu
                                    if (liveCatchingUp) {
                                        TechBadge(text = "⏩ Canlıya yetişiliyor", isHighlight = true)
                                    } else if (liveBehindSecs > 10f) {
                                        TechBadge(text = "${liveBehindSecs.toInt()} sn geride")
                                    } else if (liveBehindSecs > 0f) {
                                        TechBadge(text = "● CANLI", isHighlight = true)
                                    }
```

**B3. "Canlıya geç" en solda ve menü açılınca seçili.** Menü satırındaki ilk iki düğmeyi:
```kotlin
                                PlayerPillButton(
                                    text = "Ses ve altyazı",
                                    icon = Icons.Default.Subtitles,
                                    onClick = { showTrackSheet = true },
                                    isPortrait = false,
                                    modifier = Modifier.focusRequester(actionDockFocusRequester)
                                )
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
şununla **değiştir** (sıra: Canlıya geç → Ses ve altyazı; odak isteyici Canlıya geç'e taşındı; geride ise vurgulu):
```kotlin
                                PlayerPillButton(
                                    text = "Canlıya geç",
                                    icon = Icons.Default.LiveTv,
                                    onClick = {
                                        playerManager.jumpToLive()
                                        showActionTray = false
                                        seekRippleMessage = "Canlıya yetişiliyor…"
                                    },
                                    isActive = liveBehindSecs > 10f,
                                    activeColor = VegaColors.Live,
                                    isPortrait = false,
                                    modifier = Modifier.focusRequester(actionDockFocusRequester)
                                )
                                PlayerPillButton(
                                    text = "Ses ve altyazı",
                                    icon = Icons.Default.Subtitles,
                                    onClick = { showTrackSheet = true },
                                    isPortrait = false
                                )
```

---

## Kontrol ve gönderim
1. `./gradlew compileDebugKotlin` → hata varsa düzelt (isim uydurmadan).
2. `./gradlew testDebugUnitTest` → tüm testler geçmeli.
3. `git diff --stat`: **sadece** yukarıdaki dosyalar. Başka dosya değiştiyse geri al.
4. Commit → `fix/canli-gecikme` dalına push. **Main'e birleştirme. APK üretme. Sürüm numarasını değiştirme.**
5. `YAPILACAKLAR.md`'de G-039 durumunu `🔍 İncelemede (branch: fix/canli-gecikme)` yap.

## DOKUNMA
Kanal geçişi (G-037), tampon ayarları (`createLoadControl`), VOD oynatma ve hız ayarı, telefon oynatıcısı
(`PlayerScreen.kt`), güncelleme sistemi (AutoUpdateManager), imza, CI, sürüm numarası.

## Test (TV varsa — yoksa "test edilemedi" yaz)
- Canlı kanal aç, OK: rozet "⏩ Canlıya yetişiliyor", Tampon değeri her saniye azalır, ~1 dk içinde ~8 sn'ye iner,
  rozet "● CANLI" olur, görüntü normal hıza döner.
- ▶ → "Canlıya geç" (en solda, seçili gelir): daha hızlı (2×) 8 sn'ye iner.
- Ses perdesi değişmez (ince/kalın ses yok), sadece kısa süre hızlı akar.
- Film/dizi hızı ve telefon oynatıcısı etkilenmez.

---

## Agent raporu

- **Yapılan değişiklikler:**
  - **Bölüm A (TiviPlayerManager.kt):**
    - **A1:** `liveTargetAheadMs` (8 sn), `liveCatchUpSpeed`, `liveCatchUpStalls`, `liveCatchUpBoost`, `liveBehindSecs` ve `liveCatchingUp` akışları eklendi. `setCatchUpSpeed` ve `applyLiveCatchUp` fonksiyonları oluşturuldu. Tampon 8 sn'den büyükse (ve takılma sayısı < 2 ise) otomatik 1.5×, "Canlıya geç" tetiklenmişse 2.0× hızla fazla tampon eritilerek 8 sn'ye ulaşıldığında 1.0× normal hıza dönülmesi sağlandı.
    - **A2:** `STATE_BUFFERING` dalında, görüntü geldikten sonra takılma meydana gelirse `liveCatchUpStalls` artırıldı; 2 takılmada otomatik hızlandırma durdurulup normal hıza çekildi.
    - **A3:** `playWithFallbacks` başında hızlandırma durumu, takılma sayaçları ve boost bayrakları sıfırlandı.
    - **A4:** `jumpToLive()` fonksiyonu, bağlantıyı gereksiz yenilemek yerine `liveCatchUpBoost = true` yaparak 2.0× hızla tamponu 8 saniyeye eritecek şekilde güncellendi.
    - **A5:** `startPositionUpdates()` canlı dalında her 250 ms döngüde `applyLiveCatchUp(player)` çağrısı eklendi.
  - **Bölüm B (TvPlayerOverlay.kt):**
    - **B1:** `liveBehindSecs` ve `liveCatchingUp` akışları `collectAsState()` ile dinlendi.
    - **B2:** Bilgi kartı rozetlerine canlı durumu rozeti eklendi: Canlıya yetişiliyorsa "⏩ Canlıya yetişiliyor", 10 sn'den fazla gerideyse "X sn geride", 10 sn altındaysa "● CANLI".
    - **B3:** "Canlıya geç" düğmesi Action Dock menüsünün en soluna (1. sıraya) taşındı; `actionDockFocusRequester` odak isteyicisi ona bağlandı (menü açılınca ilk bu buton seçili gelir); yayın gerideyken (`liveBehindSecs > 10f`) aktif vurgu rengi (`VegaColors.Live`) eklendi.
- **Değiştirilen dosyalar:**
  - `app/src/main/java/com/tivimax/player/player/TiviPlayerManager.kt`
  - `app/src/main/java/com/tivimax/player/ui/tv/TvPlayerOverlay.kt`
  - `docs/gorevler/G-039-canli-gecikme-8sn-canliya-gec.md`
  - `YAPILACAKLAR.md`
- **Test sonuçları:**
  - `./gradlew compileDebugKotlin`: Başarılı (`BUILD SUCCESSFUL in 14s`).
  - `./gradlew testDebugUnitTest`: Başarılı (`BUILD SUCCESSFUL in 5s`, 28 testin tümü geçti).
  - Fiziksel TV / emülatör bağlı olmadığı için ekran üzerinde manuel TV testi yapılamadı (test edilemedi).
