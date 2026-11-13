# G-057 — Kalan sabit renkler tasarım tokenlarına, sessiz hatalara log

- **Öncelik:** 🟡
- **Agent dağılımı:** Tek agent. (G-052 main'e alındı, ön koşul sağlandı.)
- **Branch:** `chore/renk-token-ve-loglar` (main'den aç)
- **Commit mesajları:** Bölüm A `style(ui): kalan sabit renkler tasarım tokenlarına çevrildi`,
  Bölüm B `chore(log): veri ve ağ katmanındaki sessiz hatalar loglanıyor`
- **Değişecek dosyalar (yollar `app/src/main/java/com/tivimax/player/` altına göre):**
  - A: `ui/live/LiveTvWideScreenLayout.kt`, `ui/components/TvFocusable.kt`, `ui/live/LiveTvScreen.kt`,
    `ui/player/components/PlayerSettingsPanel.kt`, `ui/settings/SettingsScreen.kt`,
    `ui/components/AsyncShimmerImage.kt`, `ui/login/LoginScreen.kt` — sadece renk satırları
  - B: `data/local/AppDatabase.kt`, `data/network/BoundedDns.kt`, `data/network/StreamingHttpClient.kt`,
    `data/playlist/PlaylistRepository.kt`, `data/preferences/UserPreferences.kt`, `data/repository/XtreamRepository.kt`
    — sadece `catch` blokları
  - Rapor ve `YAPILACAKLAR.md`'de G-057 satırı

## Bölüm A — Sabit renkler
`ui/design` ve `theme/` dışındaki 28 `Color(0x…)` kullanımı (yukarıdaki 7 dosya) ve G-050'den kalan `Color.Red`
kullanımları `docs/TASARIM-KURALLARI.md`'deki tokenlara çevrilir:
- Kırmızı tonları (`0xFFEF5350` vb.), `Color.Red` → `VegaColors.Danger`
- Kart/çerçeve/yüzey grileri → `VegaColors.Card / Border / SurfaceVariant`
- Metin grileri → `VegaColors.TextSecondary / TextMuted`
- Vurgu (camgöbeği) → `VegaColors.Accent`
- Tam karşılığı olmayan renk (ör. shimmer gradyanındaki ara tonlar): `VegaColors.Xxx.copy(alpha = …)` ile en yakını;
  hiç uymuyorsa dokunma, raporda listele.
- `Color.Black.copy(alpha=…)` gradyan/karartma kullanımları kurala göre serbesttir; dokunma.
Kontrol: `grep -rn "Color(0x" app/src/main/java/com/tivimax/player/ui --include=*.kt | grep -v "ui/design\|theme/"`
→ sadece raporda gerekçelendirilenler kalmalı.

## Bölüm B — Sessiz hatalar
Yukarıdaki 6 veri/ağ dosyasında hatayı yutan `catch (_: …)` / boş `catch` bloklarına
`Log.w("<DosyaAdı>", "<ne yapılırken>", e)` ekle (değişken adı `_` ise `e` yap). Akış değişmez: dönen değer,
yeniden deneme, varsayılan değer aynen kalır.
- **İstisnalar (dokunma):** `SecurityUtils` (çözülen dizgiler loglanmamalı), sık çağrılan sıcak yollar
  (ör. her kanal kartında çalışan ayrıştırma) — bunlar için log yerine yorum `// sessiz: <neden>` ekle.
- Log mesajlarında URL, kullanıcı adı, şifre, sunucu adresi **olmaz** (`e.message` Xtream adresi içerebilir:
  sadece `e.javaClass.simpleName` yaz ya da G-051'deki `CrashLogger.sanitize(...)` ile temizle).

## Bölüm C — Ana sayfada uydurma değer (PR #63'ten kalan)
`ui/main/OpusDashboardScreen.kt` (değişecek dosyalara eklenmiştir), üstteki kullanıcı kartı:
- Bağlantı rozeti `activeCons`/`maxConnections` boşsa `"1"` uyduruyor ("1 / 1 Hat"). Değer bilinmiyorsa rozeti
  hiç gösterme; biliniyorsa metin "N / M hat" (küçük harf).
- "Abonelik aktif" her zaman yazıyor. `userInfo.status` gerçekten aktif değilse (ör. "Expired", "Banned",
  "Disabled") ya da `expDate` geçmişse `VegaColors.Danger` ile "Abonelik süresi doldu" / "Abonelik aktif değil";
  durum bilinmiyorsa satırı gizle. Durum metni karşılaştırması mevcut `AccountInfoFormatter` neyi kullanıyorsa onunla
  (yeniden yazma, onu çağır).
Commit: `fix(ana sayfa): bağlantı ve abonelik durumu sadece gerçek değerle gösteriliyor`

## Bölüm D — Açılışta +18 içeriğin anlık görünmesi (G-052 raporundan)
`ui/MainViewModel.kt` (değişecek dosyalara eklenmiştir), yalnızca üç içerik toplayıcısı (`getLive/Vod/SeriesStreamsFilteredFlow`
ile `blocked…CategoryIds`'i birleştiren `combine` blokları). Uygulama açılırken içerik listesi kategorilerden önce gelirse
engel kümesi boştur ve +18 afişler (ana sayfa dahil) bir an görünebilir.
- Her toplayıcıda, ilgili kategori akışı (`liveCategories`/`vodCategories`/`seriesCategories`) **ilk kez boş olmayan değer
  verene kadar ya da en fazla 2 sn** bekle (`withTimeoutOrNull(2_000) { xxxCategories.first { it.isNotEmpty() } }`), sonra
  birleştirmeye başla. Hiç kategorisi olmayan sağlayıcıda 2 sn sonra eski davranış.
- Bekleme yalnız ilk açılışta bir kez olmalı (kategori/arama değişimlerinde tekrar beklenmesin): bekleme `collectLatest`'in
  dışında, toplayıcı başlamadan önce yapılır.
Commit: `fix(ebeveyn): açılışta kategoriler yüklenmeden +18 içerik görünmüyor`

## 🚫 DOKUNMA
- Renk dışı UI değişikliği (Bölüm C hariç), `catch` dışı kod değişikliği.
- `AutoUpdateManager`, imza ayarları, `.github/workflows/`. Sürüm numarası. Release yapma.

## Kontrol
- `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` (düşük öncelikle, pencere açmadan) → geçmeli.

## Agent raporu
**A — Sabit renkler (commit 1):** `Color(0xFFEF5350)` (LiveTvScreen, LiveTvWideScreenLayout) ve `Color.Red` (SettingsScreen, G-050'den kalan 2 yer) → `VegaColors.Danger`; shimmer gradyanı (`AsyncShimmerImage`) → `Card / SurfaceVariant / Card`; `LiveTvWideScreenLayout` kanal logosu zemini `0xFF0F141F` → `VegaColors.Card`; `LoginScreen` gradyan ucu `Color(0xFF000000)` → `Color.Black`; `QualityBadge` (`TvFocusable`): 4K → `Gold`, HD → `Success`, 100FPS → `Accent`, 50FPS → `Accent.copy(alpha = 0.7f)` (en yakın), diğer → `SurfaceVariant`. (`VegaColors` importları eklendi.)
Tokenlara çevrilmeyip KALAN `Color(0x…)` (gerekçeli):
- `TvFocusable.kt:86` FHD rozeti `0xFF6366F1` (indigo): kalite rozetlerinin birbirinden ayrışması için karşılığı olan bir token yok (indigo vurgu rengi değil, tasarım kuralı; ama Accent/Success/Gold zaten diğer rozetlerde kullanılıyor). Karar gerekirse ayrı görev.
- `PlayerSettingsPanel.kt:583-586` ve `SettingsScreen.kt:1225-1227` altyazı rengi seçenekleri (Beyaz/Sarı/Yeşil/Mavi): bunlar tema rengi değil, kullanıcının seçtiği ve oynatıcıda aynen kullanılan altyazı renkleridir; token'a çevirmek altyazı rengini değiştirirdi.
`grep "Color(0x" ui/ | grep -v "ui/design|theme/"` artık yalnızca yukarıdaki 8 satırı döndürüyor.

**B — Sessiz hatalar (commit 2):** `Log.w` (yalnızca hata türü: `e.javaClass.simpleName`; URL/kullanıcı adı/şifre/adres YOK; JVM testlerinde `android.util.Log` mock'suz olduğundan try/catch'li küçük yardımcıyla) eklendi: `AppDatabase.close()`, `BoundedDns` son kurtarma sorgusu, `StreamingHttpClient.evictAll`, `PlaylistRepository.parsePlaylists`, `UserPreferences` (izleme ilerlemesi okuma 4 yer, cihaz modu, son aramalar 3 yer). Akış/dönen değerler değişmedi.
Sessiz bırakılıp `// sessiz: <neden>` yorumu eklenenler (sıcak yol ya da günlük sarmalayıcı): `XtreamRepository`'de kayıt başına çalışan `jsonReader.skipValue()` catch'leri, `endArray()`/`response.close()` kapanış temizlikleri (toplam 24 satır) ve günlük yazma sarmalayıcıları; `BoundedDns/StreamingHttpClient/XtreamRepository` içindeki log fallback'leri. `XtreamRepository.clearSyncTempDir` catch'ine log eklendi. `SecurityUtils`'e dokunulmadı.
Not (kapsam dışı, dokunulmadı): `XtreamRepository` satır ~577 ve ~769'daki mevcut `Log.w(..., e.message)` çağrıları hata metnini yazıyor; IOException mesajı sunucu adresi içerebilir. Ayrı küçük görev olabilir.

**C — Ana sayfa (commit 3):** `OpusDashboardScreen`: rozet yalnızca `activeCons` ve `maxConnections` ikisi de dolu iken "N / M hat" (küçük harf) gösterilir; uydurma "1" kalktı. Abonelik satırı `AccountInfoFormatter.accountStatus(userInfo)` sonucundan: GREEN → "Abonelik aktif" (Success); RED → "Süresi doldu" ise "Abonelik süresi doldu", "Askıya alındı" ise "Abonelik aktif değil" (ikisi `VegaColors.Danger`); GRAY/AMBER (bilinmiyor) → satır gizli. Formatter'a dokunulmadı.

**D — Açılışta +18 (commit 4):** `MainViewModel`: üç içerik toplayıcısının her birinde `combine/collectLatest`'ten ÖNCE bir kez `withTimeoutOrNull(2_000L) { xxxCategories.first { it.isNotEmpty() } }` (kategori/arama değişiminde tekrar beklenmez). Not: `allLiveStreams` (raf düzeni, CH+/CH−) bu beklemeyi kullanmaz; o hâlâ kategoriler gelene kadar boş engel kümesiyle çalışabilir (görev yalnız üç toplayıcıyı istiyordu).

**Test:** `:app:compileDebugKotlin` + `:app:testDebugUnitTest` → BUILD SUCCESSFUL (her bölüm sonrası). Cihazda görsel doğrulama yok (rozet/abonelik satırı, 2 sn bekleme).
