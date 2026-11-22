# 📦 VaultZero — Releases & Verification Center

Compiled binaries are distributed through
[**GitHub Releases**](https://github.com/cipherwell/Vault-Zero/releases). The source tree stays clean
for F-Droid/IzzyOnDroid; store uploads use the same source and are signed with the production key.

## ⬇️ Latest: `v1.7.18` (lock-screen reset + UI audit)

| Asset | File | Size | SHA-256 |
| --- | --- | ---: | --- |
| **APK (direct)** | [`VaultZero-v1.7.18.apk`](https://github.com/cipherwell/Vault-Zero/releases/download/v1.7.18/VaultZero-v1.7.18.apk) | 2,233,877 B | `437ae896d9a6f23637778ea7c0d443708c7ca5756196ee764b6ef73ee7e62724` |
| **Checksums** | `VaultZero-v1.7.18.apk.sha256` | — | full digest |
| **Release notes** | [`v1.7.18.md`](v1.7.18.md) | — | reset, UI audit, checksums |

**Application ID:** `com.cipherwell.vaultzero`  
**Signing certificate:** `6292a1fc630c200e7ec5342248ba7a6ae30a893fd882b2ad4188f699bbb607e7`

```bash
sha256sum -c VaultZero-v1.7.18.apk.sha256
apksigner verify --print-certs VaultZero-v1.7.18.apk
```

### Upgrading

`v1.6.0` installs over `v1.5.0` (same Cipherwell signing identity). Existing vaults and backups keep
working: PBKDF2 envelopes are transparently migrated to Argon2id on the next successful unlock.
Package migrations from the old `com.aistudio.vaultzero.secvlt` identity are documented in
[`v1.5.0.md`](v1.5.0.md).
