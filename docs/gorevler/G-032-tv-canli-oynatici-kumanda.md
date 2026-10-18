# G-032 — TV: canlı yayın tam ekran oynatıcıda kumanda tuşları düzgün çalışsın

- **Öncelik:** 🔴
- **Agent dağılımı:** Tek agent (bölme). Gemini 3.6 Flash için adım adım — **adımların dışına çıkma, kendi fikrini ekleme**.
- **Branch:** `fix/tv-canli-kumanda` (main'den aç)
- **Commit mesajı:** `fix(tv): canlı tam ekran oynatıcıda menü, kanal listesi ve ses/altyazı paneli kumandayla kullanılabilir`
- **Değişecek dosya (SADECE bu):** `app/src/main/java/com/tivimax/player/ui/tv/TvPlayerOverlay.kt`
  (+ bu görev dosyasının Agent raporu ve `YAPILACAKLAR.md` durum satırı)
- **Sadece TV'yi etkiler.** Telefon/tablet bu dosyayı kullanmaz.

## Bulunan hatalar (Claude kod incelemesi)
1. **Alt menü (Ses ve altyazı / Görüntü / Favoriye ekle / Program rehberi / son kanallar) kumandayla kullanılamıyor.**
   Kutunun en dışındaki `onPreviewKeyEvent` tuşları **önce** yakalıyor: menü açıkken OK → yayını duraklatıyor,
   ▲ → kanal değiştiriyor, ◀ → rehberi açıyor. Menüdeki düğmelere tuş hiç ulaşmıyor. (TV'de favori ekleme ve
   ses/altyazı değiştirme bu yüzden imkânsız.)
2. **▼ tuşu iki farklı iş yapıyor:** bilgi kartı görünürken menüyü açıyor, değilse önceki kanala geçiyor.
   Kanal değiştirince bilgi kartı 5 sn göründüğü için, art arda ▼ basan kullanıcı kanal yerine menüye düşüyor.
3. **OK basılı tutulunca** her tekrar olayında duraklat/oynat arka arkaya değişiyor; sonra görünmeyen bir
   "hızlı ayarlar" durumu açılıyor (ekranda karşılığı yok) ve Geri'ye basana kadar tuşlar çalışmıyor.
4. **◀ ile açılan kanal listesinde odak yok** ve liste oynayan kanala kaydırılmıyor; ilk tuş belirsiz yere gidiyor.
5. **Ses ve altyazı paneli açılınca odak panele geçmiyor**, kapatınca menüye geri dönmüyor.

## Yeni tuş haritası (tam ekran canlı yayın)
| Tuş | Ne yapar |
|---|---|
| ▲ / CH+ | Sonraki kanal (değişmedi) |
| ▼ / CH- | **Her zaman** önceki kanal |
| ◀ | Kanal listesi açılır, **oynayan kanal seçili gelir** |
| ▶ | **Menü** açılır (bilgi kartı + son kanallar + düğmeler), odak "Ses ve altyazı"da |
| OK | Bilgi kartı kapalıysa açar; açıksa duraklat/oynat (değişmedi). **Basılı tutmak artık bir şey yapmaz.** |
| Geri | En üstteki paneli kapatır; hiçbiri yoksa tam ekrandan çıkar (değişmedi) |
| Menü açıkken | ◀ ▶ ▲ ▼ ve OK **menünün içinde** çalışır; Geri menüyü kapatır |
| Kanal listesi açıkken | ▲ ▼ gezinir, OK o kanala geçer **ve listeyi kapatır**, ▶ veya Geri listeyi kapatır |

---

## Adım 1 — Yeni odak isteyicileri ve liste durumu
`val actionDockFocusRequester = remember { FocusRequester() }` satırının **hemen altına** ekle:
```kotlin
    // G-032: Kanal listesi ve ses/altyazı paneli için odak + kaydırma
    val guideFocusRequester = remember { FocusRequester() }
    val trackSheetFocusRequester = remember { FocusRequester() }
    val guideListState = rememberLazyListState()
    val guideFocusIndex = remember(allStreams, activeChannel.streamId) {
        allStreams.indexOfFirst { it.streamId == activeChannel.streamId }.coerceAtLeast(0)
    }
```

## Adım 2 — Paneller açılınca odak doğru yere gitsin
Mevcut `LaunchedEffect(showInfoCard, showActionTray, showOverlayGuide, showQuickSettings, showTrackSheet) { ... }`
bloğunun **hemen ALTINA** iki yeni blok ekle (mevcut bloğa dokunma):
```kotlin
    // G-032: Kanal listesi açılınca oynayan kanala kaydır ve odakla
    LaunchedEffect(showOverlayGuide) {
        if (showOverlayGuide && allStreams.isNotEmpty()) {
            try { guideListState.scrollToItem(guideFocusIndex) } catch (_: Exception) {}
            delay(120)
            try { guideFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    // G-032: Ses/altyazı paneli açılınca panele, kapanınca menüye odak
    LaunchedEffect(showTrackSheet) {
        if (showTrackSheet) {
            delay(150)
            try { trackSheetFocusRequester.requestFocus() } catch (_: Exception) {}
        } else if (showActionTray) {
            delay(80)
            try { actionDockFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }
```

## Adım 3 — Dış tuş yakalayıcı (`.onPreviewKeyEvent { keyEvent -> ... }` en dıştaki Box'ta)

**3a.** Şu bloğu bul:
```kotlin
                // EĞER AÇIK SÜTÜN VEYA YAN PANEL VARSA
                if (showOverlayGuide || showQuickSettings || showTrackSheet) {
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        showOverlayGuide = false
                        showQuickSettings = false
                        showTrackSheet = false
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }
```
Şununla **değiştir**:
```kotlin
                // EĞER AÇIK SÜTÜN VEYA YAN PANEL VARSA
                if (showOverlayGuide || showQuickSettings || showTrackSheet) {
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        showOverlayGuide = false
                        showQuickSettings = false
                        showTrackSheet = false
                        return@onPreviewKeyEvent true
                    }
                    // G-032: Kanal listesi açıkken ▶ listeyi kapatır
                    if (showOverlayGuide && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        showOverlayGuide = false
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }

                // G-032: Menü (alt tepsi) açıkken tuşlar menünün içinde çalışsın; Geri menüyü kapatır
                if (showActionTray) {
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        showActionTray = false
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }
```

**3b.** OK dalını bul:
```kotlin
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        if (keyEvent.nativeKeyEvent.isLongPress) {
                            showQuickSettings = true
                            return@onPreviewKeyEvent true
                        }
```
Şöyle yap (basılı tutma tekrarlarını yut, görünmeyen paneli açma):
```kotlin
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        if (keyEvent.nativeKeyEvent.repeatCount > 0) {
                            return@onPreviewKeyEvent true
                        }
```
(Bu dalın geri kalanı — `if (!showInfoCard) ... else playerManager.togglePlayPause()` — **aynen kalsın**.)

**3c.** ▼ dalını bul:
```kotlin
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                        if (showInfoCard && !showActionTray) {
                            showActionTray = true
                            try { actionDockFocusRequester.requestFocus() } catch (_: Exception) {}
                        } else {
                            val prev = viewModel.zapPreviousChannel(activeChannel.streamId)
                            if (prev != null) seekRippleMessage = "⏮ ${prev.name}"
                            showInfoCard = true
                        }
                        return@onPreviewKeyEvent true
                    }
```
Şununla **değiştir** (▼ her zaman önceki kanal; menü artık ▶ ile):
```kotlin
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                        val prev = viewModel.zapPreviousChannel(activeChannel.streamId)
                        if (prev != null) seekRippleMessage = "⏮ ${prev.name}"
                        showInfoCard = true
                        return@onPreviewKeyEvent true
                    }

                    // G-032: ▶ menüyü açar ve odağı ilk düğmeye verir
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        showInfoCard = true
                        showActionTray = true
                        coroutineScope.launch {
                            delay(150)
                            try { actionDockFocusRequester.requestFocus() } catch (_: Exception) {}
                        }
                        return@onPreviewKeyEvent true
                    }
```
(`coroutineScope` bu dosyada zaten var: `val coroutineScope = rememberCoroutineScope()`. `launch` için
`import kotlinx.coroutines.launch` yoksa ekle.)

▲, ◀ ve Geri dallarına **dokunma**.

## Adım 4 — Kanal listesi (yorum: `// 5. YAYIN ÜSTÜ YARI SAYDAM KANAL LİSTESİ`)
Bu bölümdeki `LazyColumn`'u şöyle değiştir:
- `LazyColumn(` satırına `state = guideListState,` parametresini ekle.
- `items(allStreams, key = { "guide_${it.streamId}" }) { st ->` satırını şununla değiştir:
  ```kotlin
                        itemsIndexed(allStreams, key = { _, it -> "guide_${it.streamId}" }) { index, st ->
  ```
  (`import androidx.compose.foundation.lazy.itemsIndexed` ekle.)
- Bu öğenin `Surface` modifier zincirinde `.fillMaxWidth()` satırının **hemen altına** ekle:
  ```kotlin
                                    .then(if (index == guideFocusIndex) Modifier.focusRequester(guideFocusRequester) else Modifier)
  ```
- Aynı öğenin `onClick = { viewModel.playLiveChannel(st) }` satırını şununla değiştir:
  ```kotlin
                                        onClick = {
                                            viewModel.playLiveChannel(st)
                                            showOverlayGuide = false
                                            showInfoCard = true
                                        }
  ```

## Adım 5 — Ses ve altyazı paneli (yorum: `// 6. YAYIN ÜSTÜ SES & ALTYAZI AYARLARI PANELİ`)
Paneldeki "Kapat" `IconButton`'ının modifier'ını şöyle yap:
```kotlin
                            modifier = Modifier
                                .size(32.dp)
                                .focusRequester(trackSheetFocusRequester)
```
(Panel açılınca odak buraya gelir; ▼ ile ses/altyazı seçeneklerine inilir.)

## Adım 6 — Bilgi kartında küçük tuş ipucu
`// 4. KATMAN A & B: RECENT CHANNELS & ACTION CHIPS` yorum satırının **hemen ÜSTÜNE** ekle:
```kotlin
                    if (!showActionTray) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "▲▼ Kanal değiştir   ◀ Kanal listesi   ▶ Menü",
                            color = VegaColors.TextMuted,
                            fontSize = VegaText.Caption,
                            maxLines = 1
                        )
                    }
```
Yorum satırını `// 4. KATMAN A & B: RECENT CHANNELS & ACTION CHIPS (▶ TUŞU İLE AÇILIR)` yap.

---

## Kontrol ve gönderim
1. `./gradlew compileDebugKotlin` → hata varsa düzelt (isim uydurmadan; dosyada olan değişkenleri kullan).
2. `./gradlew testDebugUnitTest` → tüm testler geçmeli.
3. `git diff --stat`: **sadece** `TvPlayerOverlay.kt`, bu görev dosyası ve `YAPILACAKLAR.md`. Başka dosya değiştiyse geri al.
4. Commit → `fix/tv-canli-kumanda` dalına push. **Main'e birleştirme. APK üretme. Sürüm numarasını değiştirme.**
5. `YAPILACAKLAR.md`'de G-032 durumunu `🔍 İncelemede (branch: fix/tv-canli-kumanda)` yap.

## DOKUNMA
`PlayerScreen.kt`, `MainActivity.kt`, `TvTiviMateScreen.kt`, `MainViewModel.kt`, `TiviPlayerManager`, telefon/tablet
ekranları, rakamla kanal seçme (zapper), güncelleme sistemi (AutoUpdateManager), imza, CI, sürüm numarası.

## Test (TV/emülatör varsa — yoksa "test edilemedi" yaz)
- Canlı kanal tam ekran: ▶ → menü açılır, odak "Ses ve altyazı"da; ◀▶ ile düğmeler arasında gezilir; OK düğmeye basar
  (Favoriye ekle çalışır, Görüntü değişir). Geri → menü kapanır.
- Menüde "Ses ve altyazı" → sağda panel açılır, odak "Kapat"ta; ▼ ile ses dilleri; Geri → panel kapanır, odak menüye döner.
- ◀ → kanal listesi açılır, oynayan kanal seçili; ▼ ile başka kanal, OK → kanal değişir ve liste kapanır.
- ▼ art arda: her basışta önceki kanala geçer (menü açılmaz).
- OK basılı tut: yayın bir kez duraklar/oynar, titreme yok, ekran kilitlenmez.

---

## Agent raporu

- **Yapılan değişiklikler:**
  - **Adım 1:** `guideFocusRequester`, `trackSheetFocusRequester`, `guideListState` ve aktif kanal index'ini hesaplayan `guideFocusIndex` tanımlandı.
  - **Adım 2:** `LaunchedEffect(showOverlayGuide)` ve `LaunchedEffect(showTrackSheet)` blokları eklenerek kanal listesi açıldığında aktif kanala kaydırma + odaklanma, ses/altyazı paneli açıldığında panele, kapandığında menüye odak geçişi sağlandı.
  - **Adım 3:** Dış `onPreviewKeyEvent` güncellendi:
    - Kanal listesi açıkken `KEYCODE_DPAD_RIGHT` ile rehberin kapatılması sağlandı.
    - Menü (`showActionTray`) açıkken tuşların menü içine iletilmesi ve `KEYCODE_BACK` ile menünün kapanması sağlandı.
    - OK tuşunda `repeatCount > 0` durumunda tuş yutuldu, hatalı hızlı ayarlar açılışı kaldırıldı.
    - `KEYCODE_DPAD_DOWN` her zaman önceki kanala geçecek şekilde sadeleştirildi.
    - `KEYCODE_DPAD_RIGHT` menüyü (`showActionTray`) açıp odağı ilk düğmeye (`actionDockFocusRequester`) verecek şekilde eklendi.
  - **Adım 4:** Yayın üstü kanal listesinde `LazyColumn`'a `state = guideListState` verildi, `itemsIndexed` kullanılarak oynayan kanala `guideFocusRequester` atandı ve kanal tıklandığında listenin kapatılması (`showOverlayGuide = false`) sağlandı.
  - **Adım 5:** Ses ve altyazı panelindeki Kapat butonuna `trackSheetFocusRequester` atandı.
  - **Adım 6:** Bilgi kartına `▲▼ Kanal değiştir   ◀ Kanal listesi   ▶ Menü` ipucu metni eklendi.
- **Değiştirilen dosyalar:**
  - `app/src/main/java/com/tivimax/player/ui/tv/TvPlayerOverlay.kt`
  - `docs/gorevler/G-032-tv-canli-oynatici-kumanda.md`
  - `YAPILACAKLAR.md`
- **Test sonuçları:**
  - `./gradlew compileDebugKotlin`: Başarılı (BUILD SUCCESSFUL in 19s).
  - `./gradlew testDebugUnitTest`: Başarılı (BUILD SUCCESSFUL in 6s, 28 actionable tasks, tüm testler geçti).
  - Canlı TV/emülatör bağlı olmadığı için fiziksel kumanda üzerinde manuel test edilemedi.
