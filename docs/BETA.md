# VaultZero beta — testers guide

Thanks for helping test VaultZero. This is a **beta of an offline, zero-knowledge password
manager**. The cryptography is tested, but the app has not yet had an independent security audit
and has seen limited real-device coverage. Please read the rules below before installing.

## Before you install — the one rule that matters

> **Use throwaway secrets only.** Create a vault with a test password and test records. Do **not**
> move your real password manager into VaultZero yet, and do not overwrite the only copy of an
> existing backup with a beta export.

VaultZero is offline by design: there is no account, no server and no "forgot password". If you
lose both your master password and your 12-word recovery kit, **nobody can recover the vault** — not
us, not anyone.

## What works (please test it)

- Create a vault, lock/unlock, auto-lock, biometric unlock (if your device has an enrolled
  fingerprint/face).
- Add, edit, delete, search, filter, favourite, group records; all six record types.
- Generate passwords/passphrases, check the audit screen, TOTP codes.
- Export an encrypted backup, import it, restore on a fresh install with the recovery phrase.
- Language switch: lock screen (before creating a vault) and Settings → Language
  (English US / Español US / System).

## Known limitations of this beta

| Area | Limitation |
| --- | --- |
| Distribution | APK from GitHub Releases; **no Play Store by design**. For automatic updates use [Obtainium](DISTRIBUTION.md) (watches this repo's releases). Manual installs work too — see [DISTRIBUTION.md](DISTRIBUTION.md). |
| Reporting | No crash reporting or telemetry (by design). Testers can't screenshot inside the app because `FLAG_SECURE` is on; use **Settings → Copy diagnostics** plus another phone/camera for pictures. |
| Device coverage | Tested on Android 14/17 emulator + Pixel 9 Pro XL (Android 17), where all 15 instrumentation tests pass including biometrics. Older Android versions (7–13) and some OEM skins may behave differently. |
| Unlock speed | Argon2id takes ~1.2 s on a flagship (Pixel 9 Pro XL) and up to a few seconds on older devices; biometric unlock skips the KDF. |
| Spanish | Translation is complete but not yet reviewed by a native speaker; wording can change. |
| Biometrics | Requires an enrolled fingerprint/face on the device; some OEMs report biometric state differently. |
| Migration | The app moved package ID in 1.5.0 (`com.aistudio.vaultzero.secvlt` → `com.cipherwell.vaultzero`). Data moves through an encrypted backup + recovery phrase, not an in-place update. |
| Audit | Self-audited and self-pentested only ([audit](AUDIT-2026-11-15-recovery-and-encryption.md), [pentest](PENTEST-2026-11-15.md)). An independent audit is still recommended before high-risk use. |

## Install

1. Download `VaultZero-v<version>.apk` from the
   [latest release](https://github.com/cipherwell/Vault-Zero/releases/latest).
2. Verify what you downloaded (Android can't run `sha256sum`; do it on a computer if you can):

   ```bash
   sha256sum VaultZero-v<version>.apk     # must match the release page
   apksigner verify --print-certs VaultZero-v<version>.apk
   # certificate SHA-256 must be 6292a1fc630c200e7ec5342248ba7a6ae30a893fd882b2ad4188f699bbb607e7
   ```

3. Allow installs from your browser/file manager when Android asks ("Install unknown apps").
   Android's app scanner may warn about a build it has not seen before — the certificate check
   above is what matters. VaultZero is not on Google Play, by design.
4. Launch, create a **test** vault, write down the 12-word recovery kit on paper.
5. Tell us the version you are on when you report anything (Settings footer shows it).

## Report a bug

Open an issue with the bug template: https://github.com/cipherwell/Vault-Zero/issues/new/choose

Include:

1. **Settings → Copy diagnostics** and paste it (contains version, device, Android, vault state and
   the last error — never passwords, titles or keys).
2. What you did, what you expected, what happened.
3. A screenshot taken with another device (the app blocks in-app screenshots on purpose) or a
   description of the screen.

Security issues: please **do not** open a public issue. See [SECURITY.md](../SECURITY.md) for the
private contact and the disclosure expectations.

## Update / uninstall

- New beta builds are announced on the project page; installing a newer APK over the same package
  keeps your vault (same signing key).
- Uninstalling removes the encrypted database, preferences and the hardware-backed Keystore keys.
  Export a backup first if you want to keep the data.
- If you want to start over: Settings → Reset vault (destroys the local vault), or uninstall/reinstall.

## Privacy

Everything stays on the device. The app has **no `INTERNET` permission**, no analytics, no crash
reporting, no accounts. The diagnostics text is only generated when you tap the button and only
goes where you paste it.
