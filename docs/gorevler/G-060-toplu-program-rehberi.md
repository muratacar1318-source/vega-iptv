# G-060 — Toplu program rehberi (XMLTV) arka planda indirilsin, rehber 2 gün ileriyi göstersin

- **Öncelik:** 🟢 (büyük özellik)
- **Agent dağılımı:** Tek agent, 4 bölüm (aynı branch, bölüm başına commit). **Ön koşul:** G-054 main'e alınmış olmalı.
- **Branch:** `feat/toplu-program-rehberi` (main'den aç)
- **Commit mesajları:** `feat(rehber): <bölüm>` (ör. `feat(rehber): veritabanı tablosu ve geçiş`)
- **Değişecek dosyalar (yollar `app/src/main/java/com/tivimax/player/` altına göre):**
  - `data/local/entity/Entities.kt`, `data/local/dao/Daos.kt`, `data/local/AppDatabase.kt`
  - `data/epg/XmltvParser.kt`, `data/epg/EpgRepository.kt` (yeni)
  - `data/repository/XtreamRepository.kt` — yalnızca canlı akış ayrıştırmasına `tv_archive_duration` ve xmltv adresi
  - `data/model/XtreamModels.kt` — yalnızca `LiveStream`'e `tvArchiveDuration`
  - `ui/vm/EpgCache.kt` (G-054'te oluştu) ve `ui/MainViewModel.kt` (yalnızca arka plan tetikleme)
  - `ui/tv/TvEpgGridScreen.kt` — veri kaynağı ve süre
  - `ui/settings/SettingsScreen.kt` — tek anahtar satırı
  - `app/build.gradle.kts` — yalnızca `testImplementation` satırı ve KSP şema yolu (imza ayarlarına DOKUNMA)
  - yeni testler `app/src/test/java/com/tivimax/player/data/epg/*`
  - `app/schemas/` (Room şema JSON'ları), rapor ve `YAPILACAKLAR.md`'de G-060 satırı

## Sorun
Program bilgisi her kanal için ayrı `get_short_epg` isteğiyle (en çok 5 program) geliyor. TV rehber ızgarası
(`TvEpgGridScreen`) birkaç saati gösterebiliyor; çok kanallı ekranlarda yüzlerce istek gidiyor, sunucu 429 döndürebiliyor.
Rakip oynatıcılar (TiviMate vb.) XMLTV'yi bir kez indirip 1–3 günlük rehber gösteriyor.

## Bölüm A — Veritabanı (geçiş YIKICI OLMAYACAK)
1. Yeni tablo `epg_programs`: `id` (otomatik), `channelEpgId: String`, `startMs: Long`, `endMs: Long`, `title: String`,
   `description: String?`. İndeks: `(channelEpgId, startMs)`.
2. `live_streams`'e yeni sütun `tvArchiveDuration: Int = 0` (G-061 geri izleme için gerekiyor; şimdiden ekleniyor ki
   ikinci bir geçiş gerekmesin).
3. `AppDatabase` sürümü 2 → 3 ve **açık `Migration(2, 3)`**: `CREATE TABLE epg_programs …`, `CREATE INDEX …`,
   `ALTER TABLE live_streams ADD COLUMN tvArchiveDuration INTEGER NOT NULL DEFAULT 0`.
   - SQL'i elle tahmin etme: `exportSchema = true` yap, `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` ekle,
     derle, `app/schemas/.../3.json` içindeki `createSql` değerlerini kullan. Şema JSON'larını commit'le.
   - `fallbackToDestructiveMigration()` kalır (bilinmeyen eski sürümler için) ama 2→3 artık açık geçişle yapılır;
     yani güncellemeden sonra kullanıcının listesi yeniden indirilmez.
4. DAO: `insertAll(batch)`, `deleteOlderThan(ms)`, `deleteAll()`, `programsFor(channelEpgId, fromMs, toMs): List`,
   `nowAndNext(channelEpgIds: List<String>, nowMs)`, `count()`.

## Bölüm B — İndirme ve ayrıştırma
1. Adres: `xmltv.php?username=…&password=…` — mevcut `buildApiUrl` neyi kullanıyorsa aynı sunucu/kimlik bilgisiyle
   (sunucu adresi ve şifre hiçbir log'a yazılmaz; G-057 kuralı).
2. G-028'deki gibi **önce dosyaya indir** (`cacheDir/epg/xmltv.xml`, gzip ise `GZIPInputStream`), sonra dosyadan oku.
   Dosya hiçbir zaman belleğe tamamen alınmaz.
3. `XmltvParser`: `XmlPullParser` ile akış halinde; `<programme start="20261113180000 +0300" stop="…" channel="…">`
   içindeki `<title>` ve `<desc>`. Saat biçimi: `yyyyMMddHHmmss Z` (bölge yoksa UTC say). Bozuk kayıt atlanır.
   Parser `XmlPullParser` örneğini dışarıdan alsın (test için).
4. Pencere: şimdiden **6 saat geri – 48 saat ileri**; dışındakiler yazılmaz. Yalnız `live_streams.epgChannelId`
   listesinde olan kanallar yazılır (önce kanal kimlik kümesini yükle).
5. Yazma: tek işlemde (`withTransaction`) önce `deleteAll()`, sonra 2.000'lik partilerle `insertAll`. Yarıda hata olursa
   işlem geri alınır, eski rehber kalır.
6. Zaman aşımı/bağlantı hatası/boş dosya → sessizce eski veriyle devam (log'a yalnız `e.javaClass.simpleName`).

## Bölüm C — Ne zaman indirilir
1. İlk içerik senkronu bittikten sonra arka planda bir kez; sonra uygulama açılışında son indirmeden **12 saat** geçtiyse.
   Son indirme zamanı DataStore'da liste başına (`keyFor("epg_last_sync", id)` gibi mevcut desen).
2. Arayüzü asla bekletmez; mevcut `BackgroundSyncBanner` ile "Program rehberi güncelleniyor" gösterilebilir
   (yüzde bilinmiyorsa belirsiz).
3. Oynatıcı açıkken indirme oynatmayı etkilememeli: indirme `Dispatchers.IO`'da, ayrıştırma da IO'da; aynı anda tek indirme.
4. Ayarlar → Oynatıcı ya da Genel: **"Program rehberini indir"** anahtarı (varsayılan açık). Kapalıysa toplu indirme yapılmaz,
   eski davranış (kısa EPG) sürer. Zayıf TV kutuları için.

## Bölüm D — Kullanım
1. `EpgCache` (ui/vm): bir kanalın programları istenince önce veritabanı (`programsFor`); veri yoksa mevcut `get_short_epg`
   yolu (geri dönüş). Kanal kartlarındaki "şimdi/sonra" bilgisi de önce veritabanından.
2. `TvEpgGridScreen`: ızgara şimdiki saatten **24 saat ileri** gösterir (veri varsa), yatay kaydırma mevcut ölçekle
   (`durationMinutes * 3.66f).dp`). Geçmiş programlar (şimdiden önce) soluk gösterilir — G-061'de tıklanabilir olacak.
3. Arayüz metinleri `docs/TASARIM-KURALLARI.md`'ye uygun ("Program rehberi"; "EPG", "XMLTV" kelimeleri arayüzde yok).

## Test
- `XmltvParserTest`: örnek XMLTV metni (2 kanal, 5 program, biri bozuk, biri pencere dışı) → doğru sayı, doğru ms, bölgesiz saat UTC.
  JVM'de `XmlPullParser` için `testImplementation("net.sf.kxml:kxml2:2.3.0")` ekle.
- Saat çözümleme: `+0300`, `-0500`, bölgesiz.
- Pencere süzgeci saf fonksiyon olarak test edilir.

## 🚫 DOKUNMA
- Film/dizi senkronu, G-028 indirme yardımcısının davranışı (yeniden kullan, değiştirme), oynatıcı dosyaları.
- `AutoUpdateManager`, `signingConfigs`, `keystore.properties`, `*.jks`, `.github/workflows/`. Sürüm numarası. Release yapma.

## Kontrol
- `./gradlew :app:testDebugUnitTest` (BelowNormal, pencere açmadan) → geçmeli.
- `app/schemas/com.tivimax.player.data.local.AppDatabase/3.json` var ve commit'li.
- Raporda: Migration SQL'i, 3.json'daki createSql ile birebir aynı mı (kopyala-yapıştır kanıtı).
- Elle deneme (cihaz varsa): güncellemeden sonra liste yeniden inmiyor; rehber 1 dk içinde doluyor. Yoksa raporda belirt.

## Agent raporu
4 commit (A veritabanı+geçiş, B indirme+ayrıştırma, C tetikleme+Ayarlar anahtarı, D kullanım). `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:compileReleaseKotlin` → BUILD SUCCESSFUL (AppDatabaseMigrationSqlTest 5/5, XmltvParserTest 7/7).

**Geçiş (YIKICI DEĞİL):** `exportSchema = true` + KSP `room.schemaLocation=$projectDir/schemas`; `app/schemas/com.tivimax.player.data.local.AppDatabase/3.json` üretildi ve commit'li. Sürüm 2 → 3, `AppDatabaseMigrations.MIGRATION_2_3` (`fallbackToDestructiveMigration` yalnız bilinmeyen eski sürümler için kaldı). `epg_programs` tablosu VE `live_streams.tvArchiveDuration` sütunu tek geçişte.
SQL, 3.json `createSql` değerlerinden kopyalandı (`${TABLE_NAME}` → tablo adı):
- Geçiş[0] = `CREATE TABLE IF NOT EXISTS `epg_programs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `channelEpgId` TEXT NOT NULL, `startMs` INTEGER NOT NULL, `endMs` INTEGER NOT NULL, `title` TEXT NOT NULL, `description` TEXT)` — 3.json ile aynı.
- Geçiş[1] = `CREATE INDEX IF NOT EXISTS `index_epg_programs_channelEpgId_startMs` ON `epg_programs` (`channelEpgId`, `startMs`)` — 3.json ile aynı.
- Geçiş[2] = `ALTER TABLE `live_streams` ADD COLUMN `tvArchiveDuration` INTEGER NOT NULL DEFAULT 0` — 3.json'da live_streams tanımının son sütunu aynen `tvArchiveDuration` INTEGER NOT NULL DEFAULT 0 (`@ColumnInfo(defaultValue = "0")`).
Doğrulama: `AppDatabaseMigrationSqlTest` bu dizgileri 3.json ile JVM'de eşitler (DROP/DELETE yok, v2 tanımı sütun çıkarılınca `directSource` TEXT, PRIMARY KEY ile biter). Sürüm 2 için şema JSON'u yoktu (exportSchema kapalıydı); v2 şeması, v1.5.1'den beri değişmeyen entity'lerden çıkarıldı. Cihazda gerçek 2→3 geçişi (MigrationTestHelper) ÇALIŞTIRILMADI.

**B:** `XmltvParser` (XmlPullParser, akış, pencere −6 sa/+48 sa, çakışma esaslı, bozuk kayıt atlanır, bölgesiz = UTC, kanal kimliği küçük harf), `EpgRepository` (önce dosyaya indirme `cacheDir/epg`, gzip otomatik, tek `withTransaction`: `deleteAll` + 2000'lik partiler, boş sonuç/hata → geri alınır, eski rehber kalır; tek indirme; log yalnız hata türü), `XtreamRepository.downloadXmltvToFile` (G-028 yardımcısı aynen kullanıldı). `LiveStream.tvArchiveDuration` hoşgörülü `LenientIntAdapter` ile okunur (bozuk değer kanalı düşürmez).
**C:** Ayarlar'da "Program rehberini indir" (varsayılan açık); canlı kanallar hazır olunca, açılışta ve liste değişiminde `scheduleEpgSync` (12 saat kuralı, liste başına `epg_last_sync`). `BackgroundSyncBanner` ile gösterim YAPILMADI (isteğe bağlıydı).
**D:** `EpgCache` önce veritabanı (şimdi −15 dk … +24 sa), yoksa `get_short_epg`; başlıklar mevcut arayüz çözümü için Base64 verilir; yeni rehber inince bellekteki kanallar tazelenir. `TvEpgGridScreen`: geçmiş programlar soluk (alpha 0.45). `nowAndNext` DAO'su hazır ama kullanılmıyor.
**Açık/denenmedi:** cihaz yok; gerçek XMLTV (50–200 MB) süre/bellek ölçülmedi; indirme iş parçacığı önceliği düşürülmedi; ızgara yalnız şu andan 15 dk geriyi gösteriyor (G-061'de geçmiş).
