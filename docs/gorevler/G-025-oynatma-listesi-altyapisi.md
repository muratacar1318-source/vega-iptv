# G-025 — Oynatma listesi altyapısı (birden fazla liste, her liste kendi verisiyle) — TEMEL

- **Öncelik:** 🟠
- **Agent dağılımı:** Tek agent (temel katman; G-026 arayüzü bunun üstüne kurulur — sırayla yapılır, paralel YAPILMAZ)
- **Branch:** `feat/oynatma-listesi-altyapi`
- **Commit mesajı:** `feat(playlist): çoklu oynatma listesi altyapısı, liste başına ayrı veritabanı ve kayıtlar`
- **Ön koşul:** G-024 main'de olmalı (sunucu/senkron düzeltmeleri). Değilse YAPMA, raporla.

## Amaç

Kullanıcı birden fazla oynatma listesi (sunucu + kullanıcı adı + şifre) ekleyebilsin ve aralarında kolayca geçebilsin.
Her listenin kanalları, filmleri, dizileri, favorileri, izleme geçmişi ve aramaları **birbirine karışmasın**
(bugünkü "başka sunucuya geçince eski arama sonuçları geliyor" sorunu bir daha yaşanmasın).

**EN ÖNEMLİ KURAL — mevcut kullanıcı hiçbir şey kaybetmeyecek:** Güncellemeyi alan kullanıcının bugünkü hesabı,
indirilmiş listesi, favorileri, izleme geçmişi aynen "ilk liste" olarak devam eder; yeniden giriş/indirme gerekmez.

## Tasarım

### 1) Model ve depolama — `data/playlist/`
- `data class Playlist(id: String, name: String, serverUrl: String, username: String, password: String,
  dbName: String, createdAt: Long, isSynced: Boolean = false, lastSyncAt: String = "")`
- `PlaylistRepository` (DataStore'da JSON liste + `activePlaylistId`):
  `playlistsFlow`, `activePlaylistFlow`, `add`, `update`, `remove`, `setActive`, `markSynced(id, timestamp)`.
- Şifre bugünkü kimlik bilgileriyle aynı yöntemle saklanır (ek şifreleme bu görevin kapsamı değil).

### 2) Liste başına ayrı veritabanı
- `AppDatabase.getInstance(context)` → `AppDatabase.open(context, dbName)` + `AppDatabase.close()`;
  aynı anda tek veritabanı açık. (Eski `getInstance` imzası kalsın ve **aktif** veritabanını döndürsün.)
- `XtreamRepository.database` `val` → değiştirilebilir; `switchDatabase(dbName)`; geçişte bellek önbellekleri temizlenir.
- **İlk liste mevcut dosyayı kullanır:** `dbName = "vega_iptv.db"`. Yeni listeler: `"vega_pl_<id>.db"`.

### 3) Liste başına kayıtlar (UserPreferences)
Aşağıdaki anahtarlar listeye özel olsun: favoriler (canlı/film/dizi), izleme ilerlemesi, sabitlenen kategoriler,
son kanallar, son aramalar, son oynatılan kanal, son senkron zamanı, `isPlaylistSynced`, hesap bilgileri
(exp_date, max/active connections, status, is_trial, created_at).
- Yöntem: `fun keyFor(base: String, playlistId: String)` → ilk liste için (`id == "default"`) **mevcut anahtar adının
  aynısı** (veri taşımaya gerek yok), diğerleri için `"${base}__${playlistId}"`.
- Bu anahtarları okuyan `Flow`'lar aktif listeye göre çalışsın:
  `activePlaylistIdFlow.flatMapLatest { id -> data.map { it[keyFor(...)] } }`.
- Uygulama geneli ayarlar (tema, cihaz türü, oynatıcı ayarları, altyazı boyutu/rengi/gecikmesi, ebeveyn PIN'i) ORTAK kalır.

### 4) Tek seferlik geçiş (migration)
Açılışta: liste kaydı yoksa ve kullanıcı giriş yapmışsa (`isLoggedInFlow`) →
`Playlist(id = "default", name = "Ana liste", serverUrl/username/password = mevcut kayıtlar, dbName = "vega_iptv.db",
isSynced = isPlaylistSyncedFlow, lastSyncAt = lastSyncTimestamp)` oluştur ve aktif yap. Hiçbir dosya/anahtar taşınmaz.
Giriş yapılmamışsa liste boş kalır (giriş ekranı ilk listeyi oluşturur — madde 6).

### 5) Geçiş: `MainViewModel.switchPlaylist(id)`
Sırasıyla: oynatıcıyı kapat (`playerManager.release()`, mini oynatıcı/çift ekran kapanır), devam eden senkron/arama/EPG
işlerini iptal et, **bellekteki tüm liste verisini sıfırla** (`resetSessionState()`: canlı/film/dizi listeleri ve
kategorileri, evrensel arama sorgu+sonuçları, EPG önbellekleri, önizleme kanalı, geri yığını, seçili sekmeler,
hesap ekranı sayıları, `userInfo`), aktif listeyi değiştir, `repository.updateCredentials(...)` + `switchDatabase(...)`,
sonra: liste senkronluysa `loadInitialData()` + `Screen.Home`; değilse `Screen.PlaylistSync` + `startPlaylistSync(1)`.
Ardından arka planda `refreshAccountInfo(silent = true)`.

### 6) Ekleme / silme / çıkış
- `addPlaylist(name, serverUrl, username, password, onResult)`: önce `repository.authenticate(...)` ile doğrula;
  başarılıysa kaydet ve `switchPlaylist(yeniId)`. Hata metinleri sade Türkçe (G-024'teki gibi), teknik metin yok.
- `login(...)` (giriş ekranı): artık `addPlaylist(name = "Ana liste" ya da kullanıcı adı, ...)` yolunu kullanır.
- `removePlaylist(id)`: o listenin veritabanı dosyasını (`context.deleteDatabase(dbName)`) ve listeye özel anahtarlarını sil.
  Aktif liste silinirse başka liste varsa ona geç, yoksa giriş ekranına dön.
- Mevcut `logout()` = **aktif listeyi kaldır** (`removePlaylist(activeId)`) — Hesabım'daki onay metni G-026'da güncellenecek.
- Senkron bitince `markSynced(activeId, zaman)`.

### 7) Güvenlik ağı
- Geçiş/ekleme sırasında hata olursa önceki aktif liste ve veritabanı geri yüklensin (kullanıcı boş ekranda kalmasın).
- `app/src/test` altına birim test: `keyFor` (default → aynı anahtar, diğer → ekli), migration (giriş yapılmış/yapılmamış),
  `removePlaylist` sonrası aktif liste seçimi.

## DOKUNMA
Oynatıcı içi, medya oturumu, senkron adımlarının iç mantığı (G-024), tasarım bileşenleri, `AutoUpdateManager`, imza, CI,
sürüm numarası. Bu görevde **arayüz eklenmez** (G-026) — sadece giriş ekranının kullandığı fonksiyon değişir.

## Test
- Mevcut kurulumdan güncelleme: uygulama açılıyor, aynı kanallar/favoriler/izleme geçmişi duruyor, yeniden indirme yok.
- (Geçici test için) kodla ikinci liste ekleyip geçince: arama ve listeler sadece yeni listenin içeriğini gösteriyor;
  geri geçince eski liste ve favorileri aynen duruyor.
- Birim testler geçiyor. Yerelde `./gradlew compileDebugKotlin` ve `testDebugUnitTest`; APK üretme.

---

## Agent raporu

- **Durum:** Tamamlandı
- **Değişiklikler:**
  - `app/src/main/java/com/tivimax/player/data/playlist/Playlist.kt`: `Playlist` veri modeli eklendi.
  - `app/src/main/java/com/tivimax/player/data/playlist/PlaylistRepository.kt`: DataStore tabanlı çoklu oynatma listesi yönetimi (`playlistsFlow`, `activePlaylistFlow`, `add`, `update`, `remove`, `setActive`, `markSynced`, `migrateIfNeeded`, `computeMigration`, `selectNextActive`, `dbNameFor`) oluşturuldu.
  - `app/src/main/java/com/tivimax/player/data/local/AppDatabase.kt`: Liste başına veritabanı açıp kapatabilmek için `open(context, dbName)` ve `close()` eklendi; `getInstance(context)` geriye dönük uyumlulukla aktif veritabanını döndürür hale getirildi.
  - `app/src/main/java/com/tivimax/player/data/repository/XtreamRepository.kt`: `database` değişkeni dinamik hale getirildi, `switchDatabase(dbName)` ve `clearMemoryCaches()` eklendi.
  - `app/src/main/java/com/tivimax/player/data/preferences/UserPreferences.kt`: `keyFor(base, playlistId)` deseni entegre edildi. İlk liste (`default`) için anahtar isimleri birebir korundu (mevcut kullanıcılar için sıfır veri kaybı). Favoriler, izleme ilerlemesi, son kanallar, son aramalar, senkronizasyon durumu ve hesap bilgileri `activePlaylistIdFlow.flatMapLatest` üzerinden dinamik sağlandı; global ayarlar (tema, cihaz modu, altyazı vb.) korundu. Liste silindiğinde tercihleri temizleyen `removePlaylistPreferences` eklendi.
  - `app/src/main/java/com/tivimax/player/ui/MainViewModel.kt`: `checkUserLoginAndNavigate` içinde tek seferlik migration çağrısı eklendi. `switchPlaylist(id)`, `addPlaylist(...)`, `removePlaylist(id)` ve oturum sıfırlama (`resetSessionState()`) fonksiyonları eklendi. `login(...)` artık `addPlaylist` üzerinden çalışıyor.
  - `app/src/test/java/com/tivimax/player/data/playlist/PlaylistArchitectureTest.kt`: `keyFor`, migration, sonraki aktif liste seçimi ve veritabanı adı kuralları için birim testleri eklendi.
- **Doğrulama:**
  - `compileDebugKotlin` ve `testDebugUnitTest` başarıyla çalıştı ve tüm testler geçti.
  - APK üretilmedi (`assembleRelease` çalıştırılmadı).

---

## 🔁 Düzeltme turu 1 (Claude incelemesi — 18 Ekim)

**İyi olanlar:** `Playlist`/`PlaylistRepository`, `keyFor` (anahtar adları eskileriyle birebir aynı — kontrol edildi),
ilk listenin `vega_iptv.db` ve eski anahtarları kullanması, `switchPlaylist`/`addPlaylist`/`removePlaylist`, birim testleri.

**Ama 3 hata var — ikisi çökme / yanlış veri riski:**

### 1) 🔴 Veritabanı değişince eski veritabanına bağlı kalan akışlar (çökme + boş/eski liste)
`MainViewModel` init'teki (~795-840) `combine(selectedLiveCategory, liveSearchQuery, liveSortOption)…collectLatest {
repository.getLiveStreamsFilteredFlow(...).collect {...} }` (film ve dizi aynısı) ve `loadLiveCategories/loadVodCategories/
loadSeriesCategories` içindeki `repository.get*CategoriesFlow().collect` akışları, **toplandıkları andaki DAO'ya** bağlı.
`switchDatabase` eski veritabanını kapatınca:
- Kapatılmış veritabanına sorgu giderse `IllegalStateException` → `viewModelScope` içinde yakalanmadığı için **uygulama çöker**.
- Çökmese bile `resetSessionState()` kategori/sorgu değerlerini zaten aynı olan değerlere ("all", "") set ettiği için
  `combine` yeniden tetiklenmez → **yeni listenin kanalları gelmez**, ekran boş/eski kalır.
`loadXCategories()` her çağrıda yeni, hiç iptal edilmeyen bir toplayıcı açıyor (birikir).

**Çözüm:**
- `XtreamRepository`: `private val _activeDb = MutableStateFlow<AppDatabase?>(database)`; `val activeDbFlow: StateFlow<AppDatabase?>`.
  `switchDatabase(dbName)` sırası: **önce `_activeDb.value = null`** (toplayıcılar iptal olsun) → eski DB'yi kapat →
  yenisini aç → `database = yeni; _activeDb.value = yeni`.
- `get*FilteredFlow` ve `get*CategoriesFlow`: `activeDbFlow.flatMapLatest { db -> db?.xDao()?.... ?: flowOf(emptyList()) }`
  şeklinde aktif veritabanını izlesin. (Diğer tek seferlik `suspend` sorgular `database` alanını kullanmaya devam edebilir.)
- `loadLive/Vod/SeriesCategories()`: toplayıcıyı bir `Job` alanında tut, yeniden çağrılınca önceki işi iptal et.
- Bu akışların `collect`'lerini `.catch { Log.w(...) }` ile koru (kapatılmış DB hatası çökertmesin).
- `getPaged*` (PagingSource) kullanılıyorsa aynı şekilde aktif DB'den alınsın; kullanılmıyorsa raporla.

### 2) 🔴 Hesap yenileme yanlış kimlikle çalışıyor
`refreshAccountInfo()` (~612) `preferences.usernameFlow/passwordFlow/serverUrlFlow`'u (global, **son eklenen listenin**
bilgileri) kullanıyor. Başka listeye geçince yanlış hesapla doğrulayıp sonucu aktif listenin anahtarlarına yazar.
**Çözüm:** `playlistRepository.getActivePlaylist()`'in `serverUrl/username/password`'ünü kullan; aktif liste yoksa çık.
`saveAuth(..., playlistId = active.id)`.

### 3) 🟠 G-024'teki https → http geçişi kayboldu
`checkUserLoginAndNavigate()` yeniden yazılırken G-024'teki tek seferlik geçiş silinmiş; eski kurulumda https kayıtlıysa
"Ana liste" https ile oluşturuluyor. **Çözüm:** `migrateIfNeeded` sonrası, **her** listede `serverUrl` ==
`AppConfig.SERVER_URL_HTTPS` ise `SERVER_URL_HTTP` yap (`playlistRepository.update`) ve global `setServerUrl`'i de güncelle.
Bunu bir kez çalışan, saf bir yardımcıyla yap ve birim testine ekle.

**AYNI BRANCH:** `git checkout feat/oynatma-listesi-altyapi && git pull && git merge origin/main`
(`YAPILACAKLAR.md` çakışırsa main'deki hali). `compileDebugKotlin` + `testDebugUnitTest`, push, rapor, dur. APK üretme.

### Düzeltme turu 1 — Agent raporu

_(Agent doldurur.)_
>>>>>>> origin/main
