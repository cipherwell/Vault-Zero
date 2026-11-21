# Testing

VaultZero has a large JVM/Robolectric security suite, a set of on-device instrumentation tests
(including hardware-backed biometrics), and a manual QA checklist for release builds.

## Test inventory (release 1.6.0)

### JVM / Robolectric — 119 tests / 14 classes

| Test class | Tests | Focus |
| --- | ---: | --- |
| `data/VaultRepositorySecurityTest` | 34 | AAD tamper/row-swap rejection, backup tamper/duplicate/merge/partial imports, hostile KDF (PBKDF2 count and Argon2id memory)/salt/envelope inputs, brute-force cooldown, metadata redaction, phrase/password restore, password rotation, last-used persistence/favourites |
| `crypto/VaultCryptoTest` | 22 | AES-GCM round trips/tamper, PBKDF2 compatibility, recovery phrase checksum, TOTP validity, strength/entropy, generator/passphrase options |
| `crypto/Argon2KdfTest` | 11 | RFC 9106 Argon2id known-answer vector (secret + associated data), deterministic/salt-bound derivation, Argon2id-vs-PBKDF2 key separation, KDF spec encode/decode round-trip, hostile/malformed spec rejection, PBKDF2 legacy dispatch |
| `ExampleRobolectricTest` | 8 | end-to-end repository/vault flows, backup cross-vault import, clipboard clearing, auto-lock behaviour |
| `core/SearchQueryTest` | 7 | search operators/aliases/combinations |
| `data/VaultRepositorySetupRestoreTest` | 7 | first-run migration: standard/phrase-only restore, password keep/rotate, wrong phrase, tampered record, no-overwrite, 3.2-era PBKDF2 backup restore then Argon2id migration on unlock |
| `ExampleUnitTest` | 6 | small utility/format checks |
| `data/VaultRepositoryKdfMigrationTest` | 5 | PBKDF2 → Argon2id re-wrap on unlock, atomicity, failure paths, recovery path after migration |
| `core/AuditFilterTest` | 4 | audit search filters/index semantics |
| `core/GeneratorHistoryTest` | 4 | session history cap/format |
| `crypto/Bip39VectorsTest` | 3 | all 24 official BIP-39 vectors + 100 random entropy cross-checks against the maintained reference library; tampered checksums rejected |
| `crypto/SecurityAuditEngineTest` | 3 | weak/reused/stale/2FA findings |
| `data/VaultRepositoryRecoveryRotationTest` | 3 | rotation preserves VMK/records/fingerprint/password, retires old phrase, old/new backup phrase compatibility, locked-vault guard |
| `core/VaultGroupingTest` | 2 | grouped-list ordering/counts |

### On-device instrumentation — 8 tests / 4 classes

| Test class | Tests | Focus |
| --- | ---: | --- |
| `crypto/BiometricKeyBindingTest` | 4 | hardware per-use key binding, auth-required flags, invalidated-key handling |
| `data/KdfMigrationDeviceTest` | 2 | on-device PBKDF2 → Argon2id unlock migration and envelope rotation timing |
| `data/VaultSetupDeviceTest` | 1 | full setup, seeded records for UI QA, persisted vault |
| `data/VaultSetupOnlyDeviceTest` | 1 | fresh-install setup and unlock |

Run them:

```bash
./gradlew :app:testDebugUnitTest
# HTML report: app/build/reports/tests/testDebugUnitTest/index.html
```

Robolectric currently runs with `@Config(sdk = [34])` because the Robolectric version does not ship
an Android 36 runtime; SQLite/crypto/Compose state are exercised on that runtime.

### Instrumented / device — 12 tests

| Test class | Tests | Needs |
| --- | ---: | --- |
| `crypto/BiometricKeyBindingTest` | 4 | real device with enrolled biometrics (Keystore behaviour/API 30+) |
| `OfflineSecurityTest` | 3 | any device/emulator |
| `data/KdfMigrationDeviceTest` | 2 | isolated TEST prefs |
| `data/VaultSetupDeviceTest` | 1 | seeds a demo vault (destructive) |
| `data/VaultSetupOnlyDeviceTest` | 1 | clean test vault (dev/QA utility) |
| `ExampleInstrumentedTest` | 1 | package check |

**Do not use `connectedDebugAndroidTest` when device state matters:** Gradle uninstalls the app
after the run, deleting the vault and biometric preference. Install and invoke manually instead:

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest -PallowScreenshots=true
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.example.crypto.BiometricKeyBindingTest \
  com.cipherwell.vaultzero.test/androidx.test.runner.AndroidJUnitRunner
```

The biometric test asserts the Keystore key is per-use auth-required, `doFinal` is rejected
without a prompt (`UserNotAuthenticatedException`/Keystore2's wrapping exception), and no wrapped
entry can be stored/decrypted without an authenticated cipher. It requires a device where
biometrics are enrolled; it clears only its own test enrolment state.

## Manual QA checklist (release)

### Core flows
- [ ] Fresh install → setup validates password length/match; recovery kit displayed once.
- [ ] Create/edit/view/delete each record type; fields round-trip; masked values copy correctly.
- [ ] Lock → unlock with password; wrong password shows error; 5 failures → countdown lockout.
- [ ] Change master password → old password no longer unlocks; new does; recovery phrase still works.
- [ ] Enable biometric unlock → prompt; lock → auto-prompt on reopen; cancel falls back.
- [ ] Search operators (`type:`, `tag:`, `is:weak`, `is:reused`, `is:no2fa`), sort/group, undo delete.
- [ ] Generator password/passphrase options; copy; save to vault; session history.
- [ ] Audit score/findings; FIX opens the editor at the right field.
- [ ] 2FA dashboard codes match an external authenticator; countdown updates; copy works.
- [ ] Launcher shortcuts while locked are deferred until unlock.

### Backups
- [ ] Export standard → file contains only ciphertext/redacted metadata (inspect with `jq`).
- [ ] Import into a fresh vault with master password → records restored.
- [ ] Import same file twice → duplicates skipped; no double entries.
- [ ] Export phrase-only → password import rejected; recovery phrase import succeeds.
- [ ] Tamper one ciphertext byte → import rejected and no partial writes.
- [ ] Backup with one corrupt record → strict rejection; "Import readable records" imports the rest.

### Security/release
- [ ] Release APK installs cleanly; `versionName`/`versionCode` correct.
- [ ] `adb exec-out screencap -p` returns 0 bytes (FLAG_SECURE).
- [ ] `adb shell run-as <pkg>` fails on release (`package not debuggable`).
- [ ] `adb backup -noapk <pkg>` archive contains no app-data entries.
- [ ] Manifest has no INTERNET/NETWORK permissions; only launcher activity exported.
- [ ] Settings: auto-lock, background lock, clipboard Off/timed, theme.
- [ ] Rotation/process death: vault re-locks, editor state handled, no secrets in logs.
- [ ] Release signed with the production upload key (see [RELEASING.md](RELEASING.md)).

### Visual/accessibility
- [ ] Light/dark/system themes on every screen; contrast in dialogs and disabled states.
- [ ] TalkBack labels on icon buttons, chips; touch targets ≥48 dp where practical.
- [ ] Large font/display size: no clipped primary actions.
- [ ] RTL layout sanity.
- [ ] Screenshot pack refreshed (see [SCREENSHOTS.md](SCREENSHOTS.md)).

## Reproducible release build

The unsigned release APK is reproducible across clean builds at different paths. Verify locally:

```bash
./scripts/verify-reproducible.sh
```

The `Reproducibility check` GitHub workflow runs the same script manually. The F-Droid recipe
declares the developer binary and allowed signing cert so F-Droid can verify/copy the signature when
its builder reproduces the build.

## CI

`.github/workflows/release-apk.yml` builds/tests/signs a release on manual dispatch (or tag push
when `RELEASE_ON_TAG=true` and signing secrets exist). It refuses to sign with the debug keystore.
Unit tests and lint are run locally before every release; adding a test-only workflow is
recommended once GitHub Actions usage is confirmed.
