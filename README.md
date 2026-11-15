<div align="center">

<img src="docs/screenshots/oneui-vault-dark.png" width="220" alt="VaultZero vault screen">

# VaultZero

**Offline, zero-knowledge password manager and encrypted vault for Android.**

AES-256-GCM · PBKDF2-HMAC-SHA256 (600,000) · hardware biometrics · no network permission · no accounts · no telemetry

🌐 [cipherwell.github.io/Vault-Zero](https://cipherwell.github.io/Vault-Zero/) · 📦 [GitHub Releases](https://github.com/cipherwell/Vault-Zero/releases) · 📚 [Documentation](docs/README.md)

[![Release](https://img.shields.io/github/v/release/cipherwell/Vault-Zero?label=release&style=flat-square)](https://github.com/cipherwell/Vault-Zero/releases/latest)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue?style=flat-square)](LICENSE)
[![Platform](https://img.shields.io/badge/Android-7.0%2B%20(API%2024)-3DDC84?style=flat-square&logo=android&logoColor=white)](#requirements)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](#tech-stack)
[![Offline](https://img.shields.io/badge/network%20access-none-black?style=flat-square)](#security-at-a-glance)

</div>

---

## What is VaultZero?

VaultZero is a local-first Android vault for passwords, 2FA codes, payment cards, API keys,
crypto wallets/seed phrases, secure notes and identity documents. It is built with Kotlin and
Jetpack Compose and is deliberately incapable of connecting to the internet: the app declares
**no `INTERNET` permission** and contains no networking code.

Everything is encrypted on the device with a random 256-bit vault key, wrapped by your master
password and a 12-word recovery phrase. Backups are encrypted files written through Android's
system file picker — there is no account, cloud sync, analytics or remote service.

> **Status:** production release **v1.2.0** (`versionCode 3`). Self-tested and pentested; an
> independent audit is still recommended before relying on it for high-risk secrets.

## Table of contents

- [Screenshots](#screenshots)
- [Features](#features)
- [Security at a glance](#security-at-a-glance)
- [Download & install](#download--install)
- [Build from source](#build-from-source)
- [Testing](#testing)
- [Architecture](#architecture)
- [Repository map](#repository-map)
- [Documentation](#documentation)
- [Privacy](#privacy)
- [Contributing](#contributing)
- [License](#license)

## Screenshots

| Vault (dark) | Light theme | 2FA dashboard |
| :---: | :---: | :---: |
| <img src="docs/screenshots/oneui-vault-dark.png" width="240"> | <img src="docs/screenshots/oneui-vault-light.png" width="240"> | <img src="docs/screenshots/oneui-totp-dashboard.png" width="240"> |
| **Settings** | **Generator** | **Audit** |
| <img src="docs/screenshots/oneui-settings-security.png" width="240"> | <img src="docs/screenshots/oneui-generator-dark.png" width="240"> | <img src="docs/screenshots/oneui-audit-dark.png" width="240"> |

More in [docs/SCREENSHOTS.md](docs/SCREENSHOTS.md).

## Features

### Vault
- Six record types: **Logins**, **Payment cards**, **API secrets**, **Crypto wallets**, **Secure notes**, **Identities**.
- AES-256-GCM per-record encryption with AAD binding; titles, categories and tags are encrypted too — SQLite only stores `[ENCRYPTED]` placeholders.
- Live RFC 6238 TOTP codes (30 s) inline in the list and in a dedicated **2FA dashboard**.
- Search operators: `type:login`, `tag:work`, `is:fav`, and audit-backed filters `is:weak`, `is:reused`, `is:stale`, `is:no2fa`, `is:clean`.
- Sort by favourites, title, recently updated or recently used; optional grouping by type; multi-select bulk delete/favourite; undo delete.
- Copy actions are sensitive-clipboard aware; revealed secrets re-mask after 15 seconds.

### Security
- Random 256-bit vault master key (VMK); per-record AES-256-GCM with `VaultZero_Record_v2:<id>:<type>` AAD.
- PBKDF2-HMAC-SHA256 with **600,000 iterations** and 128-bit salts; transparent upgrade of legacy vaults on unlock.
- 12-word checksummed recovery phrase; change master password without re-encrypting records (recovery kit stays valid).
- Hardware-backed biometric unlock through Android Keystore + `BiometricPrompt.CryptoObject` — **per-use** authentication, invalidated when biometrics are re-enrolled. The prompt auto-appears on app open when enabled.
- Configurable auto-lock, background lock (screen-off locks immediately; SAF picker exempt), clipboard auto-clear, `FLAG_SECURE`, Recent-Apps preview blocked.
- Local security audit (weak/reused/stale/2FA), offline by construction.

### Backups
- **First-run migration:** a new phone can restore a full backup with the existing 12-word recovery
  phrase — no new seed phrase — keeping the original master password (or setting a new one).
- Versioned encrypted envelope via Android's Storage Access Framework only.
- Restore with the source master password **or** 12-word recovery phrase; optional **phrase-only** export (password envelope stripped).
- Verified imports before any write: duplicate-safe merges, in-place updates, atomic Room transaction, explicit salvage of readable records from a damaged file.
- Timestamped filenames; import/export progress feedback.

### Design & ergonomics
- One UI-inspired design: true-black/`#FAFAFA` grounds, single accent blue, squircle containers, floating pill navigation, large collapsing title.
- Light / dark / system appearance; launcher shortcuts (*New login*, *Generator*); passphrase separator/capitalisation options.

## Security at a glance

| Area | Implementation |
| --- | --- |
| Records | AES-256-GCM, random per-record IV, 128-bit tag, AAD bound to record id + type |
| Key hierarchy | Random 256-bit VMK wraps records; VMK wrapped by password KEK and recovery-phrase KEK |
| Password KDF | PBKDF2-HMAC-SHA256, 600,000 iterations, 128-bit random salt |
| Recovery | 12-word phrase with SHA-256 checksum word; encrypted under the VMK at rest |
| Biometrics | AES-256-GCM Keystore key, `AUTH_BIOMETRIC_STRONG`, per-use auth (`CryptoObject`), StrongBox→TEE, invalidated by biometric enrolment changes |
| Backups | AES-256-GCM envelope, redacted metadata, 8 MiB import cap, phrase/password restore |
| Network | No `INTERNET`/`ACCESS_NETWORK_STATE` permission, no networking libraries |
| Screen capture | `FLAG_SECURE`, recents screenshot disabled on API 33+ |
| At rest | SQLite titles/categories/tags redacted; prefs contain only wrapped keys, salts, verifiers |
| Offline | No accounts, cloud, analytics, crash reporting, ads or trackers |

Detailed design: [docs/CRYPTO-SPEC.md](docs/CRYPTO-SPEC.md) · controls & evidence:
[SECURITY.md](SECURITY.md) · threat model: [docs/THREAT-MODEL.md](docs/THREAT-MODEL.md).

## Download & install

Latest production release: **[GitHub Releases → v1.2.0](https://github.com/cipherwell/Vault-Zero/releases/latest)**

| Artifact | Size | SHA-256 |
| --- | --- | --- |
| `VaultZero-v1.2.0.apk` (sideload) | 1,861,355 B | `1ea83362c53d54df10272690d7e954e2c621c712c79f6b2022b7ad27b90f15e1` |
| `VaultZero-v1.2.0.aab` (store upload) | 4,033,502 B | `cccd472cd14d40c734b5e1c11b0e584f544404bec80c450ca7d90e6a10d83601` |

Signing certificate (production upload key, APK Signature Scheme v2):
`6292a1fc630c200e7ec5342248ba7a6ae30a893fd882b2ad4188f699bbb607e7`

```bash
sha256sum -c VaultZero-v1.2.0.apk.sha256
apksigner verify --print-certs VaultZero-v1.2.0.apk
```

See [docs/GETTING-STARTED.md](docs/GETTING-STARTED.md) for installation and first-run steps.

> **Upgrading from v1.0.0 preview:** that build was debug-signed with a public key.
> Uninstall it (export a backup first) before installing v1.2.0; Android will reject an
> in-place update. Old backup files remain importable with their master password or recovery phrase.

### Requirements
Android 7.0 (API 24) or newer. No Google Play Services, no network, no account.

## Build from source

Prerequisites: **JDK 17**, Android SDK **36**, Gradle wrapper included.

```bash
git clone https://github.com/cipherwell/Vault-Zero.git
cd Vault-Zero
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk   # or your JDK 17
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew :app:assembleDebug                     # install with adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Debug builds normally enable `FLAG_SECURE`; for emulator UI automation only:
`./gradlew :app:assembleDebug -PallowScreenshots=true -PdevDisableAutoLock=true`.

Production/release builds require the keystore environment (`docs/RELEASING.md`). Full
instructions: [docs/BUILDING.md](docs/BUILDING.md).

## Testing

```bash
./gradlew :app:testDebugUnitTest       # 96 JVM/Robolectric tests
./gradlew :app:lintDebug               # Android Lint
```

There are 12 instrumented device tests too (biometric key binding, KDF migration, offline posture,
setup flow). Because `connectedAndroidTest` uninstalls the app, device tests are run manually —
see [docs/TESTING.md](docs/TESTING.md) for commands and the QA checklist.

## Architecture

```mermaid
flowchart LR
  UI["Compose UI<br/>screens · dialogs"] --> VM["VaultViewModel<br/>state · lifecycle"]
  VM --> REPO["VaultRepository<br/>envelope · import/export"]
  VM --> CRYPTO["VaultCrypto<br/>AES-GCM · PBKDF2 · TOTP"]
  VM --> BIO["BiometricKeyManager<br/>Keystore · CryptoObject"]
  REPO --> ROOM["Room database<br/>vault_items (redacted metadata)"]
  REPO --> PREFS["SharedPreferences<br/>wrapped VMK · salts"]
  UI -. no network code .-> X((Offline))
```

Single-activity Compose app; all secrets are decrypted only into memory while the vault is
unlocked. Details: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) · crypto:
[docs/CRYPTO-SPEC.md](docs/CRYPTO-SPEC.md).

## Repository map

```
vault-zero-app/
├── app/                       Android application module (Kotlin/Compose)
│   └── src/main/java/com/example/
│       ├── crypto/            AES-GCM, PBKDF2, TOTP, biometric keystore, audit engine
│       ├── data/              Room database, repository, preferences, backup IO
│       ├── model/             Vault records, payloads, audit models
│       └── ui/                Compose screens, components, theme, backup picker
├── docs/                      Documentation (start at docs/README.md)
│   └── screenshots/           Curated screenshots (+ archive/)
├── releases/                  Release notes & checksums (binaries on GitHub Releases)
├── store/                     Play Store listing pack & data-safety answers
├── fastlane/                  F-Droid/Play metadata (listing, changelog, images)
├── fdroid/                    F-Droid build recipe
├── .github/                   Issue/PR templates, release workflow, Dependabot
├── SECURITY.md                Security policy, evidence and residual risks
├── CHANGELOG.md               Version history
├── CONTRIBUTING.md            Contribution guide
└── LICENSE                    MIT
```

Full annotated tree: [docs/REPO-MAP.md](docs/REPO-MAP.md).

## Documentation

| Document | What it covers |
| --- | --- |
| [docs/README.md](docs/README.md) | Documentation index & reading paths |
| [docs/GETTING-STARTED.md](docs/GETTING-STARTED.md) | Install, first vault, recovery kit, biometrics, backups |
| [docs/FEATURES.md](docs/FEATURES.md) | Complete user guide and search syntax |
| [SECURITY.md](SECURITY.md) | Security controls, verification, limitations, disclosure |
| [docs/CRYPTO-SPEC.md](docs/CRYPTO-SPEC.md) | Algorithms, key hierarchy, record/backup formats |
| [docs/THREAT-MODEL.md](docs/THREAT-MODEL.md) | Adversaries, assets, mitigations, residual risk |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Stack, layers, data flow, database schema |
| [docs/BACKUP-FORMAT.md](docs/BACKUP-FORMAT.md) | Envelope schema, import/export semantics |
| [docs/BUILDING.md](docs/BUILDING.md) | Build, dev flags, device tests |
| [docs/TESTING.md](docs/TESTING.md) | Test matrix, commands, manual QA |
| [docs/RELEASING.md](docs/RELEASING.md) | Signing, versioning, Play/F-Droid/GitHub release |
| [docs/DESIGN.md](docs/DESIGN.md) | One UI-inspired design tokens & screens |
| [docs/PENTEST-2026-11-15.md](docs/PENTEST-2026-11-15.md) | Self-directed security assessment |
| [docs/FAQ.md](docs/FAQ.md) | Frequently asked questions |
| [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) | Common problems & fixes |
| [docs/PRIVACY.md](docs/PRIVACY.md) | Privacy policy |
| [releases/](releases/README.md) | Release notes, checksums, verification |
| [store/](store/README.md) | Play Store listing assets & answers |

## Privacy

VaultZero collects no data and has no network permission. The only thing that leaves the
device is an encrypted backup you explicitly export. Full policy:
[docs/PRIVACY.md](docs/PRIVACY.md) · [web version](docs/privacy.html).

## Contributing

Security tool: changes that weaken cryptography, add network access, log secrets or relax
storage rules are rejected. Read [CONTRIBUTING.md](CONTRIBUTING.md) and
[docs/THREAT-MODEL.md](docs/THREAT-MODEL.md) before opening a PR. For vulnerabilities use
GitHub's private advisory flow (see [SECURITY.md](SECURITY.md)); do not file a public issue.

## License

MIT — see [LICENSE](LICENSE). VaultZero is an independent, local-first tool. You are responsible
for your device security, master password strength and backups; no software can recover a
forgotten master password and recovery phrase.

<div align="center"><sub>Built by Cipherwell · Not affiliated with Samsung, Google or Android.</sub></div>
