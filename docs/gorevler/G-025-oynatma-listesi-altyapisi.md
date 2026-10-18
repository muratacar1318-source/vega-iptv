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

_(Agent doldurur.)_
