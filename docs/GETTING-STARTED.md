# Getting started with VaultZero

This guide takes you from a new phone to a working, locked-down vault. It assumes the
production release `1.1.0` from [GitHub Releases](https://github.com/cipherwell/Vault-Zero/releases/latest)
(or a Play/F-Droid build once published).

## 1. Install

- **Sideload:** download `VaultZero-v1.1.0.apk`, allow installation from your browser/file manager,
  open the APK and install. Verify the checksum/certificate if you can
  ([instructions](../releases/README.md)).
- **Upgrading from the `v1.0.0` preview:** uninstall it first. The preview was debug-signed and
  Android will refuse an in-place update. Export a backup beforehand if the preview holds data.

## 2. Create your vault

1. Open VaultZero → **Initialize Master Password**.
2. Choose a **long, unique master password** (minimum 8 characters; longer passphrases are better).
   This password is never stored or transmitted; it derives the key that protects your vault.
3. Re-enter it and tap **Create Secure Vault**.
4. The **Emergency Recovery Kit** appears with your 12-word phrase.
   - Write it down on paper or store it in a separate, offline password safe.
   - **Never** store the phrase next to your backups or in photos/notes on the same device.
   - Tap **Copy 12-Word Recovery Phrase**, then **I Saved My Recovery Kit — Open Vault**.
5. Your vault is now open and empty. Verify the lock button (top right) and the bottom tabs:
   **Vault · Generator · Audit · Settings**.

> There is no password reset. If you lose both the master password and the 12-word phrase, the
> data is unrecoverable. That is a deliberate property of zero-knowledge encryption.

## 3. Add your first record

1. Tap **+** (top right) or **Add item** in the empty state.
2. Choose a type: Login, Card, API Secret, Crypto Wallet, Secure Note or Identity.
3. Fill the fields; the editor encrypts everything on save. Optional TOTP secret fields accept
   base32 secrets (minimum 10 bytes) and show live codes.
4. Tap **Save Encrypted**. The item appears in the vault list; tap to view/copy fields.

Long-press or use **⋮ → Select items** for multi-select delete/favourite. Deleted items can be
restored with the **Undo** pill for a few seconds.

## 4. Enable biometric unlock (optional, recommended for convenience)

1. Unlock the vault, go to **Settings → Security Controls**.
2. Enable **Fast Biometric Unlock**. The system shows a biometric prompt to authorize wrapping
   your vault key with a hardware Keystore key.
3. Next time the app opens on the lock screen, the fingerprint/face prompt appears automatically.
   Cancelling falls back to the master password or the lock-screen button.
4. If you re-enroll fingerprints or change the lock screen, the hardware key is invalidated:
   unlock with the master password and enable biometrics again.

Biometrics are a local convenience, not a second factor and not portable to another device.

## 5. Choose your security settings

Open **Settings → Security Controls**:

| Setting | Default | Notes |
| --- | --- | --- |
| Auto-Lock Inactivity | 60 s | 30 s / 1 m / 5 m / 15 m, or Off (manual lock only) |
| Lock in Background | 30 s | Off / 15 s / 60 s; **screen-off locks immediately**; file picker is exempt |
| Auto-Clear Clipboard | 30 s | Off / 15 s / 30 s / 60 s; Off still wipes on vault lock |
| Fast Biometric Unlock | off | Hardware per-use authentication |

Also in Settings:
- **Change Master Password** — re-wraps the vault key; records stay encrypted; recovery kit remains valid.
- **View 12-Word Recovery Kit** — shows the phrase (decrypts it locally).
- **Export / Restore Encrypted Backup**.
- **Appearance**: System / Light / Dark.
- **Reset & Erase Vault** — destructive; back up first.

## 6. Create and verify a backup

Backups are the only way to survive a lost/broken device.

1. Settings → **Export Encrypted Vault Backup**.
2. Choose protection:
   - **Standard** — restorable with the source master password *or* its 12-word recovery phrase.
   - **12-word recovery phrase only** — password envelope is stripped; only that phrase can open it.
3. Choose a destination in the system picker. The app suggests `vaultzero-backup-YYYYMMDD-HHMM.json`.
4. Store the file offline (e.g., encrypted USB drive). The file contains no plaintext titles/secrets,
   but treat it as sensitive.

Restore: Settings → **Restore / Import Encrypted Backup** → pick the file. For a backup from the
same vault you can leave the field blank while the vault is unlocked. For a backup from another
vault enter that vault's **master password or 12-word phrase**. Imports are verified before any
write; duplicates are skipped, changed records update, and you can optionally salvage readable
records from a partially unreadable file.

## 7. Everyday use

- **Vault list:** search (`type:login`, `is:weak`, `tag:work`), filter chips, sort/group, copy
  password/TOTP, open details, edit, delete, undo.
- **Generator:** create passwords/passphrases and save directly to the vault. History is session-only.
- **Audit:** local scoring of weak/reused/stale passwords and 2FA issues; FIX jumps to the editor.
- **2FA dashboard:** vault menu ⋮ → **2FA codes** for live codes across logins.
- **Launcher shortcuts:** long-press the app icon → *New login* / *Generator*.
- **Lock** with the header lock icon or automatically per your settings.

## 8. Daily hygiene

- Keep the OS and device security patches current.
- Keep the recovery phrase offline and separate from backups.
- Re-export after major changes; keep at least one recent backup.
- Do not install re-signed APKs from third parties.
- Disable USB debugging when not developing.

See [FEATURES.md](FEATURES.md) for every screen and [TROUBLESHOOTING.md](TROUBLESHOOTING.md) if
something does not work.
