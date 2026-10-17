# G-014 — Tasarım temeli: tek vurgu rengi, renk/şekil tokenları ve ortak bileşenler

- **Öncelik:** 🟠
- **Agent dağılımı:** Tek agent (temel). G-015…G-018 bunun üstüne kurulur.
- **Branch:** `ui/tasarim-temeli`
- **Commit mesajı:** `ui(design): tek vurgu rengi, tasarım tokenları ve ortak bileşenler`

## Sorun (genel tutarlılık denetimi — Claude)

Uygulamanın farklı yerleri farklı görünüyor:
- **İki ayrı vurgu rengi:** camgöbeği `OpusCyan` (564 yerde) ve indigo `TiviPrimary` (97 yerde: oynatıcı Ayarlar
  paneli sekmeleri, oynatıcı ekranı, giriş ekranı, TV menüsü). Ayarlar'da butonlar camgöbeği, oynatıcı panelinde
  sekmeler mor — aynı uygulama gibi durmuyor.
- **390 sabit renk** (`Color(0xFF…)`) — tema seçince (Okyanus, Zümrüt, Ametist) bu yerler değişmiyor; ör. Zümrüt
  temada yeşil ekranın üstünde lacivert paneller kalıyor.
- **15 farklı köşe yuvarlaklığı** (1, 1.5, 2, 2.5, 3, 4, 5, 6, 8, 10, 12, 14, 16, 18, 20dp).
- **Odak çerçevesi 13 farklı renkte** (camgöbeği, beyaz, altın, gri, mor, kırmızı…).
- **Kırmızılar karışık:** `0xFFEF5350` (22), `0xFFEF4444` (6), `TiviAccentLive`.
- 24 farklı yazı boyutu, emoji/simge içeren buton metinleri ("★ Listemden Kaldır", "▶ S1 B2 Başlat").

Hedef kurallar: **`docs/TASARIM-KURALLARI.md`** — önce baştan sona oku.

## Yapılacaklar (sadece temel; ekranları G-015…G-018 düzeltecek)

1. **`ui/design/VegaDesign.kt`** oluştur:
   - `object VegaColors` — `TASARIM-KURALLARI.md`'deki her rol için `val X: Color get() = …` (getter! tema `var`'ları
     değiştikçe güncel değeri döndürsün). `Danger = Color(0xFFEF5350)`, `OnAccent = Color.Black`,
     `OverlayPanel = Color(0xF20F141C)`, `OverlayPill = Color(0x8C0A0E17)`, `Scrim = Color(0x73000000)`.
     Diğerleri mevcut tema değişkenlerine bağlansın (`Accent get() = OpusCyan`, `Card get() = TiviCard` …).
   - `object VegaShapes { Small = RoundedCornerShape(8.dp); Medium = 12.dp; Large = 16.dp; Pill = CircleShape }`.
   - `object VegaSizes { ButtonHeight = 44.dp; ButtonHeightTv = 48.dp; MinTouch = 44.dp; IconButton = 44.dp }`.
   - `object VegaText` — 6 boyut sabiti (22/17/15/14/13/11 sp).
2. **`ui/design/VegaComponents.kt`** oluştur (hepsi `dpadFocusable` ile kumanda uyumlu, ≥44dp):
   - `VegaButton(text, onClick, modifier, variant = PRIMARY|SECONDARY|DANGER|TEXT, icon: ImageVector? = null, enabled = true)`
   - `VegaIconButton(icon, contentDescription, onClick, modifier, onOverlay: Boolean = false)` — `onOverlay` true ise zemin `OverlayPill`.
   - `VegaChoiceChip(label, selected, onClick, modifier)`
   - `VegaSwitchRow(title, description, checked, onCheckedChange, modifier)`
   - `VegaSectionCard(modifier, content)` — `Card` + `Large` köşe + 1dp `Border` + 16dp iç boşluk.
   Bileşenlerin görünümü `TASARIM-KURALLARI.md`'deki tablolarla birebir aynı olsun.
3. **Tek vurgu rengi:** `theme/Theme.kt`'de her tema için `TiviPrimary` ve `TiviPrimaryDark` = o temanın `OpusCyan`
   değeri olsun (`TiviSecondary` da). Böylece indigo kullanan 97 yer kendiliğinden camgöbeği olur.
   Ardından `TiviPrimary` **dolgu/zemin** olarak kullanılan yerleri ara (`background(TiviPrimary)`,
   `color = TiviPrimary`, `containerColor = TiviPrimary`, `selectedColor`): içindeki metin/ikon beyazsa
   **siyaha (`VegaColors.OnAccent`)** çevir (camgöbeği üstünde beyaz okunmaz). Sadece bu metin rengi değişikliğini yap.
4. **`dpadFocusable` varsayılanı** zaten `OpusCyan`; dokunma. (Ekranlardaki farklı renkleri G-015…G-018 düzeltecek.)
5. `docs/TASARIM-KURALLARI.md`'yi değiştirme (Claude'un dosyası); uyuşmazlık görürsen raporda yaz.

## DOKUNMA
Ekranların yerleşimi ve davranışı, oynatma, güncelleme sistemi, imza, CI. Bu görevde ekran dosyalarında sadece
madde 3'teki "vurgu üstü metin rengi" değişikliği yapılır.

## Test
- Oynatıcı Ayarlar paneli sekmeleri, giriş ekranı butonu, TV yan menüsü artık camgöbeği; üzerlerindeki yazılar okunuyor.
- 4 temada da uygulama açılıyor, vurgu rengi temanın kendi rengi.
- `ui/design` bileşenleri derleniyor (en az bir `@Preview` ekle).
- Test edemediğin maddeyi "test edilemedi" yaz. APK üretme.

---

## Agent raporu

_(Agent doldurur.)_
