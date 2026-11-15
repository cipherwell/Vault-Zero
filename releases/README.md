# 📦 VaultZero — Releases & Verification Center

Compiled binaries are distributed through
[**GitHub Releases**](https://github.com/cipherwell/Vault-Zero/releases) so the
source tree stays clean for F-Droid/IzzyOnDroid. Store uploads use the same source
tree and are signed with the production upload key.

## ⬇️ Latest: `v1.3.1` (F-Droid-compatible build)

| Asset | File | Size | SHA-256 |
| --- | --- | ---: | --- |
| **APK (direct)** | [`VaultZero-v1.3.1.apk`](https://github.com/cipherwell/Vault-Zero/releases/download/v1.3.1/VaultZero-v1.3.1.apk) | 1,861,355 B | `107fea47f006210b88f38b6a6813d4ed18af58cd08f488d5e9da14a817556a1c` |
| **AAB (store upload)** | [`VaultZero-v1.3.1.aab`](https://github.com/cipherwell/Vault-Zero/releases/download/v1.3.1/VaultZero-v1.3.1.aab) | 4,049,490 B | `3039ee37d62ce144330c094925f1d104d22b2d6043a27822770dab15acd98bf7` |
| **Checksums** | `VaultZero-v1.3.1.apk.sha256`, `VaultZero-v1.3.1.aab.sha256` | — | full digests in files |
| **Release notes** | [`v1.3.1.md`](v1.3.1.md) | — | F-Droid-compatible source build |
| **F-Droid recipe** | [`fdroid/`](../fdroid/README.md) | — | submission guide |

**Signing certificate (production upload key):**
`6292a1fc630c200e7ec5342248ba7a6ae30a893fd882b2ad4188f699bbb607e7`

```bash
sha256sum -c VaultZero-v1.3.1.apk.sha256
apksigner verify --print-certs VaultZero-v1.3.1.apk
```

## Historical releases

- `v1.3.0` — BIP-39 recovery hardening (superseded by distro-build fix). Notes: [`v1.3.0.md`](v1.3.0.md).
- `v1.2.0` — first-run migration restore. Notes: [`v1.2.0.md`](v1.2.0.md).
- `v1.1.0` — production signing baseline (superseded). Notes: [`v1.1.0.md`](v1.1.0.md).
- `v1.0.0` — public preview (debug-signed; superseded, do not use for updates).
  Notes: [`v1.0.0.md`](v1.0.0.md). The old checksum file is retained so previously
  downloaded preview files can still be verified.
