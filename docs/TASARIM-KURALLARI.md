# Vega IPTV — Tasarım kuralları (tek kaynak)

Bu dosya uygulamanın görsel dilidir. Yeni ekran/buton yazan veya düzelten her agent buna uyar.
Kod karşılığı: `ui/design/VegaDesign.kt` (tokenlar) ve `ui/design/VegaComponents.kt` (hazır bileşenler) — G-014'te oluşturulur.

## Renkler (tema ile değişir — sabit `Color(0x...)` YAZMA)

| Rol | Token | Nerede |
|---|---|---|
| Vurgu (tek) | `VegaColors.Accent` (= `OpusCyan`) | Birincil buton, seçili durum, kaydırıcı, anahtar, odak çerçevesi, ilerleme |
| Vurgu üstü metin | `VegaColors.OnAccent` (siyah) | Vurgu renkli buton/çip içindeki yazı/ikon |
| Tehlike | `VegaColors.Danger` | Çıkış, sil, hata |
| Canlı | `VegaColors.Live` (= `TiviAccentLive`) | SADECE "canlı" rozeti/noktası |
| Puan / favori | `VegaColors.Gold` (= `TiviGold`) | SADECE yıldız, puan, favori |
| Başarı | `VegaColors.Success` (= `TiviGreen`) | SADECE "aktif/başarılı" durumları |
| Arka plan / yüzey / kart / çerçeve | `VegaColors.Background/Surface/SurfaceVariant/Card/Border` | Ekran, kart, liste öğesi |
| Metin | `VegaColors.TextPrimary/TextSecondary/TextMuted` | Başlık / açıklama / pasif |
| Video üstü panel | `VegaColors.OverlayPanel` (%95 opak koyu) | Oynatıcıdaki yan/alt paneller |
| Video üstü hap/düğme zemini | `VegaColors.OverlayPill` (%55 opak koyu) | Oynatıcıdaki yüzen düğmeler, üst hap |
| Video karartma | `VegaColors.Scrim` (siyah %45) ve gradyanlar için `Color.Black.copy(alpha=…)` serbest | Panel arkası, alt/üst gölge |

İndigo (`TiviPrimary`) ayrı bir vurgu rengi DEĞİLDİR; vurgu her yerde aynıdır.

## Köşe yuvarlaklığı (3 seviye + hap)

| Seviye | Değer | Kullanım |
|---|---|---|
| `VegaShapes.Small` | 8dp | Çip, küçük buton, liste satırı, rozet |
| `VegaShapes.Medium` | 12dp | Buton, giriş kutusu, kutucuk, afiş |
| `VegaShapes.Large` | 16dp | Kart, panel, diyalog, alt sayfa (üst köşeler) |
| `VegaShapes.Pill` | %50 | Hap düğme grupları, yuvarlak ikon düğmesi (`CircleShape`) |

İnce ilerleme çubukları (1–3dp) bu kuralın dışındadır.

## Butonlar

| Tür | Görünüm | Bileşen |
|---|---|---|
| Birincil | Dolgu `Accent`, yazı `OnAccent`, kalın | `VegaButton(variant = PRIMARY)` |
| İkincil | Zemin `SurfaceVariant`, 1dp `Border`, yazı `TextPrimary` | `VegaButton(variant = SECONDARY)` |
| Tehlike | Dolgu `Danger`, yazı beyaz | `VegaButton(variant = DANGER)` |
| Metin | Zemin yok, yazı `Accent` | `VegaButton(variant = TEXT)` |
| İkon | Yuvarlak, 44dp dokunma alanı, ikon 20–22dp | `VegaIconButton` |

Yükseklik: telefon 44dp, TV 48dp. Köşe `Medium`. Buton metni cümle düzeninde ("Şimdi güncelle").

## Seçim (çip / segment / radyo satırı)
- Seçili: zemin `Accent` %18, 1dp `Accent` çerçeve, yazı `Accent` kalın.
- Seçili değil: zemin `SurfaceVariant`, 1dp `Border`, yazı `TextSecondary`.
- Bileşen: `VegaChoiceChip`. Anahtar (switch): `VegaSwitchRow` (başlık + açıklama + `Switch` vurgu renginde).

## Odak (TV kumandası)
- Her odaklanabilir öğe: 2dp `Accent` çerçeve + 1.03–1.05 büyüme (`dpadFocusable` varsayılanı).
- Beyaz, altın, gri odak çerçevesi KULLANMA (tek istisna: `Accent` renkli dolgu butonda çerçeve beyaz olabilir).

## Yazı ölçeği (sp)
22 ekran başlığı · 17 bölüm başlığı · 15 vurgulu gövde · 14 gövde · 13 ikincil · 11 açıklama/rozet.
Arada kalan değerleri (10.5, 11.5, 12.5, 13.5, 16, 18…) en yakın basamağa yuvarla; oynatıcıdaki büyük saat/sayılar hariç.

## Dil ve metin
- "Sen" dili, kısa ve sade ("Bağlantını kontrol et").
- Başlık/buton/seçenek: sadece ilk harf büyük. Kategori/sekme adları: kelime başları büyük olabilir. TÜMÜ BÜYÜK HARF yok.
- Teknik terim yok (Buffer, Decoder, VOD, PiP, HTTPS, Kodek…). Sunucu adresi/URL hiçbir metinde yok.
- Buton metninde emoji veya ★ ☆ ▶ gibi simge yok — gerekiyorsa `Icon` kullan. (Rozetlerde 🌙 gibi tek simge serbest.)
- "&" yerine "ve"; "[Önerilen]" yerine "(önerilen)"; sayı ile birim arasında boşluk ("5 sn").
