# Building VaultZero

## Requirements

| Tool | Version |
| --- | --- |
| JDK | **17** (AGP 9.1.1 toolchain; JDK 21+ without javac may fail) |
| Android SDK | platform **36**, build-tools 36.x |
| Gradle | wrapper included (9.3.1) |
| Device/emulator | Android 7.0+ (API 24); biometric tests need enrolled biometrics/API 30+ |

Example environment:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export ANDROID_HOME=$HOME/Android/Sdk
```

## Clone and build

```bash
git clone https://github.com/cipherwell/Vault-Zero.git
cd Vault-Zero
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Debug builds require a local debug keystore because the project follows the AI Studio signing
layout (`signingConfigs.debugConfig` points at `debug.keystore`). It is not committed. Generate one:

```bash
keytool -genkeypair -v -keystore debug.keystore -storepass android -keypass android \
  -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US"
```

## Common tasks

| Task | Command |
| --- | --- |
| Debug APK | `./gradlew :app:assembleDebug` |
| Unit tests (90) | `./gradlew :app:testDebugUnitTest` |
| Lint | `./gradlew :app:lintDebug` (release: `lintVitalRelease` runs on assemble) |
| Instrumented APK | `./gradlew :app:assembleDebugAndroidTest` |
| Release APK + AAB | `./gradlew :app:assembleRelease :app:bundleRelease` (requires signing env) |
| Clean | `./gradlew clean` |

Reports: `app/build/reports/tests/testDebugUnitTest/index.html`,
`app/build/reports/lint-results-debug.html`.

## Debug/dev flags (never for distribution)

| Flag | Effect |
| --- | --- |
| `-PallowScreenshots=true` | Disables FLAG_SECURE for emulator automation (`BuildConfig.ALLOW_SCREENSHOTS`) |
| `-PdevDisableAutoLock=true` | Disables inactivity auto-lock and shows a dev notice in Settings |

Both are `buildConfigField`s and default to `false`; release builds never pass them. The app still
supports `FLAG_SECURE` hardening in normal debug builds used for manual QA.

## Device / emulator testing

`connectedDebugAndroidTest` **uninstalls the app** (and the test APK) when finished, deleting the
vault and biometric preferences. When device state matters, install and invoke manually:

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest -PallowScreenshots=true
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.example.data.VaultSetupDeviceTest \
  com.aistudio.vaultzero.secvlt.test/androidx.test.runner.AndroidJUnitRunner
```

`VaultSetupDeviceTest` seeds a demo vault (destructive); `VaultSetupOnlyDeviceTest` creates a
clean `test1234` vault without demo items for manual flows. The full test inventory and QA
checklist are in [TESTING.md](TESTING.md).

Emulator notes:
- Hardware acceleration (KVM) is required for usable performance.
- Enrol a fingerprint in the emulator's own Settings if you want to exercise biometric code; use
  `adb emu finger touch 1` to satisfy a prompt.
- Use a debug build with `-PallowScreenshots=true` for screenshots; release builds block capture.

## Release builds

Release builds are minified/obfuscated by R8 and signed with the production upload key from
environment variables (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`). Build, verification and
publishing steps are in [RELEASING.md](RELEASING.md). Never sign a release with `debug.keystore`.

## Troubleshooting the build

- **`Toolchain installation does not provide JAVA_COMPILER`** — point `JAVA_HOME` at JDK 17.
- **SDK licenses/versions** — install platform 36 and build-tools via `sdkmanager`; ensure
  `ANDROID_HOME` points at the SDK root.
- **`INSTALL_FAILED_UPDATE_INCOMPATIBLE`** — the installed app has a different signing key;
  uninstall it first (export a backup).
- **Room migration validation crash** — only expected if schema/migration are out of sync; see
  [ARCHITECTURE.md](ARCHITECTURE.md#database-schema-room-v2).
- **Slow first Gradle build** — dependency resolution/Kotlin/Compose compilation; subsequent builds
  are incremental. The project works offline once dependencies are cached.
