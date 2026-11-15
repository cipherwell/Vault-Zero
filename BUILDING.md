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

## Screenshot protection and UI automation

`MainActivity` sets `FLAG_SECURE` on **every normal build**, so `adb screencap`
returns an empty image and automation cannot see the UI. For emulator-only visual
QA build an explicit opt-in variant:

```bash
./gradlew :app:assembleDebug -PallowScreenshots=true
```

`BuildConfig.ALLOW_SCREENSHOTS` is `false` unless that property is passed, so a
release/normal debug build keeps the protection.
