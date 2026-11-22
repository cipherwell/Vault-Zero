#!/usr/bin/env bash
# On-device security checks for VaultZero (or another Android app).
# Read-only: installs nothing, clears nothing, never deletes app data.
#
# Usage: ./device_audit.sh --serial emulator-5554 --package com.cipherwell.vaultzero --out results/device
set -uo pipefail

SERIAL=""
PACKAGE="com.cipherwell.vaultzero"
OUT="results/device"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial) SERIAL="$2"; shift 2 ;;
    --package) PACKAGE="$2"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done
mkdir -p "$OUT"
ADB=(adb)
[[ -n "$SERIAL" ]] && ADB=(adb -s "$SERIAL")

say() { printf '\n== %s\n' "$*"; }
run() { "$@" 2>&1; }

say "device"
run "${ADB[@]}" shell getprop ro.build.version.sdk | tee "$OUT/sdk.txt"
run "${ADB[@]}" shell getprop ro.build.version.release | tee "$OUT/release.txt"
run "${ADB[@]}" shell getprop ro.product.model | tee "$OUT/model.txt"

say "package"
run "${ADB[@]}" shell dumpsys package "$PACKAGE" > "$OUT/package.txt"
grep -E "versionName|versionCode|debuggable|flags=|dataDir|exported=" "$OUT/package.txt" | head -40

say "components (exported)"
run "${ADB[@]}" shell dumpsys package "$PACKAGE" | sed -n '/Activity Resolver Table/,/^$/p' > "$OUT/activities.txt"
grep -E "exported=true|permission" "$OUT/package.txt" | head -40

say "path + apk hash"
APK_PATH="$("${ADB[@]}" shell pm path "$PACKAGE" | head -1 | sed 's/package://' | tr -d '\r')"
echo "$APK_PATH" | tee "$OUT/apk_path.txt"
if [[ -n "$APK_PATH" ]]; then
  "${ADB[@]}" pull "$APK_PATH" "$OUT/base.apk" >/dev/null 2>&1 || true
  [[ -f "$OUT/base.apk" ]] && sha256sum "$OUT/base.apk" | tee "$OUT/base.apk.sha256"
fi

say "run-as (must fail on release)"
if "${ADB[@]}" shell run-as "$PACKAGE" id > "$OUT/run_as.txt" 2>&1; then
  echo "RUN-AS SUCCEEDED — build is debuggable or test-signed" | tee "$OUT/run_as_finding.txt"
  "${ADB[@]}" shell run-as "$PACKAGE" ls -laR . > "$OUT/files.txt" 2>&1 || true
else
  echo "run-as denied (expected for release)" | tee "$OUT/run_as.txt"
fi

say "screenshot / FLAG_SECURE"
"${ADB[@]}" shell input keyevent WAKEUP >/dev/null 2>&1 || true
"${ADB[@]}" exec-out screencap -p > "$OUT/screencap.png" 2>/dev/null || true
if [[ -s "$OUT/screencap.png" ]]; then
  echo "screencap bytes: $(stat -c%s "$OUT/screencap.png") (QA build or no FLAG_SECURE)" | tee "$OUT/screencap.txt"
else
  echo "screencap empty/black — FLAG_SECURE active (expected)" | tee "$OUT/screencap.txt"
fi

say "logcat snapshot (app pid only when possible)"
APP_PID="$("${ADB[@]}" shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' | awk '{print $1}')"
echo "pid: ${APP_PID:-none}" | tee "$OUT/logcat_pid.txt"
if [[ -n "$APP_PID" ]]; then
  "${ADB[@]}" logcat -d -v brief --pid="$APP_PID" > "$OUT/logcat.txt" 2>&1 || true
else
  "${ADB[@]}" logcat -d -v brief > "$OUT/logcat.txt" 2>&1 || true
fi
grep -Ei "password|secret|vaultkey|decrypt|biometric|token|master" "$OUT/logcat.txt" | head -40 | tee "$OUT/logcat_hits.txt" || true
echo "hits: $(wc -l < "$OUT/logcat_hits.txt" 2>/dev/null || echo 0)"

say "build hardening from dumpsys flags"
if grep -q "DEBUGGABLE" "$OUT/package.txt"; then
  echo "DEBUGGABLE flag present — finding" | tee "$OUT/debuggable.txt"
else
  echo "not debuggable (no DEBUGGABLE flag)" | tee "$OUT/debuggable.txt"
fi

say "resolve launcher component"
COMPONENT="$("${ADB[@]}" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "$PACKAGE" 2>/dev/null | tail -1 | tr -d '\r')"
echo "$COMPONENT" | tee "$OUT/launcher_component.txt"

say "intent probes (no state change expected)"
{
  echo "--- start MainActivity ($COMPONENT)"
  [[ -n "$COMPONENT" ]] && "${ADB[@]}" shell am start -W -n "$COMPONENT" 2>&1
  echo "--- start with crafted extra"
  [[ -n "$COMPONENT" ]] && "${ADB[@]}" shell am start -n "$COMPONENT" --es master_password TEST-ONLY 2>&1
  echo "--- implicit VIEW deep link"
  "${ADB[@]}" shell am start -a android.intent.action.VIEW -d "vaultzero://import?file=/sdcard/Download/x" 2>&1
  echo "--- implicit SEND"
  "${ADB[@]}" shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT TEST 2>&1
} > "$OUT/intents.txt" 2>&1
grep -Ei "Error|Exception|not found|does not exist" "$OUT/intents.txt" | head -20

say "clipboard (best effort; OS may restrict)"
"${ADB[@]}" shell cmd clipboard get 2>&1 | head -5 > "$OUT/clipboard.txt" || true
cat "$OUT/clipboard.txt"

say "process / memory snapshot"
"${ADB[@]}" shell dumpsys meminfo "$PACKAGE" > "$OUT/meminfo.txt" 2>&1 || true
head -20 "$OUT/meminfo.txt"

say "backup / cleartext from the pulled APK (host aapt2 if available)"
AAPT2_BIN="${AAPT2:-$(command -v aapt2 || true)}"
if [[ -z "$AAPT2_BIN" && -x "$HOME/Android/Sdk/build-tools/36.0.0/aapt2" ]]; then
  AAPT2_BIN="$HOME/Android/Sdk/build-tools/36.0.0/aapt2"
fi
if [[ -f "$OUT/base.apk" && -n "$AAPT2_BIN" && -x "$AAPT2_BIN" ]]; then
  "$AAPT2_BIN" dump xmltree --file AndroidManifest.xml "$OUT/base.apk" 2>/dev/null |     grep -E "allowBackup|usesCleartextTraffic|debuggable|exported" | head -20 | tee "$OUT/manifest_flags.txt"
else
  echo "aapt2 or base.apk unavailable; run scripts/static_audit.py for merged-manifest flags" | tee "$OUT/manifest_flags.txt"
fi

say "done"
echo "evidence in $OUT"
