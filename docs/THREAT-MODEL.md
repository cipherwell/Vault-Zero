# Threat model

Scope: the Android app and its local data (`1.1.0`). The goal is confidentiality and integrity of
vault contents against realistic adversaries. This is a concise model, not a formal audit.

## Assets

| Asset | Sensitivity | Where it lives |
| --- | --- | --- |
| Master password | critical | user's memory/input field; never stored |
| Vault master key (VMK) | critical | RAM while unlocked; wrapped in prefs |
| 12-word recovery phrase | critical | user's offline copy; encrypted under VMK at rest |
| Decrypted records | critical | RAM only while unlocked |
| Encrypted DB / prefs | high | app-private storage, ciphertext + wrapped keys |
| Backups | high | wherever the user writes them; ciphertext envelope |
| Biometric Keystore key | high | TEE/StrongBox; never exportable |
| Clipboard contents | high | system clipboard, transient, auto-cleared |
| Metadata: KDF params, fingerprint | low | prefs/backup header |

## Adversaries and mitigations

| Threat | Mitigations | Residual risk |
| --- | --- | --- |
| Thief with locked phone | OS lock + app lock; DB/prefs ciphertext; keys wrapped by KDF/biometric; no plaintext metadata | Offline guessing of a weak master password |
| Malware/app with app-process access | Auth-required Keystore key + per-use CryptoObject; no silent decrypt; serverless design limits exfiltration | Root/OS compromise can read RAM; accessibility services can read UI |
| Network attacker | App has no INTERNET permission or network code; no endpoints to MITM | None from the app (supply chain/OS not in scope) |
| Backup file theft | AES-GCM envelope; KDF-protected key; optional phrase-only mode; verified imports | Weak master password can be brute-forced offline; phrase must be protected |
| Tampered backup file | GCM authentication; verify-before-write; bounded KDF/envelope sizes; atomic writes; partial salvage is explicit | Denial of service is limited but possible; file is rejected |
| Row/ciphertext swapping in SQLite | Per-record AAD (id + type) rejects swaps; records hidden on failure | A local attacker with DB write can delete/hide data (availability), not forge readable records |
| Malicious intent from other apps | Only launcher Activity exported; input extras ignored; shortcut actions deferred until unlock; tapjacking filter | App can still be launched (no data exposure) |
| Screenshots / screen recording / recents | FLAG_SECURE; recents screenshots disabled on API 33+ | Screen can be photographed; accessibility APIs can read UI |
| Clipboard sniffing | Sensitive flag, short auto-clear, ownership-aware wipe on lock | Other apps on old Android or with user-granted permissions may read clipboard |
| Shoulder surfing / camera | Masked fields until reveal; 15 s re-mask; FLAG_SECURE | Physical observation remains possible |
| Brute-force unlock | 30 s cooldown after 5 failures; Argon2id (64 MiB, t=3) on every envelope; verifier | Unlimited attempts over time; weak passwords remain weak |
| Malicious keyboard/autofill | No app-level keyboard; user controls input method | A compromised keyboard/autofill service can capture typed data |
| Coercion | Biometric/password required; no duress mode | Out of scope for this app |
| Supply chain | Minimal, FOSS dependencies; pinned versions; no network SDKs; production signing key offline | Upstream dependency compromise is possible; Dependabot/review reduce it |
| Update substitution | Production signing identity; documented certificate/checksums; no debug-signed releases | Users who install unverified APKs are at risk |

## Out of scope

- Rooted/compromised OS, kernel/firmware backdoors, physical chip attacks.
- OS-level biometric sensor spoofing or coercion.
- Server-side attacks (the app has no server).
- Future features not present in 1.1.0 (cloud sync, sharing, browser extensions).

## Assumptions

- Device uses a screen lock; user chooses a strong, unique master password.
- The 12-word recovery phrase is stored offline, separately from backups.
- Users install production-signed builds and verify checksums when possible.
- Android Keystore/TEE/StrongBox behaves as documented; the device is not rooted.

## Verification map

See the evidence table in [SECURITY.md](../SECURITY.md) and the adversarial log in
[PENTEST-2026-11-15.md](PENTEST-2026-11-15.md). JVM/device tests cover AAD tampering, KDF
migration, brute-force cooldown, backup tamper/duplicate/partial cases, biometric key binding, and
offline posture.
