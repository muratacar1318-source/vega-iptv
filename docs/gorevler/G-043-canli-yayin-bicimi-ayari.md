# G-043 — Ayarlar → Oynatıcı: "Canlı yayın biçimi" seçeneği (Standart / HLS) — DENEME

- **Öncelik:** 🟠
- **Agent dağılımı:** Tek agent (bölme). Gemini 3.6 Flash için adım adım — **sırayı bozma, adımların dışına çıkma**.
- **Branch:** `feat/canli-yayin-bicimi` (main'den aç)
- **Commit mesajı:** `feat(ayarlar): canlı yayın biçimi seçeneği (Standart / HLS)`
- **Değişecek dosyalar (SADECE bunlar):**
  1. `app/src/main/java/com/tivimax/player/ui/MainViewModel.kt` — 1 fonksiyon eklenecek
  2. `app/src/main/java/com/tivimax/player/ui/settings/SettingsScreen.kt` — 1 blok eklenecek
  3. `YAPILACAKLAR.md` — sadece G-043 satırı

## Neden
Canlı yayında ~30 sn gecikme var. Sebep: `.ts` biçiminde sunucu bağlanınca eski yayını yığın hâlinde
gönderiyor ve bu biçimde ileri sarılamıyor. HLS (`.m3u8`) biçiminde oynatıcı en yeni parçadan başlayabiliyor.
Kodda HLS desteği **zaten var** (`streamFormat` ayarı, `buildLiveStreamUrls(preferHls)`, m3u8 başarısız olursa
otomatik `.ts`'ye düşüş). **Sadece Ayarlar'da seçme düğmesi yok.** Bu görev yalnızca o düğmeyi ekler.
Varsayılan "Standart" (TS) kalır → seçmeyen kullanıcı için **hiçbir şey değişmez.**

## 🚫 DOKUNMA
- `TiviPlayerManager.kt`, `XtreamRepository.kt`, `UserPreferences.kt`, `TvPlayerOverlay.kt`, `PlayerScreen.kt` — **hiçbirini değiştirme.**
- `AutoUpdateManager` ve güncelleme ile ilgili hiçbir dosya.
- MainViewModel'deki `streamFormat` satırı (547 civarı) ve `previewChannel` / `playLiveChannel` / `scheduleTvZap` fonksiyonları — **değiştirme.**
- Başka ayar bloklarını taşıma/silme/yeniden düzenleme.

---

## Adım 1 — `MainViewModel.kt`: setter ekle

Şu mevcut fonksiyonu bul:
```kotlin
    fun setDecoderMode(mode: String) {
        viewModelScope.launch {
            preferences.setDecoderMode(mode)
            playerManager.setDecoderMode(mode)
        }
    }
```
**Hemen altına** (kapanış `}`'den sonra, boş bir satır bırakarak) şunu **aynen** ekle:
```kotlin

    // G-043: Canlı yayın biçimi ("TS" = Standart, "HLS"). Sonraki açılan kanalda geçerli olur.
    fun setStreamFormat(format: String) {
        val safe = if (format == "HLS") "HLS" else "TS"
        viewModelScope.launch {
            preferences.setStreamFormat(safe)
        }
    }
```
Başka hiçbir şeyi değiştirme. (`preferences.setStreamFormat` zaten `UserPreferences.kt` içinde var — ekleme.)

## Adım 2 — `SettingsScreen.kt`: seçenek bloğunu ekle

Dosyada şu satırı bul (dosyada **tek** yerde geçiyor):
```kotlin
                    // Canlı Yayın Ön Yükleme
```
Bu satırın **hemen üstüne** aşağıdaki bloğu **aynen** yapıştır (girinti 20 boşluk, çevredeki satırlarla aynı):
```kotlin
                    // G-043: Canlı yayın biçimi (Standart = TS, HLS = düşük gecikme denemesi)
                    val streamFormat by viewModel.streamFormat.collectAsState()
                    Text(text = "Canlı yayın biçimi", color = TiviTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Yayın canlıdan geride kalıyorsa 'HLS'yi dene. Kanal açılmazsa ya da takılırsa 'Standart'a dön. Değişiklik bir sonraki açtığın kanalda geçerli olur.",
                        color = TiviTextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("TS" to "Standart (önerilen)", "HLS" to "HLS (düşük gecikme)").forEach { (format, label) ->
                            val isSelected = streamFormat == format
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) OpusCyan.copy(alpha = 0.2f) else TiviSurfaceVariant,
                                border = BorderStroke(1.dp, if (isSelected) OpusCyan else TiviCardBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .dpadFocusable(
                                        focusedBorderColor = OpusCyan,
                                        shape = RoundedCornerShape(10.dp),
                                        onClick = {
                                            viewModel.setStreamFormat(format)
                                            val toastVal = if (format == "HLS") "HLS" else "Standart"
                                            Toast.makeText(context, "Canlı yayın biçimi: $toastVal", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) OpusCyan else TiviTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = TiviCardBorder)

```
Notlar:
- Bu blok, hemen üstündeki "Görüntü işleme" bloğunun birebir aynı kalıbıdır; tüm import'lar (`Surface`, `BorderStroke`,
  `dpadFocusable`, `Toast`, `OpusCyan`, `collectAsState`, `getValue` …) dosyada **zaten var**. Yeni import ekleme.
- `context` değişkeni o bölümde zaten tanımlı (Görüntü işleme bloğu da kullanıyor).
- Derleyici `streamFormat` için "getValue import" hatası verirse dosyanın başına
  `import androidx.compose.runtime.getValue` ekle (muhtemelen zaten var).

## Adım 3 — Derle ve kontrol et
1. `./gradlew :app:assembleDebug` → hatasız bitmeli.
2. `./gradlew :app:testDebugUnitTest` → geçmeli.
3. Uygulamada Ayarlar → Oynatıcı: "Görüntü işleme" ile "Canlı yayın ön yükleme" arasında "Canlı yayın biçimi"
   görünmeli; varsayılan **Standart (önerilen)** seçili olmalı; TV kumandasıyla iki düğme arasında gezilebilmeli.

## Adım 4 — YAPILACAKLAR.md
Bekleyen görevler tablosunda G-043 satırını `🔄 Agent bitirdi — Claude kontrol edecek` yap. Başka satıra dokunma.

## Adım 5 — Gönder
```
git add app/src/main/java/com/tivimax/player/ui/MainViewModel.kt app/src/main/java/com/tivimax/player/ui/settings/SettingsScreen.kt YAPILACAKLAR.md
git commit -m "feat(ayarlar): canlı yayın biçimi seçeneği (Standart / HLS)"
git push -u origin feat/canli-yayin-bicimi
```
**Main'e birleştirme yapma.** PR'ı Claude açar.

---

## Kullanıcının test edeceği (görev bittikten sonra, gerçek TV kutusunda)
1. Ayarlar → Oynatıcı → Canlı yayın biçimi → **HLS**.
2. Birkaç kanal aç (HEVC olanlar dahil). Her birinde OK ile bilgi kartını aç, **Tampon** değerine bak.
3. Şunları not et: tampon kaç saniye, kanal kaç saniyede açıldı, takılma oldu mu.
4. Aynı kanalları **Standart** ile karşılaştır.
Sonuca göre Claude ya HLS'yi varsayılan yapar + "Canlıya geç" düğmesini anlık çalışır şekilde ekler, ya da seçeneği olduğu gibi bırakır.
