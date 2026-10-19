# G-042 — TV: sekmeye girince kategorilere odak; canlıya yetişme ayarlarını geri al + tampon optimizasyonu; en-boy oranı

- **Öncelik:** 🔴
- **Agent dağılımı:** Tek agent (bölme). Gemini 3.6 Flash için adım adım — **sırayı bozma, adımların dışına çıkma**.
- **Branch:** `fix/tv-odak-tampon-enboy` (main'den aç)
- **Commit mesajı:** `fix(tv): sekmede kategorilere odak, canlıya yetişme kaldırıldı + tampon iyileştirme, TV en-boy oranı çalışıyor`
- **Değişecek dosyalar:** `TiviPlayerManager.kt`, `TvPlayerOverlay.kt`, `HomeScreen.kt`; geri almayla **silinecek**:
  `LiveBurstTrimmer.kt`, `LiveBurstTrimDataSource.kt`, `LiveBurstTrimmerTest.kt`. (+ bu görev dosyası, `YAPILACAKLAR.md`)

## Kullanıcının istekleri
1. TV'de sol menüden **Canlı TV / Filmler / Diziler**'e OK basınca imleç **doğrudan kategorilere** geçsin.
2. Canlı yayına yetişmek için yaptığımız ayarları (G-038'deki "Canlıya geç", G-039 hızlandırma, G-040 kırpma,
   G-041 teşhis) **geri al**; tamponu sadece en iyi şekilde **optimize et**.
3. **En-boy oranı** çalışmıyor (TV canlı yayında düğme yazısı değişiyor ama görüntü değişmiyor).

---

## Bölüm A — Geri alma (git ile)
Main'den dalı açtıktan sonra **aynen** çalıştır (kod dosyalarını G-038 sonrası hâline döndürür; belgeler etkilenmez):
```
git checkout fb2e9e0 -- app/src/main/java/com/tivimax/player/player/TiviPlayerManager.kt app/src/main/java/com/tivimax/player/ui/tv/TvPlayerOverlay.kt
git rm -q app/src/main/java/com/tivimax/player/player/LiveBurstTrimmer.kt app/src/main/java/com/tivimax/player/player/LiveBurstTrimDataSource.kt app/src/test/java/com/tivimax/player/player/LiveBurstTrimmerTest.kt
```
(Claude denedi: G-038'den sonra bu iki dosyayı sadece G-039/G-040/G-041 değiştirmiş; geri dönüş temiz.)

Ardından G-038'in "Canlıya geç" kalıntılarını elle kaldır:

**A1. `TiviPlayerManager.kt`:**
- Şu 4 satırı sil:
  ```kotlin
      // G-038: "Canlıya geç" — tampon hedefi (veri yetişmezse 8 sn'ye çıkar)
      private var liveJumpTargetMs = 3_000L
      private var liveJumpPlayingSinceMs = 0L
      private var liveJumpJob: Job? = null
  ```
- `STATE_BUFFERING` dalındaki şu bloğu sil:
  ```kotlin
                          // G-038: Canlıya geçtikten sonraki 60 sn içinde takılma olursa sonraki atlamalarda 8 sn tampon
                          if (isLiveStream && liveJumpPlayingSinceMs > 0L &&
                              SystemClock.elapsedRealtime() - liveJumpPlayingSinceMs < 60_000L
                          ) {
                              liveJumpTargetMs = 8_000L
                              liveJumpPlayingSinceMs = 0L
                          }
  ```
- `playWithFallbacks` içinde şu 3 satırı sil (altındaki `dbg...` sıfırlama satırları **kalsın**):
  ```kotlin
          liveJumpJob?.cancel()
          liveJumpJob = null
          liveJumpPlayingSinceMs = 0L
  ```
  ve üstündeki yorumu `// G-038: Açılış ölçümü sıfırla` yap.
- `fun jumpToLive()` fonksiyonunu **KDoc yorumuyla birlikte tamamen sil**.
- "Açılış ölçümü" (`dbg...`, `publishStartupDebug`, `startupDebugInfo`) **kalsın** (sadece geliştirici kurulumunda görünür).

**A2. `TvPlayerOverlay.kt`:** Menüdeki şu düğmeyi **tamamen sil**:
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
(`Ses ve altyazı` düğmesi ve onun `focusRequester(actionDockFocusRequester)`'ı **kalsın** — menü açılınca odak orada.)

## Bölüm B — Tampon optimizasyonu (`TiviPlayerManager.kt`)
**B1.** Takılmadan sonra tekrar takılmasın diye canlıda yeniden başlama tamponu en az 2,5 sn:
```kotlin
// ÖNCE
            val liveBufferForPlaybackAfterRebuffer = if (liveBufferSecondsState.value <= 2) 1200 else liveBufferSecondsState.value * 1000 + 500
// SONRA
            // G-042: Takılmadan sonra en az 2,5 sn tamponla devam et (art arda takılmayı önler)
            val liveBufferForPlaybackAfterRebuffer = maxOf(2_500, if (liveBufferSecondsState.value <= 2) 1200 else liveBufferSecondsState.value * 1000 + 500)
```
**B2.** Kanal açılışını hızlandır (IPTV yayınlarında ilk tam kareyi beklemeden başlayabilir):
```kotlin
// ÖNCE
        val mediaSourceFactory = DefaultMediaSourceFactory(createDataSourceFactory())
// SONRA
        // G-042: TS yayınlarda IDR olmayan anahtar karelerden de başlayabilsin (kanal açılışı hızlanır)
        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
            .setTsExtractorFlags(androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES)
        val mediaSourceFactory = DefaultMediaSourceFactory(createDataSourceFactory(), extractorsFactory)
```
(Sadece bu satır; dosyadaki ikinci `DefaultMediaSourceFactory(createDataSourceFactory())` — ikinci ekran — **dokunma**.)

## Bölüm C — En-boy oranı TV'de çalışsın (`TvPlayerOverlay.kt`)
Video `AndroidView`'ünün `update = { playerView ->` bloğunda, `playerManager.attachView(playerView, isPrimary = true, tag = "TvPlayerOverlay")`
satırının **hemen altına** ekle (telefon oynatıcısındaki eşlemenin aynısı):
```kotlin
                // G-042: En-boy oranı (telefon oynatıcısıyla aynı eşleme)
                val (targetResizeMode, targetScaleX, targetScaleY) = when (aspectRatio) {
                    AspectRatioMode.FIT -> Triple(AspectRatioFrameLayout.RESIZE_MODE_FIT, 1.0f, 1.0f)
                    AspectRatioMode.FILL -> Triple(AspectRatioFrameLayout.RESIZE_MODE_FILL, 1.0f, 1.0f)
                    AspectRatioMode.ZOOM -> Triple(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, 1.18f, 1.18f)
                    AspectRatioMode.RATIO_16_9 -> Triple(AspectRatioFrameLayout.RESIZE_MODE_FILL, 1.0f, 1.0f)
                    AspectRatioMode.RATIO_4_3 -> Triple(AspectRatioFrameLayout.RESIZE_MODE_FIT, 0.75f, 1.0f)
                }
                if (playerView.resizeMode != targetResizeMode) playerView.resizeMode = targetResizeMode
                if (playerView.scaleX != targetScaleX || playerView.scaleY != targetScaleY) {
                    playerView.scaleX = targetScaleX
                    playerView.scaleY = targetScaleY
                }
```
(`aspectRatio` dosyada zaten `collectAsState` ile okunuyor; `AspectRatioMode` import'u yoksa
`import com.tivimax.player.player.AspectRatioMode` ekle.)

## Bölüm D — Sekmeye girince kategorilere odak (`HomeScreen.kt`, sadece TV dalı)
**D1.** `val tvRailFocusRequester = remember { FocusRequester() }` satırının **hemen altına**:
```kotlin
    // G-042: Sol menüden sekmeye girince odağı içeriğe (kategori sütununa) taşımak için
    val tvFocusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val tvFocusScope = rememberCoroutineScope()
```
**D2.** `TvNavigationRail(` çağrısındaki `onTabSelected` bloğunu:
```kotlin
                    onTabSelected = { tab ->
                        if (tab != selectedTab) {
                            tvLiveFullScreen = false
                            viewModel.setVideoFullscreen(false)
                            viewModel.setSelectedHomeTab(tab)
                        }
                    },
```
şununla değiştir:
```kotlin
                    onTabSelected = { tab ->
                        if (tab != selectedTab) {
                            tvLiveFullScreen = false
                            viewModel.setVideoFullscreen(false)
                            viewModel.setSelectedHomeTab(tab)
                        }
                        // G-042: Canlı TV / Filmler / Diziler'de imleç doğrudan kategorilere geçsin
                        if (tab == HomeTab.LIVE_TV || tab == HomeTab.MOVIES || tab == HomeTab.SERIES) {
                            tvFocusScope.launch {
                                repeat(4) {
                                    delay(150L)
                                    if (!tvRailHasFocus) return@launch
                                    tvFocusManager.moveFocus(FocusDirection.Right)
                                }
                            }
                        }
                    },
```
(`kotlinx.coroutines.launch` import'u yoksa ekle. `delay` ve `FocusDirection` zaten import edilmiş.)

---

## Kontrol ve gönderim
1. `./gradlew compileDebugKotlin` ve `./gradlew testDebugUnitTest` → geçmeli.
2. `git diff --stat origin/main`: yukarıdaki 3 dosya değişmiş, 3 dosya silinmiş, + belge dosyaları. Başka dosya yok.
3. Commit → `fix/tv-odak-tampon-enboy` dalına push. **Main'e birleştirme. APK üretme. Sürüm numarasını değiştirme.**
4. `YAPILACAKLAR.md`'de G-042 durumunu `🔍 İncelemede (branch: fix/tv-odak-tampon-enboy)` yap.

## DOKUNMA
Kanal geçişi (G-037), G-038'in OK/saat/tampon-yenileme düzeltmeleri, telefon oynatıcısı, güncelleme sistemi, imza, CI, sürüm.

## Test (TV varsa — yoksa "test edilemedi" yaz)
- Sol menüde Canlı TV'ye OK → imleç kategori sütununda. Filmler ve Diziler'de de kategori sütununda.
- Canlı yayında menüde "Canlıya geç" yok; yayın normal hızda; "Ses ve altyazı" seçili gelir.
- Menüde görüntü düğmesi: Orijinal → Doldur → Zoom → 16:9 → 4:3 her basışta görüntü değişir.
- Kanal açılışı eskisinden yavaş değil (tercihen hızlı).

---

## Agent raporu

- **Yapılan değişiklikler:**
  - **Bölüm A (Geri alma):**
    - `LiveBurstTrimmer.kt`, `LiveBurstTrimDataSource.kt`, `LiveBurstTrimmerTest.kt` dosyaları `git rm` ile kaldırıldı.
    - `TiviPlayerManager.kt` ve `TvPlayerOverlay.kt` dosyaları G-038 durumuna döndürüldü; G-038'deki `liveJump...` değişkenleri, `STATE_BUFFERING` içindeki yeniden deneme bloğu, `playWithFallbacks` içindeki iptal satırları ve `jumpToLive()` fonksiyonu ile `TvPlayerOverlay.kt` içindeki "Canlıya geç" butonu tamamen temizlendi. G-038'deki açılış ölçüm logları korundu.
  - **Bölüm B (Tampon optimizasyonu):**
    - `TiviPlayerManager.kt` içindeki `createLoadControl`'de `liveBufferForPlaybackAfterRebuffer` en az 2500 ms (`maxOf(2_500, ...)`) yapılarak art arda takılmalar engellendi.
    - `DefaultExtractorsFactory`'ye `FLAG_ALLOW_NON_IDR_KEYFRAMES` bayrağı eklenerek TS canlı yayınlarda kanal açılışı hızlandırıldı.
  - **Bölüm C (En-boy oranı):**
    - `TvPlayerOverlay.kt` içindeki video `AndroidView` `update` bloğuna telefon oynatıcısındakiyle aynı `resizeMode`, `scaleX`, `scaleY` eşlemeleri eklenerek TV tam ekranda en-boy oranının (Orijinal, Doldur, Zoom, 16:9, 4:3) çalışması sağlandı.
  - **Bölüm D (Kategorilere odak):**
    - `HomeScreen.kt` dosyasına `LocalFocusManager` ve `rememberCoroutineScope` eklendi; TV sol menüsünde Canlı TV, Filmler veya Diziler seçildiğinde imlecin otomatik olarak sağdaki kategori sütununa (`FocusDirection.Right`) geçmesi sağlandı.
- **Değiştirilen ve silinen dosyalar:**
  - `app/src/main/java/com/tivimax/player/player/TiviPlayerManager.kt` (değişti)
  - `app/src/main/java/com/tivimax/player/ui/tv/TvPlayerOverlay.kt` (değişti)
  - `app/src/main/java/com/tivimax/player/ui/main/HomeScreen.kt` (değişti)
  - `app/src/main/java/com/tivimax/player/player/LiveBurstTrimmer.kt` (silindi)
  - `app/src/main/java/com/tivimax/player/player/LiveBurstTrimDataSource.kt` (silindi)
  - `app/src/test/java/com/tivimax/player/player/LiveBurstTrimmerTest.kt` (silindi)
  - `docs/gorevler/G-042-tv-kategori-odak-tampon-geri-al-en-boy.md` (değişti)
  - `YAPILACAKLAR.md` (değişti)
- **Test sonuçları:**
  - `./gradlew compileDebugKotlin`: Başarılı (`BUILD SUCCESSFUL in 24s`).
  - `./gradlew testDebugUnitTest`: Başarılı (`BUILD SUCCESSFUL in 8s`, 28 testin tümü geçti).
  - Fiziksel TV / emülatör bağlı olmadığı için ekran üzerinde manuel TV testi yapılamadı (test edilemedi).
