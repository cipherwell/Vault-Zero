# VaultZero security audit kit

A repeatable MASVS 2.1 audit/pentest for the VaultZero Android vault: methodology, checklists,
scripts and report template. Start with `SKILL.md`, then `references/methodology.md`.

## Quick start

```bash
cd security-audit
./scripts/audit.sh --project .. \
  --apk ../app/build/outputs/apk/release/app-release.apk \
  --serial emulator-5554 --out results/$(date +%F) --fuzz
```

Outputs: `results/<date>/REPORT.md`, `static.json`, `crypto.json`, `secrets*.json`,
`fuzz-corpus/`, `device/` evidence.

## What each script does

| Script | Purpose |
| --- | --- |
| `static_audit.py` | Merged/source manifest inventory, exported components, permissions, build flags (R8, SDK), risky API patterns, dependency list |
| `crypto_review.py` | KDF constants vs floors, AEAD/IV/tag checks, Keystore/biometric flags, backup KDF validation leads |
| `secret_scan.py` | Regex + entropy scan of the repo and APK (dex strings), allowlist for tests/examples |
| `device_audit.sh` | `dumpsys` facts, `run-as` check, FLAG_SECURE screenshot test, logcat scan, intent probes, clipboard best effort |
| `fuzz_backup.py` | Malformed backup corpus (structure, KDF downgrade, DoS, encoding, semantics) |
| `report.py` | Merges JSON results into a markdown report skeleton |

## Ground rules

- Test devices only; never a phone with real vaults.
- QA screenshots require `-PallowScreenshots=true`; never ship that build. Verify the shipped APK
  returns a 0-byte `screencap`.
- Every finding needs evidence and a retest. Scanner hits are leads, not findings.

## Extending

- Add a check to `static_audit.py` when new components/APIs appear.
- Add fuzz classes to `fuzz_backup.py` when the backup schema changes.
- Keep `references/masvs-checklist.csv` updated; "not tested" blocks sign-off.
