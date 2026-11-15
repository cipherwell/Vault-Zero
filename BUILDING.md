# Building VaultZero locally

The repository intentionally contains no signing material and previously no Gradle
wrapper binaries, so a clean clone could not build. This is the verified local
recipe (Arch/CachyOS, JDK 17, Android SDK 36).

## Requirements

| Tool | Version | Notes |
| --- | --- | --- |
| JDK | 17 (`/usr/lib/jvm/java-17-openjdk`) | Gradle 9.3.1 + AGP 9.1.1 toolchain |
| Gradle | 9.3.1 | provided by `./gradlew` (wrapper restored) |
| Android SDK platform | `platforms;android-36` + `platforms;android-36.1` | app uses `compileSdk 36 minorApiLevel 1` |
| Build tools | `build-tools;36.0.0` | |
| SDK licences | accepted | `sdkmanager --licenses` |

First-time SDK install:

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" \
  --install "platforms;android-36" "platforms;android-36.1" "build-tools;36.0.0"
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties   # git-ignored
```

## Debug signing key

`app/build.gradle.kts` points the `debugConfig` signing config at
`<rootDir>/debug.keystore`, which is git-ignored. Regenerate it once per clone:

```bash
keytool -genkeypair -v -keystore debug.keystore \
  -storepass android -keypass android \
  -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US"
```

Release builds read `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD` from the
environment and fall back to `my-upload-key.jks`; never commit either keystore.

## Commands

```bash
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:testDebugUnitTest      # JVM + Robolectric unit tests
./gradlew :app:lintDebug              # Android lint (currently 0 errors)
```

## Installing on the live emulator

The `dsh-plugin-avd-manager` harness plugin provisions a visible AVD named
`VaultZero-Live` (Android 14, `google_apis;x86_64`, KVM):

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.aistudio.vaultzero.secvlt/com.example.MainActivity
```

## Instrumented tests on the live emulator

`./gradlew :app:connectedDebugAndroidTest` **uninstalls both APKs when it
finishes**, which deletes the app's vault (SharedPreferences + SQLite). When the
device state matters, install and run the instrumentation by hand:

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest -PallowScreenshots=true
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.example.data.VaultSetupDeviceTest \
  com.aistudio.vaultzero.secvlt.test/androidx.test.runner.AndroidJUnitRunner
```

`VaultSetupDeviceTest` drives the real repository end to end (setup, wrong
password rejected, correct password unlocks at 600k) and intentionally leaves a
usable vault behind on the emulator. `VaultSetupOnlyDeviceTest` creates a clean
vault (master password `test1234`, no demo items) and is the one to use before
manual biometric testing on a physical phone.

On a **physical device** the hardware-backed biometric binding is proven by:

```bash
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.example.crypto.BiometricKeyBindingTest \
  com.aistudio.vaultzero.secvlt.test/androidx.test.runner.AndroidJUnitRunner
```

It checks the Keystore key is per-use auth-required, that `doFinal` is rejected
without a prompt (`Key user not authenticated` on Android 17 / Keystore2), and
that no enrolment can be persisted without an authenticated cipher. Manual
enrol/unlock on the lock screen exercises the `BiometricPrompt.CryptoObject` path.

## Screenshot protection and UI automation

`MainActivity` sets `FLAG_SECURE` on **every normal build**, so `adb screencap`
returns an empty image and automation cannot see the UI. For emulator-only visual
QA build an explicit opt-in variant:

```bash
./gradlew :app:assembleDebug -PallowScreenshots=true
```

`BuildConfig.ALLOW_SCREENSHOTS` is `false` unless that property is passed, so a
release/normal debug build keeps the protection.

## Auto-lock on a test device

Auto-lock is on for every normal build (60 s default, user-selectable 30 s–15 m).
While iterating on the emulator, build with the dev switch instead of changing
user settings:

```bash
./gradlew :app:assembleDebug -PdevDisableAutoLock=true
```

That sets `BuildConfig.DEV_DISABLE_AUTO_LOCK`, which makes the ViewModel treat
the timeout as `0` ("never lock") and replaces the Settings timeout chips with a
dev-build notice. Release builds never receive the flag.

## Building the production release

Production releases must be signed with the upload keystore (never the debug
keystore). The keystore and passwords live outside the repository:

```bash
source ~/.vaultzero-release/keystore.env
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew :app:assembleRelease :app:bundleRelease
```

Full release checklist, signing identity, store upload and F-Droid steps live in
[`RELEASING.md`](RELEASING.md). The certificate fingerprint is
`6292a1fc630c200e7ec5342248ba7a6ae30a893fd882b2ad4188f699bbb607e7`.

What to check afterwards:

- `apksigner verify --print-certs` reports the production certificate and APK
  Signature Scheme v2 (`Verifies`).
- `aapt2 dump badging` shows no `application-debuggable` line.
- `aapt2 dump permissions` must not contain `INTERNET`/`ACCESS_NETWORK_STATE`.
- Install on a clean device, create a vault, and confirm `screencap` returns
  0 bytes and `run-as` fails ("package not debuggable").
- `adb backup -noapk <pkg>` must produce an archive with no app-data entries
  (`allowBackup=false` on the non-debuggable build).
- The old `v1.0.0` preview is debug-signed; installing this build over it requires
  an uninstall first.

## Security posture

See `SECURITY.md` for the enforced controls, the verification for each one, and the
known gaps (per-use biometric key binding, JVM string lifetime, Argon2id option).
