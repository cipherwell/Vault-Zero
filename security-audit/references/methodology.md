# Methodology — MASVS 2.1 audit for an offline Android vault

This is the operating manual. It answers: what do we test, in what order, with what evidence, and
when is an audit actually finished.

## 1. Standards and why they matter

- **OWASP MASVS 2.1** defines 25 controls across 8 families: STORAGE, CRYPTO, AUTH, NETWORK,
  PLATFORM, CODE, RESILIENCE, PRIVACY. It is the "what".
- **OWASP MASTG** maps each control to concrete Android/iOS tests and tools. It is the "how".
- **MASWE** is the weakness catalogue behind the controls; use it to name findings consistently.

Sources: <https://mas.owasp.org/MASVS/>, <https://mas.owasp.org/MASTG/>, <https://mas.owasp.org/MASWE/>.

Do not invent a control set. Use `masvs-checklist.csv`; every row ends Pass / Fail / N/A(reason) /
Not tested. Not tested blocks sign-off.

## 2. Scope decisions for this app

In scope:

- The Android app (source + release APK), its local storage, backup export/import, biometrics,
  clipboard/screenshot behaviour, UI attack surface, crypto and key management.
- The F-Droid/Obtainium distribution artifacts (signature, hashes, reproducibility claims).

Out of scope (state it explicitly in the report):

- Google Play services, backend APIs (there are none), the OS keystore's own implementation.
- Physical attacks requiring disassembly/chip-off, unless the threat model says otherwise.
- Third-party library internals beyond version/known-CVE checks and configuration.

## 3. Threat model first

Complete `threat-model.md` before testing. An offline vault's realistic attackers:

1. **Malicious app on the same device** — can send intents, read clipboard, draw overlays, try
   accessibility abuse, read world-readable files (none), observe screen if not `FLAG_SECURE`.
2. **Device thief / border search** — has the locked device, may brute force the password offline if
   they extract the envelope, may try cold-boot/memory attacks (out of scope), may coerce the user.
3. **Malicious backup file** — attacker supplies a crafted `.vaultzero` file to trigger parser bugs,
   KDF downgrade, path traversal, or UI spoofing.
4. **Shoulder surfer / screen recorder** — screenshots, recents preview, notifications.
5. **Supply chain** — a compromised dependency or build artifact.
6. **Insider/CI** — signing keys, release channel.

Write abuse cases per attacker and map each to tests. If a surface has no test, either add one or
record the gap.

## 4. Test order

1. **Inventory** (manifest, deps, components, file layout) — 30 min.
2. **Static review** of crypto, storage, backup, intents, UI security — the bulk.
3. **Secrets/build** scan of source and APK — fast, high yield.
4. **Dynamic** on emulator + real device: install, walk every screen, try attacks.
5. **Fuzzing**: backup parser, recovery phrase, item fields, TOTP, import filenames.
6. **Regression**: every finding gets a retest command and, where possible, a test.

## 5. Evidence standard

A finding is only valid with at least one of:

- a command + output (`adb shell ...`, `python3 ...`) pasted into the report,
- a code reference (`VaultCrypto.kt:132`) plus the reasoning chain,
- a reproducible step list (1..n) that another tester can follow,
- a screenshot/video for UI issues (remember `FLAG_SECURE` blocks app screenshots — use a QA build
  with `-PallowScreenshots=true`, and never ship that build).

Include **what was tested and passed** as well; absence of findings is a result only if coverage is
shown.

## 6. Severity and exit criteria

Use `severity.md` (CVSS 4.0 base + MASVS impact). Exit criteria for beta:

- Zero open Critical/High.
- Medium accepted in writing by the owner, with a date.
- All MASVS rows resolved.
- Audit report reviewed by someone who did not write the code (or a documented self-review
  limitation).

## 7. Re-audit triggers

Re-run the relevant phases when: crypto/KDF/envelope format changes; backup format changes; biometric
or lock flow changes; new permissions/components/deeplinks; new dependency with native code; target
SDK bump; signing/distribution change.

## 8. Tooling map

| Need | Tool | Notes |
| --- | --- | --- |
| Manifest/components | `aapt2 dump xmltree`, `apkanalyzer manifest print` | merged manifest, not source |
| Decompile | `jadx`, `apktool` | R8 renames; use mapping.txt |
| Static scan | `semgrep`, this kit's scripts | Kotlin rules + greps |
| Secrets | `gitleaks`, `scripts/secret_scan.py`, `strings` | source + APK |
| Dependencies | Gradle lockfiles, `dependency-check` | offline CVE DB is optional |
| Device | adb, `run-as` (debug), `dumpsys`, `uiautomator` | API 24+ |
| Runtime hooks | Frida, objection | rooted test device or emulator |
| Dynamic proxy | mitmproxy | only if network exists (this app has none) |
| Fuzzing | JVM harness + corpus, `scripts/fuzz_backup.py` | deterministic, no UI needed |
| Report | `scripts/report.py` | JSON → markdown |

## 9. Test case template

Every test in the report uses this shape:

```
ID:        VZ-<area>-<nnn>            (e.g. VZ-CRYPTO-003)
Control:   MASVS-CRYPTO-2
Title:     One-line statement of the property
Method:    command / code path / steps
Expected:  the secure behaviour
Observed:  what actually happened
Evidence:  output snippet or file:line
Verdict:   Pass | Fail | N/A(reason) | Not tested(blocker)
Finding:   link to finding id if Fail
```
