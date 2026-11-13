# G-051 — Çökme günlüğü ve "Hata raporu gönder"

- **Öncelik:** 🟠
- **Agent dağılımı:** Tek agent.
- **Branch:** `feat/cokme-gunlugu` (main'den aç)
- **Commit mesajı:** `feat(destek): uygulama çökmeleri kaydediliyor, Ayarlar'dan hata raporu paylaşılabiliyor`
- **Değişecek dosyalar (SADECE bunlar):**
  1. `app/src/main/java/com/tivimax/player/util/CrashLogger.kt` (yeni)
  2. `app/src/main/java/com/tivimax/player/VegaApp.kt` — `onCreate` içine tek kurulum satırı
  3. `app/src/main/java/com/tivimax/player/ui/settings/SettingsScreen.kt` — sadece "Güncelleme ve Bakım"
     bölümündeki "Hakkında" kısmı (~1728. satır)
  4. `app/src/test/java/com/tivimax/player/util/CrashLoggerTest.kt` (yeni)
  5. Bu görev dosyasının "Agent raporu" bölümü ve `YAPILACAKLAR.md` — sadece G-051 satırı

## Sorun
Uygulamada hiçbir çökme raporu yok. Müşteride uygulama kapanırsa ne olduğunu öğrenmenin yolu yok; destek
"uygulama kapanıyor" şikâyetine kör kalıyor. Dışarıya veri gönderen bir servis (Crashlytics vb.) **istemiyoruz**;
kayıt cihazda kalır, müşteri isterse kendisi paylaşır.

## Çözüm

### 1. `CrashLogger` (util/CrashLogger.kt)
- `fun install(context: Context)`: mevcut `Thread.getDefaultUncaughtExceptionHandler()`'ı saklar, yenisini kurar.
  Yeni işleyici: raporu dosyaya **senkron** yazar (try/catch içinde, kendisi asla atmaz), sonra eski işleyiciyi
  çağırır (sistem normal çökme davranışını sürdürsün).
- Dosyalar: `filesDir/crash/crash_<epochMillis>.txt`. **En fazla 5 dosya**; fazlası en eskiden silinir.
- İçerik: tarih/saat (yerel), uygulama sürümü (`BuildConfig.VERSION_NAME` + `VERSION_CODE`), cihaz
  (`Build.MANUFACTURER`, `Build.MODEL`, `Build.VERSION.SDK_INT`), TV mi telefon mu (mevcut `DeviceTypeUtil`
  neyi sunuyorsa), iş parçacığı adı, tam yığın izi (`cause` zinciri dahil).
- **Gizlilik — zorunlu:** Xtream adresleri kullanıcı adı ve şifreyi URL içinde taşır. Dosyaya yazmadan önce
  metni `sanitize()` ile temizle:
  - `https?://\S+` → `<adres>`
  - `username=…`, `password=…` sorgu parametreleri → `username=<gizli>`, `password=<gizli>`
  - Aktif listenin kullanıcı adı ve şifresi metinde geçiyorsa (kurulumda verilen sağlayıcıdan okunabiliyorsa)
    `<gizli>` ile değiştir. Okunamıyorsa ilk iki kural yeterli; işleyici içinde DataStore'u bloklayarak okuma.
- `fun listReports(context): List<File>`, `fun buildShareText(context): String` (son 5 raporu yeniden
  `sanitize` edip birleştirir, en fazla ~50 KB), `fun clear(context)`.

### 2. Kurulum
`VegaApp.onCreate()`'de `super.onCreate()`'den hemen sonra `CrashLogger.install(this)`. Mevcut CursorWindow
koduna dokunma.

### 3. Ayarlar → Güncelleme ve Bakım → Hakkında
Sürüm satırının altına, **sadece rapor varsa** görünen:
- Metin: "Son çökmeler: N kayıt" (`TextSecondary`, 13 sp)
- `VegaButton(variant = SECONDARY)` "Hata raporu gönder" → `Intent.ACTION_SEND`, `type = "text/plain"`,
  `EXTRA_TEXT = buildShareText()`, `EXTRA_SUBJECT = "${AppConfig.APP_NAME} hata raporu"`,
  `Intent.createChooser(...)`. Müşteri WhatsApp/Telegram ile destek hattına gönderebilir.
- `VegaButton(variant = TEXT)` "Kayıtları sil" → `clear()`; liste yenilenir.
- TV'de ikisi de kumandayla odaklanabilir olmalı (`dpadFocusable`/mevcut ayarlar düzeni). TV'de paylaşım
  uygulaması olmayabilir: `resolveActivity` null ise düğmeye basınca "Bu cihazda paylaşım uygulaması yok"
  mesajı göster (çökme olmasın).
- `docs/TASARIM-KURALLARI.md`'ye uy; metinde "log", "crash", "stack trace" gibi teknik kelime yok.

### 4. Test (`CrashLoggerTest.kt`, JUnit4)
- `sanitize`: `http://a.b:8080/live/kullanici/sifre/123.ts` → `<adres>`;
  `...player_api.php?username=ali&password=123` → `ali` ve `123` metinde geçmez.
- Döndürme: 7 rapor yazıldığında 5 dosya kalır ve en eskiler silinir (geçici klasörle; `Context` gerekmeyecek
  şekilde dosya klasörünü parametre alan iç fonksiyon üzerinden test et).

## 🚫 DOKUNMA
- Uygulamadaki mevcut `catch` blokları (bu görevde değiştirilmez).
- `AutoUpdateManager`, imza ayarları, `.github/workflows/`, manifest. Yeni izin ekleme.
- Sürüm numarası. Release yapma. Yeni kütüphane ekleme.

## Kontrol
- `./gradlew :app:testDebugUnitTest` (düşük öncelikle, pencere açmadan) → geçmeli.
- İsteğe bağlı elle deneme (debug): geçici bir `throw` ile çökme oluşturup Ayarlar'da kaydın göründüğünü ve
  paylaşım metninde adres/şifre olmadığını gör; geçici kodu commit'leme.

## Agent raporu
_(Agent doldurur.)_
