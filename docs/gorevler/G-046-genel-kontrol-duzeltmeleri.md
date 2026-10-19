# G-046 — Genel kontrol düzeltmeleri (hata mesajları, TV odak hafızası, dizide kaldığın bölüm)

- **Öncelik:** 🟠
- **Agent dağılımı:** Tek agent (bölme). Gemini 3.6 Flash için. **Kod yazma yok** — Claude bütün değişiklikleri hazırladı,
  tek dosyada verdi: `docs/gorevler/G-046-degisiklikler.patch`. Agent sadece uygular, derler, gönderir.
- **Branch:** `fix/genel-kontrol` (main'den aç)
- **Commit mesajı:** `fix: TV'de hata mesajı görünür, sade hata metinleri, TV odak hafızası, dizide kaldığın bölüm`

## Neden (Claude'un kontrolünde bulunanlar)
1. **TV'de hata mesajı hiç görünmüyordu.** Tam ekran canlı yayında ve kanal önizlemesinde oynatıcı hatası ya da
   "Yayın yeniden bağlanıyor…" mesajı gösterilmiyordu. Kanal açılmayınca ekran öylece siyah kalıyordu.
   → Artık mesaj görünür; tam ekranda **OK = Tekrar dene**.
2. **Teknik hata metinleri:** Oynatıcı bazı hatalarda kullanıcıya teknik kod gösteriyordu (ör. "(404)", "ERROR_CODE_…").
   Giriş hatasında sunucunun İngilizce mesajı aynen gösteriliyordu. → Hepsi sade Türkçe oldu
   (abonelik bitmişse "Aboneliğinin süresi dolmuş" gibi).
3. **Altyazı dil tanıma:** Adı "İngilizce" olan ses kanalı İngilizce olarak tanınmıyordu (Türkçe "İ" harfi yüzünden).
   Bu yüzden İngilizce seste Türkçe altyazı kendiliğinden açılmayabiliyordu.
4. **TV odak hafızası (G-045'teki kategori sorunuyla aynı türden):**
   - Dizi detayında sezonlar satırı ve bölüm listesi: aşağı-yukarı gidip gelince seçili sezona ve aynı bölüme dönülür.
   - Arama sonuç satırları: satırlar arasında gidip gelince satırdaki aynı öğeye dönülür.
5. **Dizi detayı:**
   - Bölüm izleyip geri dönünce sezon hep 1. sezona dönüyordu. → Son seçilen sezon hatırlanır.
   - "Oynat" düğmesi kaldığın bölümü değil, sezonun 1. bölümünü açıyordu. → En son izlediğin bölümü açar
     (bitirdiysen sıradakini).

## Değişecek dosyalar (yama bunları değiştirir — başka dosyaya dokunma)
- `app/src/main/java/com/tivimax/player/player/TiviPlayerManager.kt`
- `app/src/main/java/com/tivimax/player/data/repository/XtreamRepository.kt` (sadece giriş hatası mesajı)
- `app/src/main/java/com/tivimax/player/ui/MainViewModel.kt`
- `app/src/main/java/com/tivimax/player/ui/tv/TvPlayerOverlay.kt`
- `app/src/main/java/com/tivimax/player/ui/tv/TvTiviMateScreen.kt`
- `app/src/main/java/com/tivimax/player/ui/series/SeriesDetailScreen.kt`
- `app/src/main/java/com/tivimax/player/ui/search/UniversalSearchScreen.kt`
- (+ `YAPILACAKLAR.md` — sadece G-046 satırı)

Toplam **7** kod dosyası.

## 🚫 DOKUNMA
- Yamada olmayan hiçbir değişiklik yapma. Yamayı "düzeltmeye" ya da "iyileştirmeye" çalışma.
- `AutoUpdateManager` ve güncelleme dosyaları, `keystore.properties`, `*.jks`.
- Yama dosyasının kendisini değiştirme.

---

## Adım 1 — Dalı aç
```
git checkout main
git pull
git checkout -b fix/genel-kontrol
```

## Adım 2 — Yamayı uygula (tek komut)
```
git apply --ignore-whitespace docs/gorevler/G-046-degisiklikler.patch
```
- Komut **hiçbir şey yazmadan** bitmeli.
- **Hata verirse:** hiçbir dosyayı elle değiştirme. `git checkout -- .` ile geri al, hata mesajını olduğu gibi
  `YAPILACAKLAR.md`'deki G-046 satırına yaz (`❌ Yama uygulanamadı: <mesaj>`), sadece o dosyayı commit'leyip gönder ve dur.

## Adım 3 — Kontrol
```
git status --short
```
- **7** satır ` M ...` görünmeli (yeni dosya yok).
- `git diff --stat` son satırı: `7 files changed, 133 insertions(+), 18 deletions(-)`

## Adım 4 — Derle ve test et
1. `./gradlew :app:assembleDebug` → hatasız bitmeli.
2. `./gradlew :app:testDebugUnitTest` → geçmeli.
3. Derleme hatası olursa: **kodu düzeltmeye çalışma.** Hata mesajının ilk 20 satırını `YAPILACAKLAR.md`'deki G-046
   satırına yaz (`❌ Derleme hatası: ...`), kod değişikliklerini geri al (`git checkout -- app/`),
   sadece `YAPILACAKLAR.md`'yi commit'leyip gönder ve dur. Claude düzeltip yeni yama verecek.

## Adım 5 — YAPILACAKLAR.md
Bekleyen görevler tablosunda G-046 satırını `🔄 Agent bitirdi — Claude kontrol edecek` yap. Başka satıra dokunma.

## Adım 6 — Gönder
```
git add -A app/src/main YAPILACAKLAR.md
git commit -m "fix: TV'de hata mesajı görünür, sade hata metinleri, TV odak hafızası, dizide kaldığın bölüm"
git push -u origin fix/genel-kontrol
```
**Main'e birleştirme yapma.** PR'ı Claude açar.

---

## Kullanıcının test edeceği
1. **Canlı TV tam ekran:** Açılmayan bir kanalda ekranda mesaj görünür; OK'a basınca kanal yeniden açılmayı dener.
2. **Kanal önizlemesi (TV canlı ekranı):** Açılmayan kanalda önizleme kutusunda mesaj görünür.
3. **Dizi detayı:** 3. sezondan bir bölüm izle, geri dön → 3. sezon açık kalır. "Devam et" düğmesi o bölümü açar.
   Sezon satırı ↔ bölüm listesi arasında ▲▼ ile gidip gelince seçili sezona/aynı bölüme dönülür.
4. **Arama:** Sonuç satırları arasında ▲▼ ile gidip gelince satırdaki aynı öğeye dönülür.
5. **Yanlış şifreyle liste ekleme:** Türkçe ve sade bir hata mesajı görünür.
