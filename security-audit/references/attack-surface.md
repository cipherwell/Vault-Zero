# Attack surface — VaultZero specifics

Use with `static_audit.py` output. Every row needs a test and a verdict.

## 1. Manifest and components

Current expected state (verify against the **merged** manifest, not source):

- `MainActivity` is the only exported component; `exported=true` with MAIN/LAUNCHER only.
- No services, providers, receivers, widgets, tiles, share targets, deep links.
- Permissions: `USE_BIOMETRIC` only. `INTERNET` and `ACCESS_NETWORK_STATE` explicitly removed.
- `allowBackup=false`, `dataExtractionRules` + `fullBackupContent` present.
- `usesCleartextTraffic=false`, `networkSecurityConfig` present (belt and braces; no network code).
- ProfileInstaller receiver removed (`tools:node="remove"`).

Tests:

```bash
aapt2 dump xmltree --file AndroidManifest.xml app-release.apk
adb shell dumpsys package com.cipherwell.vaultzero | sed -n '/Activity Resolver Table/,/^$/p'
adb shell am start -n com.cipherwell.vaultzero/.MainActivity --es foo bar   # no crash/state change
adb shell am start -a android.intent.action.SEND -t text/plain ...          # should not resolve
```

Finding leads: any exported component added later; `taskAffinity`/`launchMode` abuse; `excludeFromRecents` absence.

## 2. On-device storage

| Path | Expected content | Test |
| --- | --- | --- |
| `/data/data/<pkg>/databases/vault.db` (+ `-wal`, `-shm`) | item ciphertext only | `run-as` on debug; strings must not show plaintext |
| `/data/data/<pkg>/shared_prefs/*.xml` | KDF specs, envelope, flags | no VMK, no password, no recovery phrase |
| `/data/data/<pkg>/files/` | exports only if user saved there | inspect |
| Keystore | biometric-wrapped VMK, `setUserAuthenticationRequired`, `setInvalidatedByBiometricEnrollment` | code review + re-enrol test |
| Cache / noBackup | no secrets | inspect |
| Clipboard | passwords | TTL clear, no clipboard listener leaks |

Also check `dumpsys activity processes` for `android:debuggable`, and `run-as` on the release build
(should fail).

## 3. Backup / restore (the highest-risk file parser)

Attack classes:

1. **KDF downgrade** — envelope claims `argon2id:8192:1:1` or `pbkdf2:10000`; is it accepted? What is
   the floor for imported files vs. created files?
2. **Resource exhaustion** — huge base64 fields, millions of items, 10 MB of unknown keys, deeply
   nested JSON, repeated keys, wrong types (number where string expected).
3. **Path traversal / overwrite** — export filename with `../`, absolute paths, symlinks, existing
   files; import from a directory the app cannot read.
4. **Integrity** — flip a ciphertext bit, tag byte, AAD, salt, IV; must fail cleanly, never return
   plaintext or a partial import.
5. **Semantic confusion** — mixed record types, duplicate IDs, negative timestamps, invalid TOTP
   secrets, oversized strings, Unicode/RTL/control characters.
6. **UI spoofing** — import summary says "0 items" while importing 10k; filename shown untrusted.

Oracles: no crash/ANR, no data loss, bounded memory/time, clear localized error, no partial state
unless explicitly reported.

## 4. Cryptography entry points

- Password → KEK (`VaultCrypto.deriveKey`), per-envelope `KdfSpec` parsing/validation.
- KEK wraps VMK (`AES-GCM`, 12-byte IV, 128-bit tag, AAD).
- Item encryption with VMK; AAD must bind item identity/type.
- Backup envelope: salt, IV, KDF spec, ciphertext, version; export/import round-trip.
- Recovery kit: BIP-39 128-bit, checksum, normalization (NFKD), wrong-word handling.
- Biometric: Keystore key requires auth; `CryptoObject` authorizes the exact cipher; re-enrol
  invalidation.

## 5. UI / platform surfaces

- `FLAG_SECURE` on every activity window; recents preview blank.
- Clipboard: copy actions, auto-clear timeout, clear on lock/exit, no sensitive toast text.
- Autofill: does the app expose fields to autofill? (Should not offer to save the master password.)
- Tapjacking: `filterTouchesWhenObscured` or equivalent for sensitive buttons (unlock, export).
- Overlays: `TYPE_APPLICATION_OVERLAY` apps can draw over the app; sensitive actions should resist.
- Screenshot QA builds only with `-PallowScreenshots=true`.
- Notifications: none with secrets; check channels.
- Keyboard: IME can see master password; document as accepted (system IME) or recommend
  `IME_FLAG_NO_PERSONALIZED_LEARNING` / secure input where available.
- Accessibility services can read text; accepted OS risk — document.

## 6. Build and distribution

- `debuggable=false`, `testOnly=false`, R8 enabled, resource shrinking, no `-dontwarn` masks.
- Signature scheme v2/v3; certificate pinning not applicable (no network).
- `versionCode` monotonic; release notes hash matches the artifact.
- F-Droid recipe pinned to a commit; repo index signature valid.
- No secrets in `BuildConfig`, resources, or `strings`.

## 7. Test matrix

| Area | Emulator API 34 | Emulator API 24/26 | Real device (Pixel) |
| --- | --- | --- | --- |
| Install/upgrade | yes | yes | yes |
| Vault create/unlock | yes | yes | yes |
| Biometrics | simulated fingerprint | may be unavailable | enrolled fingerprint |
| Clipboard | yes | yes | yes |
| Screenshots (QA build) | yes | yes | yes |
| Backup import fuzz | yes | yes | yes |
| Auto-lock | yes | yes | yes |
| Locale EN/ES | yes | yes | yes |
