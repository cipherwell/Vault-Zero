# Cryptographic design

This document is the implementation reference for VaultZero `1.6.0`. It describes what the code
actually does; source of truth is `app/src/main/java/com/example/crypto/` and
`app/src/main/java/com/example/data/VaultRepository.kt`.

## Parameters

| Parameter | Value |
| --- | --- |
| Record cipher | AES-256-GCM |
| GCM IV | 12 bytes, random per encryption (`SecureRandom`) |
| GCM tag | 128 bits |
| Vault master key (VMK) | 32 random bytes (`SecureRandom`) |
| Password / recovery KDF | **Argon2id** (RFC 9106, version 1.3) |
| Argon2id parameters (current) | memory 65,536 KiB (64 MiB), time cost 3, lanes 1, 32-byte tag |
| Argon2id accepted range (stored/imported) | memory 8,192–131,072 KiB, time 1–10, lanes 1–4 |
| Legacy KDF (accepted, migrated on unlock) | PBKDF2-HMAC-SHA256, 600,000 iterations |
| PBKDF2 accepted range | 10,000–2,000,000 iterations |
| KDF descriptor | per envelope, persisted as `pbkdf2-sha256:<iterations>` or `argon2id:<memoryKiB>:<iterations>:<lanes>` |
| KDF salt | 16 random bytes, stored base64 |
| Verifier | HMAC-SHA256 over a domain-separated message, never a bare password hash |
| Recovery phrase | BIP-39 English, 12 words = 128-bit entropy + 4-bit checksum (new kits); legacy v1.x 136-word scheme accepted |
| TOTP | RFC 6238, HMAC-SHA1, 6 digits, 30-second time step |
| Backup envelope version | `3.3` (`VaultZero_Encrypted_Envelope`); `3.2`/older still imports |
| Base64 | `Base64.NO_WRAP` for IV/ciphertext/envelope concatenation |

## Key hierarchy

```mermaid
flowchart TD
  PW["Master password"] -->|Argon2id<br/>salt, 64 MiB, t=3| PKEK["Password KEK"]
  RP["12-word recovery phrase"] -->|Argon2id<br/>recovery salt, 64 MiB, t=3| RKEK["Recovery KEK"]
  PKEK -->|AES-GCM wrap| VMK["Vault Master Key (32 bytes)"]
  RKEK -->|AES-GCM wrap| VMK
  VMK -->|AES-256-GCM + AAD| REC["Records"]
  VMK -->|AES-GCM| EPH["Encrypted recovery phrase at rest"]
  VMK --> FP["Key fingerprint VZ-XXXX (derived, non-secret)"]
```

The VMK is wrapped independently by both KEKs, so either the master password or the recovery
phrase can unwrap it. Changing the master password rewrites only the password envelope; the VMK,
records and recovery envelope are untouched.

## Setup

1. Generate a 32-byte VMK and store only in memory after setup.
2. Generate a 16-byte password salt; derive the password KEK; wrap the VMK with AES-GCM.
3. Generate a 12-word **BIP-39** recovery phrase (128-bit CSPRNG entropy, unique words); derive the
   recovery KEK from the normalized phrase; wrap the VMK independently.
4. Encrypt the recovery phrase under the VMK so it is never stored in plaintext; store only its
   verifier separately.
5. Derive a key fingerprint from the VMK (public identifier shown on the lock screen; not a secret).
6. Atomically persist: password verifier, password salt, recovery salt, wrapped VMKs, encrypted
   recovery phrase, recovery verifier, fingerprint, iterations.

## Record encryption

The record plaintext is a JSON envelope (`_vz_meta_v2`) containing `id`, `type`, `title`,
`category`, `tags` and the typed `payload`. It is encrypted with the VMK using AES-GCM and the
AAD:

```
VaultZero_Record_v2:<record-id>:<record-type>
```

Stored value is `base64(iv || ciphertext || tag)`. The SQLite columns `title`/`category`/`tagsJson`
store `[ENCRYPTED]`/`Encrypted`/`[]`. Because the AAD is bound to id and type, swapping two rows'
ciphertexts makes both fail authentication; the app hides failed records rather than displaying
garbage. Legacy v1 records without AAD are still readable and are re-encrypted in v2 format on the
next save.

## Unlock and verification

- Unlock derives the password KEK from the stored salt and that envelope's KDF descriptor
  (Argon2id for new vaults, PBKDF2 for pre-1.6.0 ones), decrypts the wrapped VMK, and compares a
  domain-separated verifier before accepting the password.
- Five failed attempts initiate a 30 s lockout; the check happens before PBKDF2 to avoid abuse.
- **KDF migration (1.6.0).** After a successful password unlock, if either envelope's descriptor
  differs from the current Argon2id spec (e.g. a 100k/600k PBKDF2 vault, or a vault restored from a
  3.2-era backup), both envelopes are re-wrapped with fresh salts under the current spec. The VMK
  is never re-keyed, so every encrypted record stays valid; the rewrite is one atomic preference
  commit and is skipped entirely when the encrypted recovery phrase cannot be read.
- Legacy 100k-iteration vaults use the same migration path: PBKDF2 600k → Argon2id 64 MiB/t=3,
  with the PBKDF2 work factor only ever a transient state.
- Recovery uses the normalized 12-word phrase, validates either its BIP-39 checksum or the legacy
  v1.x checksum, derives the recovery KEK and unwraps the same VMK. The phrase can be used to set
  a new master password.
- **Recovery-kit rotation** (Settings → Generate New Recovery Kit) creates a fresh BIP-39 phrase and
  independent salt, re-wraps the same VMK, and atomically replaces only the recovery envelope. The
  master password, VMK, fingerprint and records are unchanged; old backups keep their own old
  recovery envelope.
- Legacy 136-word kits were generated by sampling 11 words with replacement from 136 words (~78-bit
  entropy, 34% repeat probability). They remain accepted for compatibility; users can rotate them.

## Biometric key

- Algorithm: AES-256-GCM in Android Keystore (StrongBox attempted; TEE fallback).
- `setUserAuthenticationRequired(true)`, `AUTH_BIOMETRIC_STRONG`, 0-second validity
  (per-operation) on API 30+; `-1` validity on older versions.
- `setInvalidatedByBiometricEnrollment(true)`, `setUnlockedDeviceRequired(true)` where supported.
- The wrapped VMK is stored only after an authenticated `ENCRYPT_MODE` cipher from
  `BiometricPrompt.CryptoObject`; unlock passes an authenticated `DECRYPT_MODE` cipher.
- There is no fallback decrypt path: no authorised cipher ⇒ no VMK. Re-enrolment invalidates the
  key and the app clears the wrapped value, requiring re-enable with the master password.

## Backups

Exports are JSON envelopes whose contents are encrypted with the VMK/KEKs; the item payloads are
the same AES-GCM records as in SQLite. Import supports the source master password or the source
recovery phrase. Format and semantics: [BACKUP-FORMAT.md](BACKUP-FORMAT.md).

## Recovery-kit entropy and encoding

| Scheme | Words | Entropy | Checksum | Status |
| --- | ---: | ---: | --- | --- |
| Legacy v1.x | 11 drawn from 136 + 1 checksum | ≈78 bits | custom SHA-256 mapped to list | accepted, rotatable |
| v1.3.0+ | 12 BIP-39 words | **128 bits** | 4-bit SHA-256 (BIP-39) | default for new kits |

New generation rejects repeated words and re-draws (conditioning is negligible). The official
BIP-39 wordlist and test vectors are used; cross-validation also checks against the maintained
`kotlin-bip39` implementation in tests only. See
[`AUDIT-2026-11-15-recovery-and-encryption.md`](AUDIT-2026-11-15-recovery-and-encryption.md).

## Password and passphrase generation

- Passwords use `SecureRandom` over selected character classes, optional ambiguous-character
  filtering, length 4–128 (UI 8–64), and report entropy estimates (not a guarantee).
- Passphrases pick words uniformly from the bundled word list (3–8 words), optional capitalisation
  and separator. No network, no dictionary API.

## TOTP

- Standard RFC 6238 HMAC-SHA1 with a 30-second step and 6 digits; secrets must decode as base32
  and meet a minimum byte length before a code is shown.
- Codes are computed on demand from decrypted secrets; they are never persisted. Copying uses the
  sensitive-clipboard path.

## What is not secret / visible in backups

Visible by design (not usable without a credential): format/version markers, KDF iteration count,
salts, public KDF parameters, key fingerprint, wrapped key envelopes, item ids/types, timestamps,
favourite flags, ciphertext size. Titles/categories/tags/secrets are *inside* the ciphertext.

## Memory handling and limitations

- Derived byte arrays and keys are cleared where practical; strings (master password input, field
  values) cannot be wiped from the JVM heap. See [SECURITY.md](../SECURITY.md#known-limitations--residual-risks).
- The `BiometricPrompt` may briefly show system UI; the app itself never receives biometric data.
- Recovery depends on either the master password or the 12-word phrase; there is no escrow.
