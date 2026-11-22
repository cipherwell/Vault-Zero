# 📦 VaultZero — Releases & Verification Center

Compiled binaries are distributed through
[**GitHub Releases**](https://github.com/cipherwell/Vault-Zero/releases). The source tree stays clean
for F-Droid/IzzyOnDroid; store uploads use the same source and are signed with the production key.

## ⬇️ Latest: `v1.7.6` (living key icon)

| Asset | File | Size | SHA-256 |
| --- | --- | ---: | --- |
| **APK (direct)** | [`VaultZero-v1.7.6.apk`](https://github.com/cipherwell/Vault-Zero/releases/download/v1.7.6/VaultZero-v1.7.6.apk) | 2,176,577 B | `f3a5d8fe79c1eb97e0449ddc5526da9eb2b07c4628344d00a65a6f5ec93521a1` |
| **Checksums** | `VaultZero-v1.7.6.apk.sha256` | — | full digest |
| **Release notes** | [`v1.7.6.md`](v1.7.6.md) | — | living key icon, checksums |

**Application ID:** `com.cipherwell.vaultzero`  
**Signing certificate:** `6292a1fc630c200e7ec5342248ba7a6ae30a893fd882b2ad4188f699bbb607e7`

```bash
sha256sum -c VaultZero-v1.7.6.apk.sha256
apksigner verify --print-certs VaultZero-v1.7.6.apk
```

### Upgrading

`v1.6.0` installs over `v1.5.0` (same Cipherwell signing identity). Existing vaults and backups keep
working: PBKDF2 envelopes are transparently migrated to Argon2id on the next successful unlock.
Package migrations from the old `com.aistudio.vaultzero.secvlt` identity are documented in
[`v1.5.0.md`](v1.5.0.md).
