# Cryptography review checklist

The app is offline; crypto correctness *is* the product. Review with `VaultCrypto.kt`,
`BackupProtection.kt`, `BiometricKeyManager.kt`, `Bip39Recovery.kt` open.

## 1. Randomness

| Check | Pass condition |
| --- | --- |
| Salt generation | `SecureRandom`, ≥ 16 bytes, unique per envelope |
| GCM nonce | `SecureRandom`, exactly 12 bytes, never reused with the same key |
| Recovery phrase entropy | 128 bits from `SecureRandom` (BIP-39 12 words) |
| No `java.util.Random` / `Math.random` near secrets | grep clean |
| No deterministic nonce from counter/IV stored alongside ciphertext | code review |

Nonce reuse with AES-GCM is catastrophic (plaintext recovery + auth key). Verify the code path that
encrypts again after a password change/rekey generates a fresh IV.

## 2. KDF

Expected: Argon2id, RFC 9106 low-memory-ish profile: m=65536 KiB (64 MiB), t=3, p=1; legacy PBKDF2
600k accepted for old envelopes.

| Check | Pass condition |
| --- | --- |
| Algorithm allowed list | only `argon2id` and `pbkdf2-sha256`, no "none"/unknown fallback |
| Argon2 parameters floor | **imported** envelopes must not accept m < 64 MiB, t < 3, p < 1 without a warning/explicit consent |
| PBKDF2 floor | ≥ 600k for new; ≥ 10k only for legacy read, never for write |
| Parameter encoding | parsed strictly, no overflow, no negative values, no floats |
| Password normalization | UTF-8, NFKD (or documented), no trimming surprises |
| KDF spec authenticated | spec is inside the AAD or otherwise integrity-protected |
| Timing | measured unlock times documented; no early-exit oracle on password prefix |

Downgrade test: craft an envelope with `argon2id:8192:1:1`, import it, and check whether it is
accepted and used for future writes. If accepted silently → finding (High if it also rewrites the
vault envelope with weak parameters).

## 3. AEAD usage

| Check | Pass condition |
| --- | --- |
| Cipher | `AES/GCM/NoPadding`, 128-bit tag |
| AAD | binds version, item id/type, KDF spec, envelope purpose |
| IV length | 12 bytes; tag appended by JCA |
| Decrypt failure | `AEADBadTagException` caught, generic error, no partial plaintext returned |
| Re-encryption | new IV + new salt per envelope; old key material zeroized where possible |
| Key length | AES-256 |

Bit-flip tests: mutate ciphertext, tag, IV, salt, AAD fields one at a time; every case must fail
authentication and leave the vault unchanged.

## 4. Keystore / biometrics

| Check | Pass condition |
| --- | --- |
| Key generation | `KeyGenParameterSpec` with `PURPOSE_ENCRYPT|DECRYPT`, `BLOCK_MODE_GCM`, `ENCRYPTION_PADDING_NONE` |
| Auth binding | `setUserAuthenticationRequired(true)`, `setInvalidatedByBiometricEnrollment(true)` |
| Prompt | `BiometricPrompt` with `CryptoObject`; no "device credential only" fallback for unlock |
| Cancellation | prompt cancel/error keeps vault locked and clears pending cipher |
| Re-enrol | key invalidated → user must re-authenticate with master password |
| Key alias/versioning | migration path from legacy alias, no plaintext fallback |

## 5. Recovery kit (BIP-39)

- 128-bit entropy, 12 words, English wordlist, checksum verified on import.
- Normalization: case, whitespace, NFKD; reject unknown words with a clear error.
- The phrase alone must be sufficient to decrypt; no device-bound component.
- Never log or display the phrase after setup without explicit user action.

## 6. Backup format

Required properties:

1. Versioned header + explicit KDF spec + salt + IV.
2. Whole payload authenticated (AAD over header).
3. Import validates version, sizes, types, KDF floors before doing expensive work.
4. No plaintext metadata beyond what is unavoidable (document it).
5. Re-import is idempotent; conflicts are reported, not silently overwritten.

## 7. Memory and process hygiene

- VMK/password not in `String` longer than needed (Kotlin `String` is immutable; document as
  accepted platform limitation) — prefer `CharArray`/`ByteArray` where practical and zeroize.
- No secrets in `Log`, analytics, crash handlers, `toString()` of data classes, or `SavedState`.
- Lock clears VMK, pending plaintext, clipboard (if policy says so), and Compose state that holds
  passwords.

## 8. Crypto test vectors

- Argon2id RFC 9106 test vector (raw KDF path).
- AES-GCM known-answer test (NIST) for encrypt/decrypt + tag failure.
- BIP-39 known vectors (valid + invalid checksum).
- Round-trip: create → export → wipe → import on a clean install.
