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

_(Agent doldurur: bulunan tam ekran yolları, değiştirilen dosyalar, A–H sonuçları, sorunlar.)_

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
