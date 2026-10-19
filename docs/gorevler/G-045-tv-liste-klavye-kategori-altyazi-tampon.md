# G-045 — TV kanal listesi, klavye, kategori odağı, altyazı, film/dizi tamponu

- **Öncelik:** 🔴
- **Agent dağılımı:** Tek agent (bölme). Gemini 3.6 Flash için. **Kod yazma yok** — Claude bütün değişiklikleri
  hazırlayıp denedi, tek dosyada (`docs/gorevler/G-045-degisiklikler.patch`) verdi. Agent sadece uygular, derler, gönderir.
- **Branch:** `feat/tv-liste-klavye-altyazi` (main'den aç)
- **Commit mesajı:** `feat(tv): kanal listesinde OK'sız geçiş, klavye OK ile açılır, kategori odağı, altyazı düzeltmesi, 180 sn film tamponu`

## Kullanıcının istekleri
1. **TV — sol kanal listesi:** Liste açılınca kanal değiştirmek için OK gerekmesin (▲▼ ile gezinince kanal açılsın).
   Kanal seçtikten sonra OK'a basınca liste kapansın, sadece kanal görünsün.
2. **TV — yazı kutuları:** Yazı kutusuna gelince klavye kendiliğinden açılmasın, OK'a basınca açılsın.
   (Şu an liste eklenemiyor; Geri'ye basınca klavye yine açılıyor.)
3. **TV — Filmler/Diziler:** Kategori seçilince imleç içeriklere geçsin. Sol tuşla kategorilere dönünce seçili kategoriye gelsin.
4. **Altyazı:** Türkçe dublaj seçiliyken altyazı kapalı olsun. Oynatıcıda altyazıyı kapatınca bir süre sonra
   Türkçe altyazı kendiliğinden geri açılıyor — bu olmasın.
5. **Film ve dizilerin varsayılan ön yüklemesi 180 sn** olsun.

## Claude'un bulduğu sebepler (bilgi için)
- (4) Oynatıcı 15 sn'de bir izleme konumunu kaydediyor. Her kayıtta "varsayılan ses/altyazı dili" ayarı yeniden uygulanıyor
  ve kapatılan Türkçe altyazıyı geri açıyordu. Artık sadece dil ayarı gerçekten değişince uygulanacak.
  Ayrıca adında "Dublaj" ya da "TR" geçen ses kanalları da Türkçe sayılacak.
- (5) 180 sn ön yükleme, yüksek kaliteli filmlerde cihaz belleğini aşıp oynatmayı bozabilirdi. Bu yüzden bellek sınırı eklendi
  (bellek dolunca süre dolmasa da yükleme durur) ve uygulamaya daha fazla bellek izni verildi (`largeHeap`).
- (2) Telefon/tablette hiçbir şey değişmez; sadece TV modunda çalışır.

## Değişecek dosyalar (yama bunları değiştirir — başka dosyaya dokunma)
- **Yeni dosya:** `app/src/main/java/com/tivimax/player/ui/components/TvTextFieldSupport.kt`
- `app/src/main/AndroidManifest.xml` (sadece `android:largeHeap="true"` satırı)
- `data/preferences/UserPreferences.kt`, `player/TiviPlayerManager.kt`, `ui/MainViewModel.kt`
- `ui/tv/TvPlayerOverlay.kt`, `ui/components/CategorySidebar.kt`, `ui/vod/VodScreen.kt`, `ui/catalog/VegaMediaCatalogScreen.kt`
- `ui/settings/SettingsScreen.kt`, `ui/settings/SettingsPlaylistsSection.kt`, `ui/login/LoginScreen.kt`
- `ui/search/UniversalSearchScreen.kt`, `ui/components/CategoryManagementDialog.kt`, `ui/components/MediaCategoryNavigationBar.kt`
- `ui/live/LiveTvScreen.kt`, `ui/live/shelf/SeeAllCategoryView.kt`, `ui/live/shelf/ShelfLiveLayout.kt`
- `ui/live/modern/CategorySelectorBottomSheet.kt`, `ui/live/modern/LiveTvTopBar.kt`
- `ui/player/PlayerScreen.kt`, `ui/player/components/TiviPlayerComponents.kt`
- (+ `YAPILACAKLAR.md` — sadece G-045 satırı)

Toplam **22** kod dosyası (21 değişen + 1 yeni).

## 🚫 DOKUNMA
- Yamada olmayan hiçbir değişiklik yapma. Yamayı "düzeltmeye" ya da "iyileştirmeye" çalışma.
- `AutoUpdateManager` ve güncelleme dosyaları, `keystore.properties`, `*.jks`.
- Yama dosyasının kendisini (`G-045-degisiklikler.patch`) değiştirme.

---

## Adım 1 — Dalı aç
```
git checkout main
git pull
git checkout -b feat/tv-liste-klavye-altyazi
```

## Adım 2 — Yamayı uygula (tek komut)
```
git apply --ignore-whitespace docs/gorevler/G-045-degisiklikler.patch
```
- Komut **hiçbir şey yazmadan** bitmeli.
- **Hata verirse:** hiçbir dosyayı elle değiştirme. `git checkout -- .` ile geri al, hata mesajını olduğu gibi
  `YAPILACAKLAR.md`'deki G-045 satırına yaz (`❌ Yama uygulanamadı: <mesaj>`), sadece o dosyayı commit'leyip gönder ve dur.

## Adım 3 — Kontrol
```
git status --short
```
- **21** satır ` M ...` ve **1** satır `?? app/src/main/java/com/tivimax/player/ui/components/TvTextFieldSupport.kt` görünmeli.
- `git diff --stat` son satırı yaklaşık: `21 files changed, 211 insertions(+), 61 deletions(-)` (yeni dosya hariç).

## Adım 4 — Derle ve test et
1. `./gradlew :app:assembleDebug` → hatasız bitmeli.
2. `./gradlew :app:testDebugUnitTest` → geçmeli.
3. Derleme hatası olursa: **kodu düzeltmeye çalışma.** Hata mesajının ilk 20 satırını `YAPILACAKLAR.md`'deki G-045
   satırına yaz (`❌ Derleme hatası: ...`), kod değişikliklerini **commit'leme** (`git checkout -- . && git clean -fd app/`),
   sadece `YAPILACAKLAR.md`'yi commit'leyip gönder ve dur. Claude düzeltip yeni yama verecek.

## Adım 5 — YAPILACAKLAR.md
Bekleyen görevler tablosunda G-045 satırını `🔄 Agent bitirdi — Claude kontrol edecek` yap. Başka satıra dokunma.

## Adım 6 — Gönder
```
git add -A app/src/main YAPILACAKLAR.md
git commit -m "feat(tv): kanal listesinde OK'sız geçiş, klavye OK ile açılır, kategori odağı, altyazı düzeltmesi, 180 sn film tamponu"
git push -u origin feat/tv-liste-klavye-altyazi
```
**Main'e birleştirme yapma.** PR'ı Claude açar.

---

## Kullanıcının test edeceği
1. **Canlı TV tam ekran → ◀:** Liste açılır, oynayan kanal seçili. ▲▼ ile gezin → yarım saniye durunca kanal arkada açılır.
   OK → liste kapanır, kanal tam ekranda kalır (yeniden yüklenmez).
2. **Ayarlar → Listeler → Yeni liste ekle:** Kutulara gelince klavye açılmaz; OK'a basınca açılır; Geri ile kapanır,
   ▼ ile sonraki kutuya geçilir. Liste kaydedilebilmeli.
3. **Filmler / Diziler:** Kategoriye OK → imleç afişlere geçer. ◀ ile geri gelince seçili kategori odaklı.
4. **Türkçe dublajlı film:** Altyazı kapalı başlar. İngilizce seste altyazıyı kapat → 1 dakika izle → kendiliğinden geri açılmaz.
5. **Ayarlar → Oynatıcı → Film ve dizi ön yükleme:** "180 sn (önerilen)" seçili görünür
   (daha önce elle başka değer seçtiysen o değer kalır).
