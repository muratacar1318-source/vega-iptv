# G-033 — TV: menü ↔ içerik geçişleri ve film/dizi oynatıcıda kumanda

- **Öncelik:** 🔴
- **Agent dağılımı:** Tek agent (bölme). Gemini 3.6 Flash için adım adım — **adımların dışına çıkma, kendi fikrini ekleme**.
- **Ön koşul:** G-032 main'e alınmış olmalı (aynı anda yapma).
- **Branch:** `fix/tv-menu-gecis` (main'den aç)
- **Commit mesajı:** `fix(tv): menüden içeriğe dönüşte odak hafızası, ▲▼ ile menüye kaçma engeli, film oynatıcıda zaman çubuğu odağı`
- **Değişecek dosyalar (SADECE bunlar):**
  1. `app/src/main/java/com/tivimax/player/ui/main/HomeScreen.kt` (sadece TV dalındaki içerik `Box`'ı + import/OptIn)
  2. `app/src/main/java/com/tivimax/player/ui/tv/TvTiviMateScreen.kt` (2 `LazyColumn` + import/OptIn)
  3. `app/src/main/java/com/tivimax/player/ui/components/CategorySidebar.kt` (1 `LazyColumn` + import/OptIn)
  4. `app/src/main/java/com/tivimax/player/ui/player/PlayerScreen.kt` (2 küçük yer, **sadece TV'de çalışır**)
  5. Bu görev dosyası (Agent raporu) ve `YAPILACAKLAR.md` (durum satırı)

## Bulunan sorunlar (Claude kod incelemesi + referans karşılaştırması)
Referans uygulama (kod kopyalanmadı, sadece davranış): sol menüden sağa dönünce **en son bırakılan öğeye** gelinir;
▲/▼ tuşları **asla** yandaki menüye atlamaz, menüye sadece ◀ ile girilir; liste sütunları son seçili öğeyi hatırlar.
Bizde:
1. Sol menüden ▶ ile içeriğe dönünce odak, ekranda **en yakın** öğeye gidiyor (kaldığın yere değil).
2. İçeriğin en üstünde/altında ▲/▼ basınca odak **sol menüye kaçabiliyor**.
3. Canlı TV'de kanal listesi ↔ kategori sütunu ve Film/Dizi'de kategori sütunu ↔ afişler arasında geçince son seçili
   öğe hatırlanmıyor.
4. Film/dizi oynatıcıda (TV) kontroller açılınca odak ortadaki oynat düğmesinde; ◀▶ sarmak yerine düğmeler arasında
   geziyor. Kontroller kapalıyken ▲/▼ hiçbir şey yapmıyor.

---

## Adım 1 — HomeScreen.kt: TV içerik alanı odak grubu

**1a.** `fun HomeScreen` üstündeki satırı:
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
```
şöyle yap:
```kotlin
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
```

**1b.** TV dalında (`if (isTvDevice) {` altındaki `Row` içinde, `TvNavigationRail(...)`'ın hemen sonrasında) şu `Box`'ı bul:
```kotlin
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                if (selectedTab == HomeTab.LIVE_TV) {
```
Şöyle yap (içeriğe dokunma, sadece modifier):
```kotlin
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    // G-033: ▲▼ ile sol menüye kaçma yok (menüye sadece ◀ ile girilir)
                    .focusProperties {
                        onExit = {
                            if (requestedFocusDirection == FocusDirection.Up ||
                                requestedFocusDirection == FocusDirection.Down
                            ) {
                                cancelFocusChange()
                            }
                        }
                    }
                    // G-033: menüden ▶ ile dönünce en son odaklanan öğeye gel
                    .focusRestorer()
                    .focusGroup()
            ) {
                if (selectedTab == HomeTab.LIVE_TV) {
```
Gerekli import'lar (yoksa ekle):
```kotlin
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
```
> **Derleme hatası olursa (sadece `onExit` / `requestedFocusDirection` / `cancelFocusChange` için):** bu Compose sürümünde
> yeni API yoksa `.focusProperties { ... }` bloğunu şu eski yazımla değiştir (davranış aynı):
> ```kotlin
>                     .focusProperties {
>                         exit = { direction ->
>                             if (direction == FocusDirection.Up || direction == FocusDirection.Down) {
>                                 FocusRequester.Cancel
>                             } else {
>                                 FocusRequester.Default
>                             }
>                         }
>                     }
> ```
> Hangisini kullandığını rapora yaz. Başka bir çözüm deneme.

**Telefon/tablet dalına (`} else Scaffold(`) dokunma.**

## Adım 2 — TvTiviMateScreen.kt: sütunlar son öğeyi hatırlasın
**2a.** `fun TvTiviMateScreen` üstündeki `@OptIn(UnstableApi::class)` satırını
`@OptIn(UnstableApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)` yap.

**2b.** `// Dinamik Kategoriler` yorumunun altındaki `LazyColumn`'un modifier'ı:
```kotlin
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
```
→
```kotlin
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .focusRestorer(),
```

**2c.** Kanal listesi `LazyColumn`'u (`state = channelListState,` olan):
```kotlin
                            modifier = Modifier.fillMaxSize(),
```
→
```kotlin
                            modifier = Modifier.fillMaxSize().focusRestorer(),
```
(Sadece `state = channelListState` satırının hemen altındaki modifier. Import: `androidx.compose.ui.focus.focusRestorer`.)

## Adım 3 — CategorySidebar.kt
`fun CategorySidebar` üstüne `@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)` ekle (`@Composable`'ın üstüne).
`LazyColumn(` içindeki `modifier = Modifier.fillMaxSize(),` → `modifier = Modifier.fillMaxSize().focusRestorer(),`
Import: `androidx.compose.ui.focus.focusRestorer`. (Dokunmatik ekranda odak olmadığı için telefonu etkilemez.)

## Adım 4 — PlayerScreen.kt (film/dizi, SADECE TV)
`val isTvDevice = LocalIsTvDevice.current` fonksiyonun başında zaten var; onu kullan.

**4a.** Şu bloğu bul (yorum: `// HUD açıldığında ilk butona, kapandığında ana oynatıcıya odağı ver`):
```kotlin
        if (showControls) {
            delay(120)
            try {
                firstButtonFocusRequester.requestFocus()
            } catch (e: Exception) { }
        } else {
```
Şöyle yap:
```kotlin
        if (showControls) {
            delay(120)
            try {
                // G-033: TV'de film/dizide odak zaman çubuğuna: ◀▶ sarar, OK duraklatır, ▼ alt düğmeler
                if (isTvDevice && !isLive) sliderFocusRequester.requestFocus()
                else firstButtonFocusRequester.requestFocus()
            } catch (e: Exception) {
                try { firstButtonFocusRequester.requestFocus() } catch (_: Exception) { }
            }
        } else {
```

**4b.** `// HUD KAPALIYKEN (Normal İzleme Modu):` altındaki `when (keyCode)` içinde:
- `KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP ->` dalının sonundaki tek başına `false` satırını şununla değiştir:
  ```kotlin
                            if (isTvDevice) {
                                showControls = true
                                registerInteraction()
                                return@onPreviewKeyEvent true
                            }
                            false
  ```
- `KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN ->` dalının sonundaki tek başına `false` satırını da **aynı** blokla değiştir.

(Canlı yayın dalları — `if (isLive) { ... }` — aynen kalır. ◀ ▶ OK Geri dallarına dokunma.)

---

## Kontrol ve gönderim
1. `./gradlew compileDebugKotlin` → hata varsa düzelt (isim uydurmadan; Adım 1'deki yedek yazım dışında yöntem değiştirme).
2. `./gradlew testDebugUnitTest` → tüm testler geçmeli.
3. `git diff --stat`: **sadece** yukarıdaki dosyalar. Başka dosya değiştiyse geri al.
4. Commit → `fix/tv-menu-gecis` dalına push. **Main'e birleştirme. APK üretme. Sürüm numarasını değiştirme.**
5. `YAPILACAKLAR.md`'de G-033 durumunu `🔍 İncelemede (branch: fix/tv-menu-gecis)` yap.

## DOKUNMA
`TvNavigationRail.kt`, `TvPlayerOverlay.kt`, `MainActivity.kt`, `MainViewModel.kt`, telefon/tablet yerleşimleri
(`Scaffold` dalı), canlı yayın tuşları, güncelleme sistemi (AutoUpdateManager), imza, CI, sürüm numarası.

## Test (TV/emülatör varsa — yoksa "test edilemedi" yaz)
- Filmler: bir afişe gel, ◀◀ ile sol menüye çık, ▶ ile dön → aynı afişe gelinir.
- Filmler: en üst satırda ▲ → menüye atlamaz; en solda ◀ → kategori sütunu, oradan ▶ → bıraktığın afiş.
- Canlı TV: kanal listesinde aşağı in, ◀ kategoriler, ▶ → bıraktığın kanal.
- Film oynat → OK: kontroller açılır, odak zaman çubuğunda; ◀▶ 10 sn sarar; OK duraklatır; ▼ alt düğmeler.
- Film oynarken kontroller kapalı ▲ veya ▼ → kontroller açılır.
- Telefonda: film oynatıcı ve ana ekran eskisi gibi.

---

## Agent raporu

- **Yapılan değişiklikler:**
  - **Adım 1:** `HomeScreen.kt`'de `HomeScreen` bileşenine `@OptIn(..., ExperimentalComposeUiApi::class)` eklendi. TV içerik alanındaki `Box`'a `.focusProperties { onExit = { ... } }` ile ▲/▼ basıldığında menüye kaçma engellendi, `.focusRestorer()` ve `.focusGroup()` ile menüden dönüldüğünde en son odaklanan öğeye gelinmesi sağlandı (Adım 1'de yeni `onExit`/`requestedFocusDirection`/`cancelFocusChange` API'si kullanıldı).
  - **Adım 2:** `TvTiviMateScreen.kt`'de `@OptIn` güncellendi, hem dinamik kategoriler hem de kanal listesi `LazyColumn`'larına `.focusRestorer()` eklendi.
  - **Adım 3:** `CategorySidebar.kt`'de `@OptIn` eklendi ve kategori listesi `LazyColumn`'una `.focusRestorer()` eklendi.
  - **Adım 4:** `PlayerScreen.kt`'de TV modunda (`isTvDevice && !isLive`) HUD açıldığında odak `sliderFocusRequester`'a verildi; HUD kapalıyken ▲ ve ▼ tuşlarının kontrolleri açması sağlandı.
- **Değiştirilen dosyalar:**
  - `app/src/main/java/com/tivimax/player/ui/main/HomeScreen.kt`
  - `app/src/main/java/com/tivimax/player/ui/tv/TvTiviMateScreen.kt`
  - `app/src/main/java/com/tivimax/player/ui/components/CategorySidebar.kt`
  - `app/src/main/java/com/tivimax/player/ui/player/PlayerScreen.kt`
  - `docs/gorevler/G-033-tv-menu-gecisleri-film-kumanda.md`
  - `YAPILACAKLAR.md`
- **Test sonuçları:**
  - `./gradlew compileDebugKotlin`: Başarılı (`BUILD SUCCESSFUL in 15s`).
  - `./gradlew testDebugUnitTest`: Başarılı (`BUILD SUCCESSFUL in 3s`, 28 görev, tüm birim testler geçti).
  - Fiziksel TV cihazı / emülatör bağlı olmadığı için kumanda ile manuel test edilemedi.
