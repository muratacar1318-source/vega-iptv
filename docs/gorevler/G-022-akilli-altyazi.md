# G-022 — Akıllı altyazı / ses dili eşleme (düzeltme)

- **Öncelik:** 🟠
- **Agent dağılımı:** Tek agent
- **Branch:** `feat/akilli-altyazi-secimi` (MEVCUT branch'te devam)
- **Commit mesajı:** `fix(player): akıllı altyazı kuralı sadece içerik açılışında bir kez uygulanıyor, kullanıcı seçimi ezilmiyor`

## Durum

Kullanıcı Antigravity'ye doğrudan istedi; branch'te ilk sürüm var (`7dd75f3`): Türkçe seste altyazı kapalı,
İngilizce seste Türkçe altyazı açık, ses ve altyazı aynı dildeyse altyazı kapalı. Fikir doğru, ama Claude
incelemesinde **ciddi bir kullanıcı hatası** bulundu; bu haliyle main'e alınmayacak.

## Bulunan sorunlar

1. **Kullanıcı seçimini sürekli eziyor.** `applySmartSubtitleLogic()` `extractTracks()` içinden çağrılıyor;
   `extractTracks()` her `onTracksChanged`'de çalışıyor — kullanıcı her altyazı seçtiğinde de tetikleniyor. Sonuç:
   - Türkçe sesli bir içerikte kullanıcı altyazıyı **hiç açamaz** (açar açmaz kapatılır).
   - İngilizce sesli içerikte kullanıcı altyazıyı **hiç kapatamaz** / başka dile geçemez (hemen Türkçeye döner).
   - `disableSubtitles()` / `selectTrackInternal()` yeni bir `onTracksChanged` doğurur → kural tekrar çalışır (gereksiz döngü).
2. **Ayarlardaki tercih yok sayılıyor:** Ayarlar → Oynatıcı → "Tercih edilen altyazı dili" (`setDefaultTrackLanguages`,
   ör. "Kapalı" ya da "İngilizce") ile çelişebilir.
3. **Sürüm numarası özellik branch'inde artırılmış** (`versionCode 55`, `1.3.3`, release notu). Sürüm sadece
   kullanıcı "release yap" deyince ve main'de ayrı bir commit olarak artırılır.

## Yapılacaklar

`git checkout feat/akilli-altyazi-secimi && git pull && git merge origin/main`

1. **Sürüm değişikliğini geri al:** `app/build.gradle.kts` → `versionCode = 54`, `versionName = "1.3.2"` (main'deki hali);
   `docs/release-notes/release_notes_v1.3.3.txt` dosyasını sil.
2. **Kural içerik başına BİR KEZ uygulansın:**
   - `TiviPlayerManager`'a `private var smartSubtitleAppliedForUrl: String? = null` ekle.
   - `extractTracks()` sonunda: sadece `currentPlayingUrl != null && smartSubtitleAppliedForUrl != currentPlayingUrl`
     ve ses parçaları belli (`audios.isNotEmpty()`) ise `applySmartSubtitleLogic(...)` çağır, sonra
     `smartSubtitleAppliedForUrl = currentPlayingUrl` yap.
   - `playInternal()` başında `smartSubtitleAppliedForUrl = null` (yeni içerik → kural yeniden uygulanabilir).
   - Kullanıcı **altyazıyı elle** seçtiğinde/kapattığında (`selectTrack(..., TRACK_TYPE_TEXT)`, `disableSubtitles()`
     kullanıcı çağrısı) kural bir daha çalışmasın (zaten bayrak set olduğu için çalışmaz — doğrula).
   - Kullanıcı **ses dilini elle** değiştirdiğinde (`selectTrack(..., TRACK_TYPE_AUDIO)`) kuralı bir kez uygulamak
     doğru (mevcut davranış kalsın) — ama bu, sonraki `onTracksChanged`'lerde tekrar çalışmamalı.
3. **Ayar tercihine saygı:** Kayıtlı "Tercih edilen altyazı dili" `"none"` (Kapalı) ise akıllı kural altyazıyı
   **açmasın** (sadece kapatma yönündeki kurallar çalışsın). `"auto"`/boş ise kural tam çalışır. Belirli bir dil
   seçiliyse (ör. `"en"`) İngilizce ses → Türkçe altyazı kuralı yerine o dilin altyazısını tercih et; yoksa dokunma.
   (Tercih değeri `setDefaultTrackLanguages` ile manager'a zaten geliyor; bir alanda sakla.)
4. Canlı yayında (`isLiveStream == true`) da aynı "bir kez" kuralı geçerli.

## DOKUNMA
`DelayedSubtitleController`, yüzey devri, medya oturumu (G-020), fallback, ayarlar ekranı, güncelleme, imza, CI.

## Test
- Türkçe dublajlı film: altyazı kapalı açılır; kullanıcı Türkçe altyazıyı açınca **açık kalır**.
- İngilizce film: Türkçe altyazı açık başlar; kullanıcı altyazıyı kapatınca **kapalı kalır**; İngilizce altyazıya geçince **o kalır**.
- Ayar "Tercih edilen altyazı dili: Kapalı" iken İngilizce film: altyazı kapalı başlar.
- Film içinde ses dilini İngilizceden Türkçeye çevir: altyazı kapanır (bir kez); sonra elle açılabiliyor.
- Yerelde sadece `./gradlew compileDebugKotlin`; APK üretme, release YAPMA. Test edemediğini "test edilemedi" yaz.

---

## Agent raporu

_(Agent doldurur.)_
