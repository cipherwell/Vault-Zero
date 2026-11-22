# Storage, files and backup review

## 1. Data at rest inventory

Open the debug build with `run-as` (never the release build) and enumerate:

```bash
adb shell run-as com.cipherwell.vaultzero ls -laR files databases shared_prefs cache no_backup
adb exec-out run-as com.cipherwell.vaultzero cat databases/vault.db > /tmp/vault.db
strings -n 6 /tmp/vault.db | head -50
```

Pass conditions:

- Item titles/usernames/URLs/notes/OTP secrets do **not** appear in the DB or prefs in plaintext.
- Prefs contain only: KDF specs, envelope blob, flags (biometrics enabled, theme, language, lock
  timers), never the VMK/password/recovery phrase.
- WAL/SHM files follow the same rule.
- No files in external storage (`/sdcard/Android/data/...`) unless the user explicitly exported.
- Cache contains no plaintext; `no_backup` contains no secrets.

## 2. Backup export

- Export uses SAF (`ACTION_CREATE_DOCUMENT`), not a hard-coded path.
- Suggested filename is sanitized; the user can still choose any name (OS handles it).
- The exported file contains only ciphertext + header; verify with `strings`.
- The app tells the user the file is encrypted and requires the master password/recovery phrase.
- Export does not leave a temporary plaintext copy behind.

## 3. Backup import (adversarial)

Prepare a corpus (see `fuzzing.md`) and for each file record: accepted/rejected, time, memory, error
message, state after import (items count unchanged / partially imported).

Pass conditions:

- Rejects: unknown version, missing fields, wrong types, invalid base64, KDF below floor, oversized
  payload, non-JSON, truncated, extra trailing data.
- Rejects safely: no crash, no ANR, no OOM, no partial import without an explicit report.
- Rejects before deriving keys when the header is invalid (cheap validation first).
- Never writes attacker-controlled paths; import reads a single chosen document URI.
- Error text is localized and does not echo secrets or stack traces.

## 4. Import/export state machine

- Import into an existing vault: merge rules documented? Duplicate IDs? Partial failure rollback?
- Import during unlock/lock races: no half-imported state.
- Cancel/back during import: no orphaned temp data.
- Kill the process mid-import (adb `am force-stop`): database remains consistent, next launch offers
  recovery or reports the failed import.

## 5. Room / SQL

- Queries parameterized (no string concatenation with user input).
- No `fallbackToDestructiveMigration` in release (data loss on upgrade).
- DB key not stored beside the DB (if SQLCipher/encrypted DB is used; otherwise item-level crypto
  must cover every sensitive column).
- Indexes do not leak ordering that matters? (Metadata leakage is informational.)

## 6. Auto-lock and lifecycle

- Lock on background after configured timeout; immediate lock when process is killed.
- `onStop`/`onTrimMemory` do not leave plaintext in recent-task snapshots (`FLAG_SECURE`).
- Screen-off locks if policy says so.
- Clipboard clear timer survives backgrounding? Test both directions.

## 7. Filesystem permissions

- `run-as` works only on debug builds; release is not debuggable.
- No `MODE_WORLD_READABLE/WRITABLE` (deprecated, but check).
- No exported `FileProvider` unless needed; if present, `grantUriPermissions` + path config checked.
- No symlink following in import/export.
