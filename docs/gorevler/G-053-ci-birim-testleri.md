# G-053 — Otomatik derleme kontrolü birim testlerini de çalıştırsın

- **Öncelik:** 🟠
- **Agent dağılımı:** Tek agent.
- **Branch:** `chore/ci-birim-testleri` (main'den aç)
- **Commit mesajı:** `chore(ci): PR ve main derlemesinde birim testleri çalıştırılıyor`
- **Kullanıcı onayı:** Kullanıcı Faz 2'yi (bu görev dahil) başlattı; `.github/workflows/android-build.yml`'ye
  bu görevde **yalnızca aşağıdaki değişiklik** için dokunulabilir.
- **Değişecek dosyalar (SADECE bunlar):**
  1. `.github/workflows/android-build.yml`
  2. Bu görev dosyasının "Agent raporu" bölümü ve `YAPILACAKLAR.md` — sadece G-053 satırı

## Sorun
CI normal push/PR'da sadece `./gradlew compileDebugKotlin` çalıştırıyor. Projedeki birim testleri
(~15 dosya; G-048/G-050/G-051 ile eklenenler dahil) hiç koşmuyor; bir değişiklik testi bozsa da PR yeşil görünüyor.

## Çözüm
1. "Derle (kontrol) / APK üret" adımının `else` dalında `./gradlew compileDebugKotlin --stacktrace` yerine
   `./gradlew testDebugUnitTest --stacktrace` (bu komut derlemeyi de yapar). `workflow_dispatch` dalına dokunma.
2. Test başarısız olursa PR yorumunda görünsün: mevcut "Derleme sonucunu yorum olarak yaz" adımındaki hata süzgeci
   (`/^e: |error:|FAILURE|What went wrong|Exception/`) test hatalarını yakalamıyorsa `FAILED` ve
   `Test.*>.*FAILED` satırlarını da ekle. Başlık "Derleme başarısız" kalabilir; ek olarak test hatası varsa
   "(birim testi)" yaz.
3. Test raporunu sakla: başarısızlıkta `app/build/reports/tests/testDebugUnitTest/` klasörünü
   `actions/upload-artifact@v4` ile `if: failure()` koşuluyla, 7 gün tut.

## 🚫 DOKUNMA
- İmza dosyası oluşturma/temizleme adımları, `permissions`, `concurrency`, tetikleyiciler, `workflow_dispatch` dalı.
- Uygulama kodu ve testler.

## Kontrol
- Yerelde `./gradlew :app:testDebugUnitTest` (düşük öncelikle, pencere açmadan) → geçmeli.
- PR açınca CI'ın test adımını çalıştırdığını ve yorumun geldiğini PR sayfasında gör; rapora yaz.

## Agent raporu
Sonnet oturumunda workflow dosyasını düzenleme izni otomatik izin denetimince reddedildi. Kullanıcı 13 Kasım'da
açıkça onay verdi ("Evet, izin ver"); değişikliği Claude (Opus) yaptı.
- `else` dalı: `compileDebugKotlin` → `testDebugUnitTest` (derlemeyi de yapar). `workflow_dispatch` dalı aynen.
- PR yorumu: başarıda "Derleme ve testler başarılı"; test hatasında başlığa "(birim testi)" eklenir; hata süzgecine `FAILED` eklendi.
- Başarısızlıkta `app/build/reports/tests/testDebugUnitTest/` 7 gün saklanır (`birim-testi-raporu`).
- İmza adımları, izinler, tetikleyiciler, concurrency değişmedi.
