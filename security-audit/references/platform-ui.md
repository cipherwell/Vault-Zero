# Platform, intents and UI security

## 1. Manifest / IPC

- Only `MainActivity` exported, MAIN/LAUNCHER. No deep links unless documented and validated.
- No `android:sharedUserId`, no `android:debuggable`, no `testOnly`.
- `android:allowBackup="false"`; `dataExtractionRules` excludes everything (cloud + device-to-device).
- No `android:exported="true"` providers/services/receivers.
- No implicit broadcasts with secrets; no `PendingIntent` mutable flags (`FLAG_IMMUTABLE` required).
- Task hijacking: check `launchMode`, `taskAffinity`, `allowTaskReparenting`; an exported launcher
  activity is normally fine, but a malicious app can start it — ensure no intent extras are trusted.

Attack commands:

```bash
adb shell am start -n com.cipherwell.vaultzero/.MainActivity
adb shell am start -n com.cipherwell.vaultzero/.MainActivity --es master_password x
adb shell am start -a android.intent.action.VIEW -d "vaultzero://import?file=/sdcard/x"
adb shell am startservice -n com.cipherwell.vaultzero/.AnyService
adb shell am broadcast -a com.cipherwell.vaultzero.ANY
adb shell dumpsys package com.cipherwell.vaultzero | grep -E "exported|permission"
```

Every unsupported entry point must return "Activity not found"/security exception, not a crash.

## 2. Clipboard

- Copy actions show a toast/Snackbar that does not include the secret.
- Auto-clear after the configured timeout (test 0/15/30/60 s).
- Clear on lock and on process background if policy says so.
- Clipboard is readable by other apps pre-Android 10; treat as world-readable during the TTL.
- Do not copy secrets automatically on screen open.

Test: copy an item, poll `adb shell cmd clipboard get` (API 29+ may restrict; use a helper app or
Frida hook), verify cleared after TTL and on lock.

## 3. Screenshots / recents

- `FLAG_SECURE` set on the window before the first frame.
- Recents preview blank (check with `dumpsys activity` screenshot or visual).
- QA builds only: `-PallowScreenshots=true` removes it; never distribute that build.
- Verify shipped APK: `adb exec-out screencap -p > /tmp/x.png` → 0 bytes or black.

## 4. Tapjacking / overlays

- Sensitive buttons (unlock, export, delete, reveal password) should ignore touches when obscured:
  `Modifier.filterTouchesWhenObscured` / `View.setFilterTouchesWhenObscured(true)`.
- Test with a small overlay app or `adb shell appops` + a debug overlay.
- If not implemented, rate by exposure: password entry is the highest-value target.

## 5. UI privacy

- Notifications: none containing secrets; channels documented.
- Toasts: no secrets.
- Accessibility: text is exposed to accessibility services (OS-level); document accepted risk.
- IME: password fields should use `KeyboardType.Password` and disable personalized learning where
  possible (`IME_FLAG_NO_PERSONALIZED_LEARNING`); document accepted risk.
- Autofill: do not offer to save the master password; check `importantForAutofill`.
- Screen readers: labels do not read secrets aloud unintentionally (e.g., reveal toggle).
- Locale: no truncation of security labels (see v1.7.10 fix).

## 6. Biometric flow

- Only system `BiometricPrompt` (no custom fingerprint UI that could be overlaid).
- `CryptoObject` binds auth to the exact cipher; no `onAuthenticationSucceeded` path that decrypts
  with a key not authorized by the prompt.
- Negative button = "Use master password"; cancel returns to locked state.
- Too many attempts → fallback to password, not lockout bypass.
- Re-enrolment invalidates the key (test on device).

## 7. Lifecycle / task

- Background → recents: no secret in snapshot.
- Process death while unlocked: reopening requires password (no auto-unlock from saved state).
- Rotation: no secret survives in saved instance state (`SavedStateHandle` review).
- Multi-window / picture-in-picture: app should not allow PiP with secrets; check manifest.
- `adb shell am force-stop` mid-flow: no corrupt DB, no plaintext temp files.

## 8. Error messages and logging

- No stack traces or exception class names in user-facing errors.
- No secret values in `Log.*`, `println`, crash reports, or diagnostics.
- Diagnostics report may include version/device/language/vault state; never item data or keys.

## 9. Backup and restore UX security

- Import confirmation shows file name, size, item count (after parsing) and warns about foreign
  vaults.
- No "auto-import from Downloads" behavior.
- Failed import must not leave the vault in a half state; report exactly what was imported.
