# G-019 — Yeni logo: uygulama simgesi, TV afişi ve uygulama içi logo ("Yıldızlı V")

- **Öncelik:** 🟠
- **Agent dağılımı:** Tek agent
- **Branch:** `feat/yeni-logo`
- **Commit mesajı:** `feat(brand): yeni Vega logosu — uygulama simgesi, TV afişi ve uygulama içi logo`

## Neden

Uygulamanın kendine ait logosu yok: telefondaki simge **Android Studio'nun hazır yeşil kareli simgesi**
(`res/drawable/ic_launcher_background.xml` = `#3DDC84` ızgara), uygulama içindeki logo ise sıradan
`Icons.Default.Tv` ikonu. Kullanıcı "Yıldızlı V" logosunu seçti.

**Logo:** camgöbeği (#00E5FF) kalın bir **V** + sağ üstünde beyaz **4 köşeli parıltı yıldızı** (Vega yıldızı),
koyu zemin #0A0E17. Kaynaklar (Claude hazırladı, repoda):
- `docs/brand/vega-logo.svg` — ana çizim (100×100 koordinat)
- `docs/brand/vega-icon-512.png` — 512 px simge (mağaza/önizleme için)
- `docs/brand/tv_banner.png` — 320×180 TV afişi (logo + "Vega IPTV" yazısı)

Çizim verisi (100×100 koordinatta, aşağıda tekrar kullanılacak):
- V: `M24,30 L45,74 L63,37` — çizgi, kalınlık 10, uçlar ve köşe yuvarlak, renk #00E5FF
- Yıldız: `M77,11 Q78.8,21.2 89,23 Q78.8,24.8 77,35 Q75.2,24.8 65,23 Q75.2,21.2 77,11 Z` — dolgu #FFFFFF

## Yapılacaklar

### 1) Uygulama simgesi (adaptive icon)
- `res/drawable/ic_launcher_background.xml` → tek renk düz zemin: 108×108 viewport, `#0A0E17` dolgu
  (eski yeşil ızgara yollarının hepsi silinsin).
- `res/drawable/ic_launcher_foreground.xml` → 108×108 viewport; logoyu güvenli alana sığdırmak için
  `<group android:scaleX="0.62" android:scaleY="0.62" android:translateX="23" android:translateY="23">`
  içine yukarıdaki iki yol (V: `strokeColor="#00E5FF" strokeWidth="10" strokeLineCap="round" strokeLineJoin="round" fillColor="#00000000"`;
  yıldız: `fillColor="#FFFFFF"`).
- `res/drawable/ic_launcher_monochrome.xml` → foreground ile aynı ama iki yol da `#FFFFFF`
  (Android 13 temalı simgeler için). `mipmap-anydpi-v26/ic_launcher.xml` ve `ic_launcher_round.xml`'de
  `<monochrome>` bu dosyayı göstersin.
- `mipmap-*dpi/ic_launcher*.webp` dosyaları minSdk 26 olduğu için kullanılmıyor; **silme**, dokunma.

### 2) TV afişi
- `docs/brand/tv_banner.png`'yi `res/drawable-xhdpi/tv_banner.png` olarak kopyala.
- `AndroidManifest.xml`: `android:banner="@mipmap/ic_launcher"` → `android:banner="@drawable/tv_banner"`.
  Manifest'te başka hiçbir şeye dokunma.

### 3) Uygulama içi logo bileşeni
- `res/drawable/ic_vega_mark.xml`: 100×100 viewport, 48dp; zeminsiz, sadece V (#00E5FF) ve yıldız (#FFFFFF).
- Ortak Composable: `ui/components/VegaLogo.kt`
  - `VegaLogoMark(size: Dp, modifier, withTile: Boolean = true)` — `withTile` true ise köşeleri yuvarlak
    (boyutun %22'si) `#0A0E17` kare + 1dp `TiviCardBorder` çerçeve içinde `ic_vega_mark`; false ise sadece işaret.
    `Image(painterResource(R.drawable.ic_vega_mark))` kullan (tint YOK).
  - `VegaWordmark(modifier, size: TextUnit = 22.sp)` — `VegaLogoMark` + "Vega" (kalın, `TiviTextPrimary`) +
    "IPTV" (ince, `TiviTextSecondary`) yan yana, aralarında boşluk.
- Logonun yerine geçeceği yerler (eski `Icons.Default.Tv` + mor/indigo gradyan kutular):
  - `Navigation.kt` ~114-130: açılış/yükleme ekranındaki 90dp gradyan kutu → `VegaLogoMark(size = 96.dp)`.
  - `ui/login/LoginScreen.kt` ~89: giriş ekranı logosu → `VegaLogoMark`.
  - `ui/main/HomeScreen.kt`: üst bardaki uygulama logosu (`appName` parametreli başlık, ~451) → `VegaLogoMark(40.dp)`
    + mevcut "Vega IPTV" yazısı.
  - `ui/sync/PlaylistSyncScreen.kt` ~127 ve ~305: liste indirme ekranındaki uygulama logosu → `VegaLogoMark`.
  - `ui/tv/TvNavigationRail.kt` ~94: TV yan menüsünün en üstündeki uygulama ikonu → `VegaLogoMark(32.dp)`.
  - `ui/devicemode/DeviceModeSetupScreen.kt` ~117: başlıktaki uygulama logosu → `VegaLogoMark`.
    (Aynı dosyadaki ~181 / ~223 `Icons.Default.Tv` "TV cihazı" seçeneğinin simgesidir — onlara DOKUNMA.)
  - Başka yerde "uygulamanın kendi logosu" olarak kullanılan `Icons.Default.Tv` görürsen raporla, değiştirme.
  "Canlı TV" menü simgesi, kanal yer tutucusu gibi **TV anlamındaki** `Icons.Default.Tv` kullanımları kalacak.

## DOKUNMA
Uygulama adı, paket adı, `AppConfig`, güncelleme sistemi, imza, CI, yerleşimler (sadece logo öğesi değişir).

## Test
- Telefonda ana ekran simgesi koyu zeminde camgöbeği V + beyaz yıldız; yuvarlak/kare simge şekillerinde kesilmiyor.
- Android 13+ "temalı simgeler" açıkken tek renkli görünüyor.
- TV'de uygulama satırında yeni afiş (logo + "Vega IPTV").
- Açılış, giriş, liste indirme, ana sayfa üst bar ve TV yan menüde yeni logo.
- Test edemediğin maddeyi "test edilemedi" yaz. APK üretme.

---

## Agent raporu

_(Agent doldurur.)_
