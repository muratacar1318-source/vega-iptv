# G-010 — Ayarlar paneli boş görünüyor (ACİL) + uyku zamanlayıcısı, TV kumanda odağı, dokunma alanları

- **Öncelik:** 🔴 (v1.3.0'da panel kullanılamıyor)
- **Agent dağılımı:** Tek agent
- **Branch:** `fix/ayarlar-paneli-eksikler` (main'den yeni aç)
- **Commit mesajı:** `fix(player): ayarlar paneli içeriği görünüyor, uyku zamanlayıcısı geri geldi, panel kumandayla kullanılabiliyor`
- **Not:** G-008 (yeni Ayarlar paneli) main'e alındı. Bu görev onun incelemesinde bulunan eksikleri kapatır.

## Sorun

G-008 incelemesinde 2 eksik + 1 küçük düzeltme bulundu. Ayrıca kullanıcı v1.3.0'da panelin **boş** göründüğünü
bildirdi (madde 0) — en önemli madde bu.

### 0) 🔴 Sekme butonları tüm paneli kaplıyor, içerik hiç görünmüyor — `PlayerSettingsPanel.kt`
Kullanıcının telefonunda (yatay, film ve canlı yayın): "Ses / Altyazı / Görüntü / Hız" sekme butonları panelin
başlıktan altına kadar **bütün yüksekliğini** kaplıyor; ses listesi, altyazı listesi, gecikme, boyut, renk, hız hiçbiri
görünmüyor. Panel tamamen işe yaramaz durumda.

**Sebep:** Sekme `Surface`'ının içindeki `Box(modifier = Modifier.fillMaxSize(), ...)` (~231. satır). Sekme `Row`'u
yükseklik sınırı olmadan ölçüldüğü için `fillMaxSize` kalan tüm yüksekliği alıyor; ardından gelen
`Box(Modifier.weight(1f))` içerik alanına 0 yükseklik kalıyor.

**Düzeltme:**
- Sekme `Surface`'ında `.heightIn(min = 38.dp)` yerine **sabit** `.height(40.dp)` kullan.
- İçindeki `Box`'ta `Modifier.fillMaxSize()` yerine sadece
  `Modifier.fillMaxWidth()` + `contentAlignment = Alignment.Center` kullan (ya da Box'ı kaldırıp Text'i ortala).
- Panel içinde yüksekliği sınırsız bir satırda (Row/Column, `weight` almamış) başka `fillMaxSize()` kalmadığını kontrol
  et. (`Modifier.size(...)` verilmiş butonların içindeki `fillMaxSize()` sorun değil.)
- **Doğrula:** Yatay telefonda film → "Ses & Altyazı" → Altyazı sekmesi: sekmeler ~40dp yüksekliğinde tek satır, altında
  altyazı listesi + Gecikme + Boyut + Renk görünüyor. Canlı yayında Ses sekmesinde ses parçaları (ya da "Ek ses parçası
  bulunamadı") görünüyor. Dikeyde de aynı. Raporda bunu açıkça yaz.


### 1) Uyku zamanlayıcısı kayboldu (geri getir) — `PlayerScreen.kt`
Eskiden uyku zamanlayıcısı SADECE silinen "Medya ve Oynatma Seçenekleri" sayfasından kuruluyordu. Şimdi sağ üstteki
🌙 rozeti yalnızca zamanlayıcı **zaten açıkken** görünüyor (`if (sleepTimerSeconds != null)`), yani kullanıcı artık
hiç zamanlayıcı kuramıyor (film/dizi ve canlı yayın).
- Sağ üst gruptaki (`// Sağ Üst: Gerçek zamanlı Saat ...`) uyku öğesini **her zaman** göster:
  - Zamanlayıcı kapalıyken: `Icons.Default.Bedtime` ikonlu küçük buton (dokunma alanı ≥ 40dp, ikon 20dp,
    `contentDescription = "Uyku Zamanlayıcısı"`), diğer üst butonlarla aynı görünüm.
  - Açıkken: mevcut "🌙 Nd" rozeti aynen kalsın.
  - İkisinde de tıklama → `showSleepTimerDialog = true` (mevcut diyalog; "Kapat" seçeneği zaten var).
- Uyku diyaloğunun kendisine ve `playerManager.setSleepTimer/cancelSleepTimer` mantığına DOKUNMA.
- TV'de bu butona kumandayla ulaşılabilsin (diğer sağ üst butonlarla aynı odak zinciri).

### 2) TV kumandasıyla panel kullanılamıyor — `PlayerSettingsPanel.kt`
Panel açılınca odak panele geçmiyor (kontroller gizlendiği için odak kayboluyor), kumandayla sekmelere/öğelere
ulaşılamıyor (Test I).
- Panelde `val firstFocus = remember { FocusRequester() }` oluştur; **seçili sekme butonuna** `.focusRequester(firstFocus)` ver.
- `LaunchedEffect(Unit) { delay(100); runCatching { firstFocus.requestFocus() } }` ile panel açılınca odakla.
- Sekme butonları, liste öğeleri ve [−][+][⟲], boyut, renk butonları D-pad ile seçilebilsin (renk daireleri şu an
  `clickable(indication = null)`; bunları `Surface(onClick = …)` yap ki odaklanabilsin ve odakta görünür çerçeve olsun).
- Panel kapanınca (✕ ya da dışa dokunma) PlayerScreen'deki `onClose` içinde `playerFocusRequester.requestFocus()`'u
  `try/catch` ile çağır (geri tuşu yolunda zaten var).

### 3) Dokunma alanları — `PlayerSettingsPanel.kt`
Gecikme [−] [+] [⟲] butonları 34dp, boyut ve renk butonları 36dp. Hepsini **44dp** yap (ikon boyutları aynı kalabilir).
Yatay 340dp panelde sığıyor; sığmazsa `spacedBy` aralıklarını 4dp'ye indir, metinleri kesme.

## DOKUNMA

G-007 sarma/bilgi balonu sistemi, panel sekmeleri ve düzeni, parça seçimi/altyazı mantığı
(`DelayedSubtitleController`), jestler, `ui/tv/*`, güncelleme, imza, CI.

## Test

- **Önce madde 0:** panelde her sekmenin içeriği görünüyor (yatay + dikey, film + canlı yayın).
- Film açıkken sağ üstte uyku ikonu görünüyor → 30 dk seç → rozet "🌙 30d" oluyor → tekrar tıkla → "Kapat" ile kapanıyor.
- Canlı yayında da aynı uyku ikonu var ve çalışıyor.
- TV'de (ya da emülatörde klavye okları ile): "Ses & Altyazı" pili → panel açılınca odak seçili sekmede; oklarla
  sekmeler, altyazı listesi, gecikme/boyut/renk butonları geziliyor, OK ile seçiliyor; geri tuşu paneli kapatıyor
  ve odak oynatıcıya dönüyor.
- Telefonda panel öğeleri kolay dokunuluyor; yatayda metin kesilmiyor.

---

## Agent raporu

_(Agent doldurur.)_
