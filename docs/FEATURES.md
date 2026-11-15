# VaultZero feature guide

This is the user-facing reference for release `1.1.0`. For security internals see
[CRYPTO-SPEC.md](CRYPTO-SPEC.md); for installation see [GETTING-STARTED.md](GETTING-STARTED.md).

## First-run restore (phone-to-phone migration)

A fresh install offers two paths: create a new vault, or **Restore from Backup**.

- Choose an encrypted VaultZero `.json` backup through the system picker.
- Enter the existing 12-word recovery phrase. The source vault's key fingerprint and recovery
  phrase are preserved; VaultZero never generates a second seed phrase during migration.
- A **new master password is optional for standard backups** (leave blank to keep the old one) and
  required for phrase-only backups, which deliberately carry no password envelope.
- The whole file is verified first: envelope/format, bounded KDF parameters, salts/key envelopes,
  and every record's AES-GCM authentication. Nothing is written unless all records authenticate.
- Records keep their ciphertext, favourites, timestamps and last-used values; the restore refuses
  to overwrite an already-initialized vault (use Settings import to merge into an existing vault).
- After restore the vault opens directly; biometrics must be re-enabled because they are
  device-local.

## Lock screen

- Shows the vault key fingerprint, master-password field, **Unlock Vault**, biometric button
  (when enabled) and recovery-kit entry.
- The biometric prompt is shown **automatically** on app launch/resume when enabled; one attempt
  per lock. Cancel to fall back to password.
- Failed unlocks: five wrong passwords engage a 30-second cooldown; the cooldown is enforced even
  for a correct password until it expires.
- Forgot password → **Use Emergency Kit** → enter the 12-word phrase and a new master password.
  This restores the same vault key, so existing records remain readable.

## Vault list

| Control | Behaviour |
| --- | --- |
| Large title | `Vault` + record count/KDF status; collapses smoothly on scroll |
| + | Add a record (type chooser) |
| ⋮ | Sort (favourites/title/recently updated/recently used), group by type, 2FA codes, select items |
| Lock icon | Lock immediately (also clears copied secrets) |
| Search | See syntax below; results update as you type |
| Filter chips | All / Favourites / per-type; exclusive views (selecting one clears the others) |
| Rows | Tap to open details, long-press to select, copy button for the primary secret, inline 2FA code |
| Undo pill | Appears after deletes for ~10 seconds; restores the removed records |
| Multi-select | Bulk favourite and bulk delete |

**Search syntax**

| Token | Meaning |
| --- | --- |
| `type:login`, `type:card`, `type:api`, `type:crypto`, `type:note`, `type:identity` | Type filter (`t:` alias) |
| `tag:work` | Requires every given tag |
| `is:fav`, `is:all` | Favourites only / clear favourites constraint |
| `is:weak`, `is:reused`, `is:stale`, `is:no2fa`, `is:clean` | Audit-backed filters (usable together, e.g. `type:login is:weak`) |
| anything else | Matched against title, category, tags, usernames, URLs, notes, card brand/last-4, service/environment, wallet/address, ID fields |
| `prefix:value` with unknown prefix | Treated as plain text (URLs/times are safe to type) |

## Item detail sheet

- Masked sensitive values with per-field copy and reveal. A revealed secret masks itself after 15 s.
- Password strength meter for logins; live TOTP badge with copy; website URL; notes.
- TOTP copy uses the sensitive-clipboard path (auto-clears per your setting; wipes on lock).
- **Edit** opens the full editor; **Delete** asks for confirmation and enables Undo.

## Editor

- Per-type fields: username/password/URL/TOTP/notes; cardholder/number/expiry/CVV/PIN/brand;
  API service/key/token/environment/scopes/endpoint; crypto wallet/network/address/seed/private
  key/derivation path; note content; identity name/type/number/birth/expiry.
- Live password strength for login passwords.
- Closing with unsaved changes asks **Discard changes?** (Discard/Keep editing).
- Save encrypts the whole record (title/category/tags + payload) under the vault key.

## Generator

- **Password** mode: length 8–64 (UI slider; engine accepts 4–128), character classes, avoid
  ambiguous characters; entropy/strength display.
- **Passphrase** mode: 3–8 words, separator (`-`, `.`, `_`, space), capitalisation option.
- **Regenerate**, **Copy**, **Save to Vault** (opens a prefilled editor).
- Session history (last 10, masked, RAM only; cleared on lock) with **Clear**.

## Security audit

- Local-only grading of login passwords: entropy/strength, reuse, staleness (>90 days), missing or
  malformed TOTP secrets; score/grade and metric tiles.
- Action items include **FIX**, which opens the editor focused on the relevant field.
- Nothing is uploaded; the audit engine runs on decrypted records in memory.

## 2FA dashboard

- Vault menu ⋮ → **2FA codes**. Shows each login with a valid TOTP: title, spaced code, seconds
  remaining, copy button. Codes are computed on demand; nothing is stored.
- Tap a row to copy its code (sensitive clipboard path).

## Settings

### Appearance
System / Light / Dark. Persisted; system-bar icon contrast follows the theme.

### Security controls
| Setting | Options | Notes |
| --- | --- | --- |
| Auto-Lock Inactivity | Off, 30 s, 1 m, 5 m, 15 m | Default 60 s; Off = manual lock only |
| Lock in Background | Off, 15 s, 30 s, 1 m | Default 30 s; screen-off locks immediately; SAF picker exempt |
| Auto-Clear Clipboard | Off, 15 s, 30 s, 60 s | Default 30 s; Off still clears owned clip on lock |
| Fast Biometric Unlock | Switch | Enables hardware per-use biometric prompt |
| Change Master Password | Dialog | Records unchanged; recovery kit remains valid |
| Lock Vault Now | Action | Locks now and clears copied secrets |

The clipboard only clears content the app itself copied; it will not erase unrelated clipboard
data from other apps.

### Recovery & backup
- **View 12-Word Recovery Kit** (decrypts locally; copy button uses sensitive clipboard).
- **Export Encrypted Vault Backup** — standard or phrase-only, preview of record counts,
  timestamped filename.
- **Restore / Import Encrypted Backup** — accepts master password or 12-word phrase for foreign
  vaults; blank for the current vault; verified/duplicate-safe; result dialog with counts.
- **Cryptographic Specifications** dialog — summaries of algorithms/parameters.

### Reset
**Reset & Erase Vault Completely** removes local data (database, prefs, wrapping keys). Recovery
requires an encrypted backup plus its master password/recovery phrase. The app double-confirms.

## Launcher shortcuts

Long-press the app icon → **New login** or **Generator**. Actions requested while the vault is
locked are applied after a successful unlock.

## Behavioural security details

- Screen capture and recents previews are blocked (`FLAG_SECURE`) in production builds.
- The app declares no network permission and has no networking code.
- Backups are written only through Android's system document picker (SAF), with a maximum
  import size of 8 MiB.
- Sensitive clipboard entries are marked `EXTRA_IS_SENSITIVE` on Android 13+ and auto-cleared.
- The app locks on the configured background timeout and immediately on screen-off.
- TOTP secrets shorter than the RFC 4226 floor are rejected/ignored (no bogus codes).

## Limitations you should know

- No password reset without the recovery kit.
- No cloud sync/accounts; use encrypted backups. Restoring is manual.
- Biometrics are device-local; re-enrolment invalidates them.
- No import from other password managers (the backup format is VaultZero-specific).
- No independent audit yet; see [SECURITY.md](../SECURITY.md).
