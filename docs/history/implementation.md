> Historical development notes. Earlier package identity and upgrade-compatibility decisions below were superseded by the independent com.thorautotranslate application. See the root README and RELEASING documents for current instructions.

# Thor Auto Translate — uygulama planı ve kararlar

## Donanım doğrulaması (23 Ekim 2026)

USB üzerinden AYN Thor, Android 13 doğrulandı. Mevcut mantıksal ekranlar:
- Üst: kimlik 0, 1920 × 1080, 120 Hz.
- Alt: kimlik 4, 1240 × 1080, 60 Hz.

Ekran kimlikleri firmware/bağlantıya göre değişebilir; uygulama alt ekranı DisplayManager ile bulur, kimlik 1 varsaymaz. Ayarlardan iki ekran da değiştirilebilir.

Üretici: https://www.ayntec.com/products/ayn-thor
Android ekran yakalama API'si: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#takeScreenshot(int,java.util.concurrent.Executor,android.accessibilityservice.AccessibilityService.TakeScreenshotCallback)
OCR: https://developers.google.com/ml-kit/vision/text-recognition/v2/android

## Plan ve uygulama kapsamı

1. Kamera önizleme, zoom, pozlama, CameraX, kamera izinleri ve kamera arayüzünü kaldır.
2. Android'in bağladığı AccessibilityService ile ekran bazında ön plandaki uygulamayı izle. Oyun kategorisi ve bilinen emülatör paketlerini otomatik etkinleştir; diğer uygulamalar için kullanıcı seçimi sun.
3. Yalnızca üst ekranda etkinleştirilmiş uygulama varken ekran yakala. Alt ekrana odaklanılması üst ekranın oyun oturumunu durdurmaz. Kilitte, ekran kapanınca veya oyundan çıkınca paneli ve işlemleri kaldır.
4. Üst ekran bitmap'ini cihazdaki ML Kit OCR'a ver. İki karede sabit kalan satırlarla yazı animasyonlarını dengele; değişen HUD sayaçlarını ayrı tut; kısa boşluklarda geçmişi koru. Büyük harfleri ve Unicode metni koru.
5. Mevcut Google ücretsiz / Google Cloud / ML Kit seçeneklerini bağımsız kaynak ve hedef dile uyarla. Dil ve motor oturum başına sabit; önbellek bu oturuma ait. Geciken yanıtlar yeni oyun/diyaloğa yazamaz. Ağ istekleri iptal edilebilir ve zaman aşımı içerir.
6. Çeviriyi erişilebilirlik katmanı olarak alt ekranda göster. Üstteki oyunun odağını değiştirme. Alt ekranda ayarlar açılınca paneli gizle, durum sekmesinde canlı çeviriyi göster.
7. Birim testleri, bitmap OCR ve gerçek iki ekranlı cihazda OCR → çeviri → oyun kapanışı testi; APK ve kullanım belgesi teslimi.

## Kullanıcı verisi ve uyumluluk

- Mevcut applicationId korunur; mevcut kurulumun verisi silinmez. Eski İngilizce/Türkçe yön tercihi yeni dil alanlarına varsayılan olur.
- Kamera ayarları artık kullanılmaz. Önceki kamera odaklı ses/dublaj ve bilgi sayfaları kaldırıldı; bu sürüm metin çevirisine odaklanır.
- Ekran görüntüsü dosyaya yazılmaz. Çevrimiçi modlarda OCR metni Google'a gönderilir. Cloud API anahtarı Android uygulama özel alanında saklanır; tercihler yedeklemeye/cihaz aktarımına dahil edilmez.
- ARM64 Android 11+ gerekir; Thor Android 13 üzerinde test edilir. SDK 36, JDK 21, Gradle 9.4.1 ve AGP 9.2.1 kurulu geliştirme ortamıyla doğrulanır.

## Bilinen sınırlar

- Oyun kendini Android oyun kategorisinde bildirmezse ve bilinen emülatör listesinde değilse Uygulamalar sekmesinden bir kez seçilir. Yeni kurulan ve kendini oyun olarak bildiren uygulamalar otomatik tanınır.
- Sistem erişilebilirlik iznini kullanıcı bir kez açmalıdır. Android'in zorla durdurma, izni iptal etme veya üretici pil kısıtlamaları bu servisi de durdurabilir. Normal açılış/kapanış izlenir; cihaz açılışında servis bağlamasını Android yönetir.
- FLAG_SECURE/DRM içeriği okunamaz. Görsel efektler, küçük fontlar ve hızlı geçişler OCR doğruluğunu etkiler. 400 ms aralıklı okuma ve iki kare doğrulaması nedeniyle çeviri anlık olmayan kısa bir gecikmeyle gelir; ağ gecikmesi buna eklenir.
- Kaynak OCR: Latin, Japonca, Çince, Korece ve Devanagari. Hedef diller ML Kit'in desteklediği dillerden seçilir. Diğer alfabeler için kaynak OCR desteği eklemek gerekir.
- Çevrimdışı dil modelleri ilk kullanımda indirilir. Ücretsiz Google endpoint'i resmi SLA sunmaz; değişebilir veya sınırlanabilir. Google Cloud ücretli olabilir ve etkin Translation API anahtarı gerektirir.
- Çeviri paneli alt ekranı kaplar. Alt ekranı kendisi kullanan DS/3DS emülatörlerinde oyunun alt görüntüsü panelin altında kalır; otomatik oyun ekranı yerleşimi değiştirilmez.
- Test ekranı yalnızca androidTest APK'sındadır; kullanıcı APK'sına eklenmez.

## 2.3 — yalnız canlı çeviri

Kullanıcı isteğiyle GameLibrary, TranslationCatalog, TranslationPackParser ve LibraryScreen kaldırıldı. Servis başlangıcında kurulu oyun/ROM tarama, GitHub sorgusu veya paket indirmesi yoktur; çeviri doğrudan seçilen canlı motora gider. Uygulamalar ekranı yalnız otomatik açılışın hangi paketlerde etkin olduğunu belirler. Eski veritabanı okunmaz veya değiştirilmez.

Panel son üç çeviriyi kaynak sırasıyla tutar. Geç yanıt yeni metni değiştirmez. Bir uzun cümlenin ön eki olan yeterince uzun parça tamamlandığında aynı geçmiş girdisi güncellenir. Panel, aynı hedef çevirinin tekrarında satır sayısı azalmasını da güvenli işler. Dil/motor/oyun değişikliği oturumu sıfırlar; yazı boyutu değişikliği geçmişi koruyarak yalnız paneli oluşturur.

OCR son ekran bitmap'ini ve sonucunu bellekte tutar. Bitmap.sameAs tam piksel eşitliğinde OCR atlanır; crop tercihi veya bir piksel değişiminde yeniden okunur. Böylece aynı kareler tekrar iki kare diyalog doğrulamasına katılır; OCR çalışmaması yeni metnin erken kabul edilmesine yol açmaz. Ekran bütünü her değişen karede kontrol edilir; odaklı öğretici taramasında son başarılı crop öne alınır. Native işlem iptal edilemediğinden bitmap ve recognizer yalnız işlem tamamlandıktan sonra bırakılır.

Ağ bağlantı havuzu oturumlar arasında ortaktır; dispatcher ve iptal kapsamı her oturuma özeldir. Çeviri önbelleği oturum başına 128 metindir. Çevrimdışı model hazırlanması ilk OCR ile paralel başlar. En fazla üç çeviri isteği sürer; geciken eski istekler iptal edilir.

Ayarlar Canlı / Ayarlar / Uygulamalar olarak sadeleştirildi. Dil, font ve alt bölge seçimi öndedir; motor, API anahtarı ve ekran seçimi gelişmiş bölümündedir. Canlı ekranındaki Çeviriye dön düğmesi ayar etkinliğini kapatır; üst ekranın oyun odağı korunarak alt panel tekrar görünür.

## 2.4 — GitHub release preparation

All interface, onboarding, service-state and app error copy uses Android string resources in nine locales. Android 13 uses LocaleManager and a localeConfig; Android 11/12 use configuration contexts. The service WindowManager remains attached to its original display context so locale changes cannot discard the accessibility overlay token. UI and translation languages are independent.

Three-step onboarding persists language/method/screen/readability preferences. Cloud requires a non-empty key before advancing. Screen reading is gated by completed setup and affirmative screen-reading consent, including on upgrade from older versions. Re-running setup revokes that gate until completion. New installs default to the on-device engine. SDK diagnostics/usage metrics are disclosed separately.

History holds three separate translation entries, each without maxLines or ellipsize. Long content remains manually scrollable. OCR no longer rejects entire blocks above 1200 characters; translation requests split long passages at whitespace within bounded chunks, preserving Unicode surrogate pairs. Official, unmodified Google Translate attribution is displayed as a small fixed footer.

The old launcher assets were replaced with native two-screen vector artwork, an adaptive/monochrome icon and SVG/PNG branding previews. Release signing reads private ignored signing/release.properties; public source contains only a template. The existing debug device installation is preserved. Release APK, SHA-256, source archive, privacy documentation and signing/build instructions are prepared for GitHub distribution. No repository publication is implied.

## 2.4.1 — automatic activation and service lifecycle

Foreground game activations have a separate monotonically increasing identifier. Preference/engine resets do not create a new game activation. MainActivity automatically yields to the lower translation panel when a game starts or when launched normally during gameplay. A settings launch from the panel retains the activation identifier, allowing preferences to be edited until another actual game session begins. Paused translation and incomplete consent/setup do not auto-activate.

The panel has a small localized settings button. Accessibility reconnection cancels any previous monitor before creating one new monitor, clears old translation resources and unregisters old preference listeners. HTTP cancellation and translator shutdown run on the session dispatcher executor; TLS socket cleanup cannot run synchronously on the AccessibilityService main thread. Resource-close failures cannot abort the remainder of service cleanup. Android controls the actual binding and can still stop a force-stopped or permission-revoked app.
