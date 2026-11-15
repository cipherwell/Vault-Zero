# Backup format

VaultZero backups are self-contained JSON envelopes encrypted with AES-256-GCM. They are the only
supported way to move a vault to another device. This document describes format version `3.2`
(release 1.1.0).

## File shape (standard protection)

```json
{
  "version": "3.2",
  "format": "VaultZero_Encrypted_Envelope",
  "protection": "standard",
  "fingerprint": "VZ-XXXX-XXXX-XXXX-XXXX",
  "exportedAt": 1794719910111,
  "kdfIterations": 600000,
  "recoverySalt": "<base64 16-byte salt>",
  "encryptedVmkByRecovery": "<base64 iv||ciphertext+tag>",
  "salt": "<base64 16-byte salt>",
  "encryptedVmkByPassword": "<base64 iv||ciphertext+tag>",
  "verifier": "<hex HMAC verifier>",
  "items": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "title": "[ENCRYPTED]",
      "type": "LOGIN",
      "category": "Encrypted",
      "encryptedPayload": "<base64 iv||ciphertext+tag>",
      "isFavorite": false,
      "createdAt": 1794719849224,
      "updatedAt": 1794719871063,
      "lastUsedAt": 0,
      "tagsJson": "[]"
    }
  ]
}
```

There is **no plaintext title, category, tag, username, password, TOTP secret or note** in the
file; those live inside `encryptedPayload` (the same `_vz_meta_v2` envelope used in SQLite, bound
to the record id/type via AAD).

### Phrase-only mode

`protection: "recovery_phrase_only"` removes `salt`, `encryptedVmkByPassword` and `verifier`.
Only the recovery-phrase-wrapped VMK remains, so the source master password cannot open the file.
Everything else (items, salts for recovery) is identical.

## Field reference

| Field | Meaning |
| --- | --- |
| `format` | Must be `VaultZero_Encrypted_Envelope`; otherwise rejected before writes |
| `version` | Format version (`3.2`); old files with the same envelope fields are accepted |
| `protection` | `standard` (password + phrase envelopes) or `recovery_phrase_only` |
| `fingerprint` | Public source-vault key fingerprint; used to identify the vault |
| `kdfIterations` | PBKDF2 count used for this file's envelopes; import enforces 10,000–600,000 |
| `salt` / `recoverySalt` | 16-byte random salts (base64); import enforces 16–64-byte decoded size |
| `encryptedVmkByPassword` / `ByRecovery` | VMK wrapped with password/recovery KEK (AES-GCM) |
| `verifier` | Password verifier for the source vault (not a password hash) |
| `items[].id` | Record UUID, also part of the record AAD |
| `items[].type` | `LOGIN`, `CARD`, `API_SECRET`, `CRYPTO_WALLET`, `NOTE`, `IDENTITY` |
| `items[].encryptedPayload` | AES-GCM record envelope; base64(iv‖ct‖tag) |
| `items[].isFavorite` | Non-secret UI flag (outside ciphertext) |
| `timestamps` | Creation/update/last-used epoch ms (non-secret) |
| `tagsJson` | `[]` placeholder; real tags are encrypted |

Maximum import size is **8 MiB** (`BackupFileIo.MAX_BACKUP_BYTES`). Hostile envelope sizes/KDF
counts are rejected before any cryptographic work.

## Restore semantics (1.1.0)

1. **Verify** the envelope: JSON, format marker, item array non-empty, bounds on KDF/salts/envelope
   lengths, and every record must decrypt (active vault key, supplied source master password, or
   supplied 12-word recovery phrase). A structurally/auth-invalid file aborts with **nothing
   written** (strict mode).
2. **Classify** each decrypted record against the current vault:
   - same id + identical content ⇒ **duplicate** (skipped);
   - same id + changed content ⇒ **update in place**;
   - identical SHA-256 content fingerprint under a different id ⇒ duplicate;
   - otherwise ⇒ **new**.
   Content fingerprint = `type | normalized title | category | sorted tags | payload`; favourite
   flags and timestamps are intentionally excluded so re-imports don't duplicate entries.
3. **Write** all survivors re-encrypted under the active VMK in one Room transaction
   (`insertOrUpdateAll`). The result dialog reports new/updated/duplicates/unreadable counts.

**Partial salvage:** if verification finds unreadable records, the strict import is rejected and
the UI offers *"Import readable records"*. That runs the same flow with `skipInvalidRecords=true`;
readable records are written, unreadable ones are counted and skipped. The file is never modified.

**Phrase-only files** require their recovery phrase (or the same vault's current session for its
own backup). They cannot be opened with the source password.

## First-run setup restore (phone migration)

Release 1.2.0 adds a bootstrap path on the uninitialized setup screen. It uses the same envelope
but installs a complete vault instead of merging records:

1. The envelope is validated exactly like an import (format, item array, KDF bounds, salt/key
   envelope sizes) and the VMK is unwrapped from `encryptedVmkByRecovery` using the supplied
   12-word phrase.
2. Every record must decrypt with that VMK (AAD included); the original ciphertext/metadata flags
   and timestamps are preserved, not re-encrypted.
3. Recovery material is preserved: the backup's `recoverySalt` and `encryptedVmkByRecovery` stay,
   the phrase is re-encrypted under the recovered VMK for in-app viewing, and the verifier is
   recomputed from the phrase. **The recovery phrase is unchanged.**
4. Password material:
   - standard backup + no new password ⇒ the backup's `salt`/`encryptedVmkByPassword`/`verifier`
     are preserved, so the old master password still unlocks;
   - a new password supplied ⇒ a fresh salt/KEK wraps the same VMK (phrase remains valid);
   - phrase-only backup ⇒ a new password is mandatory (there is no password envelope to restore).
5. The setup restore writes records atomically and only then commits credentials; it refuses to
   run if the device already has an initialized vault. Unlike regular import, it is not a merge.

## Compatibility and edge cases

- Backups from a vault with a *different* master password/recovery phrase require that vault's
  credential once; after import the records are re-encrypted under the current vault's VMK.
- Changing the master password does **not** invalidate existing backups: standard backups remain
  openable with the *old* password or the unchanged recovery phrase; phrase-only backups with the
  recovery phrase.
- Old export files (pre-1.1.0) keep the same envelope fields; imports apply the same verification
  and dedupe rules. Backup format version is independent of the app version.
- The `version` string does not make the file trusted: always verify checksums when moving files
  and keep the master password/recovery phrase offline.

## Recovery-phrase schemes

Backups do not store the phrase itself. On restore/import, VaultZero accepts either the new
**BIP-39 12-word** phrases (v1.3.0+, 128-bit entropy) or legacy **136-word-list** phrases generated
by v1.0.0–v1.2.0. Both derive the recovery KEK through PBKDF2-HMAC-SHA256 with the backup's
`recoverySalt`; the checksum validation scheme is detected automatically. Existing files and old
printed kits remain usable. New backups created after **Settings → Generate New Recovery Kit**
require the new phrase; older backups retain their original envelope.

## Security notes

- The envelope is encrypted but **portable**: anyone holding the file can attempt offline password
  guessing against the PBKDF2 envelope. Use a strong master password and store the file offline.
- Import is intentionally conservative (fail-closed) to avoid writing unauthenticated data.
- The file contains no biometric data; biometrics are device-local and cannot decrypt a foreign
  backup. The recovery phrase is the portable credential.
- For inspecting a backup without secrets, you can check `jq 'keys' file.json`; titles/secrets are
  not present in cleartext.
