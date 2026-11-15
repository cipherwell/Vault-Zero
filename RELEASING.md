# Releasing VaultZero

## Signing identity (critical)

- Production upload keystore: `~/.vaultzero-release/vaultzero-upload.jks`
  (alias `upload`, RSA-4096, valid ~27 years).
- Credentials: `~/.vaultzero-release/keystore.env` (`chmod 600`).
- **Back up the keystore and password offline (two copies) before the first store
  upload.** Never commit them. Losing them prevents app updates; leaking them
  allows malicious updates.
- Certificate SHA-256:
  `c54ed94be9f5d4f00ea7a9e8d7f1f5eb4d0eafc8f456055b6130d85b38ae831d`
- The old preview (`v1.0.0`) was debug-signed and is intentionally not reuseable.
- Google Play: enrol in **Play App Signing**. The upload key above signs uploads;
  Google holds the app-signing key and can reset the upload key if needed.

## 1. Version bump

- `app/build.gradle.kts`: `versionCode` +1, `versionName` (semver).
- Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (≤500 chars
  for Play).
- Update `fdroid/com.aistudio.vaultzero.secvlt.yml` (current version/code + build
  entry with the new git tag) and `releases/v<version>.md`.
- Tag the release commit: `git tag -a v<version> -m "VaultZero v<version>"`.

## 2. Build (JDK 17 + SDK 36)

```bash
cd vault-zero-app
source ~/.vaultzero-release/keystore.env
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleRelease :app:bundleRelease
```

Outputs:
- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/bundle/release/app-release.aab`
- If `STORE_PASSWORD`/`KEY_PASSWORD`/`KEYSTORE_PATH` are unset the build fails
  (never fall back to the debug key for release).

## 3. Verify the artifacts

```bash
APK=app/build/outputs/apk/release/app-release.apk
AAB=app/build/outputs/bundle/release/app-release.aab
apksigner verify --print-certs --verbose "$APK"   # expect cert C5:4E:D9:... and v2+
aapt2 dump badging "$APK" | grep -E 'package:|targetSdk|application-label'
aapt2 dump permissions "$APK"                     # must NOT contain INTERNET
sha256sum "$APK" "$AAB"
jarsigner -verify "$AAB"
```

## 4. Device smoke test (release build, non-debuggable)

1. Uninstall any debug/preview build (signature mismatch otherwise).
2. Install the release APK; create a vault; lock/unlock.
3. Enable biometrics; verify the prompt appears automatically on next launch and
   `isCrypto: true` (real hardware).
4. Export a backup; re-import it (duplicate-safe), change the master password,
   verify the recovery phrase still works.
5. Confirm `adb exec-out screencap -p` returns 0 bytes and
   `adb shell run-as <pkg>` fails ("package not debuggable").
6. `adb backup -f /tmp/x.ab -noapk <pkg>` -> confirm on device -> archive must
   contain **0 app-data entries** (allowBackup=false).

## 5. Publish

### GitHub Actions signing secrets (required for tag releases)

The workflow `.github/workflows/release-apk.yml` refuses to build a release without
the production keystore. Add these repository secrets (Settings → Secrets and
variables → Actions):

| Secret | Value |
| --- | --- |
| `VAULTZERO_KEYSTORE_BASE64` | `base64 -w0 ~/.vaultzero-release/vaultzero-upload.jks` |
| `VAULTZERO_STORE_PASSWORD` | from `~/.vaultzero-release/keystore.env` |
| `VAULTZERO_KEY_PASSWORD` | from `~/.vaultzero-release/keystore.env` |

The workflow verifies the built APK certificate equals
`c54ed94be9f5d4f00ea7a9e8d7f1f5eb4d0eafc8f456055b6130d85b38ae831d` and fails
otherwise. It never falls back to `debug.keystore`.

- **GitHub Release:** attach `VaultZero-v<version>.apk`, optional `.aab`, the
  `.sha256` files and `v<version>.md`; create tag `v<version>`; paste notes.
- **Google Play:** follow `store/PLAY_LISTING.md`. Upload the AAB to internal
  testing, complete App content (privacy policy URL, data safety, rating), verify
  the listing from the `store/` assets, then promote.
- **F-Droid:** push the tag, then open a merge request to `fdroiddata` updating
  `fdroid/com.aistudio.vaultzero.secvlt.yml` (F-Droid rebuilds and signs with its
  own key). Enable GitHub Pages (`main` / `/docs`) so the privacy URL resolves.

## 6. Never do

- Commit keystores, `keystore.env`, AAB/APK binaries, or passwords.
- Sign a release with `debug.keystore` or the old preview key.
- Reuse a `versionCode`; Play rejects it and F-Droid auto-update breaks.
