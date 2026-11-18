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
Ön koşul G-054 main'de (e9638a1). Tek commit; `:app:compileDebugKotlin` + `:app:testDebugUnitTest` → BUILD SUCCESSFUL (`CatalogPositionStoreTest` 5/5). Navigation.kt, `Screen`, `TvNavigationRail`, `HomeScreen.kt` (G-033/G-042 odak kodu), oynatıcı dosyaları, `focusRestorer()` ve G-045 `CategorySidebar` odak koduna dokunulmadı.

**Depo:** `ui/vm/CatalogPositionStore.kt` (bellekte; `save/get/clear`, `lastOpenedItemKey`; `Saved(firstIndex, offset, visibleLimit)`). `MainViewModel`'e yalnızca örnek (`val catalogPositions`) eklendi ve `resetSessionState()` içinde `catalogPositions.clear()` çağrılıyor (liste değişimi/oturum sıfırlama). Ayrı cephe fonksiyonu gerekmedi: ekranlar `viewModel.catalogPositions`'ı doğrudan kullanıyor.

**Izgaralar (film / dizi):**
- `VodScreen`: anahtar `"vod:<kategoriId>"` (aramada `"vod:<kategoriId>:q:<metin>"`); `LazyGridState`, `rememberSaveable(positionKey, saver = LazyGridState.Saver)` ile kayıtlı `firstIndex/offset`'ten kurulur (dönüşte mağazadan, döndürmede kendi saver'ından); `DisposableEffect(positionKey)` çıkışta `save(...)`; `visibleLimit` başlangıcı `max(40, saved.visibleLimit)` (`remember(filteredMovies)` anahtarı aynı kaldı) ve grid içinde `rememberUpdatedState` ile güncel sınır kaydediliyor. `MoviePosterCard`'a `modifier: Modifier = Modifier` eklendi (focusRequester `dpadFocusable`'ın önünde).
- `VegaMediaCatalogScreen` (dizi): anahtar `"series:<kategoriId>"`; aynı mekanizma. `visibleLimit` ve sayfalı liste, `else` dalından fonksiyon başına taşındı (hoist; böylece yükleniyor↔içerik geçişinde de korunur ve çıkışta kaydedilebilir). `VegaMediaContentGrid` imzasına `selectedCategory` ve `positionStore` eklendi (iki çağrı yeri güncellendi); `VegaMediaCard`'a `modifier` eklendi.
- Karta tıklanınca (detay açılmadan hemen önce) `positionStore.lastOpenedItemKey = itemKey`. Hero "Hemen izle" düğmesi için anahtar yazılmadı (tarifte yalnız kartlar).

**TV odağı:** `LocalIsTvDevice` ise ekran girişinde `lastOpenedItemKey` bir kez okunur; eşleşen kartın modifier'ına `focusRequester` bağlanır; `LaunchedEffect` bir kare bekler, gerekirse `scrollToItem(index)`, bir kare daha bekler, `requestFocus()`; istisna olursa bir kare sonra bir kez daha dener, olmazsa bırakır; sonra `lastOpenedItemKey = null`. `dpadFocusable` zaten `onFocusStateChange` ile hero/spotlight'ı güncellediği için odak geri gelince vitrin de doğru afişe döner.

**Ana sayfa (`OpusDashboardScreen`):** sayfa dikey kaydırması `rememberScrollState(saved)` + çıkışta `"home:sayfa"` (değer olarak `scrollState.value`); dört `LazyRow` (`"home:devam"`, `"home:filmler"`, `"home:diziler"`, `"home:kanallar"`) küçük bir `rememberStoredLazyListState` yardımcısıyla; kartlara (`DashboardProgressCard`, `NewlyAddedVodCard`, `NewlyAddedSeriesCard`, `DashboardRecentChannelCard`) `modifier` parametresi eklendi, TV'de açılan kartın odağı geri verilir (anahtarlar `home:progress_/vod_/series_/recent_<id>`; ızgara anahtarlarıyla çakışmaz).

**Favoriler (`FavoritesScreen`):** `"fav:<sekme>"` anahtarıyla ızgara konumu; seçili sekme de `"fav:sekme"` olarak saklanıp geri yükleniyor (önceden her dönüşte "Tümü"ye düşüyordu). `MediaPosterCard` `ui/components`'ta olduğu için (görev listesinde yok) değiştirilmedi; TV odağı için yalnız eşleşen kartı `Box(Modifier.focusRequester(...))` ile saran küçük `FocusRestoreSlot` eklendi (focusRequester, içindeki ilk odaklanabilir öğeye aktarır; bu yol cihazda doğrulanmadı).

**Bilinen sınırlar / dikkat:**
- Konumlar yalnızca bellekte (uygulama/ViewModel ömrü); liste değişince `clear()`.
- Arama metni değiştikçe film ızgarası yeni anahtar kullanır (aramadaki konum ayrı).
- Favorilerde ızgara yüksekliği değişirse (favori kaldırılırsa) kayıtlı indeks listeden büyük kalabilir; `LazyGridState` bunu kendisi sınırlar.
- Ana sayfa şeritlerinde ilk açılışta kayıt yoksa davranış eskisi gibi (baştan).
- **Elle denenmedi** (cihaz yok): TV'de Filmler → 3. kategori → 60. filme in → detay → geri → aynı afiş odakta mı; telefonda aynı kaydırma; 200. filmden dönüş; ana sayfa şeritleri; favoriler. Birleştirmeden önce kullanıcıdan istenmeli. En riskli yer: `focusRequester`'ın `dpadFocusable` zinciriyle (özellikle Favoriler'deki sarmalayıcı Box ile) cihazda gerçekten odak alması.
