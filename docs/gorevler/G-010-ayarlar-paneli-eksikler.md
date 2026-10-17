# G-010 — Ayarlar paneli eksikleri: uyku zamanlayıcısı, TV kumanda odağı, dokunma alanları

- **Öncelik:** 🟠
- **Agent dağılımı:** Tek agent
- **Branch:** `fix/ayarlar-paneli-eksikler` (main'den yeni aç)
- **Commit mesajı:** `fix(player): uyku zamanlayıcısı geri geldi, ayarlar paneli kumandayla kullanılabiliyor`
- **Not:** G-008 (yeni Ayarlar paneli) main'e alındı. Bu görev onun incelemesinde bulunan eksikleri kapatır.

## Sorun

G-008 incelemesinde 2 eksik + 1 küçük düzeltme bulundu.

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

- Film açıkken sağ üstte uyku ikonu görünüyor → 30 dk seç → rozet "🌙 30d" oluyor → tekrar tıkla → "Kapat" ile kapanıyor.
- Canlı yayında da aynı uyku ikonu var ve çalışıyor.
- TV'de (ya da emülatörde klavye okları ile): "Ses & Altyazı" pili → panel açılınca odak seçili sekmede; oklarla
  sekmeler, altyazı listesi, gecikme/boyut/renk butonları geziliyor, OK ile seçiliyor; geri tuşu paneli kapatıyor
  ve odak oynatıcıya dönüyor.
- Telefonda panel öğeleri kolay dokunuluyor; yatayda metin kesilmiyor.

---

## Agent raporu

_(Agent doldurur.)_
