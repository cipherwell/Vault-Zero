# Store listing pack (F-Droid / fastlane)

> **Google Play is intentionally not used.** `PLAY_LISTING.md` and the data-safety answers are
> kept only as an archive; nothing in the release process depends on them. See
> [docs/DISTRIBUTION.md](../docs/DISTRIBUTION.md) for how the app actually ships.

Everything needed to fill the store listing lives here or in `fastlane/`. No app code depends on
this directory.

## Assets

| File | Spec |
| --- | --- |
| `play-icon-512.png` | 512×512 32-bit PNG app icon (also in `fastlane/metadata/.../images/icon.png`) |
| `play-feature-graphic-1024x500.png` | 1024×500 feature graphic (also `images/featureGraphic.png`) |
| `../fastlane/metadata/android/en-US/images/phoneScreenshots/1..6.png` | 1080×2400 screenshots |
| `../fastlane/metadata/android/en-US/title.txt` | App name (`VaultZero`, ≤30 chars) |
| `../fastlane/metadata/android/en-US/short_description.txt` | ≤80 chars |
| `../fastlane/metadata/android/en-US/full_description.txt` | Long description |
| `../fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` | Release notes (≤500 chars) |

## Listing documents

- [`PLAY_LISTING.md`](PLAY_LISTING.md) — copy for the Play listing, content rating hints,
  declaration answers, upload-key notes and the step-by-step Play Console checklist.
- [`DATA_SAFETY.md`](DATA_SAFETY.md) — exact Data safety answers (no collection/sharing).

## Updating the pack for a release

1. Bump `versionCode` and add `fastlane/.../changelogs/<code>.txt`.
2. Refresh screenshots if the UI changed (see `../docs/SCREENSHOTS.md`; use a dev build with
   `-PallowScreenshots=true`, never a release build).
3. Regenerate the icon/feature graphic from `../store/` sources if branding changes.
4. Keep claims factual: Argon2id 64 MiB/t=3 (PBKDF2 600k legacy), 6 record types, offline/no network permission, hardware
   biometrics, SAF-only backups. Do not add “military grade” or unverified audit claims.
5. Re-run the release checklist in [`../docs/RELEASING.md`](../docs/RELEASING.md).

## Permanent decisions (before first Play upload)

- `applicationId` is immutable once published. Current: `com.cipherwell.vaultzero`.
- Contact email is required by Play (add the maintainer's address in the Play Console; it is not
  stored in this repository).
- Privacy policy URL: enable GitHub Pages for `main` → `/docs`; then
  `https://cipherwell.github.io/Vault-Zero/privacy.html`.
