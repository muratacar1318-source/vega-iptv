# G-036 — TV: Ayarlar menüsü kaymıyor, TV'nin tablet sanılması, kanallar numaraya göre

- **Öncelik:** 🔴
- **Agent dağılımı:** Tek agent (bölme). Gemini 3.6 Flash için adım adım — **adımların dışına çıkma, kendi fikrini ekleme**.
- **Branch:** `fix/tv-ayarlar-cihaz-sira` (main'den aç)
- **Commit mesajı:** `fix(tv): ayarlar menüsü kayıyor, TV algılama iyileştirildi, kanallar numara sırasında`
- **Değişecek dosyalar (SADECE bunlar):**
  1. `app/src/main/java/com/tivimax/player/ui/settings/SettingsScreen.kt` (1 satır)
  2. `app/src/main/java/com/tivimax/player/util/DeviceTypeUtil.kt`
  3. `app/src/main/java/com/tivimax/player/ui/MainViewModel.kt` (1 satır)
  4. `app/src/main/java/com/tivimax/player/data/local/dao/Daos.kt` (**sadece `interface LiveDao` içi**)
  5. Yeni test: `app/src/test/java/com/tivimax/player/util/DeviceModeRecommendTest.kt`
  6. Bu görev dosyası (Agent raporu) ve `YAPILACAKLAR.md` (durum satırı)

## Kullanıcının gördüğü sorunlar
1. TV'de Ayarlar'ın sol menüsünde aşağı inince (Ebeveyn Kilidi ve altı) **ekran kaymıyor**, seçili öğe görünmüyor.
2. Uygulama TV'de **tablet modunda** başlıyor.
3. Kanallar kanal numarasına göre değil, karışık sırada (4, 41, 5, 6, 1, 26, 3…).

## Nedenleri (Claude kod incelemesi)
1. Ayarlar'ın geniş ekran (TV) sol menüsü kaydırılamayan bir `Column`; alttaki öğeler ekranın dışında kalıyor.
   (Diğer TV ekranları tarandı: ana sayfa, film/dizi detay, arama, rehber, liste değiştirme ve form pencereleri zaten
   kaydırılabiliyor; sorun sadece burada.)
2. TV algılama sadece "resmi Android TV" işaretlerine bakıyor. Sertifikasız TV kutuları (çoğu IPTV kutusu) dokunmatik
   ekranı varmış gibi görünüyor → ilk kurulumda **Tablet** öneriliyor. Ayrıca kaydedilen cihaz türünün hızlı kopyası
   eski kurulumlarda eksik olabiliyor → açılışta bir an tablet düzeni gelip sonra TV'ye geçebiliyor.
3. G-030'da "sunucu sırası" varsayılan yapıldı; bu sağlayıcı kanalları numara sırasında göndermiyor. Kanallar için
   numara (`num`) sırası daha doğru. (Film/dizi ve kategoriler sunucu sırasında **kalacak**.)

---

## Adım 1 — SettingsScreen.kt: sol menü kaysın
Yorum `// SOL MENÜ: Kategori Sekmeleri (D-Pad Odaklanabilir)` altındaki `Column`'un modifier'ı:
```kotlin
                Column(
                    modifier = Modifier
                        .width(260.dp)
                        .fillMaxHeight()
                        .background(VegaColors.Surface)
                        .padding(horizontal = 12.dp, vertical = 16.dp)
                ) {
```
→
```kotlin
                Column(
                    modifier = Modifier
                        .width(260.dp)
                        .fillMaxHeight()
                        .background(VegaColors.Surface)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 16.dp)
                ) {
```
(`verticalScroll` ve `rememberScrollState` import'ları dosyada zaten var. Bu Column içinde `weight` yok, güvenli.)

## Adım 2 — DeviceTypeUtil.kt: daha iyi TV önerisi
**2a.** Dosyanın sonuna saf bir yardımcı ekle:
```kotlin
/**
 * G-036: Cihaz türü önerisi (saf fonksiyon — test edilebilir).
 * Sertifikasız TV kutuları dokunmatik ekran bildirebiliyor; hareket sensörü ve SIM özelliği olmaması
 * TV kutusunun güçlü işaretidir (telefon/tabletlerin neredeyse hepsinde ivmeölçer var).
 */
internal fun recommendDeviceMode(
    isTvUiMode: Boolean,
    hasLeanback: Boolean,
    hasTvFeature: Boolean,
    hasTouchscreen: Boolean,
    hasAccelerometer: Boolean,
    hasTelephony: Boolean,
    smallestWidthDp: Int
): DeviceMode {
    if (isTvUiMode || hasLeanback || hasTvFeature || !hasTouchscreen) return DeviceMode.TV
    if (!hasAccelerometer && !hasTelephony) return DeviceMode.TV
    return if (smallestWidthDp >= 600) DeviceMode.TABLET else DeviceMode.PHONE
}
```

**2b.** `fun Context.detectRecommendedDeviceMode(): DeviceMode { ... }` fonksiyonunun **gövdesini** şununla değiştir
(imza aynı kalsın):
```kotlin
fun Context.detectRecommendedDeviceMode(): DeviceMode {
    val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    @Suppress("DEPRECATION")
    return recommendDeviceMode(
        isTvUiMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION,
        hasLeanback = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK),
        hasTvFeature = packageManager.hasSystemFeature("android.hardware.type.television") ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION),
        hasTouchscreen = packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN),
        hasAccelerometer = packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER),
        hasTelephony = packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY),
        smallestWidthDp = resources.configuration.smallestScreenWidthDp
    )
}
```
**`fun Context.isTvDevice()` fonksiyonuna DOKUNMA** (başka yerler kullanıyor; davranışı değişmesin).

## Adım 3 — MainViewModel.kt: kayıtlı cihaz türünün hızlı kopyasını eşitle
Şu bloğu bul (yorum: `// Cihaz Modu Değişikliklerini Dinle`):
```kotlin
            preferences.deviceModeFlow.collect { saved ->
                if (saved != null) {
                    _deviceMode.value = saved
                }
            }
```
→
```kotlin
            preferences.deviceModeFlow.collect { saved ->
                if (saved != null) {
                    _deviceMode.value = saved
                    // G-036: Açılışta bir an yanlış düzen gelmesin diye hızlı kopyayı eşitle
                    if (OrientationManager.getFastCachedDeviceMode(getApplication()) != saved) {
                        OrientationManager.saveFastCache(getApplication(), saved)
                    }
                }
            }
```
(`OrientationManager` import'u yoksa: `import com.tivimax.player.util.OrientationManager`.)

## Adım 4 — Daos.kt: kanallar numara sırasında (SADECE `interface LiveDao` içinde)
`interface LiveDao { ... }` bloğunun **içinde** (VodDao ve SeriesDao'ya **dokunma**):
- `live_streams` tablosunu okuyan ve `ORDER BY rowid ASC` ile biten **5 sorguda** (`SELECT * FROM live_streams ...`
  satırları — kategori sorgularına dokunma) `ORDER BY rowid ASC` → 
  `ORDER BY CASE WHEN num > 0 THEN 0 ELSE 1 END, num ASC, rowid ASC`
- `getStreamsFiltered` sorgusunun en son sıralama satırı `            rowid ASC` →
  `            CASE WHEN num > 0 THEN 0 ELSE 1 END, num ASC, rowid ASC`

`live_categories` sorgularındaki `ORDER BY rowid ASC` **aynen kalır** (kategoriler sunucu sırasında).
`CASE WHEN :sort = ...` satırlarına dokunma. Veritabanı sürümü (`AppDatabase` `version`) **değişmeyecek**.
(Numarası olmayan kanallar — `num` 0 — en sonda, sunucu sırasıyla gelir.)

## Adım 5 — Birim testi (yeni dosya `app/src/test/java/com/tivimax/player/util/DeviceModeRecommendTest.kt`)
`package com.tivimax.player.util` — JUnit4. Testler (`recommendDeviceMode(...)` çağrısıyla):
1. Resmi Android TV (`hasLeanback = true`, diğerleri telefon gibi) → `DeviceMode.TV`
2. Sertifikasız kutu: `hasTouchscreen = true, hasAccelerometer = false, hasTelephony = false, smallestWidthDp = 540` → `TV`
3. Wi-Fi tablet: `hasTouchscreen = true, hasAccelerometer = true, hasTelephony = false, smallestWidthDp = 800` → `TABLET`
4. Telefon: `hasTouchscreen = true, hasAccelerometer = true, hasTelephony = true, smallestWidthDp = 411` → `PHONE`
5. Dokunmatik yok: `hasTouchscreen = false` (diğerleri telefon gibi) → `TV`

---

## Kontrol ve gönderim
1. `./gradlew compileDebugKotlin` → hata varsa düzelt (isim uydurmadan).
2. `./gradlew testDebugUnitTest` → tüm testler geçmeli.
3. `git diff --stat`: **sadece** yukarıdaki dosyalar. Başka dosya değiştiyse geri al.
4. Commit → `fix/tv-ayarlar-cihaz-sira` dalına push. **Main'e birleştirme. APK üretme. Sürüm numarasını değiştirme.**
5. `YAPILACAKLAR.md`'de G-036 durumunu `🔍 İncelemede (branch: fix/tv-ayarlar-cihaz-sira)` yap.

## DOKUNMA
`isTvDevice()`, `OrientationManager` içi, `DeviceModeSetupScreen`, `AppDatabase`, VodDao/SeriesDao, telefon düzenleri,
güncelleme sistemi (AutoUpdateManager), imza, CI, sürüm numarası.

## Test (cihaz varsa — yoksa "test edilemedi" yaz)
- TV'de Ayarlar sol menüde en alta in: liste kayıyor, seçili öğe hep görünüyor.
- Canlı TV: kanallar 1, 2, 3… sırasında; "Sırala" menüsünde A-Z hâlâ çalışıyor.
- Filmler/diziler ve kategoriler önceki gibi sunucu sırasında.

---

## Agent raporu

- **Yapılan değişiklikler:**
  - **Adım 1:** `SettingsScreen.kt` dosyasında geniş ekran / TV modundaki sol kategori menüsü `Column`'una `.verticalScroll(rememberScrollState())` eklendi.
  - **Adım 2:** `DeviceTypeUtil.kt` dosyasına test edilebilir saf `recommendDeviceMode` fonksiyonu eklendi; ivmeölçer ve telefon modülü bulunmayan sertifikasız kutuların TV olarak algılanması sağlandı; `detectRecommendedDeviceMode()` gövdesi bu fonksiyonu çağıracak şekilde güncellendi (`isTvDevice()` korunarak).
  - **Adım 3:** `MainViewModel.kt` dosyasında `preferences.deviceModeFlow` dinlenirken kaydedilen mod değiştiğinde `OrientationManager` hızlı önbelleği eşitlendi.
  - **Adım 4:** `Daos.kt` içinde `LiveDao` arayüzündeki `live_streams` tablosunu okuyan 5 sorgu ve `getStreamsFiltered` varsayılan sıralaması `CASE WHEN num > 0 THEN 0 ELSE 1 END, num ASC, rowid ASC` haline getirildi.
  - **Adım 5:** `DeviceModeRecommendTest.kt` dosyası oluşturularak 5 farklı cihaz senaryosu (Resmi TV, sertifikasız kutu, Wi-Fi tablet, telefon, dokunmatiksiz cihaz) test edildi.
- **Değiştirilen dosyalar:**
  - `app/src/main/java/com/tivimax/player/ui/settings/SettingsScreen.kt`
  - `app/src/main/java/com/tivimax/player/util/DeviceTypeUtil.kt`
  - `app/src/main/java/com/tivimax/player/ui/MainViewModel.kt`
  - `app/src/main/java/com/tivimax/player/data/local/dao/Daos.kt`
  - `app/src/test/java/com/tivimax/player/util/DeviceModeRecommendTest.kt`
  - `docs/gorevler/G-036-tv-ayarlar-kaydirma-cihaz-modu-kanal-sirasi.md`
  - `YAPILACAKLAR.md`
- **Test sonuçları:**
  - `./gradlew compileDebugKotlin`: Başarılı (`BUILD SUCCESSFUL in 32s`).
  - `./gradlew testDebugUnitTest`: Başarılı (`BUILD SUCCESSFUL in 8s`, 28 görev, yeni eklenen test dahil tüm testler geçti).
  - Fiziksel TV / emülatör bağlı olmadığı için ekran üzerinde manuel test yapılamadı.
