# G-030 — Üst çubukta sıkışma, sunucu sırası, "Kaldığın yerden" boş başlık, tekrar eden filmler

- **Öncelik:** 🟠
- **Agent dağılımı:** Tek agent (bölme). Gemini 3.6 Flash için adım adım — **adımların dışına çıkma, kendi fikrini ekleme**.
- **Branch:** `fix/ust-cubuk-ve-sira` (main'den aç)
- **Commit mesajı:** `fix(ui): üst çubuk sıkışması, sunucu sırası varsayılan, devam et şeridi ve tekrar eden filmler`
- **Değişecek dosyalar (sadece bunlar):**
  1. `app/src/main/java/com/tivimax/player/ui/main/HomeScreen.kt` (sadece `HomeTopBar`)
  2. `app/src/main/java/com/tivimax/player/data/local/dao/Daos.kt` (sadece `ORDER BY` satırları)
  3. `app/src/main/java/com/tivimax/player/ui/MainViewModel.kt`
  4. `app/src/main/java/com/tivimax/player/data/preferences/UserPreferences.kt` (2 satır)
  5. `app/src/main/java/com/tivimax/player/ui/components/CategoryManagementDialog.kt` (1 satır)
  6. `app/src/main/java/com/tivimax/player/ui/main/OpusDashboardScreen.kt`
  7. Bu görev dosyası (Agent raporu) ve `YAPILACAKLAR.md` (durum satırı)

## Kullanıcının gördüğü sorunlar (ekran görüntüsü — telefon)
1. Liste adı uzun olunca ("Montana") üst çubuktaki **arama düğmesi ince bir çizgiye dönüşüyor**, kısa adda bile sıkışık.
2. Kategoriler alfabetik, filmler/diziler "eklenme tarihine" göre geliyor. Kullanıcı **sunucunun gönderdiği sırayı** istiyor
   (kategori, kanal ve içerik sırası; varsayılan bu olsun).
3. Liste değiştirince ana ekranda **"Kaldığın Yerden Devam Et" başlığı görünüyor ama altı boş**.
4. "Son Eklenen Filmler"de aynı film iki kez görünüyor (sağlayıcı aynı filmi iki kategoride veriyor).

---

## Bölüm A — Üst çubuk (HomeScreen.kt → `private fun HomeTopBar`)

**A1.** "Aktif Liste Rozeti" `Surface`'ını (yorum: `// Aktif Liste Rozeti / Hapı`) bir `Box` içine al ki kalan boşluğu alsın
ve uzun adda **küçülsün**:
```kotlin
            // Aktif Liste Rozeti / Hapı ("Ana liste ▾")
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                Surface(
                    ... (mevcut Surface parametreleri AYNEN; modifier'a .widthIn(max = 180.dp) EKLE — en başa)
                ) {
                    Row( ... mevcut ... ) {
                        Text(
                            text = activePlaylistName,
                            ... mevcut parametreler ...,
                            modifier = Modifier.weight(1f, fill = false)   // ← widthIn(max = 100.dp) YERİNE bu
                        )
                        Icon( ... ArrowDropDown — aynen ... )
                    }
                }
            }
```

**A2.** "Orta: Arama Butonu/Çubuğu" `Surface` bloğunun **tamamını sil** (içindeki "İçerik ara..." yazısı dahil).

**A3.** "Sağ: ..." `Row`'unun **içinde, en başa** (`if (isSyncing)` bloğundan ÖNCE) arama düğmesini ekle — Yenile düğmesinin
birebir kopyası, sadece simge/eylem farklı:
```kotlin
                // Arama Butonu
                Surface(
                    shape = VegaShapes.Medium,
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, VegaColors.Border),
                    onClick = onSearchClick,
                    modifier = Modifier
                        .size(40.dp)
                        .dpadFocusable(
                            shape = VegaShapes.Medium,
                            focusedBorderColor = VegaColors.Accent,
                            onClick = onSearchClick
                        )
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Ara",
                            tint = VegaColors.Accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
```
**A4.** "Sağ" `Row`'unun önüne `Spacer(modifier = Modifier.width(8.dp))` koy (rozet ile düğmeler arasında boşluk).

Sonuç: `[logo][Vega IPTV][Liste adı ▾ … boşluk …][Ara][Yenile][Ayarlar]`. Uzun liste adı `Montan…` diye kısalır,
düğmeler hep 40×40 kalır. **TV yerleşimine (TvNavigationRail) dokunma.**

---

## Bölüm B — Sunucu sırası varsayılan

**Neden `rowid`:** Liste her indirildiğinde kayıtlar sunucunun gönderdiği sırayla tabloya yazılıyor (REPLACE), bu yüzden
SQLite'ın gizli `rowid` sütunu **sunucu sırasını** tutuyor. Yeni sütun / veritabanı sürümü değişikliği **YOK**
(veritabanı sürümünü değiştirme — değiştirirsen tüm kullanıcıların listesi silinir!).

**B1. `Daos.kt` — sadece şu metin değişiklikleri (dosyanın tamamında, birebir):**

| Bul (aynen) | Değiştir |
|---|---|
| `ORDER BY categoryName ASC` | `ORDER BY rowid ASC` |
| `ORDER BY num ASC, name ASC` | `ORDER BY rowid ASC` |
| `ORDER BY added DESC, name ASC` | `ORDER BY rowid ASC` |
| `ORDER BY lastModified DESC, name ASC` | `ORDER BY rowid ASC` |

Ve 3 adet `getStreamsFiltered` sorgusunun **en son** sıralama satırı:
- canlı: `            num ASC, name ASC` → `            rowid ASC`
- film: `            added DESC, name ASC` → `            rowid ASC`
- dizi: `            lastModified DESC, name ASC` → `            rowid ASC`

`CASE WHEN :sort = ...` satırlarına **dokunma** (kullanıcı A-Z, puan, tarih seçerse onlar çalışmaya devam eder).
Arama sorgularındaki `ORDER BY name ASC` satırlarına **dokunma**. `version = 2` **değişmeyecek**.

**B2. `UserPreferences.kt`** — varsayılanlar:
```kotlin
// ÖNCE
val vodSortFlow: Flow<String> = context.appDataStore.data.map { it[KEY_VOD_SORT] ?: "ADDED_DESC" }
val seriesSortFlow: Flow<String> = context.appDataStore.data.map { it[KEY_SERIES_SORT] ?: "ADDED_DESC" }
// SONRA
val vodSortFlow: Flow<String> = context.appDataStore.data.map { it[KEY_VOD_SORT] ?: "DEFAULT" }
val seriesSortFlow: Flow<String> = context.appDataStore.data.map { it[KEY_SERIES_SORT] ?: "DEFAULT" }
```

**B3. `MainViewModel.kt`** — `vodSortOption` ve `seriesSortOption` tanımlarında `SortOption.ADDED_DESC` geçen **4 yeri**
`SortOption.DEFAULT` yap (catch içindeki ve `stateIn` son parametresindeki). Üstteki yorumu
`// Sıralama Tercihleri (varsayılan: sunucu sırası)` yap.

**B4. `MainViewModel.kt`** — `visibleLiveCategories`, `visibleVodCategories`, `visibleSeriesCategories` içindeki
`sortedWith(...)` bloklarında **sadece** `.thenBy { it.categoryName }` satırını sil (3 yer). "Tüm…" ilk sırada ve
sabitlenen kategoriler üstte kalır; geri kalanı sunucu sırasında kalır (`sortedWith` sırayı bozmaz).

**B5. `CategoryManagementDialog.kt`** — `filteredCategories` içindeki `.thenBy { it.second }` satırını sil.

---

## Bölüm C — "Kaldığın Yerden Devam Et" boş başlık

**Neden:** Ana ekran, izleme kaydındaki filmi **o an ekrandaki film listesinde** (`vodStreams` — seçili kategoriye göre
filtreli, liste değişince farklı) arıyor; bulamazsa kart çizmiyor ama başlık kalıyor.

**C1. `MainViewModel.kt`** — `val watchProgressMap = ...` satırının **hemen ALTINA** ekle (sıra önemli — üstüne koyma):
```kotlin
    /** G-030: Ana ekran "Kaldığın Yerden Devam Et" — sadece bu listede gerçekten bulunan filmler. */
    val continueWatchingMovies: StateFlow<List<Pair<WatchProgress, VodStream>>> =
        watchProgressMap
            .map { map ->
                map.values
                    .filter { it.positionMs > 10_000L && !it.isWatched }
                    .sortedByDescending { it.timestamp }
                    .take(30)
            }
            .flatMapLatest { progressList ->
                if (progressList.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    repository.getVodStreamsByIdsFlow(progressList.map { it.streamId })
                        .map { movies ->
                            val byId = movies.associateBy { it.streamId }
                            progressList.mapNotNull { p -> byId[p.streamId]?.let { p to it } }.take(10)
                        }
                }
            }
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
```
(`kotlinx.coroutines.flow.*` zaten import edilmiş. `WatchProgress` import'u yoksa
`import com.tivimax.player.data.preferences.WatchProgress` ekle.)

**C2. `OpusDashboardScreen.kt`:**
1. `val activeVodProgressList = remember(watchProgressMap) { ... }` bloğunu **sil**; yerine:
   ```kotlin
   val continueWatching by viewModel.continueWatchingMovies.collectAsState()
   ```
2. `if (activeVodProgressList.isNotEmpty()) {` → `if (continueWatching.isNotEmpty()) {`
3. Başlık metni `"Kaldığın Yerden Devam Et (Film ve Dizi)"` → `"Kaldığın Yerden Devam Et"`
4. `LazyRow` içindeki `items(...)` bloğunu şununla değiştir:
   ```kotlin
                items(continueWatching, key = { "progress_${it.first.streamId}" }) { (progress, movie) ->
                    DashboardProgressCard(
                        movie = movie,
                        progress = progress,
                        onClick = { viewModel.playVod(movie, resume = true) }
                    )
                }
   ```
5. `watchProgressMap` artık bu dosyada kullanılmıyorsa onun `collectAsState` satırını da sil (kullanılıyorsa bırak).

---

## Bölüm D — Son eklenenlerde tekrar eden içerik (OpusDashboardScreen.kt)

`newlyAddedVodList` ve `newlyAddedSeriesList` içinde `.take(12)` satırının **hemen ÜSTÜNE** ekle:
```kotlin
            .distinctBy { it.name.trim().lowercase() }
```
(`name` boş olabilecek bir tipse `(it.name ?: "")` kullan — derleyici ne derse.) `.ifEmpty { ... }` satırlarına dokunma.

---

## Kontrol ve gönderim
1. `./gradlew compileDebugKotlin` → hata varsa düzelt (isim uydurmadan).
2. `./gradlew testDebugUnitTest` → tüm testler geçmeli.
3. `git diff --stat`: **sadece** yukarıdaki dosyalar. Başka dosya değiştiyse geri al.
4. Commit → `fix/ust-cubuk-ve-sira` dalına push. **Main'e birleştirme. APK üretme. Sürüm numarasını değiştirme.**
5. `YAPILACAKLAR.md`'de G-030 durumunu `🔍 İncelemede (branch: fix/ust-cubuk-ve-sira)` yap.

## DOKUNMA
`AppDatabase.kt` (sürüm, migration), `Entities.kt`, `XtreamRepository.kt`, senkron, oynatıcı, TV menüsü,
oynatma listesi mantığı, güncelleme sistemi (AutoUpdateManager), imza, CI, sürüm numarası.

## Test (cihaz varsa — yoksa "test edilemedi" yaz)
- Uzun liste adıyla telefonda üst çubuk: ad kısalıyor, Ara/Yenile/Ayarlar 40×40 tam görünüyor.
- Film/dizi/kanal kategorileri ve içerikleri sağlayıcının panelindeki sırayla geliyor; "Sırala" menüsünde A-Z vb. hâlâ çalışıyor.
- Liste değiştirince "Kaldığın Yerden Devam Et" ya dolu ya hiç görünmüyor (boş başlık yok).
- Son eklenen filmlerde aynı ad iki kez yok.

---

## Agent raporu

- **Durum:** Tamamlandı, Claude'un incelemesine hazır.
- **Değişiklikler:**
  - **Bölüm A (HomeScreen.kt):** `HomeTopBar` içerisindeki aktif liste rozeti `.weight(1f)` ve `.widthIn(max = 180.dp)` alan bir `Box` içine alındı, liste adı taşmasında `weight(1f, fill = false)` kullanılarak kısalması sağlandı. Ortadaki geniş arama çubuğu kaldırıldı, sağ kısma 40x40 boyutlarında özel Arama `Surface` butonu eklendi. Üst çubuk elemanlarının sıkışması tamamen giderildi.
  - **Bölüm B (Sunucu Sırası Varsayılan):**
    - `Daos.kt`: `LiveDao`, `VodDao`, `SeriesDao` içerisindeki kategori ve içerik varsayılan sıralamaları `ORDER BY rowid ASC` olarak değiştirildi.
    - `UserPreferences.kt`: `vodSortFlow` ve `seriesSortFlow` varsayılan değerleri `"DEFAULT"` yapıldı.
    - `MainViewModel.kt`: `vodSortOption` ve `seriesSortOption` varsayılan değerleri `SortOption.DEFAULT` olarak güncellendi. `visibleLiveCategories`, `visibleVodCategories`, `visibleSeriesCategories` içindeki `.thenBy { it.categoryName }` satırları kaldırılarak sunucu sırası korundu.
    - `CategoryManagementDialog.kt`: `filteredCategories` içindeki `.thenBy { it.second }` kaldırıldı.
  - **Bölüm C ("Kaldığın Yerden Devam Et" Boş Başlık Düzeltmesi):**
    - `MainViewModel.kt`: `continueWatchingMovies` Flow'u eklendi; izleme geçmişindeki `streamId`'ler veritabanından doğrudan sorgulanarak sadece aktif veritabanında gerçekten mevcut olan VOD içerikleri ve izleme süreleri filtrelendi.
    - `OpusDashboardScreen.kt`: Eski `activeVodProgressList` kaldırılıp `viewModel.continueWatchingMovies` bağlandı. Başlık metni `"Kaldığın Yerden Devam Et"` olarak sadeleştirildi; içerik yoksa başlık dahil bölüm çizilmiyor.
  - **Bölüm D (Son Eklenenlerde Tekrar Eden İçerikler):**
    - `OpusDashboardScreen.kt`: `newlyAddedVodList` ve `newlyAddedSeriesList` listelerine `.distinctBy { (it.name ?: "").trim().lowercase() }` filtresi eklenerek aynı isimli içeriklerin tekrarlanması engellendi.
- **Doğrulama:**
  - `./gradlew compileDebugKotlin` başarıyla tamamlandı.
  - `./gradlew testDebugUnitTest` başarıyla tamamlandı (60 test geçti, 0 hata).
  - Fiziksel cihaz / emülatör bağlı olmadığı için ekran görüntüleri ekran üzerinde test edilemedi (derleme ve test adımlarıyla doğrulandı).
