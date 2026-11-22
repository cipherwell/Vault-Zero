# Fuzzing the local parsers

Targets, in priority order:

1. **Backup importer** — attacker-controlled JSON, highest risk.
2. Recovery phrase parser (12 words, arbitrary Unicode).
3. Item editor fields (very long strings, control chars, RTL, emoji, zero-width).
4. TOTP secret/URI parser.
5. Any future share/file import.

## 1. Corpus design

Generate at least these classes (see `scripts/fuzz_backup.py`):

| Class | Examples |
| --- | --- |
| Structure | `{}`, `[]`, `null`, empty file, 1-byte file, truncated JSON, trailing garbage |
| Types | numbers where strings expected, `NaN`, `1e9999`, booleans, nested arrays |
| Depth | 1k nested objects/arrays (stack overflow probe) |
| Size | 10 MB string, 1M items, 100k unknown keys |
| Encoding | invalid UTF-8, lone surrogates, BOM, base64 garbage |
| Crypto header | missing/extra fields, negative salt, 0-length IV, tag 1 byte, version 999 |
| KDF downgrade | `argon2id:8192:1:1`, `pbkdf2:1000`, `argon2id:999999999:1:1`, unknown algorithm |
| Semantics | duplicate ids, huge timestamps, invalid TOTP, empty titles, 1 MB notes |
| Paths | filename `../../databases/vault.db`, absolute path, symlink target |
| AAD | valid header + modified ciphertext/tag |

## 2. Oracles

A test case **fails** if any of:

- crash (FATAL EXCEPTION), ANR, OOM, native abort;
- import returns success but count/type disagrees with the payload (silent data corruption);
- exception text or UI reveals stack traces/secrets;
- time > 10 s for a small file or memory grows without bound;
- vault state changes despite a rejected import.

A case **passes** if it is rejected with a clear, localized error and the vault is unchanged.

## 3. Harness options

1. **JVM harness (preferred)** — call the parser/decryptor directly with each file; deterministic,
   no device, CI-friendly. Put under `app/src/test/java/.../security/BackupFuzzTest.kt`.
2. **Instrumentation** — drive the real repository on device for integration-level paths.
3. **UI fuzz** — `adb shell monkey -p ... 500` for crash/ANR coverage; then inspect logcat.

## 4. Fuzz script usage

```bash
python3 security-audit/scripts/fuzz_backup.py --out /tmp/vz-fuzz --count 120
# JVM: point the test at /tmp/vz-fuzz and run
./gradlew :app:testDebugUnitTest --tests "*BackupFuzzTest*"
```

## 5. Reporting

For each failure: file name, size, class, command, stack trace line, impact (crash/DoS/data loss),
reproduce command, suggested fix. Do not paste full binary payloads; keep the seed and generator
parameters.

## 6. Regression

Add every crash seed to the permanent corpus and keep one unit test per fixed class. Re-run the full
corpus on every backup-format change.
