# Security audit — recovery phrases and encryption (2026-11-15)

**Scope:** VaultZero recovery-kit generation/validation, recovery-kit at-rest wrapping,
vault/record encryption, backup envelope, KDF, and the new first-run restore path.
**Trigger:** a generated recovery phrase contained the word "eclipse" three times.
**Verdict:** the repeated words were **expected from a weak custom design**, not a display bug.
Fixed in v1.3.0. No new critical weaknesses were found in the AES-GCM record/key/backup
encryption, but two recovery-kit weaknesses were confirmed and remediated.

## 1. Findings

| ID | Severity | Finding | Impact | Status |
| --- | --- | --- | --- | --- |
| R-1 | **High** | Recovery phrase generated from only **136 custom words**, 11 drawn **with replacement**; 12th was a deterministic checksum word. | Phrase carries only **≈78 bits** of entropy (11 × log₂136), far below the 128-bit target. Repeats occurred in **34%** of phrases (observed triple "eclipse"), reducing user confidence and enabling faster offline guessing of old backups. | **Fixed** for new phrases (BIP-39, 2048 words, 128-bit, unique words). Legacy phrases remain accepted so no old vault/backup is lost. |
| R-2 | Medium | No way to replace an existing recovery kit. | Users with a legacy weak/repeated phrase had to recreate the vault to get a stronger kit; a leaked paper kit could not be rotated. | **Fixed:** Settings → **Generate New Recovery Kit** rotates only the recovery envelope (same VMK/records/master password). Old phrase stops opening this vault; old backups retain their own old envelope. |
| R-3 | Low | Legacy checksum mapping `uint16 % 136` has a slight modulo bias. | Negligible (only legacy phrases; checksum detects transcription errors rather than adding entropy). | Legacy path retained for compatibility; new BIP-39 maps exactly 11 bits to 2048 words (no modulo bias). |
| E-1 | Info | AES-GCM, PBKDF2 and keystore usage reviewed. | No exploitable implementation defect found in this review. | Verified by tests; residual risks documented below. |

### Why repeats happened

The old generator did `repeat(11) { pick uniformly from 136 }` and appended a checksum word. This is
sampling **with replacement**:

- P(at least one repeated word in 11 draws) = `1 − 136!/(125!·136¹¹)` ≈ **34.0%**
- The 12th word was deterministic, so it could also repeat one of the first 11.
- The phrase space was ≈`136¹¹` ≈ 2⁷⁸; the checksum word adds typo detection, **not entropy**.

The old wordlist itself was duplicate-free, so the repeats were not a wordlist bug.

## 2. What changed in v1.3.0

### Recovery generation
- Uses the official **BIP-39 English 2048-word list**; 16 CSPRNG bytes (128-bit entropy) plus a
  4-bit SHA-256 checksum, encoded as 12 × 11 bits.
- Generated phrases are **valid BIP-39 mnemonics** and are rejected/regenerated if any word would
  repeat, so printed kits have 12 distinct words.
- KDF input is unchanged (`PBKDF2-HMAC-SHA256(phrase, recoverySalt, 600,000)`), so the recovery
  envelope format and all existing unlock/backup flows are unaffected.
- Server-side implementation remains small, auditable and dependency-free at runtime; the official
  vectors and the maintained Zcash **kotlin-bip39 1.0.9** library are used **test-only** as an
  independent oracle (not packaged into the APK).

### Compatibility
- Legacy 136-word phrases are still validated with their original checksum and still unwrap the
  same VMK. Backups and vaults created by v1.0.0–v1.2.0 continue to open.
- New UI copy now distinguishes "recovery phrase" (BIP-39) from legacy kits only in documentation;
  either works in the same fields.

### Rotation
- New `Settings → Generate New Recovery Kit` action re-wraps the VMK with a fresh BIP-39 phrase and
  fresh salt. It atomically updates only: recovery salt, `encryptedVmkByRecovery`,
  `encryptedRecoveryPhrase`, recovery verifier.
- Unchanged: VMK, key fingerprint, records, master password. Old backups are immutable and still
  open with the phrase under which they were exported; new backups use the new kit.

## 3. Encryption review

| Area | Implementation | Review notes |
| --- | --- | --- |
| Record AEAD | AES-256-GCM; 12-byte random IV per encryption; 128-bit tag; AAD `VaultZero_Record_v2:<id>:<type>` | Random IV from `SecureRandom`; AAD binds row identity/type; tampered/swapped rows fail closed (tests). |
| Vault key | 32 random bytes (`SecureRandom`) | Never derived from phrase/password; wrapped twice. |
| Password KEK | PBKDF2-HMAC-SHA256, 600,000 iterations, 16-byte random salt | Meets OWASP's PBKDF2 fallback recommendation; Argon2id remains a future hardening option (see residual). Verifier is HMAC-SHA256 with domain separation, not a password hash. |
| Recovery KEK | PBKDF2-HMAC-SHA256(phrase, recovery salt, 600,000) | Phrase entropy fixed above; salt independent from password salt. |
| Recovery at rest | Phrase encrypted with AES-GCM under the VMK | No plaintext phrase in prefs/DB/logs. |
| Backups | AES-GCM envelope; redacted SQLite metadata inside ciphertext; 8 MiB cap; format/KDF/salt/envelope bounds; verify-before-write; single Room transaction | Hostile-file DoS was fixed earlier; setup restore validates every record before any write. |
| Biometrics | Android Keystore AES-256-GCM, `AUTH_BIOMETRIC_STRONG`, per-use `CryptoObject`, invalidated by enrolment changes | Hardware test passed; no fallback decrypt path. |
| Network | No INTERNET/ACCESS_NETWORK_STATE permission or networking code | Release manifest checked. |
| Capture | FLAG_SECURE; recents disabled on API 33+ | Release capture returns 0 bytes. |

### Verification
- **24 official BIP-39 English vectors** (128–256 bit) cross-checked against the reference
  `kotlin-bip39` implementation; all 12-word vectors also match VaultZero encoder/validator.
- **100 random 128-bit entropies** produce identical mnemonics in VaultZero and the reference
  library; both recover the same entropy.
- **105 JVM tests / 0 failures**, including: unique-word generation, all-zero official vector,
  legacy-phrase compatibility, tampered checksum rejection, rotation/retirement of old phrase, old
  backup restore with old phrase, new backup restore with new phrase, tamper/wrong-phrase rejects.
- On-device v1.2.0 migration flow verified; rotation and new phrase generation are exercised by the
  JVM suite and the Settings UI.

## 4. Residual risks

1. **PBKDF2 is not memory-hard.** 600k iterations is OWASP's PBKDF2 fallback, but Argon2id would
   raise GPU/ASIC resistance. A KDF-versioned vault format (v4) and a vetted Android Argon2id
   library would be required; deferred rather than rushed.
   **Closed in 1.6.0 (2026-11-21):** new envelopes use Argon2id (64 MiB, t=3, p=1) via Bouncy
   Castle's RFC 9106 implementation, each envelope stores its own KDF descriptor, and PBKDF2 vaults
   migrate transparently on unlock. Backups are format 3.3 and old 3.2 files remain importable.
2. **Weak master passwords** remain the dominant offline-guessing risk; the recovery phrase is now
   128-bit but the password envelope still depends on the user's password.
3. **Legacy kits remain ~78-bit** for vaults that have not rotated; users are prompted by docs to
   generate a new kit. Old backups made with legacy kits remain ~78-bit until replaced.
4. BIP-39's 4-bit checksum catches most, not all, transcription errors; the UI reports checksum
   failures clearly and never writes on invalid input.
5. JVM strings (password/phrase/decrypted fields) cannot be reliably zeroized; rooted/compromised
   OS and accessibility/input-method malware remain outside the app's control.
6. This is a self-audit; an independent audit is still recommended before high-risk use.

## 5. References

- BIP-39 specification and official wordlist: <https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki>
- Official test vectors: <https://github.com/trezor/python-mnemonic/blob/master/vectors.json>
- Zcash `kotlin-bip39` (test-only oracle): <https://repo1.maven.org/maven2/cash/z/ecc/android/kotlin-bip39/1.0.9/>
- OWASP Password Storage Cheat Sheet: <https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html>
- NIST SP 800-63B (entropy/authenticator guidance): <https://pages.nist.gov/800-63-3/sp800-63b.html>
