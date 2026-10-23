# Verification — Thor Auto Translate 1.0.1

## Local checks — 23 October 2026

- 26 JVM tests passed. They cover dialogue confirmation, speculative local translation,
  bounded pending work, cancelled sessions, retry, chronological history, typewriter
  continuation, long passages, app activation and text chunking.
- Debug, instrumented-test and signed release APKs build for `com.thorautotranslate`.
  Version 1.0.1 / code 2, minSdk 30, targetSdk 36, ARM64.
- Release lint: 0 errors, 44 warnings. This is not a full code audit.
- The Qwen/GGUF catalog, download engine, JNI and llama.cpp build have been removed.
  APK inspection confirms no `thor-local`, llama.cpp assets or GGUF files.
- Upgrade cleanup only targets retired model requests, app-specific `models/` folders
  and the old model selection. API keys, chosen languages and ML Kit models remain.

## Device checks

The current suite has 16 tests, including synthetic OCR, optional real-game calibration,
background-only animation reuse, periodic full-screen discovery, bounded OCR attempts,
retired-model cleanup, no accessibility shortcut request, onboarding, network/offline
translation and both online/offline two-display lifecycle tests.

All 16 device tests have now passed across the initial run and targeted reruns. The first
run passed 14 tests; the two display lifecycle tests were blocked by the AYN dual-screen
control panel. After the panel was dismissed, both passed on the final debug build.
They verify automatic entry while settings are already open, ordinary app reopening,
explicit settings access, three complete history entries, a scrollable long passage,
and stopping the lower-screen panel when the upper-screen game leaves.

A separate rerun of all three translation-engine tests passed after the network timeout
change. The final debug build was installed without clearing user data; Android reports
Thor Auto Translate bound, no crashed accessibility services, and no shortcut assigned.
Retired experimental model folders are absent. The signed release APK also passes
signature and 16 KB alignment verification.

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class com.thorautotranslate.RetiredModelsCleanupTest,com.thorautotranslate.SettingsManagerTest,com.thorautotranslate.OnboardingTest,com.thorautotranslate.TranslationManagerTest,com.thorautotranslate.ScreenTranslationTest com.thorautotranslate.test/androidx.test.runner.AndroidJUnitRunner
```

Keep both screens clear of the AYN control panel while running UI tests. Tests restore
saved settings, locale and enabled accessibility services. Host verification must check
that the user's translator service is bound after instrumentation exits.

Device fixtures report `translation_method`, `live_latency_ms` (launch to translated
state) and `observed_to_result_ms` (first capture attempt for the text to confirmed result).
These are not a frame-perfect display presentation measurement. Compare the median and
slowest samples by method, with models already downloaded; measure FPS/power separately
in actual gameplay before claiming a performance gain.

The final run used an AYN Thor on Android 13, upper display 0 and lower display 4.
For the first four short English-to-Turkish fixtures per method, with ML Kit models
already present:

| Method | Launch to translated state (ms) | Median (ms) | First capture attempt to result median (ms) |
| --- | --- | --- | --- |
| ML Kit offline | 807, 705, 705, 712 | 708.5 | 382.5 |
| Keyless online | 805, 505, 705, 705 | 705 | 429.5 |

These four-sample synthetic checks establish working synchronization, not a reliable
ranking between methods or a measured speedup over the old build. The three later
fixtures per method deliberately use English-to-English to isolate long-text layout;
they are excluded from translation timing statistics. Actual gameplay FPS, battery use,
long-text translation latency and a matched before/after benchmark remain unmeasured.

The previous online synthetic samples were 604–1012 ms, median 808 ms, on the earlier
1.0.0 implementation. They are a historical reference, not a matched benchmark.

The optional copyrighted `game-tutorial.png` is excluded from public source and its
calibration test skips when absent. Synthetic fixtures are included. Historical reports
in `docs/history/` describe earlier package identities and implementations.
