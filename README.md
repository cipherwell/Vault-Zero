# VaultZero — Zero-Knowledge Offline-First Cryptographic Vault for Android

[![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024%2B)-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0%2B-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20(Material%203)-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Encryption](https://img.shields.io/badge/Encryption-AES--256--GCM%20%2B%20PBKDF2%20(600k)-10B981)](#cryptographic-architecture)
[![Network Permissions](https://img.shields.io/badge/Permissions-Zero%20Internet%20%2F%20Air--Gapped-F59E0B)](#zero-knowledge--air-gapped-guarantees)

**VaultZero** is a zero-knowledge, air-gapped password, TOTP authenticator, payment card, SSH/API secret, and cryptographic seed vault for Android. Built with 100% Kotlin and Jetpack Compose, VaultZero operates strictly offline with **zero network permissions** (`INTERNET` permission is completely omitted from `AndroidManifest.xml`), ensuring your secrets never leave your device unencrypted.

---

### Search syntax

The vault search accepts plain terms plus narrowers:

```
type:login | card | api | crypto | note | identity
is:fav            (favorites only)
tag:work          (records carrying that tag)
```

Tokens combine, e.g. `type:login netflix` or `is:fav type:card`. Unknown
`prefix:value` text is treated as a plain term. Queries are debounced (140 ms).

### Vault ergonomics

- Long-press any row (or use the ⋮ menu → "Select items") for multi-select and
  bulk delete, with a confirmation step.
- ⋮ menu → "Group by type" renders section headers with per-type counts.
- Sort: Favorites first / Title A–Z / Recently updated.
- Generator keeps a **session-only** history (10 entries, RAM only, masked in the
  UI, cleared automatically when the vault locks).
- Audit findings link straight into the editor with the offending field focused
  (password for weak/reused/stale, 2FA secret for malformed TOTP).

### Design language — One UI inspired

The interface follows Samsung One UI (7–8.5 era): two grounds only (#000000 dark,
#FAFAFA light), a single blue accent, generously rounded (12–30dp) containers,
grouped list cards with hairline dividers, thumb-reach controls (floating pill
navigation, header actions) and a system-font type scale. Settings has a
System / Light / Dark appearance selector. Full token reference and the sources
studied: [`docs/DESIGN-one-ui.md`](docs/DESIGN-one-ui.md).

> **Interface verification:** every screen and interactive flow was driven on the
> live emulator (unlock, search, filters, favorites, item detail/edit/create/delete,
> generator + save-to-vault, audit, recovery kit, settings, lock). Logic that can be
> asserted without pixels is covered by the 47 JVM tests.

> **Security posture:** see [`SECURITY.md`](SECURITY.md) for the enforced controls,
> how each is verified, and the known gaps. Short version: no network permission of
> any kind, encrypted backups leave the app only through a Storage Access Framework
> document you pick yourself, and nothing but the launcher is exported.


## 📲 Download & Installation

Production builds (R8-shrunk, release-signed, no debug key) are published on [**GitHub Releases**](https://github.com/cipherwell/Vault-Zero/releases). The CI workflow signs tagged releases only when the production keystore secrets are configured; it never falls back to the debug keystore.

| Channel | Version | Size | Minimum Android | Link |
| :--- | :--- | :--- | :--- | :--- |
| **Latest APK (GitHub Releases)** | `v1.1.0` | 1,800,311 B | Android 7.0+ (API 24+) | [**⬇️ Download `VaultZero-v1.1.0.apk`**](https://github.com/cipherwell/Vault-Zero/releases/latest/download/VaultZero-v1.1.0.apk) |
| **AAB (store upload)** | `v1.1.0` | 3,959,508 B | API 24+ | [**`VaultZero-v1.1.0.aab`**](https://github.com/cipherwell/Vault-Zero/releases/latest) |
| **Releases & verification center** | — | — | — | [`releases/README.md`](releases/README.md) |
| **Release notes & security spec** | `v1.1.0` | — | — | [`releases/v1.1.0.md`](releases/v1.1.0.md) |
| **Web download portal** | — | — | Browser | [`docs/index.html`](docs/index.html) |

> ⚠️ **Upgrading from the `v1.0.0` preview:** that build was signed with a debug
> certificate. Uninstall the preview (export a backup first) before installing
> `v1.1.0`. Backup files from the preview remain importable with their master
> password or recovery phrase. Details: [`releases/v1.1.0.md`](releases/v1.1.0.md).

### Install steps
1. Download the APK from GitHub Releases.
2. Open the file; allow "Install unknown apps" for the browser/file manager if prompted.
3. Install and open VaultZero.
4. Create a strong **Master Password** (8+ characters) and store the **12-word recovery kit** offline. The vault starts empty.

### Verify APK Integrity (SHA-256 Checksum)

**Official `v1.1.0` SHA-256 Digests:**

```text
682a081cae0349b312d494137545e5c789752b9b7191bcef9c3dc7bedba131d6  VaultZero-v1.1.0.apk
0b60dd18eb2545f8ba7ca695a253fdd06d406616095eeba0b77ad747ba3d6410  VaultZero-v1.1.0.aab
```

Production signing certificate SHA-256:
`c54ed94be9f5d4f00ea7a9e8d7f1f5eb4d0eafc8f456055b6130d85b38ae831d`

Before installing on high-security devices, you can verify the APK signature and SHA-256 checksum:

```bash
# Verify SHA-256 checksum (Linux / macOS)
shasum -a 256 VaultZero-v1.1.0.apk

# Or verify automatically against the checksum file in /releases:
cd releases && shasum -a 256 -c VaultZero-v1.1.0.apk.sha256

# Verify SHA-256 checksum (Windows PowerShell)
Get-FileHash .\VaultZero-v1.1.0.apk -Algorithm SHA256

# Verify APK signing certificate using Android SDK apksigner
apksigner verify --print-certs VaultZero-v1.1.0.apk
```

---

## 🛡️ Key Features

- **6 Specialized Secret Vault Types**:
  - **Logins & Passwords**: Website/app credentials, username, password, live RFC 6238 **TOTP 2FA code generator** with 30-second countdown ring, notes, and custom fields.
  - **Payment Cards**: Cardholder name, masked card number, brand, expiration, CVV, and billing PIN.
  - **Secure Notes**: Markdown-friendly encrypted notes for sensitive documents and recovery codes.
  - **Crypto Wallets & Seed Phrases**: 12/24-word mnemonic phrase grid viewer, network selector, public address, and private key storage.
  - **API & Developer Secrets**: Environment (`PROD`, `STAGING`, `DEV`), Bearer/API tokens, SSH keys, endpoint URLs, and expiration alerts.
- **Full Record Metadata Encryption (`_vz_meta_v2`)**:
  - Not just secret payloads—record **titles**, **categories**, and **tags** are serialized inside the AES-256-GCM encrypted envelope and redacted (`[REDACTED]`) in raw SQLite columns and backup JSON envelopes.
- **Per-Record GCM AAD Row Binding**:
  - Every record's AES-256-GCM authentication tag is cryptographically bound to its `id`, `type`, `createdAt`, `updatedAt`, and `isFavorite` metadata via Additional Authenticated Data (AAD), preventing cross-row ciphertext swapping in SQLite or backup files.
- **Dual-Envelope Key Wrapping & Instant Key Rotation**:
  - A random 256-bit **Vault Master Key (VMK)** encrypts vault records and is wrapped independently by your **Master Password KEK** and your **12-Word Recovery Phrase KEK** (`PBKDF2-HMAC-SHA256`, 600,000 iterations).
- **Hardware-Backed Biometric Unlock**:
  - Uses Android Keystore (`AES/GCM/NoPadding`, StrongBox hardware-backed where supported) paired with `BiometricPrompt.CryptoObject` for hardware-authenticated biometric unlocking.
- **Interactive Security Audit & Entropy Dashboard**:
  - Real-time calculation of Shannon + character-pool entropy (in bits), weak/reused/compromised password detection, aging secret alerts (>180 days), and malformed Base32 2FA validation (without penalizing logins that do not use 2FA).
- **Built-In Cryptographic Toolkit**:
  - High-entropy random **Password Generator** and **EFF Diceware Passphrase Generator** powered by `java.security.SecureRandom`.
- **Self-Contained Encrypted `.vzero` Backups**:
  - Export and restore portable, tamper-evident `.vzero` encrypted vault containers using Android's zero-permission Storage Access Framework (SAF), with full Master Password or 12-Word Recovery Phrase cross-device restoration.
  - Backups can be restored with the source vault's **master password OR its 12-word recovery phrase** (every export already carries the vault key wrapped under the recovery phrase). An export option **"12-word recovery phrase only"** strips the password envelope, producing a file that only that phrase can open. Biometrics remain a local convenience (they unlock this vault, not a foreign backup file).
  - **Change Master Password** in Settings rotates the password-derived envelope (fresh salt, 600k PBKDF2, atomic swap). Records are encrypted under the vault key, so they are not re-encrypted, and the 12-word recovery kit keeps working.
  - The vault list's **sort order and group-by-type preference persist across launches**.
  - Imports are **verified before anything is written**: the whole envelope is checked and every record is authenticated/decrypted first, then records are classified per-record — newly restored, updated in place (same record id, changed content), or skipped as duplicates (same id or same type + title + category + tags + secret payload). Merged files containing a mix of repeats and new data bring in exactly the new data, and the write is one atomic database transaction so a rejected or interrupted import never leaves partial data.
  - A backup with unreadable records is rejected by default, with an explicit **"Import readable records"** option (after confirmation) that salvage-imports the authenticated entries and reports how many records were skipped. Saved backups are named `vaultzero-backup-YYYYMMDD-HHMM.json`.
- **Audit-backed list filters**: `is:weak`, `is:reused`, `is:stale`, `is:no2fa`, `is:clean` — find vulnerable records directly from the vault search bar.
- **Safety guards**: the item editor asks before discarding unsaved edits, and backup operations show progress and lock out duplicate taps.
- **Safety & speed details**:
  - **Undo delete**: single and bulk deletes keep an in-memory undo pill for a few seconds; the removed records are re-inserted with their original ids/timestamps.
  - **2FA dashboard**: "2FA codes" in the vault menu shows every live TOTP code with countdown and copy, computed on the fly; nothing is persisted.
  - **Copy 2FA from the list**: the inline code row on a login copies the current code (and marks the item as recently used).
  - **Recently used sort** (new) orders by when secrets were last viewed or copied; `lastUsedAt` is a non-secret column added by a non-destructive Room v1→v2 migration.
  - **Bulk favourite** from multi-select.
  - **Launcher shortcuts**: long-press the app icon → *New login* or *Generator*; taps taken while locked are deferred until unlock.
  - **Lock in background**: configurable Off/15s/30s/1m; the screen-off receiver locks immediately, and the SAF picker is exempt so backups never lock out mid-flow.
  - **Auto-hide secrets**: a revealed password re-masks itself after 15 s.
  - **Adversarial assessment**: a documented self-pentest (`docs/PENTEST-2026-11-15.md`) covers tampering, brute force, capture, hostile intents and backup abuse.
  - **Backup dialogs**: export shows an entry/type preview, imports end with a verified result dialog (new/updated/duplicates/unreadable counts).
  - **Passphrase options**: separator (`-`, `.`, `_`, space) and word capitalisation.
- **Automatic biometric prompt**: on launch/resume the fingerprint prompt appears by itself when biometric unlock is enabled (one attempt per lock; cancel falls back to the password screen).
- **Hardened Runtime Hygiene**:
  - `FLAG_SECURE` screenshot/screen-recording blocking, Android 13+ `ClipData.EXTRA_IS_SENSITIVE` clipboard masking with a 30-second auto-scrub timer, background auto-lock timeout, and Optional **Panic Wipe** after 5 consecutive failed unlock attempts.

---

## 🔐 Cryptographic Architecture

| Component | Specification |
| :--- | :--- |
| **Data Encryption Cipher** | `AES-256-GCM` (`AES/GCM/NoPadding`) with 96-bit `SecureRandom` IV and 128-bit authentication tag |
| **Key Derivation Function (KDF)** | `PBKDF2WithHmacSHA256` with **600,000 iterations** and 128-bit CSPRNG salt |
| **Key Hierarchy** | 256-bit random **Vault Master Key (VMK)** wrapped independently by Master Password KEK and Recovery Phrase KEK |
| **Record AAD Binding** | `VaultZero-Record-v2\|<id>\|<type>\|<createdAt>\|<updatedAt>\|<isFavorite>` |
| **Recovery Phrase Format** | 12-word mnemonic from curated 256-word cryptographic dictionary with **SHA-256 checksum** embedded in the 12th word |
| **Biometric Hardware Key** | Android Keystore `AES-256-GCM` key (`setUserAuthenticationRequired`, StrongBox preferred) via `BiometricPrompt.CryptoObject` |

---

## 🔒 Zero-Knowledge & Air-Gapped Guarantees

- **Zero Network Access**: `AndroidManifest.xml` requests **no `android.permission.INTERNET`**. The app cannot transmit telemetry, analytics, or vault data to any remote server.
- **Disabled OS Cloud Auto-Backup**: Configured with `android:allowBackup="false"` alongside strict `data_extraction_rules.xml` and `backup_rules.xml` excluding SQLite databases and shared preferences from unencrypted OS device-to-device transfers.
- **RAM Zeroization**: Derived keys and decrypted VMK byte arrays are explicitly overwritten with `0x00` (`Arrays.fill(key, 0.toByte())`) immediately when the vault locks.

---

## 📦 F-Droid Compliance & Submission Guide

**VaultZero** is 100% compliant with the [F-Droid Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy/) and [Submitting to F-Droid Quick Start Guide](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/):

- **100% FOSS Dependencies & Build Plugins**: Built exclusively with open-source Jetpack Compose, Room, Biometric, and Kotlin Coroutines libraries. All proprietary Google Play Services (`com.google.gms.google-services`) and Firebase plugins/SDKs are completely excluded, and `dependenciesInfo { includeInApk = false; includeInBundle = false }` disables Google's proprietary dependency signing block.
- **Upstream Fastlane Metadata Included**:
  - `fastlane/metadata/android/en-US/title.txt`
  - `fastlane/metadata/android/en-US/short_description.txt` (`< 80` characters, no trailing period)
  - `fastlane/metadata/android/en-US/full_description.txt`
  - `fastlane/metadata/android/en-US/changelogs/1.txt` (`<= 500` bytes for `versionCode = 1`)
  - `fastlane/metadata/android/en-US/images/icon.png` (`512x512` PNG)
  - `fastlane/metadata/android/en-US/images/phoneScreenshots/` (`1.png`, `2.png`, `3.png`)
- **Ready-to-Submit F-Droid Build Recipe**:
  - Copy [`fdroid/com.aistudio.vaultzero.secvlt.yml`](fdroid/com.aistudio.vaultzero.secvlt.yml) to `metadata/com.aistudio.vaultzero.secvlt.yml` in your fork of [`fdroid/fdroiddata`](https://gitlab.com/fdroid/fdroiddata), ensure your repository has the `v1.1.0` Git tag pushed, and open a Merge Request titled `New App: com.aistudio.vaultzero.secvlt`.

---

## 🛠️ Building from Source & Running Tests

### Prerequisites
- **Android Studio** Ladybug (2024.2.1) or newer (or command-line Android SDK tools)
- **JDK 11+** (JDK 17/21 recommended for AGP 9.1.1)
- **Android SDK** API 36

### Build Debug or Release APK via Gradle
```bash
gradle :app:assembleDebug
gradle :app:assembleRelease
```

### Run Unit & Robolectric Security Suite
VaultZero includes a comprehensive JUnit 4 + Robolectric test suite (90 tests) verifying AES-256-GCM tamper detection, PBKDF2 key derivation, SHA-256 recovery mnemonic checksum validation, SQLite metadata redaction, AAD row-swap rejection, optional 2FA audit accuracy, and verified/duplicate-safe `.vzero` backup recovery (repeated imports, mixed repeat+new files, in-place updates, tamper rejection, partial-salvage imports, atomic fail-closed writes), recovery-phrase restore and phrase-only exports, master-password rotation (records and recovery kit preserved), audit-backed list filters, and the unsaved-editor guard:

```bash
gradle :app:testDebugUnitTest
```
