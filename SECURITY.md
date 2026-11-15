# VaultZero security policy

**Version:** 1.5.0 (`versionCode 7`, `com.cipherwell.vaultzero`) · **Last updated:** 15 November 2026

## Supported versions

| Version | Supported |
| --- | --- |
| `1.5.x` (Cipherwell package, release-signed) | ✅ security fixes |
| `1.4.x` and earlier (old package / signing key) | ❌ migrate via backup to 1.5.0 |

## Reporting a vulnerability

Please use GitHub's private security advisory flow:
<https://github.com/cipherwell/Vault-Zero/security/advisories/new>

Do **not** open a public issue for a suspected vulnerability. Include: affected version/build,
device/Android version, steps to reproduce, impact, and whether the vault was locked/unlocked.
You can expect an acknowledgement within a few days. This is a single-maintainer project;
fixes are prioritised by impact. Only the latest production version is supported.

## Security model in one paragraph

Records are encrypted with AES-256-GCM under a random 256-bit vault master key (VMK). Each
record's ciphertext is bound via Additional Authenticated Data to its record id and type
(`VaultZero_Record_v2:<id>:<type>`), so swapping or tampering with rows fails closed. The VMK is
wrapped twice: by a password-derived key and by a recovery-phrase-derived key, both derived with
PBKDF2-HMAC-SHA256 at 600,000 iterations with independent 128-bit salts. Titles, categories and
tags are encrypted inside the record envelope; the SQLite columns only carry `[ENCRYPTED]`
placeholders. Biometric unlock uses a hardware-backed Keystore key that requires a fresh,
per-use `BiometricPrompt` authorization and is invalidated when biometrics are re-enrolled. The
app has no network permission and no networking code.

Full algorithm details: [docs/CRYPTO-SPEC.md](docs/CRYPTO-SPEC.md).

## Controls and how they are verified

| Control | Implementation | Evidence |
| --- | --- | --- |
| Record encryption | AES-256-GCM, random 12-byte IV, 128-bit tag, AAD v2 binding | `VaultRepositorySecurityTest` (AAD swap/tamper), `VaultCryptoTest` |
| Key derivation | PBKDF2-HMAC-SHA256, 600,000 iterations, 16-byte salts; legacy 100k vaults upgrade atomically on unlock | `VaultCryptoTest`, `VaultRepositoryKdfMigrationTest`, `KdfMigrationDeviceTest` |
| Key wrapping | Random VMK wrapped by password KEK and recovery KEK; verifier is domain-separated HMAC, never a password hash | `VaultRepositorySecurityTest`, `VaultCryptoTest` |
| Recovery kit | BIP-39 12-word, 128-bit entropy, 2048-word standard, unique-word generation; legacy 136-word kits accepted; forged/reordered phrase rejected before writes; rotatable in Settings | `VaultCryptoTest`, `Bip39VectorsTest`, `VaultRepositoryRecoveryRotationTest` |
| Metadata redaction | Titles/categories/tags inside the encrypted envelope; SQLite columns `[ENCRYPTED]`; backup items redacted | `VaultRepositorySecurityTest`, live SQLite inspection |
| Biometric unlock | Keystore AES-GCM key, `setUserAuthenticationRequired(true)`, `AUTH_BIOMETRIC_STRONG`, 0-second validity, `setInvalidatedByBiometricEnrollment(true)`, StrongBox attempted; `BiometricPrompt.CryptoObject` for use | `BiometricKeyBindingTest` on Pixel 9 Pro XL (Android 17): 3/3; auth-required/per-use/invalidated, unauth `doFinal` rejected |
| Auto biometric prompt | On launch/resume when enabled; one attempt per lock; cancel falls back to password; still a CryptoObject prompt | phone run confirmed `isCrypto: true`, `StrengthRequested: 15` |
| Backup export | SAF only, no cloud API, no share sheet, 8 MiB cap; AES-GCM envelope; supports password **or** recovery phrase restore; optional phrase-only mode strips password envelope | `VaultRepositorySecurityTest`, `BackupFileIo`, live picker runs |
| First-run migration | Setup-screen restore: envelope/KDF/salt validation, recovery-phrase unwrap, every record AAD-authenticated, atomic DB replace + credential commit, refuses to overwrite an initialized vault; phrase preserved, original password optionally preserved | `VaultRepositorySetupRestoreTest` (6 tests), on-device migration run |
| Backup import | Verify-before-write: format/id/type/decryption checks; bounded KDF iterations (10k–600k), salts 16–64 B, key blobs ≤4 KiB; duplicates skipped by id or SHA-256 content fingerprint; same-id changed records update; atomic single Room transaction; optional explicit salvage of unreadable records | 105 JVM tests incl. hostile backup inputs; see [docs/BACKUP-FORMAT.md](docs/BACKUP-FORMAT.md) |
| Screen capture | `FLAG_SECURE` on every normal build; recents thumbnails disabled on API 33+; dev flag only for automation builds | release run: `screencap` returns 0 bytes |
| Clipboard | `EXTRA_IS_SENSITIVE`, ownership-aware clearing on timeout/lock; configurable Off still wipes on vault lock | Robolectric test + device runs |
| Background/auto lock | Configurable inactivity lock (30 s–15 m or Off); background lock (Off/15/30/60 s, default 30); `ACTION_SCREEN_OFF` locks immediately; SAF picker exempt | emulator: 35 s background → lock screen |
| Network | No `INTERNET`/`ACCESS_NETWORK_STATE` permission; no networking dependencies; release manifest inspected | `OfflineSecurityTest`, `aapt2 dump permissions` |
| Tapjacking | `filterTouchesWhenObscured = true` | code |
| Components | Only the launcher `MainActivity` exported; no services/receivers/providers exported; ProfileInstallReceiver stripped | merged manifest, `OfflineSecurityTest` |
| OS backup | `allowBackup=false`, data-extraction rules exclude DB/prefs; verified `adb backup` on the non-debuggable release contains no app data | release archive: 47-byte empty header |
| Signing | Production upload key only; CI refuses debug keystore; cert `62:92:A1:…:07E7`; APK Signature Scheme v2 | `apksigner verify`, [docs/RELEASING.md](docs/RELEASING.md) |
| Secrets in logs | No plaintext secrets logged; verified by logcat scan | pentest F-08 |

## Recovery-kit hardening and encryption audit (v1.3.0)

The original recovery generator used a 136-word list and sampled 11 words **with replacement**,
giving ≈78 bits of entropy and a 34% chance of a repeated word. v1.3.0 replaces generation with
BIP-39 English (2048 words, 128-bit entropy, unique words), retains legacy phrase validation, and
adds recovery-kit rotation. Full audit:
[`docs/AUDIT-2026-11-15-recovery-and-encryption.md`](docs/AUDIT-2026-11-15-recovery-and-encryption.md).

## Adversarial assessment

A self-directed pentest was performed on 15 Nov 2026 (debug build + release validation):
[`docs/PENTEST-2026-11-15.md`](docs/PENTEST-2026-11-15.md). One Medium finding (attacker-controlled
backup KDF cost / envelope sizes causing CPU-DoS) was fixed and regression-tested. The
debug-only `adb backup` exposure was closed by shipping non-debuggable release builds
(re-verified: 0 app-data entries). AAD tampering, brute-force cooldown, capture blocking, hostile
intents and log leakage all held.

## Known limitations / residual risks

0. **Recovery-kit entropy was ~78 bits before v1.3.0.** New kits use BIP-39 128-bit phrases and
   existing vaults can rotate via Settings → Generate New Recovery Kit. Backups made with legacy
   kits keep that lower entropy until a fresh backup is exported with a rotated kit.
1. **Master password exists as a JVM `String`** in Compose input; it cannot be wiped from memory.
2. **PBKDF2 is not memory-hard.** 600k iterations raises offline cracking cost but a weak master
   password remains guessable; use a long, unique passphrase.
3. **Device trust is assumed:** rooted/compromised OS, malicious keyboards/autofill/accessibility
   services can observe input or screen content; FLAG_SECURE cannot block accessibility APIs.
4. **Biometric lifecycle:** re-enrolling biometrics or changing the lock screen invalidates the
   Keystore key; the user must unlock with the master password and re-enable biometrics.
5. **Debug builds are debuggable** (`run-as` access). Only install production-signed builds;
   never distribute debug builds.
6. **Self-assessment only.** No independent audit yet; for high-value secrets, commission one
   before relying on the app.
7. Recovery is impossible without either the master password or the 12-word recovery phrase.
   Backups inherit that requirement.

## Release verification

```bash
sha256sum -c VaultZero-v1.5.0.apk.sha256
apksigner verify --print-certs VaultZero-v1.5.0.apk
# expected certificate SHA-256:
# 6292a1fc630c200e7ec5342248ba7a6ae30a893fd882b2ad4188f699bbb607e7
```

Verify the APK contains no network permission:

```bash
aapt2 dump permissions app-release.apk | grep -E 'INTERNET|ACCESS_NETWORK_STATE' || echo 'offline'
```

## Safe usage

- Use a long, unique master password; store the recovery kit offline, separately from backups.
- Prefer the current release from GitHub Releases (or Play/F-Droid when published); never install
  re-signed APKs.
- Keep the OS and device security patches current; disable USB debugging when not developing.
- Treat exported backups as sensitive ciphertext and keep them offline.
- Enable biometric unlock only on a device you trust; it is a convenience, not a second factor.
