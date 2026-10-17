# G-020 — İçerik ilk açılışta "Session ID must be unique" hatası veriyor (medya oturumu sızıntısı)

- **Öncelik:** 🔴
- **Agent dağılımı:** Tek agent
- **Branch:** `fix/medya-oturumu-tekil`
- **Commit mesajı:** `fix(player): medya oturumu doğru kapatılıyor, ilk açılıştaki "Session ID must be unique" hatası giderildi`

## Sorun (kullanıcı ekran görüntüsüyle bildirdi)

Canlı kanal / film / dizi **ilk açılışta** hata ekranı çıkıyor:
`Yayın başlatılamadı: Session ID must be unique. ID=` — "Tekrar Dene"ye basınca normal açılıyor.
Bu, uygulamadan çıkıp tekrar girdikten sonra **her içerikte** tekrarlanıyor.

## Kök neden (Claude incelemesi)

`player/TiviPlayerManager.kt`:

```kotlin
// initializePlayer() ~746-758
exoPlayer = player
...
if (mediaSession == null) {
    mediaSession = MediaSession.Builder(context, player).build()   // kimliği hep "" (varsayılan)
}
```
```kotlin
// release() ~1343
exoPlayer?.release(); exoPlayer = null      // mediaSession HİÇ kapatılmıyor
```

1. `release()` (oynatıcıdan çıkınca, mini oynatıcı kapanınca, `MainActivity.onDestroy`, `MainViewModel.onCleared`)
   ExoPlayer'ı kapatıyor ama **`MediaSession`'ı kapatmıyor**. Oturum, kapatılmış oynatıcıya bağlı olarak süreç içinde
   yaşamaya devam ediyor.
2. Media3 aynı süreçte **aynı kimlikle ikinci bir oturuma izin vermiyor**. Uygulamadan geri tuşuyla çıkıp tekrar
   girildiğinde yeni `MainViewModel` → yeni `TiviPlayerManager` (`mediaSession == null`) → `MediaSession.Builder(...).build()`
   eski, kapatılmamış oturum yüzünden `IllegalStateException("Session ID must be unique. ID=")` fırlatıyor.
3. İstisna `initializePlayer()` içinde, `playInternal()`'ın `try` bloğunda olduğu için yayın başlatma "başarısız" sayılıyor.
4. "Tekrar Dene" çalışıyor çünkü `exoPlayer` istisnadan ÖNCE atanmış; ikinci denemede `initializePlayer()` erken dönüp
   oturum koduna hiç gelmiyor.
5. Oynatıcıdan çıkınca `release()` ExoPlayer'ı tekrar null yapıyor → sonraki içerikte yeni player → oturum yine kurulmaya
   çalışılıyor → yine hata. Bu yüzden **her içeriğin ilk açılışı** bozuk.

Ek kusur: hata ekranında İngilizce teknik istisna metni gösteriliyor (`e.localizedMessage`) — `AGENTS.md` kuralına aykırı.

## Yapılacaklar (sadece `player/TiviPlayerManager.kt`)

1. **`release()` oturumu da kapatsın** (ExoPlayer'ı kapatmadan ÖNCE):
   ```kotlin
   mediaSession?.release()
   mediaSession = null
   ```
2. **Oturum kurulumu oynatmayı asla engellemesin** — `initializePlayer()`'daki blok:
   ```kotlin
   val session = mediaSession
   if (session == null) {
       try {
           mediaSession = MediaSession.Builder(context, player)
               .setId("vega_${System.identityHashCode(this)}_${SystemClock.elapsedRealtime()}")
               .build()
       } catch (e: Exception) {
           Log.w("VegaIPTV", "MediaSession kurulamadı; oynatma devam ediyor", e)
       }
   } else if (session.player !== player) {
       session.player = player   // yeni ExoPlayer'a bağla (kapatılmış oynatıcıda kalmasın)
   }
   ```
   (Benzersiz kimlik, olası eski bir oturumla çakışmayı da önler.)
3. **Hata metni:** `playInternal()` ~839:
   `"Yayın başlatılamadı: ${e.localizedMessage ...}"` → **"Yayın başlatılamadı. Lütfen tekrar dene."**
   (istisna zaten `Log.e` ile loglanıyor). Aynı dosyada kullanıcıya `e.localizedMessage`/`e.message` gösteren başka
   `_errorMessage` ataması varsa aynı şekilde genel Türkçe metne çevir.
4. İkincil (dual view) oynatıcıya oturum EKLEME; dokunma.

## DOKUNMA
Yüzey devri (`attachView/detachView`), `VodPrewarmer`, LoadControl/buffer, fallback/yeniden deneme mantığı,
parça seçimi, PiP davranışı, güncelleme sistemi, imza, CI.

## Test
- Uygulamayı aç → canlı kanal aç: hatasız açılıyor.
- Oynatıcıdan çık → başka kanal/film aç: hatasız.
- **Geri tuşuyla uygulamadan tamamen çık → tekrar aç → içerik aç: hatasız** (asıl hata buydu).
- Aynısını 3 kez art arda yap (çık-gir-içerik aç): her seferinde hatasız.
- Not: Kullanıcı "uygulama verilerini silince düzeldi" dedi — veri silmek uygulamayı tamamen kapattığı için sızan
  oturum da gidiyordu; sorun verilerle ilgili değil. Veri silmeden, sadece çık-gir ile test et.
- Film oynarken ana ekrana dön (PiP açıksa küçük pencere çalışıyor), kilit ekranında oynatma kontrolleri görünüyor.
- Uçak modunda içerik aç → "Yayın başlatılamadı. Lütfen tekrar dene." (İngilizce metin yok).
- Yerelde sadece `./gradlew compileDebugKotlin` çalıştır; APK üretme. Test edemediğini "test edilemedi" yaz.

---

## Agent raporu

- **Yapılan değişiklikler:**
  - `app/src/main/java/com/tivimax/player/player/TiviPlayerManager.kt`:
    1. `release()` metoduna `mediaSession?.release()` ve `mediaSession = null` eklendi (ExoPlayer'dan önce medya oturumu doğru şekilde serbest bırakılıyor).
    2. `initializePlayer()` içindeki `MediaSession` oluşturma mantığı güvenli hale getirildi:
       - Tekil oturum kimliği (`vega_${System.identityHashCode(this)}_${SystemClock.elapsedRealtime()}`) tanımlandı.
       - Blok `try-catch` içine alındı, olası oturum oluşturma hataları sadece `Log.w` ile loglanarak oynatmanın engellenmesi önlendi.
       - Eğer oturum önceden varsa ve yeni bir player açılmışsa `session.player = player` ile yeni ExoPlayer örneğine bağlandı.
    3. `playInternal()` içindeki hata durumunda İngilizce teknik istisna (`e.localizedMessage`) yerine `AGENTS.md` arayüz kurallarına uygun genel Türkçe mesaj `"Yayın başlatılamadı. Lütfen tekrar dene."` atandı.
- **Değiştirilen dosyalar:**
  - `app/src/main/java/com/tivimax/player/player/TiviPlayerManager.kt`
  - `docs/gorevler/G-020-medya-oturumu-hatasi.md`
  - `YAPILACAKLAR.md`
- **Test ve doğrulama:**
  - `./gradlew compileDebugKotlin` çalıştırıldı: BUILD SUCCESSFUL (44s).
  - Canlı cihaz/emülatörde çoklu çıkış-giriş ve kilit ekranı oynatma kontrolleri test edilemedi (derleme seviyesinde doğrulandı, APK üretilmedi).
- **Karşılaşılan sorunlar / Notlar:**
  - Kapsam dışındaki hiçbir dosyaya ve dual view/yüzey yönetimi mantığına dokunulmadı.
