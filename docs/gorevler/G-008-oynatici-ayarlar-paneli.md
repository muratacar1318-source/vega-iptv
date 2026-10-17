# G-008 — Oynatıcı ayarlarını tek, kompakt ve kullanıcı dostu bir panelde topla

- **Öncelik:** 🟠
- **Agent dağılımı:** Tek agent
- **Branch:** `ui/oynatici-ayarlar-paneli`
- **Commit mesajı:** `ui(player): ses/altyazı/görüntü/hız tek kompakt ayarlar panelinde; kontrol paneli sadeleştirildi`
- **Not:** G-001 (immersive) ve G-007'den SONRA yapılmalı (aynı dosyalar). G-002 canlı yayın kartına dokunur; bu görev
  canlı yayının "Ses & Dil" butonunun açtığı paneli de değiştirir — G-002'nin buton callback'lerini bozmadan.

## Sorun (kullanıcı ekran görüntüleriyle bildirdi)

Film/dizi oynatıcısında yatay ekranda:
1. **"Ses & Altyazı"** (`PlayerScreen.kt` ~2466, `ModalBottomSheet` "Medya ve Oynatma Seçenekleri") alttan ekranın
   neredeyse tamamını kaplayan uzun bir sayfa olarak açılıyor. Yatayda sadece 2-3 satır görünüyor; ses dili,
   altyazı listesi, altyazı gecikmesi, altyazı boyutu, renk ve oynatma hızı tek uzun kaydırmalı listede.
   Açılınca durum çubuğu da görünür oluyor. Hız seçenekleri alttan kesik.
2. **"Kontrol Paneli"** (`TiviPlayerHUD`, `TiviPlayerComponents.kt` ~1116, PlayerScreen ~2878) sağda açılan
   "KONTROL MERKEZİ" kartı:
   - Alt bardaki pillerin ÜSTÜNE biniyor; "En-Boy Oranı" başlığı "Ses & Altyazı" pilinin üstüne yazılıyor (üst üste metin).
   - Kartın altı ekranın altından taşıyor, ilerleme çubuğuyla çakışıyor.
   - Parlaklık ve ses seviyesi zaten ekranın sol/sağ kenarında kaydırma jestiyle ve telefonun tuşlarıyla ayarlanıyor; tekrar.
   - "Yayın Teşhis" sıradan kullanıcıya hitap etmiyor.
3. **Tekrarlar:** Hız hem alt bardaki "1.0x Hız" pilinde hem Ses & Altyazı sayfasında; en-boy oranı hem "Orijinal"
   pilinde, hem ortaya çift dokunmada, hem Kontrol Merkezi'nde.

## Hedef

Tek bir **"Ayarlar" paneli**, sekmeli ve kompakt:

**Yatay (telefon/tablet):** sağdan kayan yan panel, genişlik 340dp, tam yükseklik, video solda görünmeye devam eder.
Panelin dışına dokununca veya geri tuşuyla kapanır.
**Dikey:** alttan açılan panel, en fazla ekran yüksekliğinin %55'i.

```
┌─────────────────────────────┐
│ Ayarlar                   ✕ │
│ [ Ses ][Altyazı][Görüntü][Hız]│  ← sekmeler (canlı yayında sadece Ses ve Altyazı)
├─────────────────────────────┤
│ ● Türkçe (Stereo)           │  ← tek satır, 44dp, radyo
│ ○ İngilizce (Stereo)        │
└─────────────────────────────┘
Altyazı sekmesi:
│ ○ Kapalı                    │
│ ● Türkçe                    │
│ ─────────────               │
│ Gecikme   [−] +0,3 sn [+] ⟲ │
│ Boyut     [K][O][B][ÇB]     │  ← segmentli
│ Renk      ⚪ 🟡 🟢 🔵        │  ← sadece daireler
Görüntü sekmesi:
│ En-boy  [Orijinal][Doldur][Yakınlaştır][16:9][4:3] │
Hız sekmesi:
│ [0.75][1.0][1.25][1.5][2.0] │
```
Her sekmenin içeriği yatay telefonda **kaydırmadan** sığmalı (altyazı sekmesinde parça listesi uzunsa sadece liste kayar).

## Yapılacaklar

1. **Yeni bileşen:** `ui/player/components/PlayerSettingsPanel.kt` — parametreler: başlangıç sekmesi, isLive, ses/altyazı
   parçaları, seçili değerler ve callback'ler (mevcut `playerManager.selectTrack`, `disableSubtitles`,
   `viewModel.setSubtitleDelayMs`, `setSubtitleSize`, `setSubtitleColor`, `playerManager.setPlaybackSpeed`,
   `setAspectRatio`). İş mantığı yazma; mevcut fonksiyonları çağır.
2. **Eski "Medya ve Oynatma Seçenekleri" `ModalBottomSheet`'ini** (`showTrackSheet`) bu panelle değiştir.
   Canlı yayının "Ses & Dil" butonu da aynı paneli açsın (sadece Ses + Altyazı sekmeleri).
3. **Alt bardaki piller (film/dizi):** `[Baştan Başlat] [Ses & Altyazı] [Görüntü: Orijinal] [Hız: 1.0x]` olsun;
   her pil paneli ilgili sekmede açsın ("Ses & Altyazı" → Altyazı sekmesi). **"Kontrol Paneli" pilini kaldır.**
   Dizide "Bölümler" pili varsa kalsın.
4. **`TiviPlayerHUD` (Kontrol Merkezi):** film/dizi oynatıcısından kaldır. "Yayın Teşhis" bilgisi kaybolmasın:
   Ayarlar panelinin altına küçük, katlanmış bir "Yayın bilgisi" satırı ekle (çözünürlük, bitrate, codec — mevcut
   `StreamDiagnostics`'ten). Parlaklık/ses kaydırıcılarını kaldır (jestler ve donanım tuşları zaten var).
   `TiviPlayerHUD` başka yerde (canlı yayın, TV) kullanılıyorsa orada DOKUNMA.
5. **Katman ve boşluk:** Panel açıkken alt bar ve ortadaki kontroller gizlensin (üst üste binme olmasın).
   Panel kapanınca kontroller geri gelsin ve sistem çubukları tekrar gizlensin (G-001'deki yardımcıyı çağır).
6. **Dokunma alanları** en az 44dp; metinler kesilmesin (ellipsis); TV'de D-pad ile sekmeler ve öğeler gezilebilsin,
   geri tuşu paneli kapatsın.
7. Seçimler kalıcıysa (altyazı boyutu/rengi/gecikme) mevcut kalıcılık aynen çalışsın.

## DOKUNMA

Oynatma, parça seçimi ve altyazı gecikmesi mantığı (`DelayedSubtitleController`), sarma, jestler, TV canlı yayın
oynatıcısı (`ui/tv/*`), canlı yayın alt kartının yerleşimi (G-002), güncelleme, imza, CI.

## Test (telefon, yatay ve dikey)

- **A)** "Ses & Altyazı" → sağda panel, Altyazı sekmesi açık; video solda oynamaya devam ediyor; kaydırmadan gecikme/boyut/renk görünüyor.
- **B)** Ses dilini değiştir → ses değişiyor, panel açık kalıyor.
- **C)** "Hız: 1.0x" pili → Hız sekmesi; 1.5x seç → pil "Hız: 1.5x" oluyor.
- **D)** "Görüntü" pili → en-boy seçenekleri; seçim anında uygulanıyor.
- **E)** "Kontrol Paneli" pili yok; üst üste binen metin yok.
- **F)** Panel açıp kapattıktan sonra durum çubuğu görünmüyor.
- **G)** Dikeyde panel alttan, ekranın en fazla %55'i.
- **H)** Canlı yayında "Ses & Dil" → sadece Ses ve Altyazı sekmeleri.
- **I)** TV'de film oynatırken kumandayla panel kullanılabiliyor.

---

## Agent raporu

_(Agent doldurur: ekran görüntüsü açıklamaları, değiştirilen dosyalar, A–I sonuçları.)_
