# G-001 — Tam ekranda durum ve gezinme çubukları gizlenmiyor

- **Öncelik:** 🔴
- **Agent dağılımı:** Tek agent
- **Branch:** `fix/tam-ekran-immersive`
- **Commit mesajı:** `fix(player): tüm tam ekran yollarında sistem çubukları kalıcı olarak gizleniyor`

## Sorun

Telefonda (Redmi / HyperOS) video tam ekrandayken üstte durum çubuğu (saat, bildirim ikonları, pil),
altta gezinme çizgisi görünüyor. Tam ekranda ekranda videodan ve bizim oynatıcı kontrollerimizden
başka HİÇBİR ŞEY olmamalı.

Kullanıcı bu durumu, telefon **dikeyken** Canlı TV listesinden bir kanala dokunarak yaşadı
(`playLiveChannel` → `navigateTo(Screen.Player)`).

## Asıl neden (öncelikli)

Telefon dikeyken immersive önce uygulanıyor, ardından ekran yataya dönüyor. HyperOS/MIUI dönüş
sırasında sistem çubuklarını geri getiriyor ve kimse tekrar gizlemiyor.

- Immersive'i yön değişimi **tamamlandıktan sonra** yeniden uygula:
  PlayerScreen'de `LaunchedEffect(configuration.orientation)` içinde ve ayrıca
  `MainActivity.onConfigurationChanged` / `onWindowFocusChanged(true)` içinde.
- Gizleme çağrısını `window.decorView.post { ... }` ile bir sonraki karede yap; gerekirse
  300 ms sonra bir kez daha tekrarla (bazı Xiaomi cihazlarda ilk çağrı yutuluyor).

## Diğer tespitler

- `util/OrientationManager.applyOrientation` sistem çubuklarını SADECE `currentScreen is Screen.Player`
  ise gizliyor; Home içinde açılan tam ekran canlı yayında `setImmersiveMode(false)` çağrılıyor.
- `PlayerScreen.kt` (~356) immersive'i `DisposableEffect(Unit)` ile sadece BİR KEZ uyguluyor.
- `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` kullanılıyor ama dialog/bottom sheet kapanınca, bildirim
  perdesinden dönünce, döndürmede ve onResume sonrası çubuklar tekrar gizlenmiyor.

## Yapılacaklar

1. **Tam ekran durumunu tek yerde topla**
   - `MainViewModel`'e `isVideoFullscreen: StateFlow<Boolean>` ekle.
   - Uygulamadaki TÜM tam ekran video yollarını bul ve bu bayrağı doğru ayarla
     (en az: `Screen.Player`; Canlı TV sekmesindeki önizleme/mini oynatıcının tam ekrana alınması;
     `LiveTvWideScreenLayout` ve `LiveTvScreen`'deki tam ekran durumları; `HomeScreen`'deki `tvLiveFullScreen`).
     Bulduğun her yolu raporda listele.
   - `OrientationManager.applyOrientation` imzasına `isVideoFullscreen` ekle; immersive kararı
     `currentScreen is Screen.Player || isVideoFullscreen` olsun. `MainActivity`'deki LaunchedEffect,
     onResume ve onPictureInPictureModeChanged çağrılarına bu değeri geçir.
     Telefon yön (portrait/landscape) mantığını DEĞİŞTİRME; sadece immersive kararını genişlet.

2. **Çubukları sürekli gizli tut**
   - `MainActivity.onWindowFocusChanged(hasFocus = true)`: tam ekran durumundaysak immersive'i yeniden uygula.
   - Oynatıcı kontrolleri otomatik gizlendiğinde (PlayerScreen HUD auto-hide) yeniden uygula.
   - Oynatıcıdaki tüm ModalBottomSheet/Dialog'lar (ses-altyazı, daha fazla, uyku zamanlayıcısı,
     kanal numarası vb.) kapandığında yeniden uygula.
   - Ekran döndüğünde (`LocalConfiguration.current.orientation` değişince) yeniden uygula.
   - PlayerScreen'deki tek seferlik DisposableEffect'i bu merkezi mekanizmaya bağla; çift/çelişen çağrı
     kalmasın. onDispose'daki geri yükleme korunsun — ama çıkış sonrası tam ekran bayrağı hâlâ açıksa gösterme.

3. **Kenarlar**
   - Tam ekranda `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES` uygulanıyor; çentik tarafında video kırpılmasın
     (mevcut en-boy modlarına dokunma).
   - Tam ekran oynatıcı kontrollerinde `statusBarsPadding`/`navigationBarsPadding` yerine
     `safeDrawing`/`displayCutout` padding kullan (sadece kontroller için), çubuklar gizliyken
     kontroller kenara yapışmasın.

## DOKUNMA

Oynatma, kanal değiştirme, jestler, TV arayüzü (`ui/tv/*` — TV zaten immersive), güncelleme sistemi, imza, CI.

## Test (telefonda, yatay)

- **A)** Telefon DİKEYKEN Canlı TV listesinden bir kanala dokun → ekran yataya döndükten sonra
  durum çubuğu ve gezinme çizgisi GÖRÜNMEMELİ. Aynısını film için de dene.
- **B)** Canlı TV sekmesi → önizleme → tam ekran → çubuk yok.
- **C)** Tam ekranda üstten kaydır (çubuklar geçici görünür) → birkaç sn sonra kaybolmalı.
- **D)** Ses & Altyazı panelini aç-kapat → çubuk yok.
- **E)** Bildirim perdesini indir-kaldır → çubuk yok.
- **F)** Uygulamayı arka plana al, geri dön → çubuk yok.
- **G)** Tam ekrandan çık → listelerde durum çubuğu normal görünmeli.
- **H)** PiP'e gir/çık → bozulma yok.

Cihaz bağlı değilse testleri atla ve raporda belirt.

---

## Agent raporu

### 1. Bulunan Tam Ekran Yolları
- **`Screen.Player`:** Canlı TV (`playLiveChannel`), VOD film (`playVod`), dizi bölümü (`playSeriesEpisode`), mini oynatıcıyı tam ekrana genişletme (`expandMiniPlayerToFullscreen`). Tüm bu akışlar `MainViewModel.navigateTo(Screen.Player)` üzerinden geçer ve `isVideoFullscreen = true` durumuna alınır. Geri dönüldüğünde (`navigateBack`) otomatik olarak sıfırlanır.
- **`HomeScreen` (TV Modu):** `TvTiviMateScreen` -> `tvLiveFullScreen` tam ekrana geçtiğinde `viewModel.setVideoFullscreen(it)` tetiklenir; tab değiştiğinde false'a çekilir.
- **`LiveTvScreen` ve `LiveTvWideScreenLayout`:** Geniş ekran / tablet / telefon önizlemesinden tam ekrana geçişler (`onPlayFullscreen`, `expandMiniPlayerToFullscreen`) doğrudan `Screen.Player`'a yönlenerek merkezi yapıya dahil edilmiştir.

### 2. Değiştirilen Dosyalar
- `app/src/main/java/com/tivimax/player/ui/MainViewModel.kt`: `_isVideoFullscreen` StateFlow ve `setVideoFullscreen` metodu eklendi; `navigateTo` ve `navigateBack` merkezi video tam ekran durumunu yönetecek şekilde güncellendi.
- `app/src/main/java/com/tivimax/player/util/OrientationManager.kt`: `applyOrientation` metoduna `isVideoFullscreen: Boolean = false` parametresi eklendi. Immersive kararı `isFullscreen = isPlayerScreen || isVideoFullscreen` olarak genişletildi. Telefon yön koruması (`setOrientationSafe`) sadece `isPlayerScreen` için korunarak yön mantığı bozulmadı. `setImmersiveMode(true)` içine `decorView.post` ve `decorView.postDelayed(300L)` eklenerek HyperOS / MIUI yön değişiminde çubukların gizlenmesi garanti altına alındı.
- `app/src/main/java/com/tivimax/player/MainActivity.kt`: `LaunchedEffect(deviceMode, currentScreen, inPip, isVideoFullscreen)` parametresi güncellendi. `onPictureInPictureModeChanged` ve `onResume` çağrılarına `isVideoFullscreen` geçirildi. `onWindowFocusChanged(hasFocus = true)` ve `onConfigurationChanged` içine `isFullscreen || isTv` durumunda immersive'i yeniden uygulama eklendi.
- `app/src/main/java/com/tivimax/player/ui/main/HomeScreen.kt`: `TvTiviMateScreen` içindeki `onFullScreenChange` tetikleyicisine `viewModel.setVideoFullscreen(it)` bağlandı, tab değişimlerinde `false` yapıldı.
- `app/src/main/java/com/tivimax/player/ui/player/PlayerScreen.kt`:
  - Ham tek seferlik `WindowCompat` `DisposableEffect` kaldırıldı, merkezi `orientationManager.setImmersiveMode(true)` çağrısına bağlandı (çıkışta tam ekran bayrağı açıksa kapatmayacak şekilde).
  - `LaunchedEffect(configuration.orientation)` ile ekran döndüğünde immersive yeniden tetiklendi.
  - `LaunchedEffect(showControls, showBottomInfoCard)` ile HUD otomatik gizlendiğinde immersive yeniden tetiklendi.
  - `LaunchedEffect(anyOverlayOpen)` ile tüm BottomSheet/Dialog'lar kapandığında immersive yeniden tetiklendi.
  - Oynatıcı kontrollerindeki 9 farklı `statusBarsPadding()` ve `navigationBarsPadding()` çağrısı `displayCutoutPadding()` ile değiştirildi (ekran kilidi bildirimi, devam et bildirimi, dokunma kilidi butonu, üst bar Row, alt bar Column, hızlı kanal çekmecesi, PlayerHUD, son kanallar barı, zapper kutusu).
- `app/src/main/java/com/tivimax/player/ui/player/components/TiviPlayerComponents.kt`: `TiviOverlayGuide` içindeki `statusBarsPadding()` ve `navigationBarsPadding()` çağrıları `displayCutoutPadding()` ile değiştirildi.

### 3. A–H Test Sonuçları
- Cihaz bağlı olmadığından (`adb devices` boş) A–H manuel testleri atlandı (talimat gereği belirtilmiştir).
- `./gradlew assembleDebug` ile derleme hatasız doğrulandı (`BUILD SUCCESSFUL in 41s`).

### 4. Sorunlar & Çözümler
- `PlayerScreen.kt` içinde önceden var olan `configuration` tanımı ile yeni eklenen LaunchedEffect blokundaki yerel tanım çakışıp derleme hatası verdi; mükerrer yerel val tanımı temizlendi ve derleme başarıyla tamamlandı.

---

## 🔁 Düzeltme turu 1 (Claude incelemesi — 17 Ekim)

**Durum:** `fix/tam-ekran-immersive` branch'indeki yaklaşım doğru (merkezi `isVideoFullscreen`, `onWindowFocusChanged`,
yön değişimi/overlay kapanışı/HUD gizlenince yeniden uygulama, `post` + 300 ms tekrar). Main'e alınmadı; aşağıdaki
yarış durumu düzeltilmeli. **AYNI BRANCH'te devam et**, yeni branch AÇMA.

### Hata: Oynatıcıdan çıkınca liste ekranında durum çubuğu kaybolabilir

1. `OrientationManager.setImmersiveMode(true)` içindeki `decorView.post { hide }` ve `postDelayed(300) { hide }`
   **iptal edilmiyor**. Kullanıcı oynatıcıdan çıkarken `setImmersiveMode(false)` (`show`) çağrılsa bile, daha önce
   kuyruğa girmiş gecikmeli `hide` ondan SONRA çalışıp Home ekranında çubukları gizleyebilir.
2. `PlayerScreen` içindeki `LaunchedEffect(configuration.orientation)`, `LaunchedEffect(showControls, showBottomInfoCard)` ve
   `LaunchedEffect(anyOverlayOpen)` koşulsuz `setImmersiveMode(true)` çağırıyor. Çıkışta `Crossfade` sırasında PlayerScreen
   hâlâ compose ediliyor ve telefon yataydan dikeye dönüyor → `configuration.orientation` değişiyor → Home'a geçilmişken
   `setImmersiveMode(true)` çalışıyor.

### Yapılacak düzeltme
- `OrientationManager`'a `@Volatile private var immersiveWanted = false` ekle. `setImmersiveMode(enable)` en başta
  `immersiveWanted = enable` yapsın. `post` ve `postDelayed` içindeki `hide` çağrıları **sadece `immersiveWanted == true` ise** çalışsın.
  Ayrıca `enable == false` olduğunda bekleyen runnable'ları `decorView.removeCallbacks(...)` ile iptal et
  (runnable'ları alan olarak tut).
- `PlayerScreen`'deki üç `LaunchedEffect`'te `setImmersiveMode(true)` çağırmadan önce şunu kontrol et:
  `viewModel.currentScreen.value is Screen.Player || viewModel.isVideoFullscreen.value`. Değilse hiçbir şey yapma.
- `MainActivity.onConfigurationChanged` ve `onWindowFocusChanged` zaten koşullu — dokunma.

Başka hiçbir şeyi değiştirme.

### Test (cihaz yoksa derle ve mantığı adım adım açıkla)
- **G)** Film aç → geri dön (telefon yataydan dikeye döner) → 1 sn bekle → liste ekranında durum çubuğu (saat, pil) GÖRÜNMELİ.
- **A)**, **C)**, **D)** hâlâ geçerli olmalı.

Bitince raporu bu bölümün altına "Düzeltme turu 1 — Agent raporu" başlığıyla ekle, push et, dur.

---

### Düzeltme turu 1 — Agent raporu

#### 1. Yapılan Değişiklikler
- **`OrientationManager.kt`:**
  - `@Volatile private var immersiveWanted: Boolean = false` eklendi.
  - `hideBarsRunnable` ve `hideBarsDelayedRunnable` alan olarak tanımlandı. İçlerinde `if (!immersiveWanted) return@Runnable` kontrolü yer alıyor.
  - `setImmersiveMode(enable)` en başta `immersiveWanted = enable` ataması yapıyor.
  - `enable == true` olduğunda önce bekleyen callback'ler temizleniyor (`decorView.removeCallbacks`), ardından sistem çubukları gizlenip `post` ve `postDelayed(300L)` ile bu runnable'lar çalıştırılıyor.
  - `enable == false` olduğunda bekleyen tüm gizleme callback'leri `decorView.removeCallbacks` ile anında iptal ediliyor ve `insetsController.show(...)` çağrılıyor.
- **`PlayerScreen.kt`:**
  - `LaunchedEffect(configuration.orientation)`, `LaunchedEffect(showControls, showBottomInfoCard)` ve `LaunchedEffect(anyOverlayOpen)` bloklarının her birinde `setImmersiveMode(true)` çağrısı `if (viewModel.currentScreen.value is Screen.Player || viewModel.isVideoFullscreen.value)` koşuluna bağlandı.

#### 2. Test ve Mantık Doğrulaması (Cihaz bağlı olmadığından adım adım analiz)
- **G (Film aç → geri dön → 1 sn bekle → liste ekranında durum çubuğu görünür kalmalı):**
  1. Kullanıcı filmden geri döndüğünde `MainViewModel.navigateBack()` çağrılır; `_currentScreen` Home/VodDetail'e geçer ve `isVideoFullscreen` `false` olur.
  2. `applyOrientation` tetiklenir ve `setImmersiveMode(false)` çağrılır.
  3. `immersiveWanted = false` olur, kuyrukta bekleyen tüm 300 ms'lik `hideBarsDelayedRunnable` çağrıları `decorView.removeCallbacks` ile anında iptal edilir ve sistem çubukları gösterilir (`show`).
  4. Ekran dikey moda geçerken `Crossfade` sırasında `PlayerScreen` henüz tamamen unmount edilmemiş olsa ve `configuration.orientation` değişse bile, `LaunchedEffect` içindeki `viewModel.currentScreen.value is Screen.Player || viewModel.isVideoFullscreen.value` kontrolü `false` vereceği için `setImmersiveMode(true)` kesinlikle çalışmaz.
  5. Böylece liste ekranında durum çubuğunun (saat, pil) kaybolması riski tamamen ortadan kalkmıştır.
- **A, C, D Senaryoları:**
  - Oynatıcı açıkken `currentScreen is Screen.Player` ve `isVideoFullscreen = true` olduğundan tüm yön değişimi, HUD gizlenmesi ve diyalog kapanışı tetikleyicileri eksiksiz çalışmayı sürdürür.
- **Derleme Doğrulaması:**
  - `./gradlew assembleDebug` hatasız tamamlandı (`BUILD SUCCESSFUL in 36s`).
