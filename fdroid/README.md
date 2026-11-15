# F-Droid recipe

`com.aistudio.vaultzero.secvlt.yml` is the metadata recipe for submitting VaultZero to
[F-Droid](https://f-droid.org/). It builds the release variant from source; F-Droid signs published
APKs with its own key (so F-Droid builds are not bit-identical to GitHub/Play builds, but source is
the same).

## How it works

- `Builds` lists each version with `versionName`, `versionCode`, git `commit` (tag) and `subdir: app`.
- `gradle: - yes` tells F-Droid to build the Gradle flavor with the standard toolchain.
- `scandelete: - debug.keystore` strips the development debug keystore from the source scan. The
  repository does not ship it either; the recipe entry is defensive.
- `AutoUpdateMode: Version` + `UpdateCheckMode: Tags` means every release must have a matching
  `v<version>` git tag and recipe update.
- `CurrentVersion`/`CurrentVersionCode` must match the latest build entry.

## Submitting / updating

1. Push the release commit and tag to GitHub (`v<version>`).
2. Update the recipe: append a `Builds` entry (`versionName`, `versionCode`, `commit: v<version>`)
   and set `CurrentVersion`/`CurrentVersionCode`.
3. Fork [`fdroiddata`](https://gitlab.com/fdroid/fdroiddata), copy the recipe to
   `metadata/com.aistudio.vaultzero.secvlt.yml`, and open a merge request
   (*New App* for the first submission, *Update* afterwards).
4. F-Droid builds without the production upload keystore; the Gradle `release` signing config may
   be absent during their build. If the build requires signing, F-Droid's tooling supplies its own
   signing step; do not add fallbacks to the debug key.

## Reproduction check

```bash
git clone https://github.com/cipherwell/Vault-Zero.git
cd vault-zero-app
git checkout v1.1.0
./gradlew :app:assembleRelease   # unsigned/own key in a clean environment
```

See [docs/BUILDING.md](../docs/BUILDING.md) and [docs/RELEASING.md](../docs/RELEASING.md).
