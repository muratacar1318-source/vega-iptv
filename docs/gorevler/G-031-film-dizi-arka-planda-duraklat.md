# G-031 — Film/dizi arka plana atılınca küçük pencereye geçmesin, duraklasın; canlı yayın küçük pencerede devam etsin

- **Öncelik:** 🟠
- **Agent dağılımı:** Tek agent (bölme). Gemini 3.6 Flash için adım adım — **adımların dışına çıkma, kendi fikrini ekleme**.
- **Branch:** `fix/arka-plan-oynatma` (main'den aç)
- **Commit mesajı:** `fix(player): film/dizi arka planda duraklıyor, küçük pencere sadece canlı yayında`
- **Değişecek dosyalar (sadece bunlar):**
  1. `app/src/main/java/com/tivimax/player/MainActivity.kt`
  2. `app/src/main/java/com/tivimax/player/ui/player/PlayerScreen.kt`
  3. `app/src/main/java/com/tivimax/player/ui/settings/SettingsScreen.kt` (1 metin)
  4. Bu görev dosyası (Agent raporu) ve `YAPILACAKLAR.md` (durum satırı)

## Kullanıcının istediği
- **Film / dizi:** Oynarken kullanıcı Ana ekran tuşuna basarsa (uygulama arka plana giderse) video **küçük pencereye
  (PiP) GEÇMESİN**, **duraklasın**. Uygulamaya dönünce **kendiliğinden başlamasın** — kullanıcı oynat'a bassın.
- **Canlı TV:** Bu kural uygulanmaz. Ana ekrana dönünce kanal **küçük pencerede oynamaya devam etsin** (bugünkü gibi,
  ayar açıksa).
- Oynatıcıdaki elle "küçük pencere" düğmesi **kalsın** (film/dizide de kullanıcı isterse basabilir).
- TV cihazı davranışı **değişmeyecek**.

## Mevcut durum (neden oluyor)
- `MainActivity.onUserLeaveHint()` ve `PlayerScreen` içindeki Android 12+ otomatik PiP kaydı, **canlı/film ayırt etmeden**
  küçük pencereyi açıyor.
- `MainActivity.onResume()` ve `PlayerScreen` yaşam döngüsü gözlemcisi, dönüşte **her içeriği** yeniden başlatıyor.

---

## Adım 1 — `MainActivity.kt` → `onUserLeaveHint()`
Şu satırı bul:
```kotlin
                if (hasPip && autoPip && isPlayerScreen && isPlaying) {
```
Şöyle yap:
```kotlin
                val isLive = viewModel.playerManager.isLiveStream
                if (hasPip && autoPip && isPlayerScreen && isPlaying && isLive) {
```
TV dalına (`if (isTvModeActive())`) dokunma.

## Adım 2 — `MainActivity.kt` → `onResume()`
Şu bloğu bul:
```kotlin
            if (isPlayerScreen || isHomeScreen) {
                if (viewModel.playerManager.isLiveStream) {
                    try {
                        viewModel.playerManager.player?.seekToDefaultPosition()
                    } catch (_: Throwable) {}
                }
                viewModel.playerManager.play()
            }
```
Şöyle yap (film/dizi dönüşte kendiliğinden BAŞLAMASIN; canlı aynen devam):
```kotlin
            if ((isPlayerScreen || isHomeScreen) && viewModel.playerManager.isLiveStream) {
                try {
                    viewModel.playerManager.player?.seekToDefaultPosition()
                } catch (_: Throwable) {}
                viewModel.playerManager.play()
            }
```

## Adım 3 — `MainActivity.kt` → küçük pencere kapatılınca ses devam etmesin (ek hata düzeltmesi)
Kullanıcı küçük pencereyi **X ile kapatınca** Android aktiviteyi durdururken hâlâ "PiP içinde" gösterebiliyor; bu yüzden
`onStop` oynatıcıyı duraklatmıyor ve **ses arka planda devam edebiliyor**.
`onPictureInPictureModeChanged` içinde, `viewModel.isInPipMode.value = isInPictureInPictureMode` satırının **hemen altına** ekle:
```kotlin
            // G-031: Küçük pencere X ile kapatıldıysa (aktivite artık görünür değil) oynatmayı durdur
            if (!isInPictureInPictureMode &&
                !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
            ) {
                viewModel.playerManager.pause()
                wasPlayingBeforeStop = false
            }
```
Bu fonksiyondaki diğer satırlara dokunma.

## Adım 4 — `PlayerScreen.kt` → yaşam döngüsü gözlemcisi
Şu satırı bul (yorum: `// Lifecycle Observer (Arka plana atıldığında PiP açık değilse videoyu duraklat)` altındaki blok):
```kotlin
                if (!isControlsLocked && !isInPipMode) {
                    playerManager.play()
                }
```
Şöyle yap:
```kotlin
                if (!isControlsLocked && !isInPipMode && isLive) {
                    playerManager.play()
                }
```
(`isLive` bu fonksiyonun parametresi, zaten var.) Aynı yerde iki kez tekrar eden yorum satırı varsa birini sil.

## Adım 5 — `PlayerScreen.kt` → Android 12+ otomatik PiP
Şu satırları bul:
```kotlin
    DisposableEffect(isPlaying, autoPipEnabled) {
```
```kotlin
                        .setAutoEnterEnabled(!isTv && autoPipEnabled && isPlaying)
```
Şöyle yap:
```kotlin
    DisposableEffect(isPlaying, autoPipEnabled, isLive) {
```
```kotlin
                        .setAutoEnterEnabled(!isTv && autoPipEnabled && isPlaying && isLive)
```
Elle PiP düğmelerine (`activity.enterPictureInPictureMode(params)` olan 2 düğme) **dokunma**.

## Adım 6 — `SettingsScreen.kt` → ayar açıklaması
```kotlin
// ÖNCE
text = if (autoPipEnabled) "Uygulamadan çıkınca video köşede küçük pencerede oynamaya devam eder." else "Kapalı.",
// SONRA
text = if (autoPipEnabled) "Uygulamadan çıkınca canlı yayın köşede küçük pencerede oynamaya devam eder. Film ve diziler duraklatılır." else "Kapalı.",
```

---

## Kontrol ve gönderim
1. `./gradlew compileDebugKotlin` → hata varsa düzelt (isim uydurmadan).
2. `./gradlew testDebugUnitTest` → tüm testler geçmeli.
3. `git diff --stat`: **sadece** yukarıdaki dosyalar. Başka dosya değiştiyse geri al.
4. Commit → `fix/arka-plan-oynatma` dalına push. **Main'e birleştirme. APK üretme. Sürüm numarasını değiştirme.**
5. `YAPILACAKLAR.md`'de G-031 durumunu `🔍 İncelemede (branch: fix/arka-plan-oynatma)` yap.

## DOKUNMA
`TiviPlayerManager`, `MainViewModel`, uygulama içi mini oynatıcı (Canlı TV listesindeki önizleme), TV davranışı
(`isTvModeActive` dalları), `onPause`/`onStop` mantığı, güncelleme sistemi (AutoUpdateManager), imza, CI, sürüm numarası.

## Test (cihaz varsa — yoksa "test edilemedi" yaz)
- Film oynarken Ana ekran tuşu: küçük pencere açılmıyor, ses kesiliyor. Uygulamaya dön: film duraklatılmış, oynat'a basınca kaldığı yerden devam.
- Dizi bölümünde aynı test.
- Canlı kanal oynarken Ana ekran tuşu: kanal küçük pencerede oynamaya devam ediyor. Pencereye dokunup büyüt: tam ekran devam.
- Canlı küçük pencerede X ile kapat: ses tamamen kesiliyor.
- Filmde elle küçük pencere düğmesi: hâlâ çalışıyor.

---

## Agent raporu

- **Durum:** Tamamlandı, Claude'un incelemesine hazır.
- **Değişiklikler:**
  - `MainActivity.kt`:
    - Adım 1 (`onUserLeaveHint`): Otomatik PiP kontrolüne `isLive` şartı eklendi. Uygulama arka plana alındığında küçük pencereye (PiP) geçiş sadece canlı yayınlarda çalışıyor; film ve dizilerde PiP tetiklenmiyor.
    - Adım 2 (`onResume`): `onResume()` sırasında uygulamanın otomatik oynatma başlatması (`play()`) sadece canlı yayınlar ile sınırlandırıldı. Film ve dizi içeriklerinde uygulamaya dönüldüğünde video duraklatılmış olarak kalıyor, kullanıcının oynat düğmesine basması bekleniyor.
    - Adım 3 (`onPictureInPictureModeChanged`): PiP penceresi X butonuyla kapatıldığında (`!isInPictureInPictureMode` ve yaşam döngüsü `STARTED` seviyesinin altında kaldığında) oynatıcı duraklatılıp `wasPlayingBeforeStop = false` yapıldı, böylece sesin arka planda devam etmesi engellendi.
  - `PlayerScreen.kt`:
    - Adım 4 (Lifecycle Observer): `ON_START` / `ON_RESUME` yaşam döngüsü olaylarında `playerManager.play()` çağrısı sadece `isLive == true` durumu için geçerli kılındı.
    - Adım 5 (Android 12+ Otomatik PiP Kaydı): `DisposableEffect` bağımlılıklarına `isLive` eklendi ve `setAutoEnterEnabled(!isTv && autoPipEnabled && isPlaying && isLive)` şeklinde güncellenerek Android 12+ sistem seviyesi PiP kaydının da sadece canlı yayınlarda aktif olması sağlandı. Manuel PiP düğmelerine dokunulmadı.
  - `SettingsScreen.kt`:
    - Adım 6 (Ayar Açıklaması): PiP ayar açıklaması `"Uygulamadan çıkınca canlı yayın köşede küçük pencerede oynamaya devam eder. Film ve diziler duraklatılır."` olarak güncellendi.
- **Doğrulama:**
  - `./gradlew compileDebugKotlin` başarıyla tamamlandı.
  - `./gradlew testDebugUnitTest` başarıyla tamamlandı (60 test geçti, 0 hata).
  - Fiziksel cihaz / emülatör bağlı olmadığı için PiP canlı testi yapılamadı (derleme ve birim testleri ile doğrulandı).
