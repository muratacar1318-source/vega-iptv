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

_(Agent doldurur: EPG saat doğrulama logu, tablet davranışı, yenile butonları incelemesi, A–H sonuçları.)_
