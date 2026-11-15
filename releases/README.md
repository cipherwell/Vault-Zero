# 📦 VaultZero — Releases & Verification Center

Compiled binaries are distributed through
[**GitHub Releases**](https://github.com/cipherwell/Vault-Zero/releases) so the
source tree stays clean for F-Droid/IzzyOnDroid. Store uploads use the same source
tree and are signed with the production upload key.

## ⬇️ Latest: `v1.1.0` (production signing)

| Asset | File | Size | SHA-256 |
| --- | --- | --- | --- |
| **APK (direct)** | [`VaultZero-v1.1.0.apk`](https://github.com/cipherwell/Vault-Zero/releases/download/v1.1.0/VaultZero-v1.1.0.apk) | 1,844,971 B | `407c23e56666764ff874c60ceb5afe57a20828c44b41bd79a55a48114b0d3d82` |
| **AAB (store upload)** | [`VaultZero-v1.1.0.aab`](https://github.com/cipherwell/Vault-Zero/releases/download/v1.1.0/VaultZero-v1.1.0.aab) | 4,004,032 B | `bbfdad31fd459347a2100e3d1eae63d37a57abb86b0351687cb7317e83411bc3` |
| **Checksums** | `VaultZero-v1.1.0.apk.sha256`, `VaultZero-v1.1.0.aab.sha256` | — | full digests in files |
| **Release notes** | [`v1.1.0.md`](v1.1.0.md) | — | — |
| **Web portal** | [`docs/index.html`](../docs/index.html) | — | GitHub Pages |
| **Privacy policy** | [`docs/privacy.html`](../docs/privacy.html) | — | GitHub Pages |

**Signing certificate (production upload key):**
`c54ed94be9f5d4f00ea7a9e8d7f1f5eb4d0eafc8f456055b6130d85b38ae831d`
(CN=Cipherwell, OU=VaultZero, 4096-bit RSA, APK Signature Scheme v2).

```bash
sha256sum -c VaultZero-v1.1.0.apk.sha256
apksigner verify --print-certs VaultZero-v1.1.0.apk
```

### ⚠️ Upgrade from the preview

`v1.0.0` was signed with a debug certificate (its key was public in the
repository). Android will not update it in place. Export a backup, uninstall the
preview, then install `v1.1.0`. Old backup files remain importable with their
master password or recovery phrase.

## Historical releases

- `v1.0.0` — public preview (debug-signed; superseded, do not use for updates).
  Notes: [`v1.0.0.md`](v1.0.0.md). The old checksum file is retained so previously
  downloaded preview files can still be verified.
