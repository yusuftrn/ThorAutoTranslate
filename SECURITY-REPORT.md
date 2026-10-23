# Thor Auto Translate 1.0.1 — security review

Reviewed on 23 October 2026. This is a scoped source/APK review, not an independent security certification or penetration test. The original Camera Translator antivirus report applies to a different APK and is not evidence for this release.

## Artifact identity

| Property | Verified value |
| --- | --- |
| Application ID | `com.thorautotranslate` |
| Version | `1.0.1` (versionCode `2`) |
| APK | `thor-auto-translate-1.0.1-release.apk` |
| SHA-256 | `d0f3035059b53ffa13d44802ece897a28dc377665e054f955e101c1e52048148` |
| Signing certificate SHA-256 | `86a276451fc0ea80969b85aae09e1a5fcd1d9f50b479f01bc092ab862138b600` |
| Signature | apksigner verification passed; APK Signature Scheme v2 |
| Runtime | Android 11+ (API 30), target API 36, ARM64 |

The release APK's DEX files, Android manifest, resources and native libraries match the current local release build. Its signed bytes retain the hash above. Future APK rebuilds must receive their own hash and verification.

## Checks and results

- **Release hardening:** The merged release manifest is non-debuggable and does not enable cleartext HTTP. First-party network calls use fixed Google HTTPS endpoints with the normal OkHttp TLS/hostname verification. No custom trust-all TLS handler or HTTP logging interceptor was found.
- **Permissions/components:** The merged APK requests Internet, network-state access and its own signature-protected dynamic-receiver permission. There is no camera, microphone, broad storage or package-install permission. Accessibility binding is protected by `BIND_ACCESSIBILITY_SERVICE`; ML Kit/initialization providers are not exported. The exported AndroidX profile receiver is protected by `android.permission.DUMP`. The launcher activity is intentionally exported.
- **Consent and scope:** The service checks completed onboarding, affirmative screen consent, the enabled switch, lock/display state and the selected foreground application. It stops/cancels the session on exit or preference changes. Source review found no input automation, game-file modification or permission bypass. Foreground detection is sampled; this review does not establish frame-perfect exclusion of other content during app transitions.
- **Images/history:** OCR images and translation history stay in process memory. Source review found no screenshot upload, permanent screenshot output or persistent translation history in production code. Online translation sends recognized text to Google as documented in [PRIVACY.md](PRIVACY.md).
- **User API key:** Stored in Android private `MODE_PRIVATE` preferences, masked in the settings UI, and sent in an HTTPS header to the fixed Cloud Translation endpoint. It is not embedded in the APK. Settings/API-key preferences are excluded from both cloud backup and device transfer. This is sandbox protection, not application-level encryption: root access, device compromise or runtime instrumentation can expose the key. Use a restricted Google Cloud key and appropriate quotas.
- **Source/APK secrets and private files:** Credential-pattern checks found no matching Google API key, GitHub token or private-key marker in tracked source or APK DEX files. No private signing files, game calibration screenshot, GGUF models or retired llama.cpp runtime are included in the release APK. Pattern matching cannot prove absence of every possible secret.
- **Known dependency advisories:** The release runtime graph yielded 154 unique selected Maven name/version entries, including BOM/constraint metadata. A live [OSV query](https://osv.dev/) returned no advisory matches for those coordinates on the review date. The [saved queries/results](docs/security/1.0.1/osv-runtime.json) record the scope. This does not cover build tools, unindexed advisories, unknown vulnerabilities or a separate native-library vulnerability audit.
- **Regression checks:** The current build has 26 passing JVM tests and release lint reports 0 errors / 44 warnings. Earlier device checks for this implementation are described with their limits in [TESTING.md](TESTING.md); device tests were not rerun as part of this security review.

Machine-readable APK checks and signature output: [apk-checks.json](docs/security/1.0.1/apk-checks.json), [apk-signature.txt](docs/security/1.0.1/apk-signature.txt).

## External malware analysis

The exact signed APK above was submitted to VirusTotal with the maintainer's approval. The completed public report displayed **0 / 67 detections**, with “No security vendors flagged this file as malicious.” Kaspersky was among the engines reporting “Undetected.” Observed analysis time: 23 October 2026, 20:27:58 CEST (Europe/Stockholm).

[View the VirusTotal report for this APK](https://www.virustotal.com/gui/file/d0f3035059b53ffa13d44802ece897a28dc377665e054f955e101c1e52048148). The SHA-256 shown by VirusTotal matches the release APK. This records the visible result at review time; engine results can change. “Undetected” is not a security certification or proof that all vulnerabilities are absent.

## Remaining limits

No rooted-device assessment, hostile-network interception, runtime penetration test, comprehensive SDK telemetry capture or independent code audit was performed. Accessibility can read sensitive foreground content; enable only the intended games/apps and prefer on-device translation when text must remain local. ML Kit can send SDK diagnostics/usage metrics, as disclosed in PRIVACY.md. The keyless Google endpoint is unofficial and can change or throttle.

The scoped checks did not establish a release-blocking security defect. They do not guarantee that the APK is malware-free or that all vulnerabilities are absent.
