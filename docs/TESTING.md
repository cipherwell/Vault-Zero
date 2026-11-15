# Testing

VaultZero has a large JVM/Robolectric security suite, a set of on-device instrumentation tests
(including hardware-backed biometrics), and a manual QA checklist for release builds.

## Test inventory (release 1.1.0)

### JVM / Robolectric — 96 tests

| Test class | Tests | Focus |
| --- | ---: | --- |
| `data/VaultRepositorySecurityTest` | 32 | AAD tamper/row-swap rejection, backup tamper/duplicate/merge/partial imports, hostile KDF/salt/envelope inputs, brute-force cooldown, metadata redaction, phrase/password restore, password rotation, last-used persistence/favourites |
| `crypto/VaultCryptoTest` | 19 | AES-GCM round trips/tamper, PBKDF2, recovery phrase checksum, TOTP validity, strength/entropy, generator/passphrase options |
| `ExampleRobolectricTest` | 8 | end-to-end repository/vault flows, backup cross-vault import, clipboard clearing, auto-lock behaviour |
| `core/SearchQueryTest` | 7 | search operators/aliases/combinations |
| `core/GeneratorHistoryTest` | 6 | session history cap/format |
| `ExampleUnitTest` | 6 | small utility/format checks |
| `data/VaultRepositorySetupRestoreTest` | 6 | first-run migration: standard/phrase-only restore, password keep/rotate, wrong phrase, tampered record, no-overwrite |
| `data/VaultRepositoryKdfMigrationTest` | 5 | 100k→600k re-wrap, atomicity, recovery path |
| `core/AuditFilterTest` | 4 | audit search filters/index semantics |
| `crypto/SecurityAuditEngineTest` | 3 | weak/reused/stale/2FA findings |

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
  com.aistudio.vaultzero.secvlt.test/androidx.test.runner.AndroidJUnitRunner
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

## CI

`.github/workflows/release-apk.yml` builds/tests/signs a release on manual dispatch (or tag push
when `RELEASE_ON_TAG=true` and signing secrets exist). It refuses to sign with the debug keystore.
Unit tests and lint are run locally before every release; adding a test-only workflow is
recommended once GitHub Actions usage is confirmed.
