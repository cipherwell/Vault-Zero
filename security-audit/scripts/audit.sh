#!/usr/bin/env bash
# Audit orchestrator: static + crypto + secrets + optional device pass + report.
#
# Usage:
#   ./scripts/audit.sh --project .. --apk ../app/build/outputs/apk/release/app-release.apk \
#                      --serial emulator-5554 --out results/2026-11-21 [--skip-device] [--fuzz]
set -uo pipefail

PROJECT=".."
APK=""
SERIAL=""
OUT="results/$(date +%F)"
SKIP_DEVICE=0
FUZZ=0
PACKAGE="com.cipherwell.vaultzero"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project) PROJECT="$2"; shift 2 ;;
    --apk) APK="$2"; shift 2 ;;
    --serial) SERIAL="$2"; shift 2 ;;
    --package) PACKAGE="$2"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    --skip-device) SKIP_DEVICE=1; shift ;;
    --fuzz) FUZZ=1; shift ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done

HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$OUT"
echo "audit: project=$PROJECT apk=${APK:-none} out=$OUT"

{
  echo "date: $(date -Is)"
  echo "host: $(uname -a)"
  echo "project: $(cd "$PROJECT" && pwd)"
  if [[ -d "$PROJECT/.git" ]]; then
    echo "git: $(git -C "$PROJECT" rev-parse HEAD 2>/dev/null) $(git -C "$PROJECT" status --porcelain | wc -l) dirty files"
  fi
  if [[ -n "$APK" && -f "$APK" ]]; then
    echo "apk: $(stat -c%s "$APK") bytes sha256 $(sha256sum "$APK" | cut -d' ' -f1)"
    if command -v aapt2 >/dev/null; then
      aapt2 dump badging "$APK" 2>/dev/null | grep -E "^package:|sdkVersion|targetSdkVersion|uses-permission" | head -20
    fi
  fi
} | tee "$OUT/environment.txt"

echo "== static inventory"
python3 "$HERE/static_audit.py" --project "$PROJECT" --json "$OUT/static.json" | tee "$OUT/static.log"

echo "== crypto review"
python3 "$HERE/crypto_review.py" --project "$PROJECT" --json "$OUT/crypto.json" | tee "$OUT/crypto.log"

echo "== secret scan (source)"
python3 "$HERE/secret_scan.py" --path "$PROJECT" --json "$OUT/secrets.json" | tee "$OUT/secrets.log"

if [[ -n "$APK" && -f "$APK" ]]; then
  echo "== secret scan (apk)"
  python3 "$HERE/secret_scan.py" --apk "$APK" --json "$OUT/secrets-apk.json" | tee "$OUT/secrets-apk.log"
fi

if [[ $FUZZ -eq 1 ]]; then
  echo "== fuzz corpus"
  python3 "$HERE/fuzz_backup.py" --out "$OUT/fuzz-corpus" | tee "$OUT/fuzz.log"
fi

if [[ $SKIP_DEVICE -eq 0 && -n "$SERIAL" ]]; then
  echo "== device audit"
  "$HERE/device_audit.sh" --serial "$SERIAL" --package "$PACKAGE" --out "$OUT/device" | tee "$OUT/device.log"
else
  echo "== device audit skipped"
fi

echo "== report"
python3 "$HERE/report.py" --results "$OUT" --out "$OUT/REPORT.md" \
  --version "$(grep -oE 'versionName = "[^"]+"' "$PROJECT/app/build.gradle.kts" 2>/dev/null | head -1 | cut -d'"' -f2)" \
  --code "$(grep -oE 'versionCode = [0-9]+' "$PROJECT/app/build.gradle.kts" 2>/dev/null | head -1 | grep -oE '[0-9]+')" \
  --apk "$APK"

echo
echo "audit complete -> $OUT/REPORT.md"
grep -E "^\| (VZ|—)" "$OUT/REPORT.md" | head -20 || true
