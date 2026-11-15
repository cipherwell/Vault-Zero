# 📦 VaultZero — Releases & Verification Center

Compiled binaries are distributed through
[**GitHub Releases**](https://github.com/cipherwell/Vault-Zero/releases). The source tree stays clean
for F-Droid/IzzyOnDroid; store uploads use the same source and are signed with the production key.

## ⬇️ Latest: `v1.5.0` (Cipherwell package identity)

| Asset | File | Size | SHA-256 |
| --- | --- | ---: | --- |
| **APK (direct)** | [`VaultZero-v1.5.0.apk`](https://github.com/cipherwell/Vault-Zero/releases/download/v1.5.0/VaultZero-v1.5.0.apk) | 1,861,347 B | `bbd41378c8404558db1810a16b668dfdfa49bd2a7cabe7a0e28dfdbc75cd753c` |
| **AAB (store upload)** | [`VaultZero-v1.5.0.aab`](https://github.com/cipherwell/Vault-Zero/releases/download/v1.5.0/VaultZero-v1.5.0.aab) | 4,049,447 B | `12e902c04f0e8fdcea4b11e8a54655a80bac99185e94e768f7aa33b833e1cecf` |
| **Checksums** | `VaultZero-v1.5.0.apk.sha256`, `VaultZero-v1.5.0.aab.sha256` | — | full digests |
| **Release notes** | [`v1.5.0.md`](v1.5.0.md) | — | package migration & checksums |

**Application ID:** `com.cipherwell.vaultzero`  
**Signing certificate:** `6292a1fc630c200e7ec5342248ba7a6ae30a893fd882b2ad4188f699bbb607e7`

```bash
sha256sum -c VaultZero-v1.5.0.apk.sha256
apksigner verify --print-certs VaultZero-v1.5.0.apk
```

### Migration

The previous package was `com.aistudio.vaultzero.secvlt`. Export an encrypted backup + recovery
phrase, install v1.5.0, restore on first run, verify, then uninstall the old package. The new package
uses the same Cipherwell signing identity, so future updates are normal.
