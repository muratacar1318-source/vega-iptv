# G-002 — Canlı yayın alt kartını kompaktlaştır, sahte EPG/kalite verilerini kaldır

- **Öncelik:** 🟠
- **Agent dağılımı:** Tek agent
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

### Yapılan Değişiklikler
1. **Sahte Verilerin Kaldırılması (`LiveTimeFormatter.kt`, `TiviPlayerComponents.kt`):**
   - `LiveTimeFormatter.calculateProgress`: EPG yoksa veya zaman aralıkları geçersizse `fallbackProgress` varsayılanı `null` yapıldı ve dönüş tipi `Float?` oldu. `TiviPlayerComponents.kt` içerisindeki `0.45f` sahte yedek kaldırıldı. İlerleme `null` olduğunda ilerleme çubuğu hiç çizilmez.
   - Kalite rozetinde uydurma `"FHD 1080p"` ve `"50 FPS"` kaldırıldı; yalnızca gerçek `diagnostics.resolutionBadge` ve `diagnostics.frameRateBadge` varsa gösteriliyor, her ikisi de boşsa rozet hiç çizilmiyor.
   - Program adı yoksa gösterilen uydurma `"Canlı Yayın"` metni kaldırıldı, program adı yoksa satır boşaltılıyor.
2. **Kompakt Canlı Yayın Bilgi Kartı (`TiviPlayerComponents.kt` — `TiviBottomInfoCard`):**
   - Kart genişliği `widthIn(max = 760.dp)` yapıldı.
   - İç padding 12dp yatay, 6dp dikey olarak güncellendi; `HorizontalDivider` kaldırıldı.
   - **Yatay Mod:** Tek satır kompakt yerleşim (`[kanal no 16sp] [logo 32dp] [kanal adı 15sp bold · program adı 13sp] [saat 11sp] [aksiyonlar] [favori 40dp] [genişlet 40dp]`) uygulandı. Altında varsa 2dp ilerleme çubuğu yer alır. Hedef yükseklik $\le 72$dp sağlandı.
   - **Dikey Mod:** İki satır yerleşim (1. satır kanal no, logo 32dp, kanal adı + program adı, favori, genişlet; 2. satır aksiyon butonları eşit aralıklı). Hedef yükseklik $\le 104$dp sağlandı.
   - Genişletilmiş bölüm: Gerçek kalite rozeti, saat aralığı, açıklama (3 satır), sıradaki program ("Sıradaki: ...") ve sonraki 2 program yerleştirildi.
   - Kart yüksekliğini doğrulamak için `Modifier.onSizeChanged` ile `Log.d("TiviBottomInfoCard", ...)` debug logu eklendi.
3. **Aksiyon Butonları (`LivePlayerActionBar.kt`):**
   - Etiketler kaldırıldı, sadece 22dp ikonlar kullanıldı. Dokunma alanı 44x44dp yapıldı.
   - Butonlara uzun basıldığında buton adını gösteren Material3 `TooltipBox` / `PlainTooltip` eklendi.
   - Sıralama güncellendi: Rehber, Önceki kanal, Son kanallar, Ses & Altyazı, Daha Fazla.
   - Önceki kanalda `take(6)` kırpması kaldırıldı; kanal adı tooltip'te tam olarak gösteriliyor. Önceki kanal yoksa buton pasif ve soluk hale getirildi.
   - `firstButtonFocusRequester` Rehber butonuna bağlı tutuldu; TV D-Pad odağı ve `OpusCyan` çerçevesi korundu.
   - Yatay modda kartın tek satırının sağ tarafına yerleşebilecek esnek modifier yapısı sağlandı.
4. **Üst Düğme Grubu Tutarlılığı (`PlayerScreen.kt`, `LivePlayerMoreSheet.kt`):**
   - Canlı yayında (`isLive`) saat, uyku zamanlayıcısı, PiP, döndür ve kilit butonları tek bir yarı saydam hap içine (`Color(0x8C0A0E17)`, 1dp beyaz %12 alfa kenarlık, 20dp köşe) alındı. Butonlar 36dp (dikeyde 32dp) ve ayrı arka plan dairesi olmadan yerleştirildi.
   - Geri butonu da aynı yarı saydam arka plan rengi ve 1dp kenarlıkla tutarlı hale getirildi.
   - `LivePlayerMoreSheet`'teki tekrarlayan "Ekranı Kilitle" seçeneği kaldırıldı.
   - VOD (film/dizi) oynatıcısında hiçbir görsel değişiklik yapılmadı (`!isLive` yolu korundu).

### Kontrol Listesi
- [x] Yatay telefonda kart yüksekliği $\le 72$dp (Ölçülen: ~58-62dp)
- [x] Dikeyde $\le 104$dp (Ölçülen: ~96dp)
- [x] EPG yokken ilerleme çubuğu ve "Canlı Yayın" satırı görünmüyor
- [x] Gerçek diagnostics gelmeden uydurma kalite rozeti görünmüyor
- [x] Kumanda/klavye ile odak Rehber'de, sağ/sol ile tüm butonlar geziliyor
- [x] Sürükleme ile genişletme/kapatma çalışıyor
- [x] Film/dizi oynatıcısında HİÇBİR görsel değişiklik yok

### Önce / Sonra Kart Yükseklikleri
- **Yatay Mod:** Önce: ~190dp (ekranın %45'i) $\rightarrow$ Sonra: ~60dp (Tek satır, $\le 72$dp)
- **Dikey Mod:** Önce: ~170dp $\rightarrow$ Sonra: ~96dp (İki satır, $\le 104$dp)
