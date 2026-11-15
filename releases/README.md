# 📦 VaultZero — Releases & Verification Center

Compiled binaries are distributed through
[**GitHub Releases**](https://github.com/cipherwell/Vault-Zero/releases) so the
source tree stays clean for F-Droid/IzzyOnDroid. Store uploads use the same source
tree and are signed with the production upload key.

## ⬇️ Latest: `v1.3.0` (hardened recovery kits)

| Asset | File | Size | SHA-256 |
| --- | --- | ---: | --- |
| **APK (direct)** | [`VaultZero-v1.3.0.apk`](https://github.com/cipherwell/Vault-Zero/releases/download/v1.3.0/VaultZero-v1.3.0.apk) | 1,861,359 B | `223d9df58cc061d5463e5c7550744beb1c09b8e828a23027a0726e1d4d73bd6d` |
| **AAB (store upload)** | [`VaultZero-v1.3.0.aab`](https://github.com/cipherwell/Vault-Zero/releases/download/v1.3.0/VaultZero-v1.3.0.aab) | 4,049,538 B | `3b0cb65b619f614d34554f4cf9c9470866fa31425c671c92d3f0b76ccfe63591` |
| **Checksums** | `VaultZero-v1.3.0.apk.sha256`, `VaultZero-v1.3.0.aab.sha256` | — | full digests in files |
| **Release notes** | [`v1.3.0.md`](v1.3.0.md) | — | BIP-39 recovery hardening |
| **Security audit** | [`docs/AUDIT-2026-11-15-recovery-and-encryption.md`](../docs/AUDIT-2026-11-15-recovery-and-encryption.md) | — | findings and verification |

**Signing certificate (production upload key):**
`c54ed94be9f5d4f00ea7a9e8d7f1f5eb4d0eafc8f456055b6130d85b38ae831d`

```bash
sha256sum -c VaultZero-v1.3.0.apk.sha256
apksigner verify --print-certs VaultZero-v1.3.0.apk
```

## Historical releases

- `v1.2.0` — first-run migration restore (superseded by recovery hardening). Notes: [`v1.2.0.md`](v1.2.0.md).
- `v1.1.0` — production signing baseline (superseded). Notes: [`v1.1.0.md`](v1.1.0.md).
- `v1.0.0` — public preview (debug-signed; superseded, do not use for updates).
  Notes: [`v1.0.0.md`](v1.0.0.md). The old checksum file is retained so previously
  downloaded preview files can still be verified.
