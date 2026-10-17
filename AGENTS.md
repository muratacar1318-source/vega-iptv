# Vega IPTV — Geliştirme Kuralları
## Çalışma düzeni
- Her iş için main'den yeni bir branch aç (fix/..., feat/..., chore/...). main'e doğrudan commit ATMA.
- İş bitince ./gradlew assembleDebug ile derlendiğini doğrula, branch'e push et.
- main'e merge ETME; birleştirme kullanıcının onayıyla yapılır.
- Görev dışındaki dosyalara dokunma; refactor, biçimlendirme, import düzenlemesi yapma.
## Asla dokunulmayacaklar
- data/update/AutoUpdateManager.kt içindeki güncelleme mantığı (adresler, sürüm karşılaştırma,
  indirme, kurulum, erteleme). Yalnızca kullanıcıya görünen metinler değiştirilebilir.
- build.gradle.kts içindeki imza (signingConfigs) ayarları, keystore.properties, *.jks dosyaları.
  keystore.properties ve .jks dosyaları ASLA commit edilmez.
- .github/workflows/ altındaki dosyalar (kullanıcı istemedikçe).
## Arayüz kuralları
- Kullanıcıya görünen hiçbir metinde "GitHub", URL veya sunucu adresi geçmez.
- Hata mesajlarında e.localizedMessage / e.message kullanıcıya gösterilmez; genel Türkçe mesaj
  gösterilir, ayrıntı Log.w ile loglanır.
- TV (LocalIsTvDevice) davranışını etkileyen değişikliklerde TV yolu ayrıca kontrol edilir.
## Sürüm (release) kuralları
- Release sadece kullanıcı açıkça "release yap" dediğinde yayınlanır.
- versionCode her release'de 1 artar.
- Release açıklaması (GitHub Release body) uygulamadaki güncelleme penceresinde kullanıcıya
  AYNEN gösterilir. Bu yüzden:
  * Son kullanıcının anlayacağı sade Türkçe ile yazılır (ör. "Filmler ekranındaki çökme düzeltildi").
  * Kod/dosya/sınıf adı, teknik terim (LazyRow, PORTRAIT_CAT_NAV_CHIPS, D-Pad odak vb.),
    link, "GitHub" kelimesi ve "Full Changelog" satırı İÇERMEZ.
  * En fazla 6 madde, her madde tek satır.
- Teknik ayrıntılar istenirse docs/release-notes/ altına ayrı dosya olarak yazılır.
