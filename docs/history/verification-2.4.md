> Historical results for the old com.example.cameratranslator identity and 2.4.x builds. These results do not validate the renamed 2.5.0 application. Referenced artifacts are private local files, not repository contents.

# Verification — 2.4.1 / 23 October 2026

AYN Thor Android 13; display 0 upper, display 4 lower. Updated debug build installed without erasing existing user settings; signed release APK prepared separately.

- 18 JVM unit tests passed, including automatic activation, setup/pause gates and keeping explicitly opened settings for the current game activation.
- 9 device tests passed, final run 19.224 seconds: `artifacts/device-tests-2.4.1.txt`.
- End-to-end test starts with the app already visible on the lower screen, opens the synthetic game above and verifies automatic takeover. Launching the app during gameplay also yields to the panel. The panel settings button opens usable preferences, motor changes preserve the game activation identifier, and Return to translation restores the panel. Normal game exit removes it.
- Active HTTP cancellation and translator close from the main thread do not crash the service thread. Earlier OCR, online/offline translation, localization, consent and long passage checks still pass.
- Release lint: 0 errors, 42 warnings. Signed release built with versionCode 8, versionName 2.4.1.
- Accessibility binding renewed while preserving Game Assistant. Android lists Thor Auto Translate as **Açık** (On); Bound services contains Thor Auto Translate; Binding services and Crashed services are empty. Recorded in `artifacts/accessibility-2.4.1.txt` and `artifacts/thor-accessibility-2.4.1.png`. The original reported warning was not reproducible at the first inspection; the final visible Android state is healthy, rather than an app-only indicator.
- Test APK removed after verification. No permission or consent bypass is added to production code. Android force-stop/revocation remains controlled by the OS.

Release APK: `artifacts/thor-auto-translate-2.4.1-release.apk`
SHA-256: `02f1ea6e350e20d2251db5612d2feb1c087923eb0438816f22c4513ace887beb`

Long-duration device sleep/battery restriction and all Android firmware variants were not tested. A fresh release install is not done on the existing differently signed debug app; its data is preserved. No GitHub upload performed.

---

# Verification — 2.4.0 / 23 October 2026

Hardware: USB-connected AYN Thor, Android 13; upper display 0 (1920×1080), lower display 4 (1240×1080).

- Debug and signed release APKs built successfully. ARM64 Android 11+, target/compile SDK 36; versionCode 7, versionName 2.4.0. Release is non-debuggable; no user test activity is included.
- **16 JVM unit tests passed**: dialogue/HUD filtering, long OCR passages retain their ending, games/emulators, OCR stabilization/dropouts, source-ordered three-entry history, typewriter continuation and delayed duplicate handling, long-text chunking without word loss or split Unicode pairs.
- **8 device tests passed**, final run 17.981 seconds (`artifacts/device-tests-2.4.txt`):
  - First-run setup: empty Cloud key prevents continuation; on-device method needs no key; screen-reading consent is required; completed settings persist. This uses isolated test preferences and does not accept consent for the user.
  - All nine interface resource sets, formatted messages, full disclosure text and native adaptive icon.
  - Real Google online translation/cache and safe session close; real ML Kit offline model preparation/translation.
  - Uppercase bitmap OCR, static-frame reuse and invalidation on text/crop change.
  - Optional private RDR tutorial screenshot: instructions recognized, FPS excluded.
  - End-to-end upper screen OCR → actual Turkish translation → lower screen with three entries, font changes preserve history, settings/return button, long passage retains >400 characters and the lower panel scrolls, automatic closure when the game leaves.
- Release lint: **0 errors, 42 warnings**. Warnings include dependency updates, localization/style recommendations and deprecated Android APIs.
- Nine language resource files have matching key sets and format argument signatures. English, Turkish, German, Spanish, French, Portuguese, Japanese, Korean and Simplified Chinese are declared for Android per-app language selection.
- Native adaptive icon rendered on Thor: `branding/icon.png`; source artwork: `branding/icon.svg` and Android vectors.

Controlled screenshots: `artifacts/thor-history-ready-2.4.png`, `artifacts/thor-long-history-ready-2.4.png`, `artifacts/thor-long-history-end-2.4.png`. The last screenshot shows a forward scroll in a long synthetic passage; it is not a real game transcript or a claim that all three long entries fit simultaneously into one viewport. No maximum line count or ellipsis truncates any retained entry; the oldest entry expires only when a fourth separate translation is added.

A localization regression was found and fixed: the AccessibilityService display WindowManager must keep its original overlay token, while TextView resources use the localized configuration context. The final end-to-end test passed with localization enabled.

Earlier interrupted runs were invalidated by foreground changes during device use. The final controlled run passed after the window-token fix; only the final output is the verification artifact.

Release APK: `artifacts/thor-auto-translate-2.4.0-release.apk`.
SHA-256: `7444564aec887884cb6382ece97999a1b7cf68df9266c4c0986f00ab0cd60e2d`.
Release certificate SHA-256: `86a276451fc0ea80969b85aae09e1a5fcd1d9f50b479f01bc092ab862138b600`.

Thor keeps the updated debug-signed build so existing data is not erased. A production signing key was generated in private, ignored `signing/`; it is excluded from source and artifacts. The test APK was removed. The release APK signature is independently checked with apksigner. A fresh release install on this Thor was not performed because Android cannot replace the existing debug signature without uninstalling the app. No GitHub repository or release has been published.

Not exhaustively checked: Android 11/12 runtime language switching, all games/fonts, all nine languages by native speakers, Google Cloud with a real billable API key, fresh model download failures, long-run battery usage. The optional copyrighted calibration screenshot remains private and is omitted from the public source archive; that one test skips when the image is absent. Internet/translation-service availability can affect latency. Google ML Kit SDK metrics are disclosed separately from online translation text processing.
