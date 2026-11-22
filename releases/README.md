# 📦 VaultZero — Releases & Verification Center

Compiled binaries are distributed through
[**GitHub Releases**](https://github.com/cipherwell/Vault-Zero/releases). The source tree stays clean
for F-Droid/IzzyOnDroid; store uploads use the same source and are signed with the production key.

## ⬇️ Latest: `v1.7.14` (bilingual user guide)

| Asset | File | Size | SHA-256 |
| --- | --- | ---: | --- |
| **APK (direct)** | [`VaultZero-v1.7.14.apk`](https://github.com/cipherwell/Vault-Zero/releases/download/v1.7.14/VaultZero-v1.7.14.apk) | 2,207,141 B | `0cd96d7fc1372a91d1805f7e7caac2afc85d5b07dcb698f50f71fb1f4f51b166` |
| **Checksums** | `VaultZero-v1.7.14.apk.sha256` | — | full digest |
| **Release notes** | [`v1.7.14.md`](v1.7.14.md) | — | bilingual guide, checksums |

**Application ID:** `com.cipherwell.vaultzero`  
**Signing certificate:** `6292a1fc630c200e7ec5342248ba7a6ae30a893fd882b2ad4188f699bbb607e7`

```bash
sha256sum -c VaultZero-v1.7.14.apk.sha256
apksigner verify --print-certs VaultZero-v1.7.14.apk
```

### Upgrading

`v1.6.0` installs over `v1.5.0` (same Cipherwell signing identity). Existing vaults and backups keep
working: PBKDF2 envelopes are transparently migrated to Argon2id on the next successful unlock.
Package migrations from the old `com.aistudio.vaultzero.secvlt` identity are documented in
[`v1.5.0.md`](v1.5.0.md).
