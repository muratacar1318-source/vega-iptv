# G-034 — Detaydan/oynatıcıdan dönünce aynı afişe ve aynı kaydırma yerine dön

- **Öncelik:** 🟠
- **Agent dağılımı:** Tek agent. **Ön koşul:** G-054 (MainViewModel bölme) main'e alınmış olmalı.
- **Branch:** `fix/geri-donus-ayni-afis` (main'den aç)
- **Commit mesajı:** `fix(gezinme): film/dizi detayından dönünce aynı afiş ve kaydırma konumu korunuyor`
- **Değişecek dosyalar (yollar `app/src/main/java/com/tivimax/player/` altına göre):**
  1. `ui/vm/CatalogPositionStore.kt` (yeni) + `ui/MainViewModel.kt` (yalnızca bu sınıfın örneği ve yönlendirmeleri)
  2. `ui/vod/VodScreen.kt` — film ızgarası
  3. `ui/catalog/VegaMediaCatalogScreen.kt` — dizi ızgarası
  4. `ui/main/OpusDashboardScreen.kt` — ana sayfa yatay şeritleri
  5. `ui/favorites/FavoritesScreen.kt` — ızgara/şerit varsa
  6. `app/src/test/java/com/tivimax/player/ui/CatalogPositionStoreTest.kt` (yeni)
  7. Rapor ve `YAPILACAKLAR.md`'de G-034 satırı

## Sorun
Film/dizi detayı ya da oynatıcı açılınca `Screen.Home` ekrandan tamamen kalkıyor (Navigation.kt). Geri dönünce
ana ekran sıfırdan çiziliyor:
- Izgaralar (`VodScreen` ~544. satır, `VegaMediaCatalogScreen` ~329. satır) kendi `LazyGridState`'lerini içeride
  oluşturuyor → kaydırma en başa dönüyor.
- Sonsuz kaydırma sınırı (`visibleLimit`, 40'tan başlıyor) sıfırlanıyor → 200. filmden gelen kullanıcı listenin
  o kısmına hiç ulaşamıyor.
- TV'de odak ilk öğeye ya da menüye düşüyor; kumandayla tekrar 200 film aşağı inmek gerekiyor.
- Ana sayfa şeritlerinde de aynı şey (her şerit başa dönüyor).
Telefonda da kaydırma kaybı var; TV'de en çok rahatsız eden odak kaybı.

## Çözüm
1. **`CatalogPositionStore`** (ViewModel'e bağlı, bellekte; DataStore'a yazılmaz):
   - `fun save(key: String, firstIndex: Int, offset: Int, visibleLimit: Int)` / `fun get(key): Saved?`
   - `var lastOpenedItemKey: String?` — detayı açılan öğenin ızgaradaki `key`'i (ör. `"${streamId}_${num}"`).
   - Anahtar biçimi: `"vod:<kategoriId>"`, `"series:<kategoriId>"`, `"home:<şerit adı>"`, `"fav:<sekme>"`.
   - Liste değiştiğinde (aktif oynatma listesi değişimi, `resetSessionState`) `clear()`.
2. **Izgaralar:**
   - `val saved = store.get(key)`; `rememberLazyGridState(saved?.firstIndex ?: 0, saved?.offset ?: 0)` ile `state` ver.
     Kategori değişince key değişir; `remember(key)` ile yeni durum.
   - `visibleLimit` başlangıcı `max(40, saved?.visibleLimit ?: 0)`.
   - `DisposableEffect(key) { onDispose { store.save(key, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset, visibleLimit) } }`
   - Karta tıklanınca (detay açılmadan hemen önce) `store.lastOpenedItemKey = itemKey`.
3. **TV odağı:** `LocalIsTvDevice.current` ise, `lastOpenedItemKey`'e eşit olan kartın modifier'ına bir
   `FocusRequester` bağla; ilk çizimden sonra (`LaunchedEffect(Unit)` içinde `withFrameNanos {}` ile bir kare bekle)
   `requestFocus()` çağır, sonra `lastOpenedItemKey = null`. Öğe görünür değilse önce `state.scrollToItem(index)`.
   `requestFocus` istisnasını yakala (öğe henüz yerleşmemişse) ve bir kare sonra bir kez daha dene; yine olmazsa bırak.
   G-033'teki `focusRestorer()` ve G-042/G-045 kategori odak kodlarına **dokunma**; bu, onların çalışmadığı
   "ekran yeniden kuruldu" durumunu tamamlar.
4. **Ana sayfa şeritleri:** her `LazyRow` için `rememberLazyListState` + aynı kaydet/geri yükle; TV'de açılan afişe odak.
   Dikey kaydırma (sayfanın kendisi) için de `"home:sayfa"` anahtarıyla aynısı.
5. Oynatıcıdan dönüşte (film doğrudan oynatıldıysa) de aynı mekanizma çalışır; ek iş gerekmez.

## Test (`CatalogPositionStoreTest.kt`)
- kaydet/oku, farklı anahtarlar karışmaz, `clear()` sonrası boş, `visibleLimit` korunur.

## 🚫 DOKUNMA
- `Navigation.kt` ve `Screen` yapısı (ekranları yığına çevirmek bu görevin konusu değil).
- TV menüsü (`TvNavigationRail`), `HomeScreen.kt`'deki G-033/G-042 odak kodu, oynatıcı dosyaları.
- `AutoUpdateManager`, imza ayarları, `.github/workflows/`. Sürüm numarası. Release yapma.

## Kontrol
- `./gradlew :app:testDebugUnitTest` (düşük öncelikle, pencere açmadan) → geçmeli.
- Elle deneme gerekli (TV: Filmler → 3. kategori → 60. filme in → detay → geri → aynı afiş odakta; telefonda aynı kaydırma).
  Cihaz yoksa raporda yaz; Claude birleştirmeden önce kullanıcıdan deneme ister.

## Agent raporu
_(Agent doldurur.)_
