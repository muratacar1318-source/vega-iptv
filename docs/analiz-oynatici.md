# Vega IPTV — Oynatıcı ve Kanal Değiştirme (Zapping) Detaylı Teknik Analizi

**Tarih:** 16 Ekim 2026  
**Rol:** Kıdemli Android / Kotlin Geliştiricisi  
**Kapsam:** Jetpack Compose + Media3 ExoPlayer Oynatıcı Mimarisi, Zapping Mekanizmaları ve Kök Neden Analizi  
**İncelenen Dosyalar:**
- `app/src/main/java/com/tivimax/player/player/TiviPlayerManager.kt`
- `app/src/main/java/com/tivimax/player/ui/player/PlayerScreen.kt`
- `app/src/main/java/com/tivimax/player/ui/tv/TvTiviMateScreen.kt`
- `app/src/main/java/com/tivimax/player/ui/tv/TvPlayerOverlay.kt`
- `app/src/main/java/com/tivimax/player/ui/MainViewModel.kt`
- `app/src/main/java/com/tivimax/player/Navigation.kt`
- `app/src/main/java/com/tivimax/player/MainActivity.kt`

---

## 1. Akış Diyagramları (Kanal Değiştirme Yolları)

Uygulama içerisinde kanal değişiminin tetiklendiği 4 ana kanal geçiş yolu ve bunların adım adım fonksiyon çağrı sıraları ile ExoPlayer durum geçişleri aşağıda çıkarılmıştır.

### a) Mobil Tam Ekranda Dikey Kaydırarak Kanal Değiştirme (Vertical Swipe Zapping)

```text
[Kullanıcı Dikey Kaydırma Yapar (Drag Gestures)]
       │
       ▼
PlayerScreen.kt (Satır: 893-911)
  └─ detectDragGestures { totalDragY > size.height * 0.2f }
  └─ Yukarı Kaydırma (< 0) -> MainViewModel.zapNextChannel(streamId)
  └─ Aşağı Kaydırma (> 0) -> MainViewModel.zapPreviousChannel(streamId)
       │
       ▼
MainViewModel.kt (Satır: 1363-1389)
  └─ liveStreams listesinde indeks hesabı: (currentIndex ± 1) % size
  └─ deviceMode != TV olduğundan -> playLiveChannel(targetChannel)
       │
       ▼
MainViewModel.kt (Satır: 1317-1348) [playLiveChannel]
  ├─ 1. lastPlayedLiveChannel.value = currentChannel
  ├─ 2. recordRecentChannel(channel)
  ├─ 3. preferences.setLastPlayedStreamId(channel.streamId) [Coroutine]
  ├─ 4. activePreviewChannel.value = channel
  ├─ 5. repository.buildLiveStreamUrls(channel.streamId, preferHls)
  ├─ 6. playerManager.playWithFallbacks(urls, isLive = true)
  ├─ 7. loadEpgForChannel(channel.streamId)
  └─ 8. navigateTo(Screen.Player(streamId = channel.streamId, ...)) [KÖK HATA TETİKLEYİCİSİ]
       │
       ├─────────────────────────────────────────┐
       ▼                                         ▼
TiviPlayerManager.kt (Satır: 444-496)     Navigation.kt (Satır: 38-71)
  └─ playInternal(url, isLive = true)       └─ Crossfade(currentScreen) hedefi değişir
       ├─ player.stop()                          └─ Yeni PlayerScreen oluşturulur
       ├─ player.clearMediaItems()               └─ Eski PlayerScreen Dispose edilir!
       ├─ player.setMediaItem(mediaItem)               │
       ├─ player.prepare()                             ▼
       ├─ player.play()                   PlayerScreen.kt (Satır: 485-494)
       └─ startPositionUpdates()            └─ DisposableEffect(Unit).onDispose {
                                                 if (!viewModel.isMiniPlayerActive.value) {
                                                     playerManager.stopAndClear() // <── FELAKET
                                                 }
                                               }
                                                       │
                                                       ▼
                                          TiviPlayerManager.kt (Satır: 498-507)
                                            ├─ exoPlayer.stop()
                                            └─ exoPlayer.clearMediaItems()
                                            ==> SONUÇ: OYNATICI DURDURULUR VE SİYAH EKRANDA KALIR!
```

---

### b) Mobil Oynatıcıda Yan Kanal Çekmecesinden Kanal Seçme (Side Drawer Zapping)

```text
[Kullanıcı Çekmeceyi Açar: showChannelDrawer = true]
       │
       ▼
PlayerScreen.kt (Satır: 2278-2282)
  └─ LazyColumn içindeki kanal satırına dokunulur:
     Surface(onClick = {
         showChannelDrawer = false
         viewModel.playLiveChannel(channel)
     })
       │
       ▼
MainViewModel.kt (Satır: 1317-1348) [playLiveChannel]
  ├─ playerManager.playWithFallbacks(urls, isLive = true)
  │    └─ TiviPlayerManager: player.stop() -> clearMediaItems() -> setMediaItem() -> prepare() -> play()
  └─ navigateTo(Screen.Player(streamId = channel.streamId))
       │
       ▼
Navigation.kt (Satır: 40)
  └─ Crossfade tetiklenir -> Eski PlayerScreen onDispose -> playerManager.stopAndClear()
  ==> SONUÇ: Dikey kaydırmayla aynı race-condition ve siyah ekran riski taşır.
```

---

### c) TV Modunda (TiviMate Arayüzü) Kumandayla Kanal Değiştirme

TV modunda iki farklı durum bulunmaktadır:

#### Durum 1: Tam Ekran TV Oynatıcı (`TvPlayerOverlay.kt`)
```text
[Kumandadan DPAD_UP / DPAD_DOWN / CH_UP / CH_DOWN Tuşuna Basılır]
       │
       ▼
TvPlayerOverlay.kt (Satır: 186-203)
  └─ DPAD_UP / CH_UP       -> viewModel.zapNextChannel(channel.streamId)
  └─ DPAD_DOWN / CH_DOWN   -> viewModel.zapPreviousChannel(channel.streamId)
       │
       ▼
MainViewModel.kt (Satır: 1369-1373 & 1383-1387)
  └─ if (deviceMode.value == DeviceMode.TV) {
         previewChannel(nextChannel)  // <── playLiveChannel DEĞİL!
     }
       │
       ▼
MainViewModel.kt (Satır: 1236-1248) [previewChannel]
  ├─ 1. repository.buildLiveStreamUrls(channel.streamId, preferHls)
  ├─ 2. playerManager.playWithFallbacks(urls, isLive = true)
  ├─ 3. activePreviewChannel.value = channel
  ├─ 4. isMiniPlayerActive.value = true
  ├─ 5. recordRecentChannel(channel)
  └─ 6. loadEpgForChannel(channel.streamId)
  (NOT: navigateTo çağrılmaz! Bu sayede Crossfade tetiklenmez, ekran kapanmaz.)
       │
       ▼
TiviPlayerManager.kt (Satır: 471-491) [playInternal]
  ├─ player.stop()
  ├─ player.clearMediaItems()
  ├─ player.setMediaItem(mediaItem)
  ├─ player.prepare()
  └─ player.play()
       │
       ▼
TvPlayerOverlay.kt (Satır: 221-241) [AndroidView]
  └─ setKeepContentOnPlayerReset(true) sayesinde son kare korunarak yeni akışa bağlanır.
  (NOT: channel parametresi güncellenmediği için art arda basışlarda streamId senkronizasyonu kopar!)
```

#### Durum 2: 3-Bölmeli TV Rehber Ekranı (`TvTiviMateScreen.kt`)
```text
[Orta Sütun Kanal Listesinde D-pad ile Kanal Seçimi]
       │
       ▼
TvTiviMateScreen.kt (Satır: 319-331) [dpadFocusable onClick]
  ├─ 1. OK Tıklaması (isCurrentPreview == false):
  │    └─ viewModel.previewChannel(channel)
  │         └─ Sağ üst köşedeki 16:9 önizleme kutusunda oynatma başlar (playerManager.playWithFallbacks)
  └─ 2. OK Tıklaması (isCurrentPreview == true):
       └─ isFullScreenPlayer = true
            └─ TvPlayerOverlay devreye girer (Aynı ExoPlayer örneği kesintisiz tam ekrana geçer).
```

---

### d) Diğer Kanal Değiştirme Yolları

1. **Yarı Saydam Rehber (TiviOverlayGuide):**
   - `PlayerScreen.kt` (Satır: 3017-3024) -> `onChannelSelected` -> `viewModel.playLiveChannel(channel)` çağrılır.
2. **Son İzlenen Kanallar Şeridi (TiviRecentChannelsBar):**
   - `PlayerScreen.kt` (Satır: 3034-3037) -> `onChannelSelected` -> `viewModel.playLiveChannel(channel)` çağrılır.
3. **Tek Tuşla Önceki Kanala Dönüş (Zapping Recall / TLC):**
   - `PlayerScreen.kt` (Satır: 704 & 1724) veya `MainActivity.kt` (Satır: 218 & 239) -> `viewModel.zapRecall()` çağrılır. `lastPlayedLiveChannel` hedeflenerek `playLiveChannel` veya `previewChannel` tetiklenir.
4. **Numaratörle Hızlı Geçiş (Numeric Zapper):**
   - `MainActivity.kt` (Satır: 201-210) veya `TvPlayerOverlay.kt` (Satır: 156-160) -> `viewModel.onNumericKeyPressed(digit)` -> 1500ms debounce veya OK basımı -> `viewModel.executeZapperSwitch()` -> `playLiveChannel(matched)`.
5. **Canlı TV Ana Ekran Listeleri (`LiveTvScreen.kt` / `LiveTvModernPortraitLayout.kt`):**
   - Kart tıklaması doğrudan `viewModel.playLiveChannel(channel)` fonksiyonunu çağırır.

---

## 2. Tespit Edilen Sorunlar (Önem Sırasına Göre)

### KRİTİK 1: Dikey Kaydırarak Kanal Değiştirince Yayının Gelmemesi / Siyah Ekranda Kalması
- **İlgili Dosyalar ve Satırlar:**
  - `app/src/main/java/com/tivimax/player/ui/MainViewModel.kt`: Satır 1337–1347 (`playLiveChannel` içinde `navigateTo` çağrısı)
  - `app/src/main/java/com/tivimax/player/Navigation.kt`: Satır 40–71 (`Crossfade(targetState = currentScreen)`)
  - `app/src/main/java/com/tivimax/player/ui/player/PlayerScreen.kt`: Satır 485–494 (`DisposableEffect(Unit)` onDispose bloğu)
  - `app/src/main/java/com/tivimax/player/player/TiviPlayerManager.kt`: Satır 498–507 (`stopAndClear()` fonksiyonu)
  - `app/src/main/java/com/tivimax/player/ui/player/PlayerScreen.kt`: Satır 3664–3666 (`AndroidView` onRelease bloğu)

- **Kök Neden:**
  Kullanıcı dikey kaydırma yaptığında `playLiveChannel` fonksiyonu `playerManager.playWithFallbacks(...)` çağırarak ExoPlayer'da yeni kanalı hazırlamaya (`prepare()`) ve oynatmaya (`play()`) başlar. Ancak hemen ardından `navigateTo(Screen.Player(...))` çağrılır.
  `Navigation.kt` içindeki `Crossfade`, `currentScreen` değiştiği için eski `PlayerScreen` bileşenini yok eder (dispose).
  Eski `PlayerScreen` yok edilirken satır 488'deki `DisposableEffect(Unit)` tetiklenir ve `isMiniPlayerActive` mobilde `false` olduğu için **`playerManager.stopAndClear()`** çağrılır!
  `stopAndClear()` fonksiyonu tam o salisede ExoPlayer'ın `stop()` ve `clearMediaItems()` fonksiyonlarını çalıştırır.
  Aynı zamanda `AndroidView.onRelease` (satır 3665) `playerView.player = null` yapar.
  Yeni açılan `PlayerScreen` ise `LaunchedEffect(streamUrl)` içinde `currentPlayingUrl == null` yarışına girer; ancak ExoPlayer medyası silinmiş ve `STATE_IDLE` durumunda kalmış olur. Ekranda görüntü açılmaz, siyah ekranda asılı kalır.

---

### YÜKSEK 2: Hızlı Art Arda Kanal Değiştirmede Yanlış Kanalın Açılması veya Eski Sesin Gelmesi (Race Condition)
- **İlgili Dosyalar ve Satırlar:**
  - `app/src/main/java/com/tivimax/player/ui/MainViewModel.kt`: Satır 1317–1348 (`playLiveChannel`) ve 1363–1389 (`zapNextChannel`)
  - `app/src/main/java/com/tivimax/player/player/TiviPlayerManager.kt`: Satır 378–401 (`onPlayerError` içerisindeki coroutine) ve 458–496 (`playInternal`)
  - `app/src/main/java/com/tivimax/player/ui/player/PlayerScreen.kt`: Satır 894–910 (`detectDragGestures`)

- **Kök Neden:**
  1. **Debounce / İptal Mekanizması Yok:** Zapping fonksiyonlarında (kaydırma veya kumanda tuşları) herhangi bir Coroutine `Job` iptali veya geciktirme (debounce) yoktur. Kullanıcı 1 saniyede 3 kanal geçtiğinde 3 ayrı HTTP bağlantısı ve ExoPlayer `setMediaItem` komutu ardı ardına gönderilir.
  2. **Asenkron Ağ Yarışı:** 1. kanalın OkHttp / DNS bağlantısı geç tamamlanıp 2. kanalın arkasından buffer doldurmaya başladığında ExoPlayer codec kuyrukları çakışır.
  3. **Arka Planda Unutulan Hata Kurtarma Coroutine'leri:** `TiviPlayerManager.kt` satır 390'da bir ağ gecikmesi olduğunda `scope.launch { delay(backoffDelay); player.stop(); player.prepare(); player.play() }` çalıştırılır. Yeni bir kanala geçildiğinde bu coroutine **İPTAL EDİLMEMEKTEDİR**. 1.5 saniye sonra uyanan eski kanalın coroutine'i `player.prepare()` ve `player.play()` çağırarak yeni seçilen kanalın üzerine eski kanalın akışını/sesini basar!

---

### YÜKSEK 3: Hata Durumunda Yeniden Deneme (Retry/Fallback) Mantığının Kanal Değişimiyle Çakışması
- **İlgili Dosyalar ve Satırlar:**
  - `app/src/main/java/com/tivimax/player/player/TiviPlayerManager.kt`: Satır 267–270 (global sayaçlar: `decoderRecoveryAttempt`, `networkRecoveryAttempt`, `isReconnecting`)
  - `app/src/main/java/com/tivimax/player/player/TiviPlayerManager.kt`: Satır 441–452 (`fallbackUrls` ve `fallbackAttempt`)
  - `app/src/main/java/com/tivimax/player/player/TiviPlayerManager.kt`: Satır 348–423 (`onPlayerError`)

- **Kök Neden:**
  Hata kurtarma ve fallback durum değişkenleri `TiviPlayerManager` sınıfında paylaşımlı global değişken olarak tutulmaktadır.
  Kullanıcı A kanalındayken bir ağ hatası oluşup `networkRecoveryAttempt = 1`, `isReconnecting = true` yapıldığında, kullanıcı beklemeden B kanalına geçerse:
  - `playWithFallbacks` çağrısı `networkRecoveryAttempt`, `decoderRecoveryAttempt` ve `isReconnecting` değişkenlerini **sıfırlamaz**.
  - B kanalı açıldığında `isReconnecting` hala `true` kaldığı için B kanalındaki olası bir bağlantı hatasında otomatik kurtarma devre dışı kalır.
  - A kanalının arkada kalan fallback listesi ile B kanalının URL listesi `fallbackUrls` mutable listesinde birbirinin üzerine yazar; B kanalında hata olunca A kanalının URL'leri denenmeye çalışılır (`IndexOutOfBounds` veya alakasız kanalın açılması).

---

### ORTA 4: Birden Fazla ExoPlayer Örneği (`exoPlayer` vs `secondaryExoPlayer`) ve Yaşam Döngüsü Uyuşmazlığı
- **İlgili Dosyalar ve Satırlar:**
  - `app/src/main/java/com/tivimax/player/player/TiviPlayerManager.kt`: Satır 57–61, 908–945 (`startDualView`), 962–973 (`closeDualView`), 975–988 (`release`)
  - `app/src/main/java/com/tivimax/player/player/TiviPlayerManager.kt`: Satır 526–548 (`play()`, `stop()`, `stopAndClear()`)

- **Kök Neden:**
  1. **Donanım Dekoderi Tüketimi (Codec Exhaustion):** Çoğu Android TV ve orta segment telefonda aynı anda yalnızca 1-2 adet güvenli donanım video dekoderi (`MediaCodec`) bulunur. Dual-View aktifken zapping yapıldığında `secondaryExoPlayer` serbest bırakılmadan ana oynatıcı yeniden başlatılırsa sistem dekoder bulamaz ve `ERROR_CODE_DECODER_INIT_FAILED` hatası fırlatır.
  2. **Eksik Yaşam Döngüsü Kontrolleri:** `stopAndClear()` fonksiyonu (satır 498) yalnızca `exoPlayer`'ı durdurur; `secondaryExoPlayer` arka planda oynamaya, ağ tüketmeye ve ses kaynağını tutmaya devam eder.
  3. `play()` fonksiyonu (satır 526) sadece `exoPlayer?.play()` çağırır; Dual View sırasında ekranlar yer değiştirdiğinde (`swapDualViewScreens`) ses/odak yönetimi senkronunu kaybeder.

---

### ORTA 5: TV Modunda Tam Ekran Overlay İçinde `channel` Parametresinin Bayatlaması (Stale Parameter)
- **İlgili Dosyalar ve Satırlar:**
  - `app/src/main/java/com/tivimax/player/ui/player/PlayerScreen.kt`: Satır 119–128
  - `app/src/main/java/com/tivimax/player/ui/tv/TvPlayerOverlay.kt`: Satır 65–69 ve 186–203
  - `app/src/main/java/com/tivimax/player/ui/MainViewModel.kt`: Satır 1369–1373 (`previewChannel`)

- **Kök Neden:**
  TV modunda kumandayla zapping yapılırken `zapNextChannel`, `navigateTo` çağırmamak için `previewChannel` kullanır. `activePreviewChannel` güncellenir.
  Ancak `TvPlayerOverlay`'e `channel` nesnesi `PlayerScreen`'in compose argümanından (`streamId`) parametre olarak geçilmiştir:
  `TvPlayerOverlay(viewModel = viewModel, channel = activeChan ?: fallbackChan)`.
  Ekran yeniden navigate edilmediği için `PlayerScreen`'in `streamId` parametresi değişmez. Dolayısıyla `TvPlayerOverlay` içindeki `channel.streamId` **eski kanal olarak kalır**!
  Kullanıcı kumandadan 2. kez yukarı bastığında `viewModel.zapNextChannel(channel.streamId)` yine başlangıçtaki eski kanalın ID'sini gönderir ve zapping takılır / yanlış kanala atlar.

---

## 3. Kod Tekrarları

Projede kanal değiştirme, oynatma hazırlığı ve zapping akışının tek bir merkez yerine dağınık yerlerde tekrarlandığı tespit edilmiştir:

1. **Yayın URL'si Oluşturma Mantığı (5 Farklı Yerde Tekrar):**
   `LiveStream` nesnesinden doğrudan kaynak veya HLS/TS URL'si oluşturma mantığı birebir kopyalanmıştır:
   - `MainViewModel.kt`: Satır 1237–1242 (`previewChannel` içinde)
   - `MainViewModel.kt`: Satır 1266–1271 (`expandMiniPlayerToFullscreen` içinde)
   - `MainViewModel.kt`: Satır 1328–1333 (`playLiveChannel` içinde)
   - `MainViewModel.kt`: Satır 1351–1356 (`startDualView` içinde)
   - `LiveTvScreen.kt` ve `LiveTvWideScreenLayout.kt`: URL oluşturma blokları

2. **Sonraki / Önceki Kanal İndeks Hesaplama:**
   - `MainViewModel.kt` satır 1363–1389 (`zapNextChannel` ve `zapPreviousChannel` fonksiyonlarında)
   - `MainActivity.kt` satır 222–235 (Donanım `KEYCODE_CHANNEL_UP/DOWN` olayında)
   - `PlayerScreen.kt` satır 894–910 (Dikey gesture içinde)
   - `TvPlayerOverlay.kt` satır 186–203 (D-pad key event içinde)
   *Not:* `PlayerScreen` kumanda olaylarında aşağı tuşu son kanallar çubuğunu açarken, `TvPlayerOverlay`'de önceki kanala geçmektedir. Davranışlar ekranlar arasında tutarsızdır.

3. **TV vs Mobil Çatallanması (Forked Navigation Logic):**
   `MainViewModel` içindeki `zapNextChannel`, `zapPreviousChannel` ve `zapRecall` fonksiyonlarının tamamında:
   ```kotlin
   if (deviceMode.value == DeviceMode.TV) {
       previewChannel(...)
   } else {
       playLiveChannel(...)
   }
   ```
   kontrolü tekrarlanmıştır. `previewChannel` ile `playLiveChannel` arasındaki yan etkiler (Son izlenen kanal kaydı, Preferences kaydı, Navigation tetiklemesi) eşitlenmemiştir.

4. **PlayerView / Surface Tanımları:**
   `PlayerScreen.kt` (`VideoPlayerSurface`), `TvPlayerOverlay.kt` ve `TvTiviMateScreen.kt` dosyalarında `AndroidView` içinde `PlayerView` oluşturma, `setKeepContentOnPlayerReset(true)` ve `useController = false` gibi konfigürasyonlar 3 ayrı yerde tekrarlanmıştır.

---

## 4. Önerilen Mimari

### Temel Prensipler

1. **Tek Kanal Değiştirme Giriş Noktası (Unified Zapping Controller):**
   Tüm kanal değişimleri (`UI gesture`, `kumanda`, `numaratör`, `EPG/liste seçimi`) ViewModel katmanında tek bir askıya alınabilir (suspend/cancellable) fonksiyona bağlanmalıdır.
2. **Oynatıcı Ekranında Sayfa Değiştirmeme (In-Place Zapping):**
   Kullanıcı zaten oynatıcı ekranındayken kanal değiştiğinde **asla `navigateTo(Screen.Player(...))` çağrılmamalıdır**. `PlayerScreen`, ViewModel'deki `activeChannel: StateFlow<LiveStream?>` durumunu dinlemeli; UI başlığı ve EPG Compose recomposition ile güncellenirken ExoPlayer aynı yüzey üzerinde kesintisiz yeni yayına geçmelidir.
3. **Zapping Coroutine İptali & Debounce:**
   Her yeni kanal isteği, bir önceki kanalın URL alma, buffer yükleme ve hata deneme Coroutine `Job`'ını anında `cancel()` etmelidir.
4. **Hata Kurtarma Durumlarının İzolasyonu:**
   `networkRecoveryAttempt` ve `fallbackUrls` her yeni zapping çağrısında sıfırlanmalı ve aktif `Job`'a bağlanmalıdır.
5. **Kesintisiz Geçiş (Zero-Black-Screen / Gapless Transition):**
   Zapping sırasında `player.stop()` çağrılmamalıdır. Media3 ExoPlayer'da mevcut akış oynarken doğrudan `setMediaItem(newMediaItem)` ve `prepare()` çağrıldığında, `setKeepContentOnPlayerReset(true)` sayesinde eski kanalın son karesi donar ve yeni kanal açıldığı anda araya siyah ekran girmeden yumuşak geçiş yapılır.

---

### Önerilen Kod Mimarisi (Kotlin Örneği)

#### 1. ViewModel: Merkezi Kanal Değiştirme Yöneticisi
```kotlin
// MainViewModel.kt içerisinde önerilen yapı

class MainViewModel(...) : ViewModel() {
    
    // Aktif kanal tek kaynaktan (Single Source of Truth) yayınlanır
    private val _currentPlayingChannel = MutableStateFlow<LiveStream?>(null)
    val currentPlayingChannel: StateFlow<LiveStream?> = _currentPlayingChannel.asStateFlow()

    private var zappingJob: Job? = null

    /**
     * Tüm zapping ve kanal seçimlerinin tek giriş noktası.
     * @param targetChannel Açılmak istenen kanal
     * @param isZapping Halihazırda oynatıcı açıkken mi geçiş yapılıyor?
     */
    fun zapToChannel(targetChannel: LiveStream, isZapping: Boolean = true) {
        // 1. Önceki zapping ve hata kurtarma işlemlerini anında iptal et (Race Condition Çözümü)
        zappingJob?.cancel()
        playerManager.cancelAllRecoveryJobs()

        zappingJob = viewModelScope.launch {
            val previousChannel = _currentPlayingChannel.value
            if (previousChannel != null && previousChannel.streamId != targetChannel.streamId) {
                lastPlayedLiveChannel.value = previousChannel
            }

            // 2. Durumları güncelle
            _currentPlayingChannel.value = targetChannel
            activePreviewChannel.value = targetChannel
            recordRecentChannel(targetChannel)
            preferences.setLastPlayedStreamId(targetChannel.streamId)
            loadEpgForChannel(targetChannel.streamId)

            // 3. Tek noktadan URL çözümleme
            val urls = resolveStreamUrls(targetChannel)

            // 4. Oynatıcıya kesintisiz aktar (stop çağırmadan direkt setMediaItem)
            playerManager.zapPlay(urls = urls, isLive = true)

            // 5. YALNIZCA oynatıcı açık DEĞİLSE ekran geçişi yap (Siyah Ekran Çözümü)
            val isAlreadyOnPlayer = currentScreen.value is Screen.Player
            if (!isAlreadyOnPlayer && deviceMode.value != DeviceMode.TV) {
                navigateTo(
                    Screen.Player(
                        streamUrl = urls.firstOrNull() ?: "",
                        title = targetChannel.name,
                        isLive = true,
                        streamId = targetChannel.streamId,
                        categoryId = targetChannel.categoryId
                    )
                )
            }
        }
    }

    fun zapNext() {
        val list = liveStreams.value
        val currentId = _currentPlayingChannel.value?.streamId ?: return
        val idx = list.indexOfFirst { it.streamId == currentId }
        val nextChannel = if (idx != -1) list[(idx + 1) % list.size] else list.firstOrNull()
        nextChannel?.let { zapToChannel(it, isZapping = true) }
    }

    fun zapPrevious() {
        val list = liveStreams.value
        val currentId = _currentPlayingChannel.value?.streamId ?: return
        val idx = list.indexOfFirst { it.streamId == currentId }
        val prevChannel = if (idx > 0) list[idx - 1] else list.lastOrNull()
        prevChannel?.let { zapToChannel(it, isZapping = true) }
    }

    private fun resolveStreamUrls(channel: LiveStream): List<String> {
        val preferHls = streamFormat.value == "HLS"
        return if (!channel.directSource.isNullOrBlank()) {
            listOf(channel.directSource)
        } else {
            repository.buildLiveStreamUrls(channel.streamId, preferHls)
        }
    }
}
```

#### 2. TiviPlayerManager: Güvenli ve Sıfır Siyah Ekran Geçişi
```kotlin
// TiviPlayerManager.kt içerisinde önerilen yapı

class TiviPlayerManager(...) {
    private var playbackJob: Job? = null
    private var recoveryJob: Job? = null

    fun cancelAllRecoveryJobs() {
        recoveryJob?.cancel()
        recoveryJob = null
        networkRecoveryAttempt = 0
        decoderRecoveryAttempt = 0
        isReconnecting = false
    }

    /**
     * Zapping için optimize edilmiş oynatma:
     * player.stop() ÇAĞRILMAZ, doğrudan yeni MediaItem set edilir.
     * Bu sayede eski kare ekranda donar, siyah ekran/flaş oluşmaz.
     */
    fun zapPlay(urls: List<String>, isLive: Boolean = true) {
        cancelAllRecoveryJobs()
        this.fallbackUrls = urls.toMutableList()
        this.fallbackAttempt = 0
        this.isLiveStream = isLive
        _errorMessage.value = null

        val firstUrl = urls.firstOrNull() ?: return
        this.currentUrl = firstUrl
        this.currentPlayingUrl = firstUrl

        val player = initializePlayer(isLive)
        
        val mediaItem = MediaItem.Builder()
            .setUri(firstUrl)
            .apply {
                if (firstUrl.contains(".m3u8", ignoreCase = true)) {
                    setMimeType(MimeTypes.APPLICATION_M3U8)
                } else if (firstUrl.contains(".ts", ignoreCase = true) || isLive) {
                    setMimeType(MimeTypes.VIDEO_MP2T)
                }
            }.build()

        // KRİTİK: stop() çağırmadan doğrudan setMediaItem ile sıfır ekran kaybı
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }
}
```

---

## Özet ve Sonuç

| Sorun | Kök Neden | Önerilen Çözüm |
| :--- | :--- | :--- |
| **Kaydırmada Siyah Ekran** | Her zapping'de `navigateTo(Screen.Player)` çağrılması ve eski ekran dispose olurken `playerManager.stopAndClear()` çalıştırması. | Zapping sırasında `navigateTo` çağrılmamalı, `PlayerScreen` içindeyken `currentPlayingChannel` StateFlow üzerinden kanal bilgisi güncellenmelidir. |
| **Hızlı Zapping Race Condition** | Zapping isteklerinin iptal edilmemesi ve arkada çalışan `onPlayerError` Coroutine'inin eski kanalı geri getirmesi. | `zappingJob?.cancel()` ve `cancelAllRecoveryJobs()` ile her zapping isteğinde önceki tüm asenkron süreçler temizlenmelidir. |
| **Dual Player Çökmesi** | `secondaryExoPlayer` serbest bırakılmadan donanım dekoderlerinin tükenmesi ve `stopAndClear`'ın ikincil oynatıcıyı temizlememesi. | Zapping sırasında Dual-View açıksa ikincil oynatıcı otomatik durdurulmalı veya yazılım dekoderine (`SOFTWARE`) yönlendirilmelidir. |
| **TV Overlay Bayat Kanal Hatası** | `TvPlayerOverlay`'in Compose parametresi olarak sabit `streamId` alması ve `previewChannel` sonrası parametrenin güncellenmemesi. | `TvPlayerOverlay`, `viewModel.currentPlayingChannel` StateFlow'unu observe etmelidir. |
