# G-023 — Oynatıcıdaki alttan açılan sayfaları kompakt yan panele çevir ("Daha fazla", Bölümler, Çift ekran)

- **Öncelik:** 🟠
- **Agent dağılımı:** Tek agent (hepsi `PlayerScreen.kt` ve aynı yeni bileşene dokunuyor — bölünmez)
- **Branch:** `ui/oynatici-yan-paneller`
- **Commit mesajı:** `ui(player): Daha fazla, Bölümler ve Çift ekran sayfaları kompakt yan panele taşındı`
- **Önce oku:** `docs/TASARIM-KURALLARI.md`

## Sorun (kullanıcı ekran görüntüsüyle bildirdi)

Canlı yayında **⋯ (Daha fazla)** düğmesi `LivePlayerMoreSheet` (`ModalBottomSheet`) açıyor ve telefon yatayken
**hiç kullanışlı değil**:
- Kartlar çok büyük (her biri ~90dp): ekrana sadece 2 seçenek sığıyor, 3.'sü kesik, gerisi için kaydırmak gerekiyor.
- Alttan açılan sayfa videonun tamamını kapatıyor.
- Sayfa ayrı bir pencere olduğu için **durum çubuğu (saat, pil) görünür oluyor** ve oynatıcının üst düğmeleriyle üst üste biniyor.
- "En-boy oranı" burada, ses/altyazı başka panelde — aynı tür ayarlar farklı yerlerde.

Film/dizi tarafında **aynı sorun** var:
- **Bölümler** (`PlayerScreen.kt` ~2645, `ModalBottomSheet`): yatayda alttan tam ekran açılıyor, sezonlar + bölüm
  listesi videoyu tamamen kapatıyor, durum çubuğu görünüyor.
- **Çift ekran kanal seçici** (`DualViewChannelPicker`, ~3224, `ModalBottomSheet` + `fillMaxHeight(0.75f)`): aynı sorun.

Oysa Ayarlar paneli (`PlayerSettingsPanel`, G-008/G-010) doğru çalışıyor: yatayda **sağdan 340dp yan panel**, video
solda görünür; dikeyde alttan ≤ %55; aynı pencerede olduğu için durum çubuğu çıkmıyor; kumanda odağı ve geri tuşu çalışıyor.
Hedef: hepsi bu davranışı paylaşsın.

## Yapılacaklar

### 1) Ortak kap: `PlayerSidePanel`
`ui/player/components/PlayerSidePanel.kt`:
`PlayerSidePanel(visible, title, onClose, modifier, isPortrait, content: @Composable ColumnScope.() -> Unit)`
- `PlayerSettingsPanel`'deki kap davranışını AYNEN taşı: karartma (dışa dokununca kapanır), yatayda sağda 340dp tam
  yükseklik, dikeyde altta ≤ %55 yükseklik, giriş/çıkış animasyonu, `BackHandler`, başlık + ✕ (44dp), açılınca ilk öğeye
  odak, `VegaColors.OverlayPanel` zemin, `VegaShapes.Large` köşe.
- `PlayerSettingsPanel` bu kabı kullanacak şekilde sadeleştirilsin (görünümü ve davranışı DEĞİŞMESİN).

### 2) Canlı yayın "Daha fazla" → `LiveMorePanel` (yan panel)
`LivePlayerMoreSheet` (`ModalBottomSheet`) yerine `PlayerSidePanel` içinde **kompakt satırlar**
(her satır 52dp: ikon 22dp + başlık 15sp + sağda ok/değer; açıklama satırı YOK):
1. **Kanal listesi** — mevcut `onOpenChannelDrawer`
2. **Kanal numarası gir** — mevcut `onOpenNumpad`
3. **Çift ekran** — mevcut `onOpenDualView` (aktifse sağda "Açık" rozeti)
4. **Uyku zamanlayıcısı** — `showSleepTimerDialog = true` (açıksa sağda "30 dk" gibi kalan süre)
5. **Yayın bilgisi** — dokununca satırın altında açılan küçük bilgi (çözünürlük • FPS • kodek; sadece gerçek değerler,
   yoksa "Bilgi bekleniyor…") — `PlayerSettingsPanel.DiagnosticsFooter` ile aynı mantık, kopyala ya da ortak yap.
- Başlık: **"Daha fazla"**. Panel yatayda kaydırmadan sığmalı (5 × 52dp + başlık).
- **En-boy oranı buradan çıkar:** canlı yayında Ayarlar panelinin sekmelerine **Görüntü** sekmesini de ekle
  (`PlayerSettingsPanel` `availableTabs`: canlıda `AUDIO, SUBTITLE, ASPECT_RATIO`; `SPEED` sadece film/dizide).
  Canlı alt çubuktaki "Ses ve altyazı" düğmesi aynen paneli Ses sekmesinde açmaya devam etsin.
- `LivePlayerMoreSheet.kt` artık kullanılmıyorsa sil; kullanılıyorsa raporla.

### 3) Film/dizi "Bölümler" → yan panel
`showEpisodesDrawer` `ModalBottomSheet`'i `PlayerSidePanel(title = dizi adı)` içine taşı:
- Üstte sezon çipleri (`VegaChoiceChip`, yatay kaydırmalı tek satır).
- Altta bölüm listesi (`LazyColumn`), satır 64dp: bölüm no (kalın) · bölüm adı (tek satır, ellipsis) · altında küçük
  süre/izlenme ilerlemesi varsa ince çubuk; oynayan bölüm `Accent` ile işaretli. Uzun açıklama (`plot`) satırda GÖSTERİLMESİN.
- Açılınca oynayan bölüme kaydır ve odakla. Seçim davranışı (bölüm değiştirme) AYNEN kalsın.

### 4) Çift ekran kanal seçici → yan panel
`DualViewChannelPicker`'ı `PlayerSidePanel(title = "İkinci kanalı seç")` içine taşı: üstte arama kutusu, altta kompakt
kanal satırları (56dp: logo 32dp + kanal adı). Arama/filtre ve `onChannelSelected` davranışı AYNEN.

### 5) Genel
- Panel açıkken oynatıcı kontrolleri gizlensin (Ayarlar panelindeki gibi) ve `isUserInteracting` / `anyOverlayOpen`
  listelerine yeni durumlar eklensin; kapanınca immersive yeniden uygulansın.
- TV: kumandayla tüm satırlar geziliyor, geri tuşu paneli kapatıyor, odak oynatıcıya dönüyor.
- `NumericKeypadDialog`, uyku zamanlayıcısı diyaloğu ve kanal çekmecesi (`showChannelDrawer`) bu görevin dışında — dokunma.

## DOKUNMA
Oynatma/kanal değiştirme/çift ekran mantığı, parça seçimi, sarma, jestler, medya oturumu, `ui/tv/*`,
güncelleme, imza, CI. Sürüm numarası.

## Test (telefon yatay ve dikey)
- Canlı → ⋯: sağda dar panel, 5 satır kaydırmadan görünüyor, video solda oynuyor, **durum çubuğu görünmüyor**.
- Canlı → Ses ve altyazı → "Görüntü" sekmesi var, en-boy değişiyor.
- Dizi → Bölümler: sağ panelde sezonlar + bölümler; oynayan bölüm görünür ve işaretli; başka bölüme geçiş çalışıyor.
- Çift ekran: sağ panelde arama + kanal listesi; seçince çift ekran başlıyor.
- Dikeyde paneller alttan, en fazla ekranın %55'i.
- TV kumandası ile hepsi kullanılabiliyor.
- Yerelde sadece `./gradlew compileDebugKotlin`; APK üretme. Test edemediğini "test edilemedi" yaz.

---

## Agent raporu

_(Agent doldurur.)_
