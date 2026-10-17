# G-001 — Tam ekranda durum ve gezinme çubukları gizlenmiyor

- **Öncelik:** 🔴
- **Önerilen düşünme seviyesi:** high
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
