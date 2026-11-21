# Architecture

VaultZero is a single-activity Android app written in Kotlin with Jetpack Compose. It is
intentionally small and offline: the security-critical surface is a handful of `crypto/` and
`data/` classes, while the UI is stateless where possible.

## Tech stack

| Component | Version/choice |
| --- | --- |
| Language | Kotlin 2.2.10 |
| UI | Jetpack Compose (BOM 2024.09.00), Material 3, Material Icons |
| Architecture | Single `MainActivity` + one `VaultViewModel`; repository/data layer |
| Persistence | Room 2.7.0 (KSP), SharedPreferences (KTX) for envelopes/settings |
| Crypto | JCA/JCE AES-256-GCM + Argon2id (Bouncy Castle) with PBKDF2 legacy support, `SecureRandom`, Keystore |
| Biometrics | `androidx.biometric` 1.2.0-alpha05, `BiometricPrompt.CryptoObject` |
| Build | AGP 9.1.1, Gradle 9.3.1, KSP 2.3.5, JDK 17 |
| SDK | compile/target 36, min 24 |
| Tests | JUnit4 + Robolectric + Compose/Espresso instrumentation |

There is no DI framework, no navigation-compose usage, no networking library and no
Google Play Services dependency. See `app/build.gradle.kts` and `gradle/libs.versions.toml`.

## Module/layer map

```
app/src/main/java/com/example/
├── MainActivity.kt          Single Activity; Compose host; FLAG_SECURE; SAF picker bridge;
│                            biometric prompt launch; auto/biometric/background lock; shortcuts
├── core/                    Pure/testable helpers: SearchQuery, AuditFilter, VaultGrouping,
│                            GeneratorHistory, ThemeMode
├── crypto/                  VaultCrypto (AES-GCM, Argon2id/PBKDF2 KDF specs, TOTP, recovery, strength, generator),
│                            BiometricKeyManager, SecurityAuditEngine
├── data/                    VaultDatabase/Room, VaultDao, VaultItemEntity, VaultRepository,
│                            VaultPreferences, BackupImportReport
├── model/                   VaultItem + typed payloads, audit models
└── ui/
    ├── VaultViewModel.kt    All UI state (StateFlow), locking, audit, search, editor state
    ├── screens/             Lock, Vault list, Generator, Audit, Settings, detail/editor/TOTP dialogs
    ├── components/          Reusable Compose pieces (icons, strength meter, badges, status pill)
    ├── backup/              BackupFileIo (SAF read/write, 8 MiB cap), picker channel bridge
    └── theme/               One UI-inspired colour/type/shape tokens
```

## Data flow

### Unlock
```
Master password ──Argon2id(salt,64MiB,t=3)──► password KEK
                    (PBKDF2 600k for pre-1.6.0 vaults, migrated on unlock)
Vault prefs: encryptedVmkByPassword ──AES-GCM decrypt──► VMK (in-memory SecretKey)
DAO records ──AES-GCM decrypt + AAD check──► VaultItem models ──► allItems StateFlow
```
`VaultRepository.lock()` drops the in-memory key; the UI observes `VaultLockState`.

### Read path
`VaultDao.getAllFlow()` → `VaultRepository.getVaultItemsFlow()` decrypts each entity
(`decryptRecordEntity`, v2 envelope first with legacy fallback) → `VaultViewModel.allItems`
→ `filteredItems` (`combine(allItems, searchSpec, type, favourites, sort)`) → Compose list.
Titles are redacted in SQLite, so sorting happens after decryption (favourites first, then title).

### Write path
Editor → `VaultViewModel.saveItem()` → `VaultRepository.saveItem()`:
build v2 JSON envelope (metadata + payload) → AES-256-GCM with
`VaultZero_Record_v2:<id>:<type>` AAD → store ciphertext + redacted columns via Room.

### Lock/timeouts
`VaultViewModel` owns `autoLockJob`, `clipboardJob`, `deletedExpiryJob`, a 1 s `clockTicker`
(TOTP countdown + auto-lock check). `MainActivity` adds lifecycle background locking, screen-off
receiver, and a picker exemption flag so SAF round-trips never strand the vault.

### Backups
`VaultRepository.exportEncryptedBackup()` builds a JSON envelope (version 3.2) with the VMK
wrapped by the password/recovery envelopes and per-record ciphertext. The UI writes it through
SAF. Import runs verify → classify (new/updated/duplicate) → encrypt surviving records → single
Room transaction. Format details: [BACKUP-FORMAT.md](BACKUP-FORMAT.md).

### First-run restore
`LockScreen` requests the SAF import picker, reads the envelope through `BackupFileIo`, and passes
it plus the recovery phrase to `VaultViewModel.restoreBackupAtSetup`. `VaultRepository` validates
the envelope, unwraps the source VMK from the recovery envelope, authenticates every record, and
calls `VaultDao.replaceAllForRestore` (one Room transaction). Credentials are committed after the
records through the atomic `saveEnvelopeCredentials` write; the active VMK is installed only after
both complete, so a failure leaves the app uninitialized and retryable.

### Recovery-kit rotation
`VaultViewModel.regenerateRecoveryKit()` calls `VaultRepository.regenerateRecoveryKit()`, which
generates a fresh BIP-39 phrase/salt, derives a new recovery KEK, re-wraps the same VMK, and
atomically replaces only the recovery fields via `VaultPreferences.rotateRecoveryEnvelope()`.
Records, password envelope, VMK and fingerprint are untouched.

## Database schema (Room v2)

`vault_items` — one row per record; all sensitive metadata is inside `encryptedPayload`.

| Column | Type | Notes |
| --- | --- | --- |
| `id` | TEXT PK | UUID; also part of AAD |
| `title` | TEXT | always `[ENCRYPTED]` |
| `type` | TEXT | enum name (needed for AAD/decrypt routing) |
| `category` | TEXT | always `Encrypted` |
| `encryptedPayload` | TEXT | base64(iv||ciphertext+tag) |
| `isFavorite` | INTEGER | non-secret flag; export/import preserved |
| `createdAt` / `updatedAt` | INTEGER | epoch ms |
| `lastUsedAt` | INTEGER | non-secret; drives "Recently used" sort |
| `tagsJson` | TEXT | always `[]` in DB |

Migration `1 → 2`: `ALTER TABLE vault_items ADD COLUMN lastUsedAt INTEGER NOT NULL DEFAULT 0`
(non-destructive, validated by Room schema on first open).

## State management rules

- UI reads `StateFlow`s from the ViewModel; no crypto or DB calls directly from composables.
- Secrets stay in memory only while unlocked. `lockVault()` clears selection, editor/view state,
  generator history, clipboard ownership, undo batches, pending biometric material.
- Expensive work (KDF, decrypt, import/export) runs off the main thread via `Dispatchers.Default`
  or `IO` in the repository.
- One error channel: `authError`/status message for user-visible failures; no secret in messages.

## Background/lifecycle integration

| Event | Behaviour |
| --- | --- |
| App open/resume on lock screen | Auto biometric prompt if enabled (one attempt per lock) |
| Screen off | Immediate lock when background lock is enabled |
| Background > configured delay | Lock on return; SAF picker round-trip exempt |
| Launcher shortcut while locked | Action deferred until unlock |
| Process death | Vault re-locks; DB contents remain encrypted |

## Extending safely

1. New record type: add `model` payload + editor fields + `VaultType` enum + icon; AAD uses the
   enum name, so changing names breaks existing records — never rename existing types.
2. New settings: prefer `StateFlow` + async SharedPreferences write; security-relevant values use
   atomic commits.
3. New persistent columns: bump Room version + explicit migration; add export/import mapping and
   tests.
4. Never add networking, cloud backup APIs, telemetry or plaintext logs. See
   [CONTRIBUTING.md](../CONTRIBUTING.md).
