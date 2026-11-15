# Troubleshooting

## Installation

**`INSTALL_FAILED_UPDATE_INCOMPATIBLE` / "App not installed" after downloading a new build**
The APK is signed with a different key than the installed app. The `v1.0.0` preview was
debug-signed; uninstall it first (export a backup if needed), then install `v1.1.0`. Never install
an APK re-signed by a third party.

**`INSTALL_FAILED_VERSION_DOWNGRADE`**
The installed build has a higher versionCode. Uninstall or install the same/newer version.

**Play Protect warning when sideloading**
Expected for sideloaded apps. Verify the SHA-256 and signing certificate against the release
notes before choosing "Install anyway".

## Lock/unlock

**Forgot master password**
Use the 12-word recovery kit ("Use Emergency Kit"). Without it there is no recovery path.

**Five wrong attempts → lockout**
Wait for the countdown (30 s); the cooldown is enforced even for a correct password until it
expires.

**Biometric prompt does not appear**
Check: biometric unlock enabled in Settings, fingerprints/face enrolled in system settings, and
the app was not re-enrolled (which invalidates the hardware key). Re-enable biometrics after
unlocking with the password. Emulators need an enrolled fingerprint; the button also allows manual
retry. After cancelling once, the prompt does not auto-repeat until the next lock/unlock.

**"Biometric key is no longer valid"**
Fingerprints changed or the lock screen was modified. Unlock with the master password and enable
biometric unlock again so a new hardware key is created.

## Vault/UI

**Search or filter shows unexpected results**
Filters are exclusive: selecting a category clears favourites (and vice versa). Use the back/close
chips or "Clear filters". `is:weak`/`is:reused` etc. use audit data and may exclude non-login
records.

**Vault list is empty after an import**
Imports may have been all duplicates, or the filter/search hides records. Clear filters; check the
import result dialog counts. A foreign backup needs that vault's password or recovery phrase.

**Editor closes/asks to discard**
The editor protects unsaved changes. Choose "Keep editing" or "Discard".

**2FA code is wrong**
The phone clock must be roughly correct (TOTP uses local time); check the secret is valid base32
(≥10 bytes). Codes are HMAC-SHA1/30 s/6 digits.

## Backups/import

**"Import rejected — nothing was written"**
The file failed verification (wrong format, tampered ciphertext, unreadable records, or wrong
credential). Nothing was written by design. Read the dialog/detail message.

**"recovery phrase checksum does not match"**
Recheck word order/spelling; the 12th word is a checksum. All words must be from the wordlist.

**"unsupported KDF iteration count"**
The backup was crafted with out-of-range PBKDF2 parameters; the app refuses it before doing KDF
work. Use an authentic backup.

**Import reports unreadable records**
The file is partially damaged. Confirm "Import readable records" to salvage authenticated
records, or obtain a clean backup.

**Backup is too large / import fails**
Imports are capped at 8 MiB and must be VaultZero JSON envelopes.

**Screenshot gives a black/empty image**
FLAG_SECURE is working. For automation only, build with `-PallowScreenshots=true`; never
distribute that build.

## Settings/security

**Auto-lock not working in a dev build**
Builds made with `-PdevDisableAutoLock=true` intentionally disable inactivity locking. Normal
release builds do not set this flag.

**Clipboard not cleared**
The app only clears content it copied; if another app overwrote the clipboard it leaves it alone.
With clipboard Off, the owned secret still clears when the vault locks.

**Background lock: file picker returns to a locked app**
The app exempts the SAF picker from background locking so exports/imports are not stranded. If the
vault locks for another reason during the trip, retry with the vault unlocked.

**Database migration error after upgrade**
Only production builds are supported. If the app crashes on first open after an upgrade from a
custom/debug build, export (if possible) and reinstall the production build; report the exact
version trail with logs that contain no secrets.

## Performance

**Import/export takes a few seconds**
KDF (600k PBKDF2) and decrypt/verify run off the UI thread by design; the UI shows progress and
disables duplicate actions while working. Large vaults take longer.

**App start feels slow after unlock**
PBKDF2 600k is intentionally expensive. Biometric unlock avoids repeated password KDF by using a
hardware-wrapped key.

## Getting help

Check [FAQ.md](FAQ.md), [GETTING-STARTED.md](GETTING-STARTED.md), then open a GitHub issue with
version/device/steps (no secrets). Security issues: private advisory per
[SECURITY.md](../SECURITY.md).
