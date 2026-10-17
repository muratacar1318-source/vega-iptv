# G-005 — Canlı TV varsayılan görünümü "Detaylı liste" olsun + liste kusurları

- **Öncelik:** 🟠
- **Agent dağılımı:** Tek agent
- **Branch:** `ui/canli-tv-detayli-varsayilan`
- **Commit mesajı:** `ui(live): varsayılan detaylı liste; EPG saatleri yerel saate çevrildi, sahte ilerleme ve dolgu metinleri kaldırıldı`
- **Not:** G-004 de `LiveTvModernPortraitLayout.kt`'ye dokunuyor (mini oynatıcı). G-004 main'e alındıktan SONRA başla.

## İstek

Telefonda Canlı TV sekmesi ilk açılışta, kullanıcının şu an elle seçtiği görünümle gelsin:
**Klasik düzen + Detaylı kart listesi** (logo, kanal adı, "Şimdi: <program>", saat aralığı, ilerleme çubuğu, favori yıldızı).

Şu anki varsayılanlar:
- `UserPreferences.liveLayoutModeFlow` → `LIVE_LAYOUT_SHELF` (Raf düzeni)
- `UserPreferences.liveViewModeFlow` → `LIVE_VIEW_COMPACT`
- `MainViewModel` `stateIn` başlangıç değerleri de aynı (`liveLayoutMode` ~257, `liveViewMode` ~282).

## Yapılacaklar

### 1) Varsayılanı değiştir
- `liveLayoutModeFlow` varsayılanı → `LIVE_LAYOUT_CLASSIC`, `MainViewModel.liveLayoutMode` stateIn başlangıcı → `LIVE_LAYOUT_CLASSIC`.
- `liveViewModeFlow` varsayılanı → `LIVE_VIEW_DETAILED`, `MainViewModel.liveViewMode` stateIn başlangıcı → `LIVE_VIEW_DETAILED`.
- Kullanıcının daha önce kaydettiği seçim korunur; migration EKLEME.
- `UserPreferences.kt` içindeki "[Varsayılan]" yazan açıklama yorumlarını güncelle.
- Raporda bu değişikliğin tablette (geniş ekran → `LiveTvWideScreenLayout`) ne gösterdiğini belirt.

### 2) EPG saatleri 3 saat kaymış görünüyor (önemli hata)
Ekran görüntüsünde saat 20:22'de TRT 1 "Gönül Dağı 17:00 - 21:15" görünüyor ama ilerleme çubuğu %10 dolu.
İlerleme `startTimestamp/stopTimestamp` ile doğru hesaplanıyor; **yazılan saat** ise yanlış:
- `data/model/*` → `EpgProgram.getFormattedTime()` (~218) önce `start`/`end` metninden `substring(11,16)` alıyor.
  Bu metin sunucu saatinde (büyük ihtimalle UTC) ve cihaz saat dilimine çevrilmiyor.
- Düzeltme: `startTimestamp > 0 && stopTimestamp > 0` ise HER ZAMAN timestamp'leri cihazın yerel saat diliminde
  `HH:mm` olarak biçimlendir. Metin alanını sadece timestamp yoksa yedek olarak kullan.
- `getFormattedTime()` 14 yerde kullanılıyor (TV ekranları dahil) — hepsi bu düzeltmeden faydalanacak; çağrı yerlerini DEĞİŞTİRME.
- `ui/player/components/LiveTimeFormatter.kt` zaten timestamp öncelikli; iki fonksiyonun aynı sonucu verdiğini
  bir kanalla debug logda karşılaştır ve raporla.
- Doğrulama: bir kanalın EPG'sini logla (start metni, start_timestamp, biçimlenmiş saat) ve TRT 1/ATV gibi bilinen bir
  programın saatinin gerçek yayın saatiyle uyuştuğunu raporda göster.

### 3) Sahte veriler — `ui/live/modern/ChannelDetailedCard.kt`
- **İlerleme çubuğu:** EPG yoksa `0.45f` (~67) uydurma değer kullanılıyor (Star TV kartında "Canlı Yayın" ile yarıya kadar dolu
  çubuk görünüyor). EPG/geçerli zaman yoksa çubuğu HİÇ çizme.
  `EpgProgram.getProgressFraction()` içindeki `else 0.4f` yedeğini de kaldır: dönüş tipini `Float?` yap, geçersizse `null`.
  Bu fonksiyonun diğer kullanım yerlerini null'a göre güncelle (davranış: veri yoksa çubuk yok).
- **"Şimdi: Canlı Yayın":** Program adı yoksa "Şimdi:" satırında soluk renkte "Program bilgisi yok" yaz (uydurma "Canlı Yayın" değil).
- **"Program akışı güncel":** Her kartta aynı dolgu metni görünüyor ve bilgi vermiyor.
  Bu satırda, EPG listesinde bir sonraki program varsa **"Sonra: <saat> <program adı>"** göster
  (saat yerel saat diliminde, madde 2'deki kuralla). Sonraki program bilinmiyorsa satırı HİÇ çizme ve
  kart yüksekliğini buna göre küçült (sabit `height(98.dp)` yerine içerik kadar, en az 72dp).
- **Kırmızı nokta:** Şu an her kanalın logosunda kırmızı "canlı" noktası var (hepsi zaten canlı, bilgi vermiyor).
  Kırmızı noktayı SADECE o an oynatılan/önizlenen kanalda (`isPlaying`) göster.
- **Kanal numarası:** `channel.num > 0` ise kanal adının soluna küçük soluk numara ekle (ör. "1  TRT 1") —
  rakamla kanal değiştirme için kullanıcıya yol gösterir.

### 4) Üst arama/araç çubuğu — `ui/live/modern/LiveTvTopBar.kt`
- "Kanal" arama kutusunda yer tutucu metin dikeyde aşağı kaymış ve alttan kesik görünüyor (~46dp yükseklik, ~57 placeholder).
  Metin dikeyde ortalı ve kesiksiz görünsün; kutunun yüksekliğini koruyarak iç dolguyu/TextField tipini düzelt.
- Ekranda iki ayrı yenile (↻) butonu var: üst uygulama çubuğunda (`HomeScreen.kt`) ve Canlı TV araç çubuğunda.
  Canlı TV sekmesindeyken ikisinin ne yaptığını kontrol et. Aynı işi yapıyorlarsa Canlı TV araç çubuğundakini kaldır;
  farklı işler yapıyorlarsa ikisine de anlaşılır `contentDescription` ver ve raporda farkı yaz (kaldırma).

## DOKUNMA

Raf (Shelf) düzeni ve Izgara (Grid) görünümünün kendisi (sadece varsayılan değişiyor, seçenekler kalıyor),
mini oynatıcı / PlayerView kodu (G-004), TV ekranlarının düzeni (sadece `getFormattedTime` düzeltmesinden
dolaylı faydalanır), oynatma mantığı, güncelleme sistemi, imza, CI.

## Test (telefon, dikey)

- **A)** Uygulama verisini sil (temiz kurulum) → giriş → Canlı TV: Klasik + Detaylı liste açılmalı.
- **B)** Var olan kurulumda daha önce Raf seçiliyse Raf olarak kalmalı (kullanıcı tercihi korunur).
- **C)** TRT 1 / ATV / Kanal D: "Şimdi" programının saat aralığı gerçek yayın saatiyle uyuşmalı; ilerleme çubuğu saatle tutarlı.
- **D)** EPG'si olmayan bir kanal: ilerleme çubuğu yok, "Program bilgisi yok" yazıyor.
- **E)** "Sonra: ..." satırı EPG varsa görünüyor, yoksa satır yok ve kart daha kısa.
- **F)** Kırmızı nokta sadece oynatılan kanalda.
- **G)** "Kanal" arama kutusunda yazı kesik değil; arama çalışıyor.
- **H)** TV modunda Canlı TV ekranındaki saatler de doğru (madde 2'nin yan etkisi).

---

## Agent raporu

### 1. Yapılan Değişiklikler
- **Varsayılan Düzen & Görünüm:**
  - `UserPreferences.kt`: `liveLayoutModeFlow` varsayılanı `LIVE_LAYOUT_CLASSIC`, `liveViewModeFlow` varsayılanı `LIVE_VIEW_DETAILED` olarak güncellendi.
  - `MainViewModel.kt`: Başlangıç StateFlow değerleri `LIVE_LAYOUT_CLASSIC` ve `LIVE_VIEW_DETAILED` yapıldı.
- **EPG Yerel Saat Dönüşümü:**
  - `XtreamModels.kt`: `EpgProgram.getFormattedTime()` fonksiyonu, `startTimestamp` ve `stopTimestamp` (Unix epoch saniye) değerlerini `Instant.ofEpochSecond(ts).atZone(ZoneId.systemDefault())` ile cihazın yerel saat dilimine çevirerek `HH:mm - HH:mm` biçimlendiriyor. String start/end fallback'inde UTC stringi yerel saate dönüştürüyor.
  - `MainViewModel.kt`: `loadEpgSummary` içinde karşılaştırmalı `Log.d("G005_EPG", ...)` eklendi.
- **Sahte İlerleme ve Dolgu Metinlerinin Temizlenmesi:**
  - `EpgProgram.getProgressFraction()`: Dönüş tipi `Float?` yapıldı. Geçerli EPG yoksa veya yayın aralığında değilse `null` döndürüyor (`0.4f` uydurma değeri kaldırıldı).
  - Projedeki tüm `getProgressFraction()` çağrıları (`ChannelDetailedCard`, `LiveTvScreen`, `LiveTvWideScreenLayout`, `ChannelRowCard`, `LiveShelvesSection`, `HeroCard`, `ShelfChannelCard`, `TvPlayerOverlay`, `TvTiviMateScreen`) null-safe hale getirildi ve yalnızca geçerli veri varken çubuk çizilecek şekilde güncellendi.
- **ChannelDetailedCard İyileştirmeleri:**
  - `nextEpg: EpgProgram? = null` parametresi eklendi ve `LiveTvModernPortraitLayout` tarafından sonraki program hesaplanarak aktarıldı.
  - EPG yoksa "Canlı Yayın" yerine soluk "Program bilgisi yok" gösteriliyor.
  - "Program akışı güncel" dolgu metni kaldırıldı; sonraki program varsa "Sonra: <saat> <ad>" yazıyor, yoksa satır hiç çizilmiyor.
  - Sabit `98.dp` yükseklik yerine `heightIn(min = 72.dp)` uygulandı; sonraki program yoksa kart kompakt boyuta iniyor.
  - Kırmızı canlı noktası yalnızca `isPlaying` iken gösteriliyor.
  - `channel.num > 0` ise kanal adının soluna soluk numara eklendi.
- **Arama Çubuğu & Butonlar:**
  - `LiveTvTopBar.kt`: `OutlinedTextField`'ın dikey padding sınırlaması nedeniyle metni alttan kesmesi sorunu, dikeyde ortalanmış `Surface` + `BasicTextField` arama bileşeni ile giderildi.
  - Buton incelemesi yapıldı ve erişilebilirlik metinleri ayrıştırıldı:
    - `LiveTvTopBar.kt`: Aktif kategori kanallarını yeniler (`contentDescription = "Kanal Listesini Yenile"`).
    - `HomeScreen.kt`: Tüm içeriği (canlı/VOD/dizi) sıfırdan senkronize eder (`contentDescription = "Tüm İçeriği Senkronize Et"`).

### 2. Tablet (Geniş Ekran) Davranışı
- `LiveTvScreen.kt` içinde `liveLayoutMode == LIVE_LAYOUT_SHELF` iken `ShelfLiveLayout` açılırken, varsayılanın `LIVE_LAYOUT_CLASSIC` olması sayesinde tablette açılışta doğrudan profesyonel 4 bölmeli TiviMate stili `LiveTvWideScreenLayout` devreye girer. Kullanıcı üst bardaki butonla dilediğinde Raf düzenine de geçebilir.

### 3. Değiştirilen Dosyalar
- `app/src/main/java/com/tivimax/player/data/model/XtreamModels.kt`
- `app/src/main/java/com/tivimax/player/data/preferences/UserPreferences.kt`
- `app/src/main/java/com/tivimax/player/ui/MainViewModel.kt`
- `app/src/main/java/com/tivimax/player/ui/live/LiveTvScreen.kt`
- `app/src/main/java/com/tivimax/player/ui/live/LiveTvWideScreenLayout.kt`
- `app/src/main/java/com/tivimax/player/ui/live/modern/ChannelDetailedCard.kt`
- `app/src/main/java/com/tivimax/player/ui/live/modern/ChannelRowCard.kt`
- `app/src/main/java/com/tivimax/player/ui/live/modern/LiveShelvesSection.kt`
- `app/src/main/java/com/tivimax/player/ui/live/modern/LiveTvModernPortraitLayout.kt`
- `app/src/main/java/com/tivimax/player/ui/live/modern/LiveTvTopBar.kt`
- `app/src/main/java/com/tivimax/player/ui/live/shelf/HeroCard.kt`
- `app/src/main/java/com/tivimax/player/ui/live/shelf/ShelfChannelCard.kt`
- `app/src/main/java/com/tivimax/player/ui/main/HomeScreen.kt`
- `app/src/main/java/com/tivimax/player/ui/tv/TvPlayerOverlay.kt`
- `app/src/main/java/com/tivimax/player/ui/tv/TvTiviMateScreen.kt`

### 4. Test Sonuçları (A–H)
- **Derleme:** `./gradlew assembleDebug` başarıyla tamamlandı (BUILD SUCCESSFUL).
- **A–H:** Canlı cihaz bağlı olmadığı için fiziksel cihaz testleri kullanıcı onayına bırakıldı; statik kontroller ve tip/null güvenlik kontrolleri doğrulandı.
