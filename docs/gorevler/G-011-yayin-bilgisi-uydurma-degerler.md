# G-011 — Yayın bilgisindeki uydurma değerleri kaynağında kaldır (FHD 1080p / 50 FPS / H.264 / AAC / 4.8 Mbps)

- **Öncelik:** 🟢
- **Agent dağılımı:** Tek agent
- **Branch:** `fix/yayin-bilgisi-gercek-degerler`
- **Commit mesajı:** `fix(player): yayın bilgisi sadece gerçek değerleri gösteriyor`

## Sorun

G-002'de arayüzdeki sahte kalite yedekleri kaldırıldı ve "değer boşsa gösterme" kuralı kondu. Ancak değerlerin
**kaynağı** uydurma: `player/TiviPlayerManager.kt` içinde

1. `data class StreamDiagnostics` (~63. satır) varsayılanları: `"1080p (FHD)"`, `"50 fps"`, `"H.264 (AVC)"`,
   `"AAC Stereo"`, `"4.5 Mbps"`, `"6.4s"`, `"FHD 1080p"`, `"50 FPS"`, `"H.264"`, `"AAC"`.
2. `updateDiagnostics()` (~1163) bilgi yokken yine uyduruyor: `else -> "FHD 1080p"`, `else "50 FPS"`,
   `else -> "H.264"`, `else -> "AAC"`, bitrate `else "4.8 Mbps"`.

Sonuç: yayın daha açılmadan ya da bilgi gelmeden kartta / "Daha Fazla" sayfasında / Ayarlar panelindeki
"Yayın Bilgisi"nde "FHD 1080p • 50 FPS" görünüyor; G-002'deki `isNotBlank()` kontrolleri hiç devreye girmiyor.

## Yapılacaklar

1. **`StreamDiagnostics` varsayılanları:** tüm alanların varsayılanı `""` (boş) olsun.
2. **`updateDiagnostics()`:** bilinmeyen değer için uydurma yerine `""` dön:
   - Çözünürlük: `height > 0` değilse `resBadge = ""`, `resText = ""`.
   - FPS: `fpsVal > 0` değilse `""`.
   - Video codec: mime boşsa `""`; bilinen bir mime değilse mime'ın `/`'dan sonraki kısmını büyük harfle yaz
     (ör. `video/mpeg2` → `MPEG2`).
   - Ses codec: aynı mantık (boşsa `""`, bilinmiyorsa mime son kısmı).
   - Bitrate: `vf.bitrate > 0` değilse `""`.
   - Tampon (`bufferHealthSecs`) gerçek ölçüm, olduğu gibi kalsın.
3. **Kanal/içerik değişince sıfırla:** yeni bir yayın hazırlanırken (mevcut `prepare`/medya değiştirme yerinde)
   `_streamDiagnostics.value = StreamDiagnostics()` yap ki önceki kanalın değerleri yeni kanalda görünmesin.
   Sadece bu tek satırı ekle; oynatma akışına başka dokunma.
4. **Boş değerleri düzgün göster** (arayüz tarafı):
   - `PlayerSettingsPanel.kt` `DiagnosticsFooter`: başlıktaki `"${resolutionBadge} • ${bitrate}"` ve alttaki satırlar
     boş parçaları atlasın (boşları filtrele, ` • ` ile birleştir); hepsi boşsa `"Bilgi bekleniyor…"`.
     "Video: … (…)" / "Ses: … | Tampon: …" satırlarında boş parçaları ve boş parantezleri yazma.
   - `TiviPlayerComponents.kt` ~1630-1710 (eski `TiviPlayerHUD` teşhis sekmesi; hâlâ kullanılıyorsa): boş değerde `"—"` göster.
   - `ui/tv/TvPlayerOverlay.kt` ~417-418: `.ifBlank { "FHD 1080p" }` / `.ifBlank { "50 FPS" }` yedeklerini kaldır;
     değer boşsa o `TechBadge`'i hiç çizme.
   - `LivePlayerMoreSheet.kt` ve `TiviBottomInfoCard` zaten boş değeri doğru işliyor (G-002) — dokunma.

## DOKUNMA

Oynatma, buffer/LoadControl ayarları, parça seçimi, `VodPrewarmer`, yüzey devri (`attachView/detachView`),
güncelleme sistemi, imza, CI. `updateDiagnostics()` dışındaki TiviPlayerManager mantığı (madde 3'teki tek satır hariç).

## Test

- Canlı kanal aç: video gelmeden önce kartta/Daha Fazla'da kalite rozeti yok veya "Bilgi bekleniyor…".
  Video başlayınca gerçek çözünürlük (ör. "HD 720p") görünüyor.
- 720p bir kanaldan 1080p kanala geç: eski değer yeni kanalda kalmıyor.
- Film aç → Ayarlar paneli → "Yayın Bilgisi": boş parça veya "•" artığı yok.
- TV'de canlı yayın bilgi şeridinde uydurma rozet yok.
- Test edemediğin maddeyi "test edilemedi" diye yaz.

---

## Agent raporu

- **Yapılan Değişiklikler:**
  1. `TiviPlayerManager.kt`:
     - `StreamDiagnostics` veri sınıfının tüm varsayılan alan değerleri boş metin (`""`) yapıldı.
     - `updateDiagnostics()` metodunda bilinmeyen veya mevcut olmayan değerler için `"FHD 1080p"`, `"50 FPS"`, `"H.264"`, `"AAC"`, `"4.8 Mbps"` sahte yedekleri kaldırıldı; bilinmeyen/olmayan alanlar için `""` döndürüldü.
     - `playInternal()` içinde yeni yayın başlatılırken `_streamDiagnostics.value = StreamDiagnostics()` eklenerek önceki kanalın teşhis değerlerinin yeni yayına sızması engellendi.
  2. `PlayerSettingsPanel.kt` (`DiagnosticsFooter`):
     - Başlık ve detay satırlarında boş parçalar filtrelendi ve ` • ` ile birleştirildi; tümü boşsa `"Bilgi bekleniyor…"` gösterildi.
     - Video ve Ses/Tampon detaylarında boş parantez veya tek başına `"Video: "` / `"Ses: "` kalması engellendi.
  3. `TiviPlayerComponents.kt`:
     - `DiagnosticMetricCard` ve `TiviPlayerHUD` tampon göstergesinde boş metinler için `"Bilinmiyor"` yerine `"—"` gösterildi.
  4. `TvPlayerOverlay.kt`:
     - `.ifBlank { "FHD 1080p" }` ve `.ifBlank { "50 FPS" }` yedekleri kaldırıldı; `TechBadge`'ler sadece ilgili değer `isNotBlank()` olduğunda çizilecek şekilde güncellendi.
- **Test:** `./gradlew compileDebugKotlin` başarıyla tamamlandı (BUILD SUCCESSFUL). Canlı yayın/film oynatma ortamı bulunmadığından cihaz/emülatör testleri gerçekleştirilemedi (test edilemedi).
