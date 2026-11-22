# Changelog

VaultZero is now maintained under the **Cipherwell** identity. Earlier release history was
intentionally removed during the identity migration.

## [1.7.1] — 2026-11-21

### Added
- **Copy diagnostics** in Settings → About: app version, device, Android level, language, vault
  state, biometric state and the last error — never titles, secrets or key material. Gives beta
  testers a usable bug-report path while the app intentionally ships no telemetry and blocks
  screenshots.
- `docs/BETA.md` (tester guide + known limitations), `docs/SOCIAL-BETA-POST.md` (EN/ES announcement
  drafts) and `docs/PLAY-INTERNAL-TESTING.md` (maintainer checklist).

### Fixed
- Android biometric wording no longer says "Touch ID" (iOS branding): "Fingerprint / Biometrics",
  "Huella digital / Biometría".

### Security
- No cryptographic changes. The diagnostics payload is built from a pure, unit-tested function and
  is only generated when the user taps the button.

## [1.7.0] — 2026-11-21

### Added
- **Bilingual English (US) / Spanish (US)** interface. Every screen, dialog, empty state,
  accessibility description, status message and validation error is translated; 477 strings and
  19 plurals per language, tested for key/placeholder parity in CI.
- **Language switch on the lock/setup screen**, so a first-time user can read the setup flow in
  their language before any vault exists.
- **Language switch in Settings**, applied instantly through a localized composition — no activity
  recreation, so an unlocked vault is not dropped back to the lock screen.
- Languages are persisted outside the vault preferences: the choice survives app restarts and
  "Reset vault", and Android 13+ lists the app in the system per-app language picker
  (`android:localeConfig`).

### Changed
- `versionCode 9`, `versionName 1.7.0`.
- Data-layer failures now carry a typed error code (`VaultError`) so the UI can render them in the
  selected language while logs and tests keep the English message.
- Password-strength labels, record-type names, sort options and audit findings are resolved through
  resources instead of model-enum English strings.

### Security
- No change to the cryptographic design; Argon2id envelopes, backups and migrations are untouched.

## [1.6.0] — 2026-11-21

### Security
- **Argon2id key derivation** (RFC 9106, version 1.3) replaces PBKDF2-HMAC-SHA256 for every new
  password and recovery-phrase envelope: 64 MiB memory, 3 iterations, 1 lane, 256-bit tag, 128-bit
  random salt. Pure-Java Bouncy Castle implementation, pinned and verified against the RFC 9106
  known-answer vector in the test suite.
- Every envelope now records its own KDF descriptor (`argon2id:<mKiB>:<t>:<p>` or
  `pbkdf2-sha256:<iterations>`), so password and recovery envelopes can never drift apart when one
  of them rotates or is restored from an older backup.
- **Transparent migration:** a successful unlock of a PBKDF2 vault (or of a vault restored from a
  3.2-era backup) re-wraps both envelopes with fresh salts under Argon2id in one atomic commit. The
  vault master key and every encrypted record stay untouched.
- Backup format `3.3` carries both KDF descriptors. `3.2` and older backups still import and are
  treated as PBKDF2. Import validates algorithm and parameters against the supported ranges before
  any derivation, so a crafted file cannot force a 1 GiB allocation or long CPU burn.

### Changed
- `versionCode 8`, `versionName 1.6.0`; runtime dependency on `org.bouncycastle:bcprov-jdk18on:1.81`
  (Argon2id only; no provider is registered).

### Upgrade notes
- Existing vaults open with the same master password or recovery phrase and migrate in place.
- Downgrading to 1.5.0 or older is not supported once an envelope has migrated to Argon2id.

## [1.5.0] — 2026-11-15

### Changed
- Application ID moved from `com.aistudio.vaultzero.secvlt` to **`com.cipherwell.vaultzero`**,
  completing the Cipherwell identity migration. This is a new Android package; the old package can
  be uninstalled after migrating data through an encrypted backup.
- F-Droid metadata file renamed to `com.cipherwell.vaultzero.yml`; store/privacy/docs links updated.

### Security
- Same Cipherwell signing identity, AES-256-GCM vault format, BIP-39 recovery kits, verified
  backups and offline posture as 1.4.0.
- 105 JVM tests / 0 failures; lint clean.

### Upgrade notes
- `com.cipherwell.vaultzero` installs alongside the old package. Export an encrypted backup +
  recovery phrase from the old app, install v1.5.0, restore, then uninstall the old package.
- v1.4.0 and earlier GitHub releases were removed in favour of the new package identity.

## [1.4.0] — 2026-11-15

### Changed
- New **Cipherwell** project identity and a new production signing key (`CN=Cipherwell`).
  Existing installs must be uninstalled/reinstalled; export a backup first.
- Repository, documentation, F-Droid metadata and release assets rebuilt under the new identity.

### Security
- BIP-39 128-bit recovery kits with unique words; legacy 136-word kits still accepted and rotatable
  in Settings.
- AES-256-GCM records with AAD binding, PBKDF2-HMAC-SHA256 (600,000), hardware per-use biometrics.
- First-run restore from encrypted backup with the existing recovery phrase.
- Verified, duplicate-safe backup import; SAF-only backups; no network permission or telemetry.
- 105 JVM tests / 0 failures; lint clean.

### Upgrade notes
- v1.4.0 is signed with a new key; it cannot update v1.3.1 or older builds in place.
- Export an encrypted backup + recovery phrase, uninstall the old app, install v1.4.0, restore.
- Future releases continue under the Cipherwell signing identity.
