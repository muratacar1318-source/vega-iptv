# G-048 — Kayıtlı listeler ve "kaldığın yer" güncellemede kaybolmasın (R8 dayanıklılığı)

- **Öncelik:** 🔴
- **Agent dağılımı:** Tek agent.
- **Branch:** `fix/kayitli-veri-r8` (main'den aç)
- **Commit mesajı:** `fix(veri): kayıtlı listeler ve izleme ilerlemesi sürüm güncellemesinde korunuyor`
- **Değişecek dosyalar (SADECE bunlar):**
  1. `app/src/main/java/com/tivimax/player/data/playlist/Playlist.kt`
  2. `app/src/main/java/com/tivimax/player/data/preferences/UserPreferences.kt` — sadece `WatchProgress` sınıfı
  3. `app/proguard-rules.pro` — sadece sona yeni blok
  4. `app/src/test/java/com/tivimax/player/data/playlist/PlaylistJsonCompatTest.kt` (yeni)
  5. Bu görev dosyasının "Agent raporu" bölümü ve `YAPILACAKLAR.md` — sadece G-048 satırı

## Sorun
`Playlist` (liste adı, sunucu, kullanıcı adı, şifre…) ve `WatchProgress` (film/dizide kalınan saniye)
DataStore'da Gson ile JSON olarak saklanıyor. Bu iki sınıfta `@SerializedName` yok ve `proguard-rules.pro`
`data.playlist` / `data.preferences` paketlerini korumuyor. Release derlemede R8 alan adlarını kısaltıyorsa
(`a`, `b`, `c`…) JSON anahtarları da bu kısa adlar olur. Sınıfa alan eklenince ya da R8 farklı ad verince
bir sonraki sürümde eski kayıt okunamaz: listeler kaybolur, müşteri tekrar giriş yapmak zorunda kalır,
Kotlin'de null olamayan alanlar null gelip çökme olabilir.

## Adım 1 — Gerçek durumu ölç (ZORUNLU, tahmin etme)
Bu bilgisayar release'lerin derlendiği bilgisayardır; v1.5.1 etiketiyle aynı kodu derlersen yayındaki APK ile
aynı R8 adlandırmasını görürsün.
1. `git checkout v1.5.1` (main ile aynı commit: `e0b7f46`; değilse `git checkout e0b7f46`).
2. Release'i **düşük öncelikle ve pencere açmadan** derle (Bash aracı):
   ```
   powershell -NoProfile -Command "(Get-Process -Id `$PID).PriorityClass='BelowNormal'; ./gradlew :app:assembleRelease"
   ```
   Aynı anda başka ağır iş (ikinci gradle, ffmpeg vb.) çalıştırma. APK'yı hiçbir yere yükleme, kurma, yayınlama.
3. `app/build/outputs/mapping/release/mapping.txt` içinde şu iki sınıfın satırlarını bul ve rapora AYNEN kopyala:
   - `com.tivimax.player.data.playlist.Playlist -> …:` ve altındaki alan satırları
   - `com.tivimax.player.data.preferences.WatchProgress -> …:` ve altındaki alan satırları
4. `git checkout main` ile geri dön, sonra `git checkout -b fix/kayitli-veri-r8`.

## Adım 2 — Sonuca göre düzelt
Her alan için kalıcı JSON anahtarı tanımla: `@SerializedName(value = "<gerçek ad>", alternate = [<eski kısa ad>])`.
- **Yazarken** artık hep gerçek ad kullanılır (`id`, `name`, `serverUrl`, `username`, `password`, `dbName`,
  `createdAt`, `isSynced`, `lastSyncAt`; `streamId`, `positionMs`, `durationMs`, `timestamp`).
- **Okurken** eski kısa ad da kabul edilir → mevcut kullanıcıların kaydı kaybolmaz; ilk yazmada yeni biçime geçer.

Durumlar:
- **A) mapping'de alanlar kısa ada çevrilmiş** (ör. `java.lang.String password -> e`): `alternate` içine o kısa adı yaz.
- **B) alanlar hiç adlandırılmamış / kendi adıyla kalmış:** `alternate` gerekmez, sadece `@SerializedName("<gerçek ad>")`.
- **C) bir alan mapping'de hiç yok (R8 silmiş):** o alan şu an kaydedilmiyor demektir; `alternate` yazma,
  sadece gerçek adı ver, raporda belirt.
- Beklenmeyen bir şey görürsen (ör. sınıf adının kendisi yok, iki sınıf aynı kısa adları kullanıyor ve çakışma
  ihtimali var): **dur, raporla.**

Ek olarak `proguard-rules.pro` sonuna şu bloğu ekle (alan adları bundan sonra hiç değişmesin):
```
# G-048: DataStore'da JSON olarak saklanan sınıflar (alan adları JSON anahtarıdır)
-keep class com.tivimax.player.data.playlist.Playlist { <fields>; <init>(...); }
-keep class com.tivimax.player.data.preferences.WatchProgress { <fields>; <init>(...); }
```
`@SerializedName` zaten anahtarı sabitler; keep kuralı R8'in alanı silmesini de engeller.

## Adım 3 — Birim testi
`PlaylistJsonCompatTest.kt` (JUnit4, düz Gson, Android gerektirmez):
1. Eski biçim: Adım 1'de bulduğun kısa anahtarlarla elle yazılmış bir JSON dizisi → `TypeToken<List<Playlist>>`
   ile okunduğunda tüm alanlar doğru gelir. (Durum B/C ise bu testi gerçek adlarla yaz.)
2. Yeni biçim: `Playlist` → `toJson` → içinde `"password"` ve `"serverUrl"` anahtarları geçer; geri okununca eşit.
3. Aynı iki test `WatchProgress` için (`Map<String, WatchProgress>`).

## 🚫 DOKUNMA
- `PlaylistRepository` ve `UserPreferences` içindeki okuma/yazma mantığı (sadece `WatchProgress` sınıfına
  anotasyon eklenir). DataStore anahtar adları (`playlists_json`, `watch_progress_json`…) değişmez.
- `AutoUpdateManager`, imza ayarları, `keystore.properties`, `*.jks`, `.github/workflows/`.
- Sürüm numarası. Release yapma.

## Kontrol
1. `./gradlew :app:testDebugUnitTest` (düşük öncelikle) → geçmeli.
2. Tekrar release derle (düşük öncelikle), yeni `mapping.txt`'de `Playlist` alanlarının adları korunmuş olmalı.
   Release APK'yı hiçbir yere yükleme.

## Agent raporu
_(Agent doldurur: Adım 1'deki mapping satırları, hangi durum (A/B/C), değişen dosyalar, test sonuçları, sorunlar.)_
