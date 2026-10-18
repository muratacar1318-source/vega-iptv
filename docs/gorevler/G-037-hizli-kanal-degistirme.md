# G-037 — Hızlı kanal değiştirince kanalların açılmaması

- **Öncelik:** 🔴
- **Agent dağılımı:** Tek agent (bölme). Gemini 3.6 Flash için adım adım — **adımların dışına çıkma, kendi fikrini ekleme**.
- **Branch:** `fix/hizli-zap` (main'den aç)
- **Commit mesajı:** `fix(live): hızlı kanal değiştirmede sadece son kanala bağlanılıyor, eski yeniden deneme işleri iptal ediliyor`
- **Değişecek dosyalar (SADECE bunlar):**
  1. `app/src/main/java/com/tivimax/player/player/TiviPlayerManager.kt`
  2. `app/src/main/java/com/tivimax/player/ui/MainViewModel.kt`
  3. Bu görev dosyası (Agent raporu) ve `YAPILACAKLAR.md` (durum satırı)

## Kullanıcının gördüğü
Kumandayla hızlı hızlı birkaç kanal geçince kanallar açılmıyor; biraz bekleyince geliyor. Tekrar hızlı geçince yine oluyor.

## Neden (Claude kod incelemesi)
Kullanıcının aboneliği **tek bağlantılı** (ekranda "0/1 Bağlantı"). Sağlayıcı, kapatılan bağlantıyı birkaç saniye daha
"kullanımda" sayıyor.
1. Her tuşa basışta yeni kanala **hemen** bağlanılıyor → 5 hızlı basış = sağlayıcıya 5 bağlantı denemesi → sağlayıcı
   reddediyor (403/509).
2. Reddedilince oynatıcı 1,5 / 3 / 5 sn sonra **yeniden deneme** planlıyor. Bu planlanmış işler **iptal edilmiyor**:
   kullanıcı başka kanala geçtikten sonra eski iş tetiklenip **yeni kanalı durdurup yeniden başlatıyor** → yine fazladan
   bağlantı → zincirleme ret.
3. Yeniden deneme sayaçları sadece görüntü gelince sıfırlanıyor; görüntü gelmeden geçilen kanallarda sayaç dolu kalıyor,
   sonraki kanal hiç yeniden denenmeden yedek adreslere geçiyor (her biri yine yeni bağlantı).

## Çözüm (TiviMate benzeri)
- TV'de ▲▼ / CH+ CH- ile geçerken kanal adı/bilgisi **hemen** değişir, eski yayın **hemen** kapanır, ama yeni kanala
  **450 ms boyunca başka tuşa basılmazsa** bağlanılır. Böylece hızlı geçişte sağlayıcıya sadece son kanal için bağlanılır.
- Yeni kanal başlarken önceki kanaldan kalan tüm yeniden deneme işleri iptal edilir ve sayaçlar sıfırlanır.

---

## Adım 1 — TiviPlayerManager.kt: bekleyen yeniden deneme işi
`private var isReconnecting = false` satırının **hemen altına**:
```kotlin
    // G-037: Planlanmış yeniden deneme işi (yeni kanal başlarken iptal edilir)
    private var pendingRetryJob: Job? = null
```

## Adım 2 — TiviPlayerManager.kt: `playWithFallbacks` başında sıfırla
`fun playWithFallbacks(...)` fonksiyonunda `firstUrlAuthRetried = false` satırının **hemen altına**:
```kotlin
        // G-037: Önceki kanaldan kalan yeniden deneme işlerini iptal et, sayaçları sıfırla
        pendingRetryJob?.cancel()
        pendingRetryJob = null
        networkRecoveryAttempt = 0
        decoderRecoveryAttempt = 0
        isReconnecting = false
```

## Adım 3 — TiviPlayerManager.kt: `onPlayerError` içindeki iki planlı işi kaydet
**3a.** VOD için olan blok:
```kotlin
                        scope.launch {
                            delay(1500L)
                            playInternal(sameUrl, isLive = false, startPositionMs = currentPos)
                        }
```
→
```kotlin
                        pendingRetryJob = scope.launch {
                            delay(1500L)
                            playInternal(sameUrl, isLive = false, startPositionMs = currentPos)
                        }
```

**3b.** Ağ yeniden bağlanma bloğu:
```kotlin
                        _errorMessage.value = "Yayın yeniden bağlanıyor... ($networkRecoveryAttempt/3)"
                        
                        scope.launch {
                            delay(backoffDelay)
                            android.util.Log.w("VegaIPTV", "Ağ koptu, yeniden bağlanılıyor (Deneme $networkRecoveryAttempt)...")
                            player.stop()
```
→
```kotlin
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
```
(Bloğun geri kalanı — `player.seekTo(currentPos)`, `prepare()`, `play()`, `isReconnecting = false` — aynen kalır.)

## Adım 4 — MainViewModel.kt: TV'de hızlı geçişte sadece son kanala bağlan
**4a.** `private var syncJob: Job? = null` satırını **değiştirmeden**, `fun zapNextChannel(` fonksiyonunun **hemen ÜSTÜNE** ekle:
```kotlin
    // G-037: TV'de hızlı kanal değiştirme — bilgi hemen değişir, bağlantı son kanal için kurulur
    private var tvZapJob: Job? = null

    private fun scheduleTvZap(channel: LiveStream) {
        activePreviewChannel.value = channel
        tvZapJob?.cancel()
        playerManager.stopAndClear()
        tvZapJob = viewModelScope.launch {
            delay(450L)
            previewChannel(channel)
        }
    }
```

**4b.** `zapNextChannel`, `zapPreviousChannel` ve `zapRecall` fonksiyonlarındaki **TV dalında** (`if (deviceMode.value == com.tivimax.player.util.DeviceMode.TV) {`):
- `previewChannel(nextChannel)` → `scheduleTvZap(nextChannel)`
- `previewChannel(prevChannel)` → `scheduleTvZap(prevChannel)`
- `previewChannel(prev)` → `scheduleTvZap(prev)`

`else` dallarına (`playLiveChannel(...)` — telefon/tablet) **dokunma**. `previewChannel` fonksiyonunun kendisine dokunma.

---

## Kontrol ve gönderim
1. `./gradlew compileDebugKotlin` → hata varsa düzelt (isim uydurmadan).
2. `./gradlew testDebugUnitTest` → tüm testler geçmeli.
3. `git diff --stat`: **sadece** yukarıdaki dosyalar. Başka dosya değiştiyse geri al.
4. Commit → `fix/hizli-zap` dalına push. **Main'e birleştirme. APK üretme. Sürüm numarasını değiştirme.**
5. `YAPILACAKLAR.md`'de G-037 durumunu `🔍 İncelemede (branch: fix/hizli-zap)` yap.

## DOKUNMA
`previewChannel`, `playLiveChannel`, VOD oynatma, `VodLoadErrorHandlingPolicy`, tampon ayarları, yedek adres listesi,
telefon/tablet kanal geçişi, güncelleme sistemi (AutoUpdateManager), imza, CI, sürüm numarası.

## Test (TV varsa — yoksa "test edilemedi" yaz)
- Tam ekran canlıda ▲'ya 5-6 kez hızlıca bas: kanal adı her basışta değişir, durunca ~0,5 sn sonra son kanal açılır.
- Bunu birkaç kez tekrarla: kanallar açılmaya devam eder (eskiden açılmıyordu).
- Tek tek, yavaş geçişte her kanal açılır.
- Kanal listesinden (OK) kanal seçme ve telefonda kanal geçişi eskisi gibi.

---

## Agent raporu

_(Agent doldurur.)_
