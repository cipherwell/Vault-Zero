# 📦 VaultZero — Releases & Verification Center

Compiled binaries are distributed through
[**GitHub Releases**](https://github.com/cipherwell/Vault-Zero/releases) so the
source tree stays clean for F-Droid/IzzyOnDroid. Store uploads use the same source
tree and are signed with the production upload key.

## ⬇️ Latest: `v1.2.0` (production signing)

| Asset | File | Size | SHA-256 |
| --- | --- | ---: | --- |
| **APK (direct)** | [`VaultZero-v1.2.0.apk`](https://github.com/cipherwell/Vault-Zero/releases/download/v1.2.0/VaultZero-v1.2.0.apk) | 1,861,355 B | `1ea83362c53d54df10272690d7e954e2c621c712c79f6b2022b7ad27b90f15e1` |
| **AAB (store upload)** | [`VaultZero-v1.2.0.aab`](https://github.com/cipherwell/Vault-Zero/releases/download/v1.2.0/VaultZero-v1.2.0.aab) | 4,033,502 B | `cccd472cd14d40c734b5e1c11b0e584f544404bec80c450ca7d90e6a10d83601` |
| **Checksums** | `VaultZero-v1.2.0.apk.sha256`, `VaultZero-v1.2.0.aab.sha256` | — | full digests in files |
| **Release notes** | [`v1.2.0.md`](v1.2.0.md) | — | first-run migration restore |
| **Web portal** | [`docs/index.html`](../docs/index.html) | — | GitHub Pages |
| **Privacy policy** | [`docs/privacy.html`](../docs/privacy.html) | — | GitHub Pages |

**Signing certificate (production upload key):**
`6292a1fc630c200e7ec5342248ba7a6ae30a893fd882b2ad4188f699bbb607e7`
(CN=Cipherwell, OU=VaultZero, 4096-bit RSA, APK Signature Scheme v2).

```bash
sha256sum -c VaultZero-v1.2.0.apk.sha256
apksigner verify --print-certs VaultZero-v1.2.0.apk
```

### ⚠️ Upgrade from the preview

`v1.0.0` was signed with a debug certificate (its key was public in the repository). Android will
not update it in place. Export a backup, uninstall the preview, then install `v1.2.0` (or restore
it from the first-run screen with the recovery phrase). Old backup files remain restorable.

## Historical releases

- `v1.1.0` — production signing baseline (superseded). Notes: [`v1.1.0.md`](v1.1.0.md).
- `v1.0.0` — public preview (debug-signed; superseded, do not use for updates).
  Notes: [`v1.0.0.md`](v1.0.0.md). The old checksum file is retained so previously
  downloaded preview files can still be verified.
