# Dynamic testing recipes

Assumes a **test** device/emulator. Never run these against a phone with real vaults.

## 1. Environment

```bash
export ANDROID_HOME=$HOME/Android/Sdk
adb devices
# QA build allows screenshots; never ship it
./gradlew :app:assembleRelease -PallowScreenshots=true
adb install -r app/build/outputs/apk/release/app-release.apk
```

For memory/Frida work: emulator with `-writable-system` or a rooted test device; install
`frida-server` matching the ABI, then `frida-ps -U`.

## 2. Package facts

```bash
adb shell dumpsys package com.cipherwell.vaultzero | \
  grep -E "version|debuggable|flags|permission|exported|dataDir"
adb shell pm path com.cipherwell.vaultzero
adb pull $(adb shell pm path com.cipherwell.vaultzero | sed 's/package://') base.apk
```

## 3. Storage inspection (debug builds only)

```bash
adb shell run-as com.cipherwell.vaultzero ls -laR .
adb exec-out run-as com.cipherwell.vaultzero cat shared_prefs/vaultzero_prefs.xml
adb exec-out run-as com.cipherwell.vaultzero cat databases/vault.db > /tmp/v.db
strings -n 5 /tmp/v.db | sort -u | head -80
```

On release builds `run-as` must fail — record that as a pass.

## 4. Logcat

```bash
adb logcat -c
# exercise create/unlock/copy/export/import/lock
adb logcat -d -v brief | grep -Ei "password|secret|vault|key|token|otp|share|prefs|decrypt"
```

Pass: no secret values. Look for `VaultCrypto`, `VaultRepository`, exception messages.

## 5. Screenshots and recents

```bash
adb exec-out screencap -p > /tmp/app.png; ls -l /tmp/app.png   # expect 0 bytes / black
adb shell input keyevent APP_SWITCH; sleep 1
adb exec-out screencap -p > /tmp/recents.png
```

QA build (screenshots allowed) is for visual evidence only.

## 6. Intents and components

```bash
adb shell am start -n com.cipherwell.vaultzero/.MainActivity --es password test
adb shell am start -a android.intent.action.VIEW -d "vaultzero://x"
adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT hi
adb shell dumpsys activity activities | grep -i vaultzero
```

Expect: no crash, no state change, no secret echo.

## 7. Biometrics on emulator

```bash
adb -e emu finger touch 1        # valid fingerprint
adb -e emu finger touch 999      # invalid
```

Flow checks: enable biometrics, lock, unlock with fingerprint; cancel prompt; fail 5×; re-enrol a
fingerprint (`adb emu finger remove 1` + add) and verify the key is invalidated.

## 8. Auto-lock / lifecycle

```bash
adb shell input keyevent HOME; sleep 35; adb shell am start -n com.cipherwell.vaultzero/.MainActivity
adb shell am kill com.cipherwell.vaultzero
adb shell am force-stop com.cipherwell.vaultzero
```

Expect re-auth after timeout/kill; no plaintext in recents.

## 9. Backup import drive

Manual: push a corpus to `/sdcard/Download/`, then use the in-app import picker.

```bash
adb push fuzz-corpus/ /sdcard/Download/vz-fuzz/
adb shell am start -n com.cipherwell.vaultzero/.MainActivity
```

Record per file: accepted, time, error text, item count before/after. Watch `adb logcat` for OOM/ANR.

## 10. Frida starters

```javascript
// hook the KDF to log parameters (never log the password in a real report)
Java.perform(() => {
  const VC = Java.use('com.example.crypto.VaultCrypto');
  VC.deriveKey.overload('java.lang.String','java.lang.String','int','int').implementation = function (p, s, m, t) {
    console.log('KDF params m=' + m + ' t=' + t);
    return this.deriveKey(p, s, m, t);
  };
});
```

```javascript
// confirm BiometricPrompt requires a CryptoObject
Java.perform(() => {
  const BP = Java.use('androidx.biometric.BiometricPrompt');
  BP.authenticate.overload('androidx.biometric.BiometricPrompt$CryptoObject').implementation = function (c) {
    console.log('CryptoObject present: ' + (c !== null));
    return this.authenticate(c);
  };
});
```

## 11. DoS / resource checks

- Import a 100 MB backup; measure time and memory (`adb shell dumpsys meminfo`).
- 100k items; scroll performance and import time.
- Repeated failed unlocks; UI responsiveness.
- Kill mid-import; verify DB integrity next launch.

## 12. Evidence collection

```bash
mkdir -p results/device
adb exec-out screencap -p > results/device/screen.png
adb logcat -d > results/device/logcat.txt
adb shell dumpsys package com.cipherwell.vaultzero > results/device/package.txt
adb shell run-as com.cipherwell.vaultzero ls -laR . > results/device/files.txt
```

Redact any real secret before committing results.
