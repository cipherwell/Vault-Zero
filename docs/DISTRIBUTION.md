# Getting VaultZero (and keeping it updated) without Google

VaultZero is **not distributed through Google Play** and has no Google Play Services, Firebase or
proprietary dependency — that is a permanent design decision, not a temporary gap. Here is how to
install it and how updates work.

## 1. Direct APK from GitHub Releases (canonical)

1. Open the [latest release](https://github.com/cipherwell/Vault-Zero/releases/latest) and download
   `VaultZero-v<version>.apk`.
2. Verify it if you can (computer recommended):

   ```bash
   sha256sum VaultZero-v<version>.apk          # must match the release page
   apksigner verify --print-certs VaultZero-v<version>.apk
   # cert SHA-256: 6292a1fc630c200e7ec5342248ba7a6ae30a893fd882b2ad4188f699bbb607e7
   ```

3. Allow installs from the browser/file manager when Android asks. Android's app scanner may warn
   about a build it has not seen before — the certificate check above is the real trust anchor.

There is **no auto-update** on this path: repeat for each new release. Your vault (and language
setting) survive an update as long as the package stays `com.cipherwell.vaultzero`.

## 2. Obtainium (automatic updates, no Google account)

[Obtainium](https://github.com/ImranR98/Obtainium) is a FOSS Android app that watches GitHub
Releases and offers updates — the closest privacy-respecting equivalent of a store update, without
Google Play or an account.

1. Install Obtainium from its own GitHub releases.
2. Add app → `https://github.com/cipherwell/Vault-Zero`.
3. Obtainium checks the release page and prompts when a new APK is available; it verifies the
   download against the release.

## 3. F-Droid

- **Main F-Droid repository:** VaultZero is prepared (`fdroid/com.cipherwell.vaultzero.yml`,
  reproducible builds) and can be submitted after the three-month public-history window
  (earliest ~21 January 2027). F-Droid builds the app itself from source with its own key.
- **Self-hosted F-Droid repository (optional):** `fdroid/README.md` documents how to publish this
  repo's own signed index (for example on GitHub Pages) so testers can add the repo URL to
  F-Droid/Neo Store/Droid-ify and receive updates signed by the Cipherwell key, with zero Google
  infrastructure. If we do this, the repo URL and its signing certificate will be published in the
  release notes.

## Why no Play Store

- Play distribution requires a Google account, Play Console and Play Services on the device track in
  some configurations; it also implies Google holding an app-signing key and a review pipeline.
- The APK intentionally has **no `INTERNET` permission** and no analytics; the distribution channel
  should be consistent with that posture.
- The trade-off is manual updates unless you use Obtainium or an F-Droid repository — documented
  above so testers can choose.

## Signing / update integrity

All official builds are signed with the Cipherwell upload key (certificate SHA-256 above). A new
build with a different certificate is **not** an update — do not install it. Release hashes are
published on every release page and in [`releases/`](../releases/README.md).
