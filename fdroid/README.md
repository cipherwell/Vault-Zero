# Submitting VaultZero to F-Droid

**Status:** F-Droid submission is **deferred** until the public repo passes F-Droid’s 3-month maintenance rule (Quality point 6). The earlier New App MR was closed for that policy and for not using the template. Resubmit after ~21 January 2027 with the filled template, full commit hash, AuthorEmail and `New App` label. Builds and the recipe below are otherwise ready.

- **Package ID:** `com.aistudio.vaultzero.secvlt`
- **Version to submit:** `1.4.0` (`versionCode` 6), tag **`v1.4.0`** (new Cipherwell signing identity)
- **License:** MIT (recognized FOSS)
- **Source/issue tracker:** https://github.com/cipherwell/Vault-Zero
- **Website:** https://cipherwell.github.io/Vault-Zero/
- **F-Droid recipe:** [`com.aistudio.vaultzero.secvlt.yml`](com.aistudio.vaultzero.secvlt.yml)
- **Build pattern:** standard Gradle — `gradle: - yes` with `subdir: app` (F-Droid runs Gradle in `app/`; the parent Gradle settings are resolved automatically, and the resulting APK is found in `app/build/outputs/apk/release`)
- **Network/signing requirements:** none; release builds become **unsigned** when no production
  keystore environment is present, so F-Droid can sign with its own key.

## Why this app is a good F-Droid fit

- MIT-licensed source, no proprietary dependencies, no Google Play Services/Firebase.
- No `INTERNET`/network permission, no ads, analytics, trackers or accounts (no anti-features).
- Fastlane metadata already in the repo (title, short/full description, icon, feature graphic,
  screenshots, per-version changelogs).
- Every release is tagged; `AutoUpdateMode: Version` + `UpdateCheckMode: Tags`.
- Security design and a self-audit are documented in `docs/` and `SECURITY.md`.

## Signature warning — read before choosing a channel

Android requires updates to be signed by the same key. The GitHub release is signed with the
project's **upload key**; F-Droid signs its own build with the **F-Droid key**. Therefore:

- You cannot update a GitHub-signed install with the F-Droid build (or vice versa); Android returns
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
- Pick one distribution channel per device. To switch, export an encrypted backup (plus the 12-word
  recovery phrase) from the old install, uninstall it, install the other build, then restore.
- Vault contents and backups are portable; app-local preferences/biometric enrolment are not.
- For a real migration, keep the old app installed until the new one restores successfully on a
  spare device/profile.

## 1. Fork and clone `fdroiddata`

Create a GitLab account and fork <https://gitlab.com/fdroid/fdroiddata> in the GitLab UI, then:

```bash
git clone --depth=1 https://gitlab.com/<YOUR_GITLAB_USER>/fdroiddata.git
cd fdroiddata
git checkout -b com.aistudio.vaultzero.secvlt
mkdir -p metadata
cp /path/to/vault-zero-app/fdroid/com.aistudio.vaultzero.secvlt.yml \
   metadata/com.aistudio.vaultzero.secvlt.yml
```

The recipe must be copied unchanged unless you deliberately bump the version (keep
`commit: v1.4.0` and `CurrentVersion: 1.4.0` aligned).

## 2. Validate the metadata (recommended)

> Local validation performed for this recipe: `fdroid readmeta`, `fdroid rewritemeta` and
> `fdroid lint` pass, and `fdroid build --test --no-refresh --no-tarball
> com.aistudio.vaultzero.secvlt:6` successfully built the unsigned APK from tag v1.4.0 with
> fdroidserver 2.4.2 (Gradle 9.3.1, JDK 17, Android SDK 36).

F-Droid's CI will build it, but local linting saves a review round-trip. The documented server-tools
container approach:

```bash
git clone --depth=1 https://gitlab.com/fdroid/fdroidserver ~/fdroidserver
sudo apt-get install -y docker.io   # if not present
sudo docker run --rm -itu vagrant --entrypoint /bin/bash \
  -v ~/fdroiddata:/build:z \
  -v ~/fdroidserver:/home/vagrant/fdroidserver:Z \
  registry.gitlab.com/fdroid/fdroidserver:buildserver
```

Inside the container:

```bash
. /etc/profile
export PATH="$fdroidserver:$PATH" PYTHONPATH="$fdroidserver"
export JAVA_HOME=$(java -XshowSettings:properties -version 2>&1 > /dev/null \
  | grep 'java.home' | awk -F'=' '{print $2}' | tr -d ' ')
cd /build
fdroid readmeta
fdroid rewritemeta com.aistudio.vaultzero.secvlt
fdroid lint com.aistudio.vaultzero.secvlt
fdroid build com.aistudio.vaultzero.secvlt
```

Notes for this app:

- The build needs **JDK 17+**, Android SDK **36**, and network access for Gradle/Maven on the
  builder.
- The release variant is unsigned when `KEYSTORE_PATH` is unset; F-Droid signs afterwards.
- The Gradle wrapper and version catalog are committed. F-Droid removes the wrapper jar and uses
  its own Gradle; `subdir: app` is required so F-Droid looks in `app/build/outputs` (verified with
  fdroidserver 2.4.2).
- Test-only dependency `cash.z.ecc.android:kotlin-bip39-jvm` (FOSS, test scope) is fetched by Gradle
  and is not packaged in the APK.

## 3. Open the merge request

```bash
git add metadata/com.aistudio.vaultzero.secvlt.yml
git commit -m "New App: com.aistudio.vaultzero.secvlt"
git push -u origin com.aistudio.vaultzero.secvlt
```

Open a merge request from your fork branch against `fdroid/fdroiddata:master`:

- **Title:** `New App: com.aistudio.vaultzero.secvlt`
- **Label:** `New App` (if the label is available)
- Describe the app in two lines, link the source/website and mention that signing is
  F-Droid-managed.
- GitLab CI runs `fdroid lint`/build checks; fix any reported metadata/build issue and push to the
  same branch.

F-Droid's inclusion policy also expects the app author to be aware of/agree to inclusion. You are
the author, so state that in the MR.

## 4. Review and publication

- Packagers review source/licence, scan for non-free assets/dependencies, and build in the
  F-Droid build server.
- After merge, builds are published in the next cycle (typically ~24–48 h after signing).
- Watch the pipeline and MR comments; answer packager questions.
- Once live: <https://f-droid.org/packages/com.aistudio.vaultzero.secvlt/>

## 5. Future releases

The recipe uses tags + versionName/versionCode in `app/build.gradle.kts`:

1. Bump `versionCode`/`versionName`, add
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, tag `v<versionName>`.
2. Push commit and tag, publish the GitHub release.
3. F-Droid's checkupdates opens an MR for the new tag; or update the recipe's `Builds` entry
   yourself (`commit`, `CurrentVersion`, `CurrentVersionCode`).
4. Do not reuse a `versionCode`; F-Droid clients rely on it for updates.

## Maintainer details

- F-Droid metadata filename = package ID.
- Category `Security`/`System`; licence MIT; no anti-features.
- The built APK has no network permission; no anti-feature declarations are needed.
- Changelog is currently linked to `releases/v1.4.0.md`; future version bumps should update it.
- F-Droid builds cannot be compared bit-for-bit with the GitHub APK because the signing keys and
  build environment differ; reproducible builds are not required for inclusion.

## What still needs your GitLab account

I can prepare, validate and publish the upstream tag/build metadata (done), but the final
`fdroiddata` fork/push and merge request require **your GitLab credentials**. If you want me to open
the MR directly, provide a GitLab token scoped to your fork (or run `glab auth login`) and I can
perform the fork/clone/MR steps without printing the secret. Otherwise follow sections 1–3 above.
