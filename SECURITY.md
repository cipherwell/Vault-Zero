# Security posture — VaultZero

This file records what the app actually enforces, how each claim was verified, and
what is deliberately **out of scope** for now. Claims without a verification line
do not belong here.

## Threat model

In scope: a lost/stolen or borrowed device, another app on the device, a malicious
overlay, logcat/screenshot capture, cloud backup extraction, and an attacker who
gets the vault files but not the master password.

Out of scope: a rooted or physically instrumented device while unlocked, RAM
scraping of the live process, a compromised OS, and hardware-level attacks on the
Keystore/StrongBox (which are delegated to the platform).

## Enforced controls

| Area | Control | Verified by |
| --- | --- | --- |
| Network | No `INTERNET` / `ACCESS_NETWORK_STATE` permission; no networking libraries; `networkSecurityConfig` refuses cleartext; `usesCleartextTraffic=false` | `OfflineSecurityTest` on `emulator-5554` (permission checks), dependency list |
| Backup path | Encrypted envelope can leave the app **only** through a user-picked SAF document (`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`); no clipboard, no share sheet, no persistable grant, 8 MiB import cap | live AVD run (picker → file → restore), `BackupFileIo` |
| Backup contents | AES-256-GCM envelope; titles/categories/tags redacted to `[ENCRYPTED]`; 0 plaintext secrets or titles in the exported file | `VaultRepositorySecurityTest.exported backup envelope leaks no plaintext...`, live file inspection |
| Data at rest | Per-record AES-256-GCM with per-record AAD binding (row swap rejected); random 256-bit VMK; SQLite metadata redacted | `VaultRepositorySecurityTest` (AAD swap, tamper, backup tamper) |
| Key derivation | PBKDF2-HMAC-SHA256, 600,000 iterations, 128-bit CSPRNG salts, separate password/recovery salts; legacy 100k vaults transparently re-wrapped with fresh salts on unlock (VMK unchanged, single atomic commit) | `VaultRepositoryKdfMigrationTest` (5 JVM) + `KdfMigrationDeviceTest` (2 on-device) |
| Recovery kit | 12-word phrase with SHA-256 checksum word; encrypted under the VMK; forged phrase rejected | `VaultRepositorySecurityTest`, `VaultCryptoTest` |
| Biometric key | Hardware-backed wrapping of the VMK with a **per-use authentication-required** Keystore key (`setUserAuthenticationRequired(true)`, `AUTH_BIOMETRIC_STRONG`, 0-second validity, `setInvalidatedByBiometricEnrollment(true)`, `setUnlockedDeviceRequired(true)`), StrongBox attempted with TEE fallback. Enrolment and unlock both pass a `BiometricPrompt.CryptoObject`; the wrapped key cannot be decrypted without the prompt-authorised cipher (no silent fallback path). | `BiometricKeyBindingTest` on a Pixel 9 Pro XL (Android 17 / API 37): key properties are auth-required/per-use, unauthenticated `doFinal` is rejected by Keystore2 (`Key user not authenticated`), no decrypt/enrolment material exists without the cipher; live enable + lock/unlock flows confirmed `isCrypto: true` prompts and a successful vault unlock |
| Screen capture | `FLAG_SECURE` on every normal build; Recent-Apps screenshot disabled on API 33+; dev builds opt in explicitly with `-PallowScreenshots=true` | manifest code, live `adb screencap` behaviour |
| Overlays / tapjacking | `decorView.filterTouchesWhenObscured = true` | code |
| Component surface | Only `MainActivity` is exported; AndroidX `ProfileInstallReceiver` removed from the merged manifest; no providers/services/receivers exported | `OfflineSecurityTest.onlyTheLauncherActivityIsExported`, merged manifest |
| Backup/transfer | `allowBackup=false`, Data Extraction rules exclude database/prefs/files for both cloud backup and device transfer | `res/xml/*` |
| Brute force | 5 failed unlocks engage a 30 s cooldown; the cooldown is checked before deriving keys | `VaultRepositorySecurityTest.five failed unlocks lock out...` |
| Audit integrity | Weak/reused/stale/malformed-TOTP rules tested directly; TOTP secrets below the RFC 4226 floor never render a code | `SecurityAuditEngineTest`, `VaultCryptoTest` |
| Release build | R8 shrink + obfuscate (UI classes renamed, `crypto`/`data`/`model` pinned for crash forensics), `debuggable=false` | release APK dex inspection + install/launch on AVD |
| Generator history | Session-only, in RAM, capped at 10 masked entries; never persisted and cleared on lock | `GeneratorHistory`, `VaultViewModel.lockVault` |
| Dev switches | `-PallowScreenshots=true` and `-PdevDisableAutoLock=true` exist only behind BuildConfig flags; release builds never set them | `BUILDING.md`, BuildConfig |

## Known gaps / next hardening steps

1. **Master password lives in a JVM `String`** (Compose `TextField`) and cannot be
   wiped from memory; only derived byte arrays are zeroed. A `CharArray`-based
   input path would reduce exposure but Compose makes this awkward.
2. **PBKDF2 vs memory-hard KDF.** 600k PBKDF2 is the platform-native choice without
   new dependencies; Argon2id would be stronger against GPU/ASIC cracking but adds
   a crypto dependency and a vault-format migration.
3. **SAF destination is user-controlled.** A user can export to a cloud-synced
   folder; the envelope stays encrypted (no plaintext), but the ciphertext leaves
   the device. This is the one intentional external surface.
4. **Biometric enrolment lifecycle** is handled conservatively: re-enrolling a
   fingerprint (or changing the lock screen) invalidates the Keystore key, so the
   user must unlock with the master password and re-enable biometrics once. There
   is no silent re-wrap; the failure path is a clear re-enrol prompt.
5. **Dev builds weaken two protections by design** (screenshots, auto-lock). They
   are BuildConfig-gated; never distribute a build made with those flags.
   The secured build installed on the personal test device (Pixel 9 Pro XL) was
   built without them and confirmed: `screencap` returns black, auto-lock restored.

## Verifying the claims

```bash
./gradlew :app:testDebugUnitTest            # 62 tests (crypto, KDF migration, security paths, audit rules)

# On real hardware (biometric binding cannot be proven on a JVM):
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.example.crypto.BiometricKeyBindingTest \
  com.aistudio.vaultzero.secvlt.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class com.example.OfflineSecurityTest \
  com.aistudio.vaultzero.secvlt.test/androidx.test.runner.AndroidJUnitRunner
```

See `BUILDING.md` for the hardened release build and for the manual
install/instrument workflow (the Gradle connected-test task uninstalls the app,
which deletes the vault).
