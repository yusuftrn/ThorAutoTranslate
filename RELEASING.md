# Releasing Thor Auto Translate

The repository root contains the standalone Android project. Application ID and namespace: `com.thorautotranslate`. Current version: `1.0.1` (versionCode `2`). Preserve the original MIT license and [NOTICE.md](NOTICE.md).

## Installation identity

Camera Translator and earlier Thor development builds used `com.example.cameratranslator`. The independent application installs separately and does not migrate their settings, credentials or model files. Users must finish setup and enable Accessibility for the new app; disable the old service to avoid duplicate translation. Do not uninstall an existing app or erase its data as part of release preparation.

For future updates to `com.thorautotranslate`, keep the same application ID and release signing key and increase versionCode. Debug and release keys differ, so a debug installation cannot be updated in place by the release APK.

## Private signing

Private signing files live in ignored `signing/`. Back up both the keystore and its passwords outside this repository. Never upload them to GitHub, attach them to a release or include them in a source ZIP.

If a signing identity already exists, keep it. For a new signing identity only:

```sh
mkdir -p signing
keytool -genkeypair -keystore signing/thor-release.keystore \
  -alias thor-auto-translate -keyalg RSA -keysize 4096 -validity 10000
cp release.properties.example signing/release.properties
```

Edit the private properties file locally with the keystore path and passwords. Do not put passwords in command-line arguments or logs. Without this file, the release APK is unsigned and must not be offered as an installable release.

## Verify and package

Use the toolchain described in [README.md](README.md):

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleDebugAndroidTest :app:assembleRelease
# Use apksigner from your installed Android SDK build-tools:
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
mkdir -p artifacts
cp app/build/outputs/apk/release/app-release.apk artifacts/thor-auto-translate-1.0.1-release.apk
```

Confirm application ID `com.thorautotranslate`, version `1.0.1`/`2`, ARM64 support, non-debuggable release status and the expected signing certificate. Run device checks described in [TESTING.md](TESTING.md); compile/lint checks do not establish runtime correctness on two displays.

Review `git status --short --untracked-files=all` before the first commit. Signing files, local.properties, IDE/cache/build folders, APKs, downloaded model files, artifacts and the private game screenshot must remain excluded. Create a new empty GitHub repository for Thor Auto Translate and connect its URL as the origin remote; do not point it at the upstream Camera Translator repository.

After reviewing and committing the public files, create source from Git rather than zipping the working folder:

```sh
git archive --format=zip --prefix=ThorAutoTranslate-1.0.1/ \
  --output=artifacts/thor-auto-translate-1.0.1-source.zip HEAD
cd artifacts
shasum -a 256 thor-auto-translate-1.0.1-release.apk thor-auto-translate-1.0.1-source.zip > SHA256SUMS-1.0.1
```

Publish tag `v1.0.1` with the signed APK, source ZIP and `SHA256SUMS-1.0.1`. Release notes must mention the new package identity, fresh setup requirement, hardware support, translation modes and known limits. Source preparation and local builds do not create a public repository or release.
