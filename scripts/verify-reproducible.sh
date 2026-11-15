#!/usr/bin/env bash
# Build the unsigned release twice from the committed tree at two different paths
# and fail unless the APKs are byte-identical.
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

mkdir -p "$TMP/a" "$TMP/b"
git -C "$ROOT" archive HEAD | tar -x -C "$TMP/a"
git -C "$ROOT" archive HEAD | tar -x -C "$TMP/b"

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export SOURCE_DATE_EPOCH=1700000000

for dir in a b; do
  echo "==> Building $dir"
  (
    cd "$TMP/$dir"
    env -u KEYSTORE_PATH -u STORE_PASSWORD -u KEY_PASSWORD \
      ./gradlew --no-daemon --no-build-cache --console=plain :app:assembleRelease
  )
done

APK_A="$(find "$TMP/a/app/build/outputs/apk/release" -maxdepth 1 -name '*.apk' | head -1)"
APK_B="$(find "$TMP/b/app/build/outputs/apk/release" -maxdepth 1 -name '*.apk' | head -1)"
[ -n "$APK_A" ] && [ -n "$APK_B" ] || { echo "APK missing"; exit 1; }

sha256sum "$APK_A" "$APK_B"
if cmp -s "$APK_A" "$APK_B"; then
  echo "REPRODUCIBLE: unsigned release APKs are byte-identical"
else
  echo "NOT REPRODUCIBLE: release APKs differ" >&2
  exit 1
fi
