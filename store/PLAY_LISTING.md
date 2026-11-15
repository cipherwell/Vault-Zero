# Google Play listing pack — VaultZero 1.1.0

Assets in this folder / fastlane:
- `store/play-icon-512.png` — 512×512 32-bit PNG app icon
- `store/play-feature-graphic-1024x500.png` — feature graphic
- `fastlane/metadata/android/en-US/images/phoneScreenshots/1..6.png` — 1080×2400 screenshots
- `fastlane/metadata/android/en-US/{title,short_description,full_description}.txt`
- `fastlane/metadata/android/en-US/changelogs/2.txt` (≤500 chars)

## App details
- App name: **VaultZero** (≤30)
- Category: Tools
- Contact email: **cipherwell@proton.me** (required by Play)
- Website: `https://cipherwell.github.io/Vault-Zero/`
- Privacy policy URL: `https://cipherwell.github.io/Vault-Zero/privacy.html`
  (enable GitHub Pages for the `docs/` folder on `main`; the files are in `docs/`)
- License: MIT; source: https://github.com/cipherwell/Vault-Zero

## Data safety answers
- Does the app collect or share any required user data types? **No.**
- Is data encrypted in transit? N/A (the app has no network permission/code).
- Can users request data deletion? Users can uninstall the app (deletes local data) or delete individual records; the developer holds no data.
- No analytics, ads, trackers, crash reporting or in-app purchases.

## Content rating questionnaire hints
- No violence, sexual content, profanity, gambling, drugs, or user-generated public content.
- No user interaction / no social features / no ads.
- It is a utility that stores user-provided secrets locally.
- Recommended rating: Everyone / PEGI 3.

## Target audience
Adults and general users with basic app literacy; no child-directed design. No ads or purchases.

## App access
All functionality is local; no login/account is required. Reviewers can create a vault with any master password (no server/back end).

## Permissions declaration
- `USE_BIOMETRIC` (and legacy `USE_FINGERPRINT`) — optional biometric unlock.
- `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` — AndroidX-internal, app-scoped (not user-facing).
- No location, camera, microphone, contacts, storage, Internet, or notification permissions.

## Upload key / Play App Signing
- The release AAB is signed with the local production upload key
  (`~/.cipherwell-release/cipherwell-upload.jks`, alias `upload`).
- Back up that keystore offline **before** the first upload; leaking or losing it
  affects update capability. Enrol in Play App Signing so Google holds the app
  signing key and you keep the upload key.
- Signature certificate SHA-256:
  `6292a1fc630c200e7ec5342248ba7a6ae30a893fd882b2ad4188f699bbb607e7`

## Important release note
`v1.5.0` is the first release under the final Cipherwell package
(`com.cipherwell.vaultzero`). Older packages cannot update in place; users must export
an encrypted backup + recovery phrase, install v1.5.0, restore, and then uninstall the
old app. Play has no prior uploads, so this is a fresh first upload there.

## Play Console steps (after creating the app)
1. Enable GitHub Pages (`main` → `/docs`) so the privacy URL resolves.
2. Create the app; complete App content (privacy policy, data safety, content
   rating, target audience, ads = no).
3. Upload `VaultZero-v1.5.0.aab` to the internal testing track first.
4. Add store listing assets/text from this folder.
5. Install from internal testing on a device and complete the checklist in
   `../docs/RELEASING.md` before promoting to production.
