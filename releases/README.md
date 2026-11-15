# 📦 VaultZero — Releases & Verification Center

Compiled binaries are distributed through
[**GitHub Releases**](https://github.com/cipherwell/Vault-Zero/releases). The source tree stays clean
for F-Droid/IzzyOnDroid; store uploads use the same source and are signed with the production key.

## ⬇️ Latest: `v1.4.0` (Cipherwell identity)

| Asset | File | Size | SHA-256 |
| --- | --- | ---: | --- |
| **APK (direct)** | [`VaultZero-v1.4.0.apk`](https://github.com/cipherwell/Vault-Zero/releases/download/v1.4.0/VaultZero-v1.4.0.apk) | 1,861,359 B | `d29347dbf90dc771b5451d27f135f75b887d38152b6f953709536b50ee2323ef` |
| **AAB (store upload)** | [`VaultZero-v1.4.0.aab`](https://github.com/cipherwell/Vault-Zero/releases/download/v1.4.0/VaultZero-v1.4.0.aab) | 4,049,393 B | `7ba6f53f0200fd1d78dd56c31546f7752e2d16b6fa32d74c5ebc7ba37cd5c0f7` |
| **Checksums** | `VaultZero-v1.4.0.apk.sha256`, `VaultZero-v1.4.0.aab.sha256` | — | full digests |
| **Release notes** | [`v1.4.0.md`](v1.4.0.md) | — | identity, checksums, upgrade |

**Signing certificate (new Cipherwell upload key):**
`6292a1fc630c200e7ec5342248ba7a6ae30a893fd882b2ad4188f699bbb607e7`

```bash
sha256sum -c VaultZero-v1.4.0.apk.sha256
apksigner verify --print-certs VaultZero-v1.4.0.apk
```

### ⚠️ Upgrade from earlier releases

Earlier builds were signed by a different key. Android cannot install v1.4.0 over them: export an
encrypted backup + recovery phrase, uninstall, install v1.4.0, then restore. Old published artifacts
and release notes were intentionally removed during the identity migration.
