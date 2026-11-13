# G-050 — Ebeveyn kilidi gerçekten kilitlesin + yedekleme kuralları

- **Öncelik:** 🔴
- **Agent dağılımı:** Tek agent (iki bölüm, aynı branch).
- **Branch:** `fix/ebeveyn-pin-yedekleme` (main'den aç)
- **Commit mesajları:** Bölüm A: `fix(ebeveyn): varsayılan 0000 PIN'i her zaman kilidi açmıyor, ilk kullanımda PIN belirleniyor`
  · Bölüm B: `fix(yedek): içerik veritabanları yedeğe girmiyor, yedekten dönüşte liste yeniden indiriliyor`
- **Değişecek dosyalar (SADECE bunlar):**
  - A: `ui/MainViewModel.kt` (sadece PIN fonksiyonları ve `parentalPin` alanı), `data/preferences/UserPreferences.kt`
    (sadece PIN akışı/kaydı), `ui/live/LiveTvScreen.kt` (sadece PIN diyaloğu), `ui/settings/SettingsScreen.kt`
    (sadece PIN değiştirme diyaloğu), yeni test `app/src/test/java/com/tivimax/player/ui/ParentalPinTest.kt`
  - B: `app/src/main/res/xml/backup_rules.xml`, `app/src/main/res/xml/data_extraction_rules.xml`,
    `app/src/main/AndroidManifest.xml` (sadece `<application>`'a iki öznitelik),
    `ui/MainViewModel.kt` (sadece açılıştaki `if (activePl.isSynced)` dalı), gerekirse `data/local/dao/Daos.kt`
    (sadece yeni bir sayım sorgusu)
  - Bu görev dosyasının "Agent raporu" bölümü ve `YAPILACAKLAR.md` — sadece G-050 satırı

(Yollar `app/src/main/java/com/tivimax/player/` altına göredir.)

---

## Bölüm A — Ebeveyn kilidi

### Sorun
1. `MainViewModel.unlockParental()` içinde `pin == parentalPin.value || pin == "0000"` yazıyor: kullanıcı PIN'i
   değiştirse bile **0000 her zaman kilidi açıyor**. Kilit fiilen işlevsiz.
2. Kayıtlı PIN yoksa `parentalPinFlow` `"0000"` döndürüyor ve diyalog "(Varsayılan: 0000)" diye herkese söylüyor.

### Çözüm
1. `UserPreferences.parentalPinFlow`: kayıt yoksa `""` döndür (boş = PIN belirlenmemiş). `MainViewModel.parentalPin`
   `stateIn` başlangıç değeri de `""` olsun.
2. `unlockParental(pin)`: `parentalPin.value` boş değilse ve `pin == parentalPin.value` ise aç. `"0000"` istisnası
   tamamen kalksın.
3. PIN belirlenmemişse (`parentalPin.value.isEmpty()`) LiveTvScreen'deki kilit diyaloğu **"PIN belirle"** moduna geçer:
   - Başlık: "Ebeveyn PIN'i belirle". Metin: "+18 içerikleri korumak için 4 haneli bir PIN belirle."
   - İki alan: "PIN" ve "PIN tekrar". 4 rakam değilse ya da eşleşmiyorsa hata metni ("PIN'ler aynı değil" /
     "4 rakam gir"). Doğruysa `setParentalPin` ile kaydet, kilidi aç, diyaloğu kapat.
   - Normal moddaki metinden "(Varsayılan: 0000)" kısmını kaldır: "+18 içerikleri açmak için PIN'ini gir."
4. Ayarlar'daki PIN değiştirme diyaloğu: PIN belirliyse önce **mevcut PIN** sorulur; yanlışsa değiştirmez.
   PIN belirlenmemişse doğrudan yeni PIN (iki kez) ister.
5. Mevcut kullanıcılar: daha önce PIN'ini değiştirmiş olan kendi PIN'iyle devam eder. Hiç değiştirmemiş olan
   (kayıt yok) ilk denemede PIN belirler. Ayrı bir geçiş kodu gerekmez.
6. TV: diyaloglardaki rakam alanları kumandayla odaklanabilir olmalı (mevcut `TvTextFieldSupport` neyi
   kullanıyorsa onu kullan). TV yolunu ayrıca kontrol et (AGENTS.md).
7. Metinler `docs/TASARIM-KURALLARI.md`'ye uygun ("sen" dili, sabit `Color(0x…)` yazma, `VegaColors` kullan).

### Test (`ParentalPinTest.kt`)
Kilidi açma kararını saf bir yardımcıya taşı (ör. `ParentalPin.canUnlock(stored: String, entered: String): Boolean`
ve `validateNewPin(pin, repeat): String?` hata metni ya da null) ve test et:
- kayıtlı "1234", girilen "0000" → false (eski açığın testi)
- kayıtlı "1234", girilen "1234" → true
- kayıtlı "" → her girişte false
- "12a4", "123", eşleşmeyen tekrar → hata

### Sadece raporla (kod yazma)
+18 kilidi şu an yalnızca Canlı TV kategorilerinde mi uygulanıyor? Film ve dizi kategorilerinde de yetişkin
içerik var mı ve kilitleniyor mu? Bulduğunu rapora yaz; ayrı görev olarak açılacak.

---

## Bölüm B — Yedekleme kuralları

### Sorun
`allowBackup=true` ama `backup_rules.xml` ve `data_extraction_rules.xml` boş şablon. Android içerik
veritabanlarını (`vega_iptv.db`, `vega_pl_*.db` — on binlerce kanal/film) Google yedeğine koymaya çalışıyor;
25 MB sınırı aşılınca yedeğin tamamı sessizce başarısız olur. Ayrıca veritabanı yedekte varsa bile eski olur.

### Çözüm
1. Her iki XML'de **`database` alanını tamamen hariç tut** (`<exclude domain="database" path="." />`).
   `data_extraction_rules.xml`'de hem `<cloud-backup>` hem `<device-transfer>` için. Tercihler (DataStore:
   listeler, favoriler, izleme ilerlemesi) yedekte kalır — telefon değiştiren müşteri tekrar giriş yapmaz,
   favorilerini kaybetmez. Önbellek klasörleri zaten yedeğe girmez.
2. Yedekten dönüş durumu: liste `isSynced = true` olarak gelir ama veritabanı boştur. Açılıştaki
   `if (activePl.isSynced)` dalında (MainViewModel ~1037. satır) aktif listenin veritabanında hiç canlı kanal,
   film ve dizi yoksa `isSynced` sayılmasın → `Screen.PlaylistSync` + `startPlaylistSync(fromStep = 1)`.
   Sayımı ana iş parçacığında yapma (IO). DAO'da hazır bir sayım sorgusu varsa onu kullan, yoksa
   `SELECT COUNT(*)` ekle. 131. satırdaki liste değiştirme yolunda da aynı kontrol gerekiyorsa uygula.
3. **Bu XML'ler şu an manifest'e bağlı değil** (`<application>` içinde sadece `android:allowBackup="true"` var),
   yani tek başına düzenlemek hiçbir şey değiştirmez. `AndroidManifest.xml`'de `<application>` etiketine şu iki
   özniteliği ekle, başka hiçbir satırına dokunma:
   `android:fullBackupContent="@xml/backup_rules"` ve `android:dataExtractionRules="@xml/data_extraction_rules"`.
   (Değişecek dosyalara `app/src/main/AndroidManifest.xml` — sadece bu iki öznitelik — eklenmiştir.)

### Kontrol
- `adb` ile bir emülatör açıksa: `adb shell bmgr backupnow com.tivimax.player` sonrası logda veritabanı
  dosyalarının yedeklenmediğini gör. Emülatör yoksa bu adımı atla ve raporda belirt (emülatörü kendin başlatma).

---

## 🚫 DOKUNMA
- Kategori filtreleme mantığı (`CategoryFilteringTest` kapsamındaki kod), favori/izleme ilerlemesi kodu.
- `AutoUpdateManager`, imza ayarları, `keystore.properties`, `*.jks`, `.github/workflows/`.
- Sürüm numarası. Release yapma.

## Kontrol (genel)
- `./gradlew :app:testDebugUnitTest` (düşük öncelikle, pencere açmadan) → geçmeli.

## Agent raporu
**Bölüm A — Ebeveyn kilidi**
- `UserPreferences.parentalPinFlow`: kayıt yoksa `""` (boş = PIN belirlenmemiş). `MainViewModel.parentalPin` başlangıç değeri de `""`.
- `MainViewModel.kt`: yeni saf yardımcı `object ParentalPin` (`canUnlock(stored, entered)`, `validateNewPin(pin, repeat)`); `unlockParental()` artık sadece `ParentalPin.canUnlock` kullanıyor, `|| pin == "0000"` istisnası TAMAMEN kaldırıldı. Yeni `createParentalPin(pin, repeat)` ilk PIN'i kaydedip kilidi açar.
- `LiveTvScreen.kt` PIN diyaloğu: PIN yoksa "Ebeveyn PIN'i belirle" modu (PIN + PIN tekrar, "4 rakam gir" / "PIN'ler aynı değil" hataları); PIN varsa "+18 içerikleri açmak için PIN'ini gir." ("(Varsayılan: 0000)" kalktı). Alanlar `tvKeyboardOptions` + `tvOpenKeyboardOnOk` ile TV kumandasından açılır (mevcut düzenle aynı).
- `SettingsScreen.kt` PIN diyaloğu: PIN belirliyse önce "Mevcut PIN" sorulur (yanlışsa "Mevcut PIN yanlış", kaydetmez), sonra yeni PIN iki kez; belirli değilse doğrudan yeni PIN iki kez. Alanlar için küçük `PinDialogField` bileşeni eklendi (aynı dosyada, private). Ayrıca rakam dışı karakter artık girilemiyor.
- Mevcut kullanıcılar: PIN'ini değiştirmiş olan kendi PIN'iyle devam eder; hiç değiştirmemiş olan (kayıt yok) ilk denemede PIN belirler. Geçiş kodu yok.
- Yeni `ParentalPinTest.kt` (6 test): kayıtlı 1234/girilen 0000 → false (eski açık), 1234/1234 → true, kayıtlı boş → hiçbir giriş açmaz, "12a4"/"123"/"12345"/boş → "4 rakam gir", eşleşmeyen tekrar → "PIN'ler aynı değil".

**Bölüm B — Yedekleme**
- `backup_rules.xml` ve `data_extraction_rules.xml` (hem `<cloud-backup>` hem `<device-transfer>`): `<exclude domain="database" path="." />`. Tercihler (DataStore) yedekte kalır.
- `AndroidManifest.xml`: `<application>`'a yalnızca `android:fullBackupContent="@xml/backup_rules"` ve `android:dataExtractionRules="@xml/data_extraction_rules"` eklendi.
- `MainViewModel.kt`: yeni `hasLocalContent()` (IO'da; hazır DAO sorguları `getTotalStreamCount()` canlı+film+dizi). Açılıştaki `if (activePl.isSynced)` dalı ve `switchPlaylist` içindeki `targetPlaylist.isSynced` dalı artık `isSynced && hasLocalContent()`; üçü de 0 ise `Screen.PlaylistSync` + `startPlaylistSync(fromStep = 1)`. Sayım okunamazsa eski davranış (true). `Daos.kt` değişmedi (sayım sorguları zaten vardı).
- Not: gerçekten içeriği hiç olmayan (üçü de 0) bir sağlayıcıda liste her açılışta yeniden senkron akışına girer; görev tarifi böyle istediği için bırakıldı.
- Emülatör/cihaz bağlı değildi (`adb devices` boş) → `bmgr backupnow` kontrolü ATLANDI, elle doğrulanmadı.

**Test:** `:app:testDebugUnitTest` ve `:app:processReleaseResources` → BUILD SUCCESSFUL (ParentalPinTest dahil).

**+18 kapsamı bulgusu (kod yazılmadı):**
- Kategori adına göre süzme (`isAdultCategory`: "+18", "18+", "xxx", "adult", "yetişkin", "erotik", "porn") `MainViewModel`'de Canlı TV, Film (`visibleVodCategories`, `VodScreen`) ve Dizi (`visibleSeriesCategories`, `VegaMediaCatalogScreen`) kategorilerinin üçüne de aynı `isParentalUnlocked` bayrağıyla uygulanıyor; yani film/dizide de yetişkin kategoriler kilit açılana kadar gizli.
- AMA PIN soran diyalog ve kilit düğmesi sadece Canlı TV ekranında (`LiveTvScreen`). Film/Dizi ekranlarında kilidi açma yolu yok: bu kategoriler ancak Canlı TV'den kilit açılırsa (oturum boyunca) görünür.
- Süzme yalnız kategori ADINA bakıyor; adı bu kelimeleri içermeyen kategorideki yetişkin içerik, "Tümü" kategorisi ve aramada kilitsiz görünebilir (arama/"Tümü" için ayrı doğrulama gerekir; kontrol edilmedi). Ayrıca `isParentalUnlocked` oturum bayrağı uygulama kapanınca sıfırlanıyor, ekran/uygulama arka plana gidince kilitlenme davranışı incelenmedi.
- Ayrı görev önerisi: Film/Dizi ekranlarına da PIN diyaloğu + kilit düğmesi, "Tümü"/arama süzmesi.
