# Releasing VaultZero

This is the authoritative release checklist. It assumes the production upload keystore exists
outside the repository.

## Signing identity

| Item | Value |
| --- | --- |
| Keystore | `~/.vaultzero-release/vaultzero-upload.jks` (alias `upload`, PKCS12, RSA-4096) |
| Credentials | `~/.vaultzero-release/keystore.env` (`chmod 600`) |
| Certificate SHA-256 | `c54ed94be9f5d4f00ea7a9e8d7f1f5eb4d0eafc8f456055b6130d85b38ae831d` |
| Signature scheme | APK Signature Scheme v2 (minSdk 24) |

**Back the keystore and password up offline (two copies) before the first store upload.** Never
commit them. Losing the key blocks updating an installed app; leaking it allows malicious updates.
The old `v1.0.0` preview was debug-signed and must never be used as a release identity.

## 1. Prepare the version

1. Bump `versionCode` (+1) and `versionName` (semver) in `app/build.gradle.kts`.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (≤500 chars for Play).
3. Update `fdroid/com.aistudio.vaultzero.secvlt.yml` (current version/code + new build entry with
   the git tag) and [releases/](../releases/README.md) with a new `v<version>.md`.
4. Update [CHANGELOG.md](../CHANGELOG.md) and any docs with version-specific statements.
5. Commit the release changes, then tag: `git tag -a v<version> -m "VaultZero v<version>"`.

## 2. Build

```bash
cd vault-zero-app
source ~/.vaultzero-release/keystore.env
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew --console=plain :app:testDebugUnitTest :app:lintDebug
./gradlew --console=plain :app:assembleRelease :app:bundleRelease
```

Outputs:
- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/bundle/release/app-release.aab`

The release signing config reads only `KEYSTORE_PATH`/`STORE_PASSWORD`/`KEY_PASSWORD`; it must never
fall back to `debug.keystore`.

## 3. Verify

```bash
APK=app/build/outputs/apk/release/app-release.apk
AAB=app/build/outputs/bundle/release/app-release.aab
APKSIGNER=$(ls $ANDROID_HOME/build-tools/*/apksigner | sort -V | tail -1)
AAPT2=$(ls $ANDROID_HOME/build-tools/*/aapt2 | sort -V | tail -1)

$APKSIGNER verify --print-certs --verbose "$APK"
$AAPT2 dump badging "$APK" | grep -E 'package:|targetSdk|application-debuggable'
$AAPT2 dump permissions "$APK"   # must NOT list INTERNET / ACCESS_NETWORK_STATE
sha256sum "$APK" "$AAB"
jarsigner -verify "$AAB"
```

Checklist:
- [ ] Certificate equals `C5:4E:D9:…:831D`; `Verifies` with v2.
- [ ] No `application-debuggable` line.
- [ ] No network permissions.
- [ ] `run-as <pkg>` fails ("package not debuggable") on device.
- [ ] `adb exec-out screencap -p` returns 0 bytes.
- [ ] `adb backup -noapk <pkg>` archive contains **no app-data entries** (47-byte empty header in
      the current release).
- [ ] Fresh install + setup + unlock; export/import; change password; biometric auto-prompt.
- [ ] Upgrade from the previous production build (same signing key) installs in place.
- [ ] `versionName`/`versionCode` match the tag.

## 4. Staging artifacts

Copy APK/AAB + checksums + notes to a local distribution directory (do not commit binaries):

```bash
VERSION=1.1.0
DIST=$HOME/VaultZero-Release/v$VERSION
mkdir -p "$DIST"
cp "$APK" "$DIST/VaultZero-v$VERSION.apk"
cp "$AAB" "$DIST/VaultZero-v$VERSION.aab"
cd "$DIST" && sha256sum VaultZero-v$VERSION.* > checksums
```

Update `releases/v<version>.md`, `releases/README.md`, `README.md` and the `.sha256` files in the
repo with the final sizes/digests. Commit the documentation.

## 5. Publish

### GitHub
Push `main` and the tag:

```bash
git push origin main
git push origin v<version>
```

Create the release (binary assets are not committed):

```bash
gh release create v<version> \
  --title "VaultZero v<version>" \
  --notes-file releases/v<version>.md \
  "$DIST/VaultZero-v$VERSION.apk" \
  "$DIST/VaultZero-v$VERSION.aab" \
  "$DIST"/VaultZero-v$VERSION.apk.sha256 \
  "$DIST"/VaultZero-v$VERSION.aab.sha256
```

The automated workflow (`.github/workflows/release-apk.yml`) can build and publish instead, but only
when configured:
- Repository secrets: `VAULTZERO_KEYSTORE_BASE64` (base64 of the JKS),
  `VAULTZERO_STORE_PASSWORD`, `VAULTZERO_KEY_PASSWORD`.
- Repository variable `RELEASE_ON_TAG=true` enables tag-triggered publishing; without it, use
  **Actions → Run workflow**. The workflow verifies the production certificate and refuses to run
  without the secrets; it never uses the debug keystore.

### Google Play
Follow [store/PLAY_LISTING.md](../store/PLAY_LISTING.md):
1. Create the app (decide the permanent `applicationId` first; it cannot be changed later).
2. Enrol in Play App Signing; keep the upload key safe.
3. Upload the AAB to internal testing.
4. Complete App content: privacy policy URL (GitHub Pages `/docs/privacy.html`), data safety, content
   rating, target audience, ads=No.
5. Fill listing assets/text from `store/` and `fastlane/`.
6. Test the internal build, then promote.

### F-Droid
Push the tag, update `fdroid/com.aistudio.vaultzero.secvlt.yml` (build entry/tag), and open a merge
request to `fdroiddata` (see [fdroid/README.md](../fdroid/README.md)). F-Droid rebuilds and signs
with its own key; the recipe removes `debug.keystore` from the source scan. `AutoUpdateMode` uses
tags, so every release needs a matching tag.

## 6. Never

- Commit keystores, `keystore.env`, APK/AAB binaries, or passwords.
- Sign a release with `debug.keystore` or the old preview key.
- Reuse a `versionCode` or push a tag whose versionName differs.
- Enable tag auto-publishing before the signing secrets are verified.
