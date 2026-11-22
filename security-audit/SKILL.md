---
name: vaultzero-security-audit
description: Use when auditing, pentesting or signing off VaultZero (or a comparable offline Android password vault) before release. Covers MASVS 2.1 mapped static and dynamic testing, cryptography and key-management review, storage/backup analysis, on-device attack attempts, fuzzing, severity triage and the final audit report.
---

# VaultZero security audit & pentest

A repeatable, evidence-first audit for an **offline, local-only Android vault**. It is written for
VaultZero (`com.cipherwell.vaultzero`) but the method applies to any app with the same threat model:
no backend, no network permission, all secrets on the device, one master password.

## What this kit is for

- A full pre-release audit (static + dynamic + crypto + storage + privacy), mapped to OWASP MASVS 2.1.
- A pentest pass that tries to **break** the app: intents, clipboard, screenshots, backups, malformed
  input, biometric bypass, downgrade, UI overlays, process-level inspection.
- A report that a non-author can read: severity, evidence, reproduction, fix, retest status.

## Ground rules

1. **Authorization first.** Only audit apps/builds you own or are contracted to test. This kit runs
   destructive-ish checks (install/uninstall, force-stop, fuzz imports) — use a dedicated test
   device/emulator, never a production phone with real secrets.
2. **Never exfiltrate secrets.** Reports quote *synthetic* values (`TEST-PASSWORD-123`, generated
   items). Redact fingerprints/serials if the report leaves the team.
3. **Evidence over intuition.** Every finding needs a command/output, a code reference (`file:line`),
   or a reproducible step list. "Looks weak" is a lead, not a finding.
4. **Do not weaken shipped builds to test.** Screenshots need `-PallowScreenshots=true`; the shipped
   APK must keep `FLAG_SECURE`. Rebuild normally after QA and verify `adb screencap` returns 0 bytes.
5. **Two-strike rule.** If a test fails to run twice, change approach or record it as "not tested"
   with the blocker — never mark it "pass".

## How to run

```bash
cd security-audit
./scripts/audit.sh --project .. --apk ../app/build/outputs/apk/release/app-release.apk \
                   --serial emulator-5554 --out results/$(date +%F)
```

The orchestrator writes `result.json`, `static/`, `device/` evidence and `REPORT.md`. Read
`references/methodology.md` before improvising, and `references/severity.md` before rating anything.

## Phases (do not skip the order)

| # | Phase | Output |
| --- | --- | --- |
| 0 | Scope + threat model (`references/threat-model.md`) | assets, trust boundaries, attacker profiles, out-of-scope list |
| 1 | Inventory (`scripts/static_audit.py`) | components, permissions, SDK, deps, storage, entry points |
| 2 | Static review (`references/crypto-review.md`, `storage-backup.md`, `platform-ui.md`) | code-level findings with `file:line` |
| 3 | Secrets + build hardening (`scripts/secret_scan.py`, `apk_audit`) | leaked material, debuggable/backup/obfuscation state |
| 4 | Dynamic/pentest (`references/dynamic-testing.md`, `scripts/device_audit.sh`) | on-device evidence, bypass attempts |
| 5 | Fuzzing (`references/fuzzing.md`, `scripts/fuzz_backup.py`) | malformed-input robustness results |
| 6 | Triage (`references/severity.md`) | severity, exploitability, confidence |
| 7 | Report (`references/report-template.md`, `scripts/report.py`) | `REPORT.md` + findings JSON |
| 8 | Fix + retest | patched build, retest evidence, regression test |

## Minimum bar for a beta sign-off

- No Critical/High findings open. Medium findings have an owner and a dated fix or an explicit risk
  acceptance signed by the product owner.
- Every MASVS control in `references/masvs-checklist.csv` is **Pass**, **Fail (with finding)** or
  **N/A (with reason)**. "Not tested" blocks sign-off.
- On-device checks executed on at least two API levels (e.g., 24/26 and 34/36) and one OEM skin if
  available.
- Fuzz corpus (malformed backups, wrong KDF specs, oversized fields, deep nesting) produces no crash,
  ANR, or silent data loss.
- `FLAG_SECURE`, `allowBackup=false`, `debuggable=false`, no `INTERNET` permission, no secrets in the
  APK/logs/clipboard-after-timeout.

## Reference index

| File | Use |
| --- | --- |
| `references/methodology.md` | MASVS/MASTG mapping, test case template, evidence rules |
| `references/threat-model.md` | assets, boundaries, attacker profiles, STRIDE table |
| `references/attack-surface.md` | VaultZero-specific surfaces and concrete test ideas |
| `references/crypto-review.md` | Argon2id/AES-GCM/Keystore/BIP-39 review checklist |
| `references/storage-backup.md` | Room/prefs/files, backup format, DoS/path traversal |
| `references/platform-ui.md` | intents, deeplinks, clipboard, screenshots, tapjacking, overlays |
| `references/dynamic-testing.md` | adb/Frida/objection recipes and device setup |
| `references/fuzzing.md` | corpus design, oracles, harness rules |
| `references/severity.md` | CVSS 4.0 + MASVS severity, triage and false positives |
| `references/masvs-checklist.csv` | 25 MASVS 2.1 controls with status columns |
| `references/report-template.md` | report structure and tone |
| `scripts/audit.sh` | orchestrator (static + secrets + crypto + device + report) |
| `scripts/static_audit.py` | manifest, components, build flags, risky APIs, deps |
| `scripts/secret_scan.py` | regex + entropy scan of repo and APK strings |
| `scripts/crypto_review.py` | KDF/nonce/AAD/Keystore heuristics with thresholds |
| `scripts/device_audit.sh` | on-device storage, logs, clipboard, screenshots, intents |
| `scripts/fuzz_backup.py` | malformed backup corpus generator |
| `scripts/report.py` | JSON → `REPORT.md` renderer |
| `templates/finding.md`, `templates/report.md` | finding and report skeletons |

## Known-good reference points for VaultZero

- `docs/CRYPTO-SPEC.md` — envelope format, KDF spec, measured unlock timings.
- `docs/TESTING.md` — test inventory and device matrix.
- `docs/BETA.md` — distribution and beta gates.
- `releases/*.md` — per-release evidence trail.

Treat those as claims to verify, not as proof.
