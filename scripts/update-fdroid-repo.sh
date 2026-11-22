#!/usr/bin/env bash
# Rebuild the self-hosted F-Droid repository served from docs/fdroid/repo.
#
# Usage: scripts/update-fdroid-repo.sh <signed-release.apk> <versionName> <versionCode>
# Example: scripts/update-fdroid-repo.sh app/build/outputs/apk/release/app-release.apk 1.7.1 10
#
# Requirements (one-time, not committed):
#   ~/.cipherwell-release/fdroid-repo.jks   repo index signing key (RSA-4096)
#   ~/.cipherwell-release/fdroid-repo.env   exports FDROID_REPO_PASS
#   ~/.local/venvs/fdroid                   python venv with fdroidserver installed
#
# The repo publishes only the current APK (old versions are pruned from the index and directory);
# releases stay available on GitHub Releases.
set -euo pipefail

if [ "$#" -ne 3 ]; then
    echo "usage: $0 <apk> <versionName> <versionCode>" >&2
    exit 2
fi

APK="$(readlink -f "$1")"
VERSION="$2"
CODE="$3"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="${VAULTZERO_FDROID_WORK:-$HOME/.local/share/vaultzero-fdroid}"
FDROID="${FDROID_BIN:-$HOME/.local/venvs/fdroid/bin/fdroid}"
KEYSTORE="$HOME/.cipherwell-release/fdroid-repo.jks"
KEYENV="$HOME/.cipherwell-release/fdroid-repo.env"

for f in "$APK" "$FDROID" "$KEYSTORE" "$KEYENV"; do
    [ -e "$f" ] || { echo "missing required file: $f" >&2; exit 1; }
done
# shellcheck disable=SC1090
source "$KEYENV"
: "${FDROID_REPO_PASS:?FDROID_REPO_PASS not set}"

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}"
export PATH="$JAVA_HOME/bin:$PATH"

mkdir -p "$WORK/repo" "$WORK/metadata" "$WORK/icons"
cd "$WORK"

cat > config.yml <<EOF
repo_url: https://cipherwell.github.io/Vault-Zero/fdroid/repo
repo_name: VaultZero (Cipherwell beta channel)
repo_icon: repo-icon.png
repo_description: |
  VaultZero — an offline, zero-knowledge password manager.
  This is the Cipherwell beta channel: every APK here is signed with the VaultZero upload key.
  No Google Play, no Google account, no Play Services.
archive_older: 0
keystore: $KEYSTORE
repo_keyalias: vaultzero-repo
keystorepass: $FDROID_REPO_PASS
keypass: $FDROID_REPO_PASS
keydname: CN=Cipherwell F-Droid Repo, O=Cipherwell, C=US
androidobservatory: false
EOF
chmod 600 config.yml

cp "$ROOT/fdroid-repo/metadata/com.cipherwell.vaultzero.yml" metadata/
cp "$ROOT/fdroid-repo/repo-icon.png" repo-icon.png

# Keep a single current APK in the repo.
find repo -maxdepth 1 -name '*.apk' ! -name "VaultZero-$VERSION.apk" -delete
cp "$APK" "repo/VaultZero-$VERSION.apk"

"$FDROID" update --pretty

# Publish the generated repo into the Pages tree (docs/fdroid/repo).
mkdir -p "$ROOT/docs/fdroid/repo"
rsync -a --delete --exclude 'status/' "$WORK/repo/" "$ROOT/docs/fdroid/repo/"

echo
echo "Published repository -> docs/fdroid/repo (version $VERSION, code $CODE)"
echo "Repo URL:  https://cipherwell.github.io/Vault-Zero/fdroid/repo"
echo "Fingerprint: BC:CC:45:55:E6:94:84:E3:14:06:32:BA:07:0E:25:63:95:C9:65:F6:EF:FA:FD:74:06:0B:E7:6A:09:82:F9:49"
echo "Next: commit docs/fdroid/repo (and the metadata version bump), push, then verify the Pages URL."
