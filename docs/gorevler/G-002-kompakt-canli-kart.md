# G-002 — Canlı yayın alt kartını kompaktlaştır, sahte EPG/kalite verilerini kaldır

- **Öncelik:** 🟠
- **Branch:** `ui/kompakt-canli-oynatici`
- **Commit mesajı:** `ui(player): kompakt canlı yayın kartı, sahte EPG/kalite verileri kaldırıldı, tutarlı üst düğmeler`
- **Not:** G-001 ile aynı dosyalara (PlayerScreen) dokunur. G-001 main'e alındıktan SONRA başla.

## Sorun

Telefon yatayken tam ekran canlı yayında alttaki bilgi kartı ekran yüksekliğinin ~%45'ini (~190dp)
kaplıyor. Kartta uydurma veriler var. Kullanıcı kompakt bir görünüm istiyor.

## Kapsam

SADECE telefon/tablet tam ekran CANLI YAYIN oynatıcısının alt bilgi kartı ve üst sağ düğme grubu.

- `ui/player/components/TiviPlayerComponents.kt` → `TiviBottomInfoCard` (tek kullanım: PlayerScreen.kt ~1697)
- `ui/player/components/LivePlayerActionBar.kt` (tek kullanım: PlayerScreen.kt ~1711)
- `ui/player/PlayerScreen.kt` → üst sağ düğme grubu (~1490-1610)
- `ui/player/components/LivePlayerMoreSheet.kt` (sadece 4. madde)

## Yapılacaklar

### 1) Sahte verileri kaldır
- a. `LiveTimeFormatter.calculateProgress`: EPG yoksa veya zaman aralığı geçersizse 0.45 yerine `null` dön
  (dönüş tipi `Float?`). Kartta progress null ise ilerleme çubuğunu HİÇ çizme. Ayrıca
  `TiviPlayerComponents.kt` ~543'teki `else 0.45f` yedeğini kaldır. Bu fonksiyonun başka kullanım yerleri
  varsa null'a göre güncelle; davranışlarını bozma.
- b. Kalite rozeti: `diagnostics.resolutionBadge` / `frameRateBadge` boşsa uydurma "FHD 1080p" / "50 FPS"
  GÖSTERME. Sadece gerçek değer varsa göster; ikisi de boşsa rozeti hiç çizme.
- c. Program adı yoksa "Canlı Yayın" yer tutucu satırını çizme; satır tamamen kalksın.

### 2) Kompakt yerleşim — TiviBottomInfoCard
- **Yatay** (isPortrait == false) → TEK SATIR, hedef yükseklik ≤ 72dp (ilerleme çubuğu dahil):
  `[kanal no 16sp] [logo 32dp] [kanal adı 15sp bold · program adı 13sp (tek satır, ellipsis)] [saat aralığı 11sp]`
  sağda: aksiyon ikonları (madde 3) + favori yıldızı + genişlet oku.
  Altında, SADECE gerçek EPG varsa, 2dp ilerleme çubuğu.
  Kalite rozeti (gerçekse) tek satırda yer kalmazsa genişletilmiş bölüme taşınsın.
  Kart genişliği: `widthIn(max = 760.dp)`.
- **Dikey** → İKİ SATIR, hedef yükseklik ≤ 104dp:
  1. satır: kanal no, logo 32dp, kanal adı + program adı (tek satır), favori, genişlet.
  2. satır: aksiyon ikonları eşit aralıklı.
- Genel:
  - "Sıradaki: ..." satırı varsayılan görünümden kaldırılsın; sadece genişletilmiş bölümde görünsün.
  - İç padding: yatay 12dp, dikey 6dp. Ayırıcı çizgi (HorizontalDivider) kaldırılsın.
  - Genişlet (˄) davranışı, yukarı/aşağı sürükleme jesti, onDismiss, görünürlük animasyonu AYNEN kalsın.
  - Genişletilmiş bölüm: açıklama (3 satır), sonraki 2 program, kalite rozeti (gerçekse), "Sıradaki".

### 3) Aksiyon butonları — LivePlayerActionBar
- Etiketleri kaldır, SADECE ikon. İkon 22dp, dokunma alanı en az 44x44dp.
- contentDescription'lar kalsın. Uzun basınca Material3 `TooltipBox`/`PlainTooltip` ile adını göster.
- "Önceki kanal": `take(6)` kırpması kaldırılsın; ad sadece tooltip'te ve basınca çıkan mevcut
  seekRipple mesajında tam görünsün. Önceki kanal yoksa buton soluk ve pasif.
- Sıra: Rehber, Önceki kanal, Son kanallar, Ses & Altyazı, Daha Fazla.
- Parametreler, callback'ler ve `firstButtonFocusRequester` AYNEN korunsun; ilk butona (Rehber) bağlı kalsın.
- Yatay modda bu buton satırı kartın tek satırının sağ tarafına yerleşebilsin
  (Row içinde kullanılabilsin; fillMaxWidth zorunlu olmasın).

### 4) Üst sağ düğme grubu — PlayerScreen.kt
- Saat, (varsa) uyku zamanlayıcısı, PiP, döndür ve kilit TEK bir yarı saydam hap içinde dursun:
  arka plan `Color(0x8C0A0E17)`, 1dp kenarlık beyaz %12 alfa, köşe 20dp.
  (Siyah kenar boşluklarında da görünsün diye düz %25 siyah KULLANMA.)
- Hap içindeki butonlar 36dp (dikeyde 32dp), ikon 20dp; ayrı ayrı arka plan dairesi olmasın.
- Geri butonu da aynı arka plan rengini kullansın.
- onClick davranışları, contentDescription'lar, `lockButtonFocusRequester` AYNEN kalsın.
- `LivePlayerMoreSheet`'teki "Ekranı Kilitle" satırını kaldır (sağ üstteki kilidin tekrarı).

## DOKUNMA

VOD (film/dizi) kontrolleri, TV arayüzü (`ui/tv/*`), TiviPlayerManager, kanal değiştirme,
jestler (parlaklık/ses/kaydırma), EPG yükleme, immersive mantığı (G-001), güncelleme sistemi, imza, CI.

## Kontrol listesi

- Yatay telefonda kart yüksekliği ≤ 72dp mi? (`Modifier.onSizeChanged` ile debug log)
- Dikeyde ≤ 104dp mi?
- EPG yokken ilerleme çubuğu ve "Canlı Yayın" satırı görünmüyor mu?
- Gerçek diagnostics gelmeden uydurma kalite rozeti görünmüyor mu?
- Kumanda/klavye ile: kontroller açılınca odak Rehber'de mi, sağ/sol ile tüm butonlar geziliyor mu?
- Sürükleme ile genişletme/kapatma çalışıyor mu?
- Film/dizi oynatıcısında HİÇBİR görsel değişiklik yok mu?

---

## Agent raporu

_(Agent doldurur: değişiklik özeti, kontrol listesi sonuçları, önce/sonra kart yükseklikleri dp.)_
