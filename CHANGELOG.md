# Changelog

VaultZero is now maintained under the **Cipherwell** identity. Earlier release history was
intentionally removed during the identity migration.

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
