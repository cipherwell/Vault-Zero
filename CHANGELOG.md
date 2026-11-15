# Changelog

All notable changes to VaultZero are documented here. Format based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
[Semantic Versioning](https://semver.org/).

## [1.1.0] — 2026-11-15 — first production-signed release

### Added
- Six vault record types (logins, cards, API secrets, crypto wallets/seed phrases, secure notes, identities).
- Hardware per-use biometric unlock with `BiometricPrompt.CryptoObject`; automatic prompt on app
  open when enabled; per-use Keystore key invalidated by biometric re-enrolment.
- Verified, duplicate-safe backup import: updates same-id changed records, skips repeats,
  atomic write, explicit salvage mode for partially unreadable files, hostile-input bounds.
- Backups can be restored with the master password **or** the 12-word recovery phrase;
  optional phrase-only export (password envelope stripped).
- Change Master Password without re-encrypting records; recovery kit remains valid.
- Live 2FA dashboard and inline copy-TOTP from the vault list.
- Audit-backed search filters (`is:weak`, `is:reused`, `is:stale`, `is:no2fa`, `is:clean`).
- Undo delete, recently-used sort, bulk favourite, launcher shortcuts.
- Background locking (15/30/60 s or Off), screen-off immediate lock, SAF-picker exemption.
- One UI-inspired redesign with light/dark/system appearance, collapsing title, floating pill nav.
- Passphrase separator/capitalisation options, export previews, import result summary,
  unsaved-editor guard, auto-masking revealed secrets, timestamped backup names.

### Changed
- Password KDF increased to **600,000** PBKDF2-HMAC-SHA256 iterations; legacy vaults upgrade
  transparently on unlock.
- Auto-lock/clipboard options now include Off (clipboard Off still wipes on vault lock).
- Version `1.1.0`, `versionCode 2`, target/compile SDK 36, min SDK 24.
- Production release signing established; repository/CI no longer use a debug key for releases.

### Security
- Per-record AES-256-GCM now always bound with v2 AAD (`VaultZero_Record_v2:<id>:<type>`);
  tampered row swaps fail closed and are hidden (pentest F-03).
- Backup import hardened against hostile KDF iteration counts, oversized salts and key envelopes
  (pentest F-01, fixed).
- Release build verified: non-debuggable, `run-as` blocked, screenshots blocked, `adb backup`
  contains no app data.
- Pentest report: `docs/PENTEST-2026-11-15.md`.

### Fixed
- Category/selection filters could show stale results or reset scroll position incorrectly.
- Settings timeout/auto-lock/clipboard changes were laggy due to synchronous preference writes
  and non-observable state.
- Biometric behaviour was previously cosmetic (no auth-required key, no CryptoObject); replaced.
- Duplicate imports and partial imports; backup verification is now completed before writing.

### Upgrade notes
- `v1.0.0` was a debug-signed public preview. Uninstall it before installing `v1.1.0`; backups
  remain importable.
- Existing v1.0.x vaults upgrade automatically on first unlock (KDF migration); Room DB migrates
  v1→v2 non-destructively (adds last-used timestamp).

## [1.0.0] — 2026-10-20 — public preview (debug-signed, superseded)

Initial preview: encrypted vault, AES-256-GCM records, PBKDF2 100k, 12-word recovery phrase,
TOTP, audit, generator, encrypted backups. Superseded by 1.1.0; do not use for updates.

[Unreleased]: https://github.com/cipherwell/Vault-Zero/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/cipherwell/Vault-Zero/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/cipherwell/Vault-Zero/releases/tag/v1.0.0
