# G-044 — Canlı yayın takılma bekçisi (yuvarlak sonsuza kadar dönmesin)

- **Öncelik:** 🔴
- **Agent dağılımı:** Tek agent (bölme). Gemini 3.6 Flash için adım adım — **sırayı bozma, adımların dışına çıkma**.
- **Branch:** `fix/canli-takilma-bekcisi` (main'den aç)
- **Commit mesajı:** `fix(oynatıcı): canlı yayında uzun takılmada otomatik yeniden bağlanma`
- **Değişecek dosyalar (SADECE bunlar):**
  1. `app/src/main/java/com/tivimax/player/player/TiviPlayerManager.kt`
  2. `YAPILACAKLAR.md` — sadece G-044 satırı

## Sorun
Canlı yayında (özellikle HEVC kanallarda) görüntü donuyor, ekranda yükleniyor yuvarlağı dönüyor ve **beklense de düzelmiyor**.
Sebep: sunucu bağlantıyı kapatmadan veri göndermeyi kesince ya da çözücü takılınca oynatıcı hata vermiyor,
sadece "yükleniyor" durumunda bekliyor (bağlantı zaman aşımı 60 sn). Hiçbir kod bu durumu fark edip yeniden bağlanmıyor.

## Çözüm
Canlı yayında oynatıcı **"yükleniyor" durumunda uzun süre kalırsa** aynı kanala otomatik yeniden bağlan:
- Kanal ilk açılırken: **20 sn** (HEVC kanallar geç açıldığı için uzun tutuldu — açılışı bölmesin).
- Yayın başladıktan sonraki takılmalarda: **10 sn**.
- En fazla **3** deneme; yayın yeniden akınca sayaç sıfırlanır. 3 denemede de olmazsa kullanıcıya mesaj gösterilir.
- **Filmler/diziler (VOD) etkilenmez** (`isLiveStream` kontrolü).

## 🚫 DOKUNMA
- `createLoadControl`, `createDataSourceFactory`, `baseOkHttpClient`, `initializePlayer` içindeki renderers/extractors/mediaSourceFactory satırları.
- `onPlayerError` içindeki mevcut mantık (sadece Adım 4'teki **tek satır** eklenecek).
- `playInternal`, `startDualView` ve ikinci oynatıcı (dual view) kodu.
- Başka hiçbir dosya. `AutoUpdateManager` ve güncelleme dosyalarına kesinlikle dokunma.

---

## Adım 1 — Alanları ve bekçi fonksiyonunu ekle
`TiviPlayerManager.kt` içinde şu mevcut satırları bul:
```kotlin
    // G-037: Planlanmış yeniden deneme işi (yeni kanal başlarken iptal edilir)
    private var pendingRetryJob: Job? = null
```
**Hemen altına** şunu **aynen** ekle:
```kotlin

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
```
Not: `Job`, `scope`, `delay`, `launch`, `Player`, `exoPlayer`, `currentPlayingUrl`, `hasRenderedFirstFrameOrReady`,
`isReconnecting`, `_errorMessage` dosyada zaten var. Yeni import gerekmez.

## Adım 2 — `initializePlayer` içindeki dinleyiciye bağla
`initializePlayer` fonksiyonunda `player.addListener(object : Player.Listener {` altındaki `onPlaybackStateChanged`
bloğunu bul (içinde `Player.STATE_BUFFERING -> { _isBuffering.value = true` olan blok; **`timingAnalyticsListener` içindekini değil**).

**2a.** Şunu:
```kotlin
                    Player.STATE_BUFFERING -> {
                        _isBuffering.value = true
                        _errorMessage.value = null
                    }
```
şununla değiştir:
```kotlin
                    Player.STATE_BUFFERING -> {
                        _isBuffering.value = true
                        _errorMessage.value = null
                        armLiveStallWatchdog() // G-044
                    }
```

**2b.** Aynı bloktaki `Player.STATE_READY -> {` satırının **hemen altına** şu iki satırı ekle:
```kotlin
                        cancelLiveStallWatchdog() // G-044
                        liveStallRecoveries = 0
```

**2c.** Aynı bloktaki şunu:
```kotlin
                    Player.STATE_IDLE -> {
                        _isBuffering.value = false
                    }
```
şununla değiştir:
```kotlin
                    Player.STATE_IDLE -> {
                        _isBuffering.value = false
                        cancelLiveStallWatchdog() // G-044
                    }
```
`STATE_ENDED` dalına **dokunma**.

## Adım 3 — Yeni kanal / durdurma / kapatmada bekçiyi iptal et
**3a.** `playWithFallbacks` içinde şu satırları bul:
```kotlin
        pendingRetryJob?.cancel()
        pendingRetryJob = null
```
**Hemen altına** ekle:
```kotlin
        cancelLiveStallWatchdog() // G-044
        liveStallRecoveries = 0
```

**3b.** `fun stopAndClear() {` satırının **hemen altına** ekle:
```kotlin
        cancelLiveStallWatchdog() // G-044
```

**3c.** `fun release() {` satırının **hemen altına** ekle:
```kotlin
        cancelLiveStallWatchdog() // G-044
```

## Adım 4 — Hata gelince bekçiyi durdur
`initializePlayer` içindeki `override fun onPlayerError(error: PlaybackException) {` satırının **hemen altındaki**
`_isBuffering.value = false` satırından **sonra** şu tek satırı ekle:
```kotlin
                cancelLiveStallWatchdog() // G-044: hata akışı kendi yeniden denemesini yapar
```
Dikkat: dosyada iki `onPlayerError` var. Doğru olan, içinde `val httpStatusCode = extractHttpStatusCode(error)` geçendir
(`timingAnalyticsListener` içindekine ekleme).

## Adım 5 — Derle ve kontrol et
1. `./gradlew :app:assembleDebug` → hatasız.
2. `./gradlew :app:testDebugUnitTest` → geçmeli.
3. `grep -c "G-044" app/src/main/java/com/tivimax/player/player/TiviPlayerManager.kt` → **8** çıkmalı.

## Adım 6 — YAPILACAKLAR.md
Bekleyen görevler tablosunda G-044 satırını `🔄 Agent bitirdi — Claude kontrol edecek` yap. Başka satıra dokunma.

## Adım 7 — Gönder
```
git add app/src/main/java/com/tivimax/player/player/TiviPlayerManager.kt YAPILACAKLAR.md
git commit -m "fix(oynatıcı): canlı yayında uzun takılmada otomatik yeniden bağlanma"
git push -u origin fix/canli-takilma-bekcisi
```
**Main'e birleştirme yapma.** PR'ı Claude açar.
